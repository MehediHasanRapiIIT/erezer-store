package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.OrderEditRequestDTO;
import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.OrderItem;
import kn.org.deliverybackend.entity.OrderNote;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.event.StockUpdateEvent;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.OrderItemRepository;
import kn.org.deliverybackend.repository.OrderNoteRepository;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.repository.VariantRepository;
import kn.org.deliverybackend.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * An admin correcting an order after it was placed: the customer's details,
 * the items and their quantities, and the prices.
 *
 * <p><b>Stock.</b> An order holds the stock of its items from the moment it is
 * placed, so every change to the items moves stock the same way: a larger
 * quantity or an added item takes it (and is refused when there is not enough),
 * a smaller quantity or a removed item gives it back. A cancelled order holds
 * no stock (cancelling returned it), so editing one moves none. Made-to-order
 * lines never held any.
 *
 * <p><b>Money.</b> The subtotal is the lines added up. The total is that, less
 * the discount, plus delivery, plus whatever else the order's total already
 * carried (the surcharge of a made-to-order line, tax): that last part is read
 * off the order as it stands rather than recomputed, so an edit never changes
 * money it was not asked to change.
 *
 * <p><b>Record.</b> Every edit leaves an internal note on the order saying who
 * changed what, from what to what. The customer is not emailed; "Send invoice"
 * does that when the admin wants it.
 *
 * <p>Prices (a line's price, delivery, discount) need their own permission,
 * passed in as {@code mayReprice}.
 */
@Service
@RequiredArgsConstructor
public class OrderEditService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_QUANTITY = 999;
    private static final int MAX_LINES = 50;
    private static final BigDecimal MAX_MONEY = new BigDecimal("10000000");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderNoteRepository noteRepository;
    private final VariantRepository variantRepository;
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;

    /** Applies the edit and returns what changed, one line each; empty when nothing did. */
    @Transactional
    public List<String> edit(UUID orderId, OrderEditRequestDTO request, boolean mayReprice, String editor) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> !Boolean.TRUE.equals(o.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (request == null) throw new InvalidRequestException("Nothing to change.");

        List<String> changes = new ArrayList<>();
        List<OrderItem> lines = new ArrayList<>(orderItemRepository.findByOrderId(orderId));

        // What the total carries besides goods, discount and delivery, as the order stands now.
        BigDecimal oldSubtotal = order.getSubtotalAmount() != null ? order.getSubtotalAmount() : subtotalOf(lines);
        BigDecimal oldDiscount = nz(order.getDiscountAmount());
        BigDecimal oldShipping = shippingOf(order);
        BigDecimal other = order.getTotalAmount() == null ? BigDecimal.ZERO
                : order.getTotalAmount().subtract(oldSubtotal.subtract(oldDiscount).add(oldShipping));
        if (other.abs().compareTo(new BigDecimal("0.01")) < 0) other = BigDecimal.ZERO;

        editCustomer(order, request, changes);
        if (request.getItems() != null) {
            other = other.subtract(editItems(order, lines, request.getItems(), mayReprice, changes));
        }

        BigDecimal shipping = oldShipping;
        if (request.getShippingFee() != null && money(request.getShippingFee()).compareTo(money(oldShipping)) != 0) {
            requireReprice(mayReprice);
            shipping = checkedMoney(request.getShippingFee(), "The delivery fee");
            changes.add("Delivery fee: " + taka(oldShipping) + " → " + taka(shipping));
        }
        BigDecimal subtotal = subtotalOf(lines);
        BigDecimal discount = oldDiscount;
        if (request.getDiscountAmount() != null && money(request.getDiscountAmount()).compareTo(money(oldDiscount)) != 0) {
            requireReprice(mayReprice);
            discount = checkedMoney(request.getDiscountAmount(), "The discount");
            changes.add("Discount: " + taka(oldDiscount) + " → " + taka(discount));
        }
        if (discount.compareTo(subtotal) > 0) {
            if (request.getDiscountAmount() != null && money(request.getDiscountAmount()).compareTo(money(oldDiscount)) != 0) {
                throw new InvalidRequestException("The discount (" + taka(discount) + ") is more than the items cost (" + taka(subtotal) + ").");
            }
            // The items now cost less than a discount the order already had: it cannot take off more than they cost.
            changes.add("Discount: " + taka(discount) + " → " + taka(subtotal) + " (it cannot be more than the items cost)");
            discount = subtotal;
        }

        BigDecimal total = subtotal.subtract(discount).add(shipping).add(other.max(BigDecimal.ZERO));
        if (total.signum() < 0) total = BigDecimal.ZERO;
        BigDecimal oldTotal = nz(order.getTotalAmount());

        order.setSubtotalAmount(money(subtotal));
        order.setDiscountAmount(money(discount));
        order.setShippingFee(money(shipping));
        order.setDeliveryCharge(shipping.doubleValue());
        order.setTotalAmount(money(total));

        if (changes.isEmpty()) return changes;
        if (money(total).compareTo(money(oldTotal)) != 0) {
            changes.add("Order total: " + taka(oldTotal) + " → " + taka(total));
        }
        orderRepository.save(order);

        OrderNote note = new OrderNote();
        note.setOrderId(orderId);
        String who = editor == null || editor.isBlank() ? "admin" : editor.trim();
        String body = "Order edited by " + who + ":\n• " + String.join("\n• ", changes);
        note.setBody(body.length() > 3900 ? body.substring(0, 3900) + "…" : body);
        note.setAuthor(who.length() > 200 ? who.substring(0, 200) : who);
        noteRepository.save(note);
        return changes;
    }

    // ── the customer's details ────────────────────────────────────────────────

    private void editCustomer(Order order, OrderEditRequestDTO r, List<String> changes) {
        if (r.getCustomerName() != null) {
            String name = r.getCustomerName().trim();
            if (name.isEmpty()) throw new InvalidRequestException("The customer's name cannot be empty.");
            if (name.length() > 255) throw new InvalidRequestException("The customer's name is too long.");
            if (!name.equals(order.getCustomerName())) {
                changes.add("Customer name: " + shown(order.getCustomerName()) + " → " + name);
                order.setCustomerName(name);
            }
        }
        if (r.getCustomerPhone() != null) {
            String phone = r.getCustomerPhone().trim();
            if (phone.isEmpty()) throw new InvalidRequestException("The phone number cannot be empty: the courier needs it.");
            if (phone.length() > 40 || !phone.matches("[0-9+()\\- ]{6,40}")) {
                throw new InvalidRequestException("That does not look like a phone number.");
            }
            if (!phone.equals(order.getCustomerPhone())) {
                changes.add("Phone: " + shown(order.getCustomerPhone()) + " → " + phone);
                order.setCustomerPhone(phone);
            }
        }
        if (r.getCustomerEmail() != null) {
            String email = r.getCustomerEmail().trim();
            if (!email.isEmpty() && (email.length() > 255 || !EMAIL.matcher(email).matches())) {
                throw new InvalidRequestException("That does not look like an email address.");
            }
            String now = email.isEmpty() ? null : email;
            if (!Objects.equals(now, blankToNull(order.getCustomerEmail()))) {
                changes.add("Email: " + shown(order.getCustomerEmail()) + " → " + shown(now));
                order.setCustomerEmail(now);
            }
        }
        if (r.getDeliveryAddress() != null) {
            String address = r.getDeliveryAddress().trim();
            if (address.isEmpty()) throw new InvalidRequestException("The delivery address cannot be empty.");
            if (address.length() > 255) throw new InvalidRequestException("The delivery address is too long (255 letters at most).");
            if (!address.equals(order.getDeliveryAddress())) {
                changes.add("Delivery address: " + shown(order.getDeliveryAddress()) + " → " + address);
                order.setDeliveryAddress(address);
            }
        }
    }

    // ── the items ─────────────────────────────────────────────────────────────

    /**
     * Makes the order's lines what the request says they are, moving stock as
     * it goes. {@code lines} is updated in place. Returns the surcharge of any
     * made-to-order line that was removed, which the total must lose too.
     */
    private BigDecimal editItems(Order order, List<OrderItem> lines, List<OrderEditRequestDTO.Line> wanted,
                                 boolean mayReprice, List<String> changes) {
        if (wanted.isEmpty()) {
            throw new InvalidRequestException("An order needs at least one item. To drop the whole order, cancel it instead.");
        }
        if (wanted.size() > MAX_LINES) throw new InvalidRequestException("An order can have at most " + MAX_LINES + " items.");
        boolean holdsStock = !"CANCELLED".equalsIgnoreCase(order.getOrderStatus());

        Map<UUID, OrderItem> byId = new LinkedHashMap<>();
        for (OrderItem line : lines) byId.put(line.getId(), line);
        Set<UUID> kept = new HashSet<>();
        List<OrderItem> added = new ArrayList<>();

        for (OrderEditRequestDTO.Line want : wanted) {
            int quantity = want.getQuantity() == null ? 0 : want.getQuantity();
            if (quantity < 1 || quantity > MAX_QUANTITY) {
                throw new InvalidRequestException("A quantity has to be between 1 and " + MAX_QUANTITY + ". To take an item off the order, remove it.");
            }
            if (want.getId() != null) {
                OrderItem line = byId.get(want.getId());
                if (line == null) throw new InvalidRequestException("An item in the list is not on this order any more. Reload the page and try again.");
                if (!kept.add(line.getId())) throw new InvalidRequestException("The same item is in the list twice.");
                changeLine(line, quantity, want.getUnitPrice(), holdsStock, mayReprice, changes);
            } else {
                added.add(newLine(order, lines, added, want, quantity, holdsStock, mayReprice, changes));
            }
        }

        BigDecimal surchargeRemoved = BigDecimal.ZERO;
        for (OrderItem line : new ArrayList<>(lines)) {
            if (kept.contains(line.getId())) continue;
            if (holdsStock && !isCustom(line)) give(line.getProductId(), line.getVariantId(), nz(line.getQuantity()));
            surchargeRemoved = surchargeRemoved.add(nz(line.getCustomSurcharge()));
            changes.add("Removed: " + describe(line) + " × " + nz(line.getQuantity()));
            orderItemRepository.delete(line);
            lines.remove(line);
        }
        lines.addAll(added);
        return surchargeRemoved;
    }

    private void changeLine(OrderItem line, int quantity, BigDecimal unitPrice, boolean holdsStock, boolean mayReprice, List<String> changes) {
        boolean changed = false;
        int had = nz(line.getQuantity());
        if (quantity != had) {
            if (holdsStock && !isCustom(line)) {
                if (quantity > had) take(line.getProductId(), line.getVariantId(), quantity - had, describe(line));
                else give(line.getProductId(), line.getVariantId(), had - quantity);
            }
            changes.add(describe(line) + ": quantity " + had + " → " + quantity);
            line.setQuantity(quantity);
            changed = true;
        }
        if (unitPrice != null && money(unitPrice).compareTo(money(nz(line.getPriceAtOrder()))) != 0) {
            requireReprice(mayReprice);
            BigDecimal price = checkedMoney(unitPrice, "A price");
            changes.add(describe(line) + ": price " + taka(nz(line.getPriceAtOrder())) + " → " + taka(price));
            line.setPriceAtOrder(price);
            changed = true;
        }
        if (changed) orderItemRepository.save(line);
    }

    private OrderItem newLine(Order order, List<OrderItem> lines, List<OrderItem> added, OrderEditRequestDTO.Line want,
                              int quantity, boolean holdsStock, boolean mayReprice, List<String> changes) {
        if (want.getProductId() == null) throw new InvalidRequestException("Choose the product to add.");
        Product product;
        try {
            product = inventoryService.lockAndGetProduct(want.getProductId());
        } catch (RuntimeException e) {
            throw new InvalidRequestException("That product no longer exists.");
        }
        if (Boolean.TRUE.equals(product.getDeleted())) throw new InvalidRequestException("That product no longer exists.");

        List<Variant> sizes = variantRepository.findByProductId(product.getId()).stream()
                .filter(v -> !Boolean.TRUE.equals(v.getDeleted())).toList();
        Variant variant = null;
        if (want.getVariantId() != null) {
            variant = sizes.stream().filter(v -> v.getId().equals(want.getVariantId())).findFirst()
                    .orElseThrow(() -> new InvalidRequestException("That size or colour of " + product.getName() + " no longer exists."));
        } else if (!sizes.isEmpty()) {
            throw new InvalidRequestException("Choose which size or colour of " + product.getName() + " to add.");
        }
        Long variantId = variant == null ? null : variant.getId();
        for (OrderItem line : lines) {
            if (!isCustom(line) && product.getId().equals(line.getProductId()) && Objects.equals(variantId, line.getVariantId())) {
                throw new InvalidRequestException(describe(line) + " is already on this order. Change its quantity instead.");
            }
        }
        for (OrderItem line : added) {
            if (product.getId().equals(line.getProductId()) && Objects.equals(variantId, line.getVariantId())) {
                throw new InvalidRequestException(describe(line) + " is in the list twice.");
            }
        }

        BigDecimal shopPrice = PricingSupport.effectiveUnitPrice(product, variant);
        BigDecimal price = shopPrice;
        if (want.getUnitPrice() != null && money(want.getUnitPrice()).compareTo(money(shopPrice)) != 0) {
            requireReprice(mayReprice);
            price = checkedMoney(want.getUnitPrice(), "A price");
        }

        OrderItem line = new OrderItem();
        line.setOrderId(order.getId());
        line.setProductId(product.getId());
        line.setProductName(product.getName());
        line.setProductCode(product.getProductCode());
        line.setQuantity(quantity);
        line.setPriceAtOrder(money(price));
        if (variant != null) {
            String chosen = ProductOptions.variantName(ProductOptions.parse(product.getOptionsJson()),
                    variant.getOptionKey(), variant.getFit(), variant.getSize());
            line.setVariantId(variant.getId());
            line.setVariantName(chosen);
            line.setVariantSize(chosen);
        }
        if (holdsStock) take(product.getId(), variantId, quantity, describe(line));
        changes.add("Added: " + describe(line) + " × " + quantity + " at " + taka(price));
        return orderItemRepository.save(line);
    }

    // ── stock ─────────────────────────────────────────────────────────────────

    /** Takes units for the order; refused, with what is left, when there are not enough. */
    private void take(Long productId, Long variantId, int quantity, String what) {
        if (quantity <= 0) return;
        if (variantId != null) {
            Variant variant = variantRepository.findById(variantId).filter(v -> !Boolean.TRUE.equals(v.getDeleted()))
                    .orElseThrow(() -> new InvalidRequestException(what + " is no longer sold in that size or colour, so more cannot be added."));
            int available = variant.getStockQuantity() != null ? variant.getStockQuantity() : 0;
            if (available < quantity) throw notEnough(what, quantity, available);
            variant.setStockQuantity(available - quantity);
            variantRepository.saveAndFlush(variant);
            inventoryService.followSizes(variant.getProductId());
            return;
        }
        if (productId == null) return;
        Product product;
        try {
            product = inventoryService.lockAndGetProduct(productId);
        } catch (RuntimeException e) {
            throw new InvalidRequestException(what + " no longer exists, so more cannot be added.");
        }
        int available = inventoryService.getAvailableStock(productId);
        if (available < quantity) throw notEnough(what, quantity, available);
        inventoryService.decrementStock(product, quantity);
        publish(product);
    }

    /** Gives units back. A product or size that is gone has nowhere to take them; that is not an error. */
    private void give(Long productId, Long variantId, int quantity) {
        if (quantity <= 0) return;
        if (variantId != null) {
            variantRepository.findById(variantId).ifPresent(variant -> {
                int current = variant.getStockQuantity() != null ? variant.getStockQuantity() : 0;
                variant.setStockQuantity(current + quantity);
                variantRepository.saveAndFlush(variant);
                inventoryService.followSizes(variant.getProductId());
            });
            return;
        }
        if (productId == null) return;
        Product product;
        try {
            product = inventoryService.lockAndGetProduct(productId);
        } catch (RuntimeException e) {
            return;
        }
        inventoryService.incrementStock(product, quantity);
        publish(product);
    }

    private void publish(Product product) {
        eventPublisher.publishEvent(new StockUpdateEvent(this, product.getId(),
                inventoryService.getAvailableStock(product.getId()), inventoryService.computeStatus(product)));
    }

    private static InvalidRequestException notEnough(String what, int wanted, int available) {
        return new InvalidRequestException("Not enough stock for " + what + ": " + wanted + " more needed, "
                + (available == 0 ? "none left." : available + " left."));
    }

    // ── small things ──────────────────────────────────────────────────────────

    private static void requireReprice(boolean mayReprice) {
        if (!mayReprice) {
            throw new InvalidRequestException("Changing prices on an order needs the “Change prices on an order” permission.");
        }
    }

    private static boolean isCustom(OrderItem line) {
        return line.getCustomMeasurements() != null && !line.getCustomMeasurements().isBlank();
    }

    private static String describe(OrderItem line) {
        String name = line.getProductName() != null && !line.getProductName().isBlank() ? line.getProductName()
                : line.getProductCode() != null ? line.getProductCode() : "Item";
        String chosen = line.getVariantName() == null ? "" : line.getVariantName().trim();
        return chosen.isEmpty() ? name : name + " (" + chosen + ")";
    }

    private static BigDecimal subtotalOf(List<OrderItem> lines) {
        BigDecimal sum = BigDecimal.ZERO;
        for (OrderItem line : lines) {
            sum = sum.add(nz(line.getPriceAtOrder()).multiply(BigDecimal.valueOf(nz(line.getQuantity()))));
        }
        return sum;
    }

    private static BigDecimal shippingOf(Order order) {
        if (order.getShippingFee() != null) return order.getShippingFee();
        return order.getDeliveryCharge() != null ? BigDecimal.valueOf(order.getDeliveryCharge()) : BigDecimal.ZERO;
    }

    private static BigDecimal checkedMoney(BigDecimal value, String what) {
        if (value.signum() < 0) throw new InvalidRequestException(what + " cannot be less than 0.");
        if (value.compareTo(MAX_MONEY) > 0) throw new InvalidRequestException(what + " is too large.");
        return money(value);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static String taka(BigDecimal value) {
        return "৳" + money(value).stripTrailingZeros().toPlainString();
    }

    private static String shown(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
