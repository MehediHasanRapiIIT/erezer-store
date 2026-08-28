package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.OrderDTO;
import kn.org.deliverybackend.dto.OrderItemDTO;
import kn.org.deliverybackend.dto.order.CancelOrderRequestDTO;
import kn.org.deliverybackend.dto.order.GuestOrderRequestDTO;
import kn.org.deliverybackend.dto.order.UpdateOrderContactRequestDTO;
import kn.org.deliverybackend.dto.order.OrderStatusHistoryDTO;
import kn.org.deliverybackend.dto.order.OrderTrackingDTO;
import kn.org.deliverybackend.dto.request.order.OrderItemRequestDTO;
import kn.org.deliverybackend.dto.request.order.PlaceOrderRequestDTO;
import kn.org.deliverybackend.entity.Coupon;
import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.OrderItem;
import kn.org.deliverybackend.entity.OrderStatusHistory;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.entity.ShippingZone;
import kn.org.deliverybackend.enumeration.CouponDiscountType;
import kn.org.deliverybackend.enumeration.OrderStatus;
import kn.org.deliverybackend.event.OrderPlacedEvent;
import kn.org.deliverybackend.event.OrderStatusChangedEvent;
import kn.org.deliverybackend.event.StockUpdateEvent;
import kn.org.deliverybackend.exception.EmailNotVerifiedException;
import kn.org.deliverybackend.exception.InsufficientStockException;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.CartRepository;
import kn.org.deliverybackend.repository.OrderItemRepository;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.repository.OrderStatusHistoryRepository;
import kn.org.deliverybackend.repository.ShippingZoneRepository;
import kn.org.deliverybackend.repository.UsersRepository;
import kn.org.deliverybackend.repository.VariantRepository;
import kn.org.deliverybackend.service.BundleService;
import kn.org.deliverybackend.service.CouponService;
import kn.org.deliverybackend.service.InventoryService;
import kn.org.deliverybackend.service.OrderService;
import kn.org.deliverybackend.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final UsersRepository usersRepository;
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;
    private final CartRepository cartRepository;

    // Phase 4: coupon + shipping pricing
    private final CouponService couponService;
    private final ShippingService shippingService;
    private final ShippingZoneRepository shippingZoneRepository;
    private final VariantRepository variantRepository;

    // Phase 9: automatic product/category/global discounts
    private final DiscountEngine discountEngine;
    // Bundle offers ("Buy X Get Y") — server-authoritative fixed pricing.
    private final BundleService bundleService;

    @org.springframework.beans.factory.annotation.Value("${app.orders.cancellation-window-minutes:60}")
    private long cancellationWindowMinutes;

    // ── Authenticated checkout ─────────────────────────────────────────────────

    @Override
    @Transactional
    public OrderDTO placeOrder(PlaceOrderRequestDTO request) {
        // A signed-in customer must have a verified email before they can order.
        requireVerifiedEmail(request.getClientId());

        // Reserve inventory and compute the raw subtotal (no shipping yet).
        BigDecimal subtotal = computeAndReserveStock(request.getItems(), null);
        BigDecimal autoDiscount = computeAutoDiscount(request.getItems());
        BigDecimal customSurcharge = computeCustomSurcharge(request.getItems());
        BigDecimal bundleDiscount = request.getBundleId() != null
                ? bundleService.bundleDiscount(request.getBundleId(), request.getItems(), subtotal) : null;

        PricedOrder priced = price(subtotal,
                autoDiscount,
                customSurcharge,
                bundleDiscount != null ? null : request.getCouponCode(),
                request.getShippingZoneId(),
                request.getDeliveryAddress(),
                request.getClientId(),
                bundleDiscount);

        Order order = new Order();
        order.setClientId(request.getClientId());
        order.setDeliveryAddress(request.getDeliveryAddress());
        order.setPaymentMethod(request.getPaymentMethod());
        order.setShopId(request.getShopId());
        order.setDeliveryCharge(priced.shippingFee.doubleValue());
        order.setShippingFee(priced.shippingFee);
        order.setLatitude(request.getLatitude());
        order.setLongitude(request.getLongitude());
        order.setSubtotalAmount(priced.subtotal);
        order.setDiscountAmount(priced.discountAmount);
        order.setCouponId(priced.couponId);
        order.setCouponCode(priced.couponCode);
        order.setShippingZoneId(priced.shippingZoneId);
        order.setTotalAmount(priced.total);
        order.setOrderStatus(OrderStatus.PLACED.name());

        // Snapshot the customer's identity for emailing & display continuity
        if (request.getClientId() != null) {
            usersRepository.findById(request.getClientId()).ifPresent(user -> {
                order.setCustomerEmail(user.getEmail());
                order.setCustomerName(joinName(user.getFirstName(), user.getLastName()));
                // Prefer the phone entered at checkout; fall back to the profile.
                order.setCustomerPhone(
                        (request.getPhone() != null && !request.getPhone().isBlank())
                                ? request.getPhone().trim()
                                : user.getPhoneNumber());
            });
        }
        if (order.getCustomerPhone() == null && request.getPhone() != null && !request.getPhone().isBlank()) {
            order.setCustomerPhone(request.getPhone().trim());
        }

        Order savedOrder = orderRepository.save(order);
        persistItems(savedOrder.getId(), request.getItems());
        recordHistory(savedOrder.getId(), null, OrderStatus.PLACED, null, "customer");

        // Persist coupon redemption (and bump times_used).
        if (priced.appliedCoupon != null) {
            couponService.recordRedemption(priced.appliedCoupon, request.getClientId(),
                    savedOrder.getId(), priced.discountAmount);
        }

        // Clear cart on successful authenticated checkout
        if (request.getClientId() != null) {
            cartRepository.deleteByUserId(request.getClientId());
        }

        eventPublisher.publishEvent(new OrderPlacedEvent(this, savedOrder.getId(), order.getCustomerEmail()));

        return toOrderDTO(savedOrder);
    }

    // ── Guest checkout ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public OrderDTO placeGuestOrder(GuestOrderRequestDTO request) {
        BigDecimal subtotal = computeAndReserveStock(request.getItems(), null);
        BigDecimal autoDiscount = computeAutoDiscount(request.getItems());
        BigDecimal customSurcharge = computeCustomSurcharge(request.getItems());
        BigDecimal bundleDiscount = request.getBundleId() != null
                ? bundleService.bundleDiscount(request.getBundleId(), request.getItems(), subtotal) : null;

        PricedOrder priced = price(subtotal,
                autoDiscount,
                customSurcharge,
                bundleDiscount != null ? null : request.getCouponCode(),
                request.getShippingZoneId(),
                request.getDeliveryAddress(),
                null,
                bundleDiscount);

        Order order = new Order();
        order.setClientId(null); // guest — no Users row
        order.setDeliveryAddress(request.getDeliveryAddress());
        order.setPaymentMethod(request.getPaymentMethod());
        order.setShopId(request.getShopId());
        order.setDeliveryCharge(priced.shippingFee.doubleValue());
        order.setShippingFee(priced.shippingFee);
        order.setSubtotalAmount(priced.subtotal);
        order.setDiscountAmount(priced.discountAmount);
        order.setCouponId(priced.couponId);
        order.setCouponCode(priced.couponCode);
        order.setShippingZoneId(priced.shippingZoneId);
        order.setTotalAmount(priced.total);
        order.setOrderStatus(OrderStatus.PLACED.name());
        order.setCustomerEmail(request.getEmail());
        order.setCustomerName(joinName(request.getFirstName(), request.getLastName()));
        order.setCustomerPhone(request.getPhone() == null ? null : request.getPhone().trim());

        Order savedOrder = orderRepository.save(order);
        persistItems(savedOrder.getId(), request.getItems());
        recordHistory(savedOrder.getId(), null, OrderStatus.PLACED, null, "guest:" + request.getEmail());

        if (priced.appliedCoupon != null) {
            couponService.recordRedemption(priced.appliedCoupon, null,
                    savedOrder.getId(), priced.discountAmount);
        }

        eventPublisher.publishEvent(new OrderPlacedEvent(this, savedOrder.getId(), order.getCustomerEmail()));

        return toOrderDTO(savedOrder);
    }

    /** Internal carrier for pricing output. */
    private static final class PricedOrder {
        BigDecimal subtotal;
        BigDecimal discountAmount;
        BigDecimal shippingFee;
        BigDecimal total;
        Long shippingZoneId;
        UUID couponId;
        String couponCode;
        Coupon appliedCoupon;
    }

    /**
     * Computes a priced order given the already-tallied subtotal. Centralises
     * coupon + zone + shipping arithmetic so {@code placeOrder} and
     * {@code placeGuestOrder} can't drift apart.
     */
    private PricedOrder price(BigDecimal subtotal,
                              BigDecimal autoDiscount,
                              BigDecimal customSurcharge,
                              String couponCode,
                              Long shippingZoneId,
                              String deliveryAddress,
                              UUID userId,
                              BigDecimal bundleDiscount) {
        PricedOrder out = new PricedOrder();
        out.subtotal = subtotal != null ? subtotal : BigDecimal.ZERO;
        BigDecimal auto = autoDiscount != null ? autoDiscount : BigDecimal.ZERO;
        BigDecimal surcharge = customSurcharge != null ? customSurcharge : BigDecimal.ZERO;

        // Zone
        ShippingZone zone = shippingZoneId != null
                ? shippingZoneRepository.findById(shippingZoneId)
                        .filter(z -> !Boolean.TRUE.equals(z.getDeleted())
                                && Boolean.TRUE.equals(z.getIsActive()))
                        .orElse(shippingService.resolveZone(deliveryAddress))
                : shippingService.resolveZone(deliveryAddress);
        out.shippingZoneId = zone != null ? zone.getId() : null;
        BigDecimal shipping = shippingService.computeFee(zone, out.subtotal);

        // Bundle offer: fixed deal, exclusive of coupons/auto-discounts.
        if (bundleDiscount != null) {
            // Judge the free-shipping threshold on the bundle price actually paid,
            // not the inflated pre-discount list subtotal (see CheckoutQuoteService).
            BigDecimal bundleGoods = out.subtotal.subtract(bundleDiscount);
            if (bundleGoods.signum() < 0) bundleGoods = BigDecimal.ZERO;
            shipping = shippingService.computeFee(zone, bundleGoods);
            out.shippingFee = shipping;
            out.discountAmount = bundleDiscount;
            out.total = bundleGoods.add(shipping).add(surcharge);
            if (out.total.signum() < 0) out.total = BigDecimal.ZERO;
            return out;
        }

        // Automatic discounts come off first; the coupon then applies to the remainder.
        BigDecimal couponBase = out.subtotal.subtract(auto);
        if (couponBase.signum() < 0) couponBase = BigDecimal.ZERO;

        // Coupon (re-validated server-side)
        BigDecimal couponDiscount = BigDecimal.ZERO;
        if (couponCode != null && !couponCode.isBlank()) {
            try {
                Coupon coupon = couponService.getActiveByCode(couponCode);
                CouponDiscountType type = CouponDiscountType.parse(coupon.getDiscountType())
                        .orElseThrow(() -> new InvalidStockOperationException(
                                "Stored coupon has bad type: " + coupon.getDiscountType()));
                couponDiscount = computeDiscount(type, coupon.getDiscountValue(), couponBase);
                if (type == CouponDiscountType.FREE_SHIPPING) {
                    shipping = BigDecimal.ZERO;
                }
                out.appliedCoupon = coupon;
                out.couponId = coupon.getId();
                out.couponCode = coupon.getCode();
            } catch (ResourceNotFoundException | InvalidStockOperationException ex) {
                // Soft-fail: invalid coupon doesn't block the order, it just
                // doesn't apply. The storefront should pre-validate.
                log.warn("Coupon '{}' could not be applied: {}", couponCode, ex.getMessage());
            }
        }

        out.shippingFee = shipping;
        out.discountAmount = auto.add(couponDiscount);
        // Custom-size surcharge is a service fee — added after discounts (not discounted).
        out.total = out.subtotal.subtract(out.discountAmount).add(shipping).add(surcharge);
        if (out.total.signum() < 0) out.total = BigDecimal.ZERO;
        return out;
    }

    /**
     * Sums automatic product/category/global discounts across the order's lines,
     * deriving each line's subtotal the same way {@link #computeAndReserveStock}
     * does (variant price-override else product base price) so the numbers stay
     * coherent with {@code out.subtotal}.
     */
    private BigDecimal computeAutoDiscount(List<OrderItemRequestDTO> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequestDTO item : items) {
            Product product = inventoryService.lockAndGetProduct(item.getProductId());
            Variant variant = item.getVariantId() != null
                    ? variantRepository.findById(item.getVariantId()).orElse(null)
                    : null;
            BigDecimal unitPrice = PricingSupport.effectiveUnitPrice(product, variant);
            BigDecimal lineSubtotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(discountEngine.discountForLine(
                    product.getId(), product.getCategoryId(), lineSubtotal));
        }
        return total;
    }

    /** A line is a (valid) custom made-to-order line when it carries measurements and the product enables it. */
    private boolean isCustomLine(OrderItemRequestDTO item, Product product) {
        return item.getCustomMeasurements() != null && !item.getCustomMeasurements().isBlank()
                && Boolean.TRUE.equals(product.getCustomSizeEnabled());
    }

    /** Flat surcharge for a single line (server-authoritative), or ZERO when not a custom line. */
    private BigDecimal customSurchargeForLine(OrderItemRequestDTO item, Product product) {
        if (!isCustomLine(item, product)) return BigDecimal.ZERO;
        BigDecimal s = product.getCustomSizeSurcharge();
        return s != null ? s : BigDecimal.ZERO;
    }

    /** Sum of flat custom-size surcharges across the order's lines. */
    private BigDecimal computeCustomSurcharge(List<OrderItemRequestDTO> items) {
        if (items == null || items.isEmpty()) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequestDTO item : items) {
            Product product = inventoryService.lockAndGetProduct(item.getProductId());
            total = total.add(customSurchargeForLine(item, product));
        }
        return total;
    }

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private BigDecimal computeDiscount(CouponDiscountType type, BigDecimal value, BigDecimal subtotal) {
        return switch (type) {
            case PERCENT -> {
                BigDecimal pct = value == null ? BigDecimal.ZERO : value;
                BigDecimal raw = subtotal.multiply(pct).divide(HUNDRED, 2, java.math.RoundingMode.HALF_UP);
                yield raw.min(subtotal);
            }
            case FLAT -> {
                BigDecimal flat = value == null ? BigDecimal.ZERO : value;
                yield flat.min(subtotal);
            }
            case FREE_SHIPPING -> BigDecimal.ZERO;
        };
    }

    // ── Customer-initiated cancel ──────────────────────────────────────────────

    @Override
    @Transactional
    public OrderDTO cancelOrder(UUID userId, UUID orderId, CancelOrderRequestDTO request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getClientId() == null || !order.getClientId().equals(userId)) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }

        OrderStatus current = OrderStatus.parse(order.getOrderStatus())
                .orElseThrow(() -> new InvalidStockOperationException(
                        "Order is in an unknown status and cannot be cancelled."));

        if (!current.isCancellableByCustomer()) {
            throw new InvalidStockOperationException(
                    "This order can no longer be cancelled (status: " + current.normalize() + ").");
        }
        if (!current.canTransitionTo(OrderStatus.CANCELLED)) {
            throw new InvalidStockOperationException(
                    "Cannot cancel an order in status: " + current.normalize());
        }
        // Time-window: customer self-cancel is only allowed within N minutes
        // of placing. Admin override via PATCH /admin/orders/{id}/status is
        // not subject to this check.
        if (order.getCreatedAt() != null) {
            LocalDateTime placedAt = LocalDateTime.ofInstant(
                    order.getCreatedAt().toInstant(), java.time.ZoneId.systemDefault());
            if (placedAt.plusMinutes(cancellationWindowMinutes).isBefore(LocalDateTime.now())) {
                throw new InvalidStockOperationException(
                        "Cancellation window of " + cancellationWindowMinutes +
                                " minutes has expired. Please contact support.");
            }
        }

        order.setOrderStatus(OrderStatus.CANCELLED.name());
        order.setCancellationReason(request != null ? request.getReason() : null);
        order.setCancelledAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);

        recordHistory(saved.getId(), current, OrderStatus.CANCELLED,
                request != null ? request.getReason() : null, "customer");

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                this, saved.getId(), saved.getCustomerEmail(),
                current, OrderStatus.CANCELLED,
                request != null ? request.getReason() : null,
                null, null));

        return toOrderDTO(saved);
    }

    @Override
    @Transactional
    public OrderDTO updateOrderContact(UUID userId, UUID orderId, UpdateOrderContactRequestDTO request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Ownership (mask as not-found to avoid leaking other users' orders).
        if (order.getClientId() == null || !order.getClientId().equals(userId)) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }

        // Editable only while still PLACED (not yet accepted/processed).
        OrderStatus current = OrderStatus.parse(order.getOrderStatus())
                .orElseThrow(() -> new InvalidStockOperationException("Order is in an unknown status."));
        if (current != OrderStatus.PLACED) {
            throw new InvalidStockOperationException(
                    "Shipping details can only be changed while the order is Placed (status: "
                            + current.normalize() + ").");
        }

        order.setDeliveryAddress(request.getDeliveryAddress().trim());
        order.setCustomerPhone(request.getPhone() == null || request.getPhone().isBlank()
                ? null : request.getPhone().trim());
        return toOrderDTO(orderRepository.save(order));
    }

    // ── Customer-facing tracking ───────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public OrderTrackingDTO getOrderTracking(UUID userId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        // 404 (not 403) on ownership mismatch so we don't leak that the order exists.
        if (order.getClientId() == null || !order.getClientId().equals(userId)) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        return getOrderTracking(orderId);
    }

    @Override
    public OrderTrackingDTO getOrderTracking(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        OrderStatus current = OrderStatus.parse(order.getOrderStatus()).orElse(OrderStatus.PLACED);

        List<OrderStatusHistoryDTO> history = statusHistoryRepository
                .findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(this::toHistoryDTO)
                .toList();

        // Compute the cancellation deadline and only allow CANCELLED in
        // allowedCustomerNextStates if the deadline hasn't passed.
        LocalDateTime deadline = null;
        if (order.getCreatedAt() != null) {
            deadline = LocalDateTime.ofInstant(order.getCreatedAt().toInstant(),
                            java.time.ZoneId.systemDefault())
                    .plusMinutes(cancellationWindowMinutes);
        }
        boolean withinWindow = deadline == null || deadline.isAfter(LocalDateTime.now());

        List<String> customerAllowed = (current.isCancellableByCustomer() && withinWindow)
                ? List.of(OrderStatus.CANCELLED.name())
                : List.of();

        return OrderTrackingDTO.builder()
                .orderId(orderId)
                .currentStatus(current.normalize().name())
                .courierName(order.getCourierName())
                .trackingNumber(order.getTrackingNumber())
                .history(history)
                .allowedCustomerNextStates(customerAllowed)
                .cancellationDeadline(deadline != null ? deadline.toString() : null)
                .build();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /**
     * Enforces that a signed-in customer has verified their email before
     * ordering. Guest checkout passes {@code clientId == null} and is exempt
     * (guests have no account to verify). Throws {@link EmailNotVerifiedException}
     * (HTTP 403) otherwise.
     */
    private void requireVerifiedEmail(UUID clientId) {
        if (clientId == null) {
            return; // guest checkout — no account to verify
        }
        boolean verified = usersRepository.findById(clientId)
                .map(u -> Boolean.TRUE.equals(u.getEmailVerified()))
                .orElse(false);
        if (!verified) {
            throw new EmailNotVerifiedException(
                    "Please verify your email address before placing an order. "
                            + "Check your inbox for the verification link or request a new one.");
        }
    }

    /**
     * Locks each product row, validates stock, decrements inventory, publishes
     * stock-update events, and returns the running total (item cost + shipping).
     */
    private BigDecimal computeAndReserveStock(List<OrderItemRequestDTO> items, Double shippingFee) {
        if (items == null || items.isEmpty()) {
            throw new InvalidStockOperationException("Order must contain at least one item.");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<Product> lockedProducts = new ArrayList<>();
        // Aligned with items/lockedProducts; null = product-level (no variant).
        List<Variant> lockedVariants = new ArrayList<>();
        // Aligned with items; true = made-to-order custom line (no stock reservation).
        List<Boolean> customFlags = new ArrayList<>();

        for (OrderItemRequestDTO item : items) {
            Product product = inventoryService.lockAndGetProduct(item.getProductId());
            int requested = item.getQuantity();
            Variant variant = null;
            boolean custom = isCustomLine(item, product);

            if (custom) {
                // Made-to-order custom line: no variant and no stock reservation.
            } else if (item.getVariantId() != null) {
                // Variant order: validate against the *variant's* stock & price.
                variant = variantRepository.findById(item.getVariantId())
                        .filter(v -> !Boolean.TRUE.equals(v.getDeleted()))
                        .orElseThrow(() -> new InvalidStockOperationException(
                                "Variant not found: " + item.getVariantId()));
                if (!product.getId().equals(variant.getProductId())) {
                    throw new InvalidStockOperationException(
                            "Variant " + item.getVariantId() + " does not belong to product " + product.getId());
                }
                int vAvail = variant.getStockQuantity() != null ? variant.getStockQuantity() : 0;
                if (vAvail < requested) {
                    throw new InsufficientStockException(product.getId(), requested, vAvail);
                }
            } else {
                int available = inventoryService.getAvailableStock(item.getProductId());
                if (available < requested) {
                    throw new InsufficientStockException(product.getId(), requested, available);
                }
            }

            // Canonical effective price (variant override → sale → base) — must
            // match the checkout quote so the customer is charged what they saw.
            BigDecimal unitPrice = PricingSupport.effectiveUnitPrice(product, variant);

            lockedProducts.add(product);
            lockedVariants.add(variant);
            customFlags.add(custom);
            totalAmount = totalAmount.add(unitPrice.multiply(BigDecimal.valueOf(requested)));
        }

        for (int i = 0; i < items.size(); i++) {
            if (customFlags.get(i)) continue; // made-to-order: nothing to decrement
            int qty = items.get(i).getQuantity();
            Variant variant = lockedVariants.get(i);
            if (variant != null) {
                variant.setStockQuantity(variant.getStockQuantity() - qty);
                variantRepository.save(variant);
            } else {
                Product product = lockedProducts.get(i);
                inventoryService.decrementStock(product, qty);
                int newQty = inventoryService.getAvailableStock(product.getId());
                eventPublisher.publishEvent(new StockUpdateEvent(this, product.getId(), newQty,
                        inventoryService.computeStatus(product)));
            }
        }

        if (shippingFee != null) {
            totalAmount = totalAmount.add(BigDecimal.valueOf(shippingFee));
        }
        return totalAmount;
    }

    private void persistItems(UUID orderId, List<OrderItemRequestDTO> items) {
        for (OrderItemRequestDTO item : items) {
            OrderItem oi = new OrderItem();
            oi.setOrderId(orderId);
            oi.setProductId(item.getProductId());
            oi.setQuantity(item.getQuantity());
            // priceAtOrder snapshot — use the same canonical effective price.
            Product p = inventoryService.lockAndGetProduct(item.getProductId());
            oi.setVariantId(item.getVariantId());
            Variant variant = item.getVariantId() != null
                    ? variantRepository.findById(item.getVariantId()).orElse(null)
                    : null;
            oi.setPriceAtOrder(PricingSupport.effectiveUnitPrice(p, variant));
            // Snapshot variant attributes so order history survives later edits/deletes.
            if (variant != null) {
                // Variants are size-only — snapshot the size as the name too.
                oi.setVariantName(variant.getSize());
                oi.setVariantSize(variant.getSize());
            }
            // Custom (made-to-order) snapshot: measurements + server-authoritative surcharge.
            if (isCustomLine(item, p)) {
                oi.setCustomMeasurements(item.getCustomMeasurements());
                oi.setCustomSurcharge(customSurchargeForLine(item, p));
                oi.setVariantSize("Custom");
                oi.setVariantName("Custom");
            }
            orderItemRepository.save(oi);
        }
    }

    private void recordHistory(UUID orderId,
                                OrderStatus from,
                                OrderStatus to,
                                String note,
                                String changedBy) {
        OrderStatusHistory h = OrderStatusHistory.builder()
                .orderId(orderId)
                .fromStatus(from == null ? null : from.normalize().name())
                .toStatus(to.normalize().name())
                .note(note)
                .changedBy(changedBy)
                .build();
        statusHistoryRepository.save(h);
    }

    private OrderDTO toOrderDTO(Order order) {
        OrderDTO dto = new OrderDTO();
        dto.setId(order.getId());
        dto.setClientId(order.getClientId());
        dto.setDeliveryAddress(order.getDeliveryAddress());
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setShopId(order.getShopId());
        dto.setDeliveryCharge(order.getDeliveryCharge());
        dto.setLatitude(order.getLatitude());
        dto.setLongitude(order.getLongitude());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setOrderStatus(order.getOrderStatus());

        List<OrderItemDTO> itemDTOs = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(this::toItemDTO).toList();
        dto.setOrderItems(itemDTOs);
        dto.setCustomerName(order.getCustomerName());
        dto.setCustomerPhone(order.getCustomerPhone());
        return dto;
    }

    private OrderItemDTO toItemDTO(OrderItem item) {
        OrderItemDTO dto = new OrderItemDTO();
        dto.setId(item.getId());
        dto.setOrderId(item.getOrderId());
        dto.setProductId(item.getProductId());
        dto.setQuantity(item.getQuantity());
        dto.setPriceAtOrder(item.getPriceAtOrder());
        dto.setVariantId(item.getVariantId());
        dto.setVariantName(item.getVariantName());
        dto.setVariantSize(item.getVariantSize());
        dto.setCustomMeasurements(item.getCustomMeasurements());
        dto.setCustomSurcharge(item.getCustomSurcharge());
        return dto;
    }

    private OrderStatusHistoryDTO toHistoryDTO(OrderStatusHistory h) {
        java.util.Date created = h.getCreatedAt();
        LocalDateTime ldt = created == null ? null
                : LocalDateTime.ofInstant(created.toInstant(), java.time.ZoneId.systemDefault());
        return OrderStatusHistoryDTO.builder()
                .id(h.getId())
                .orderId(h.getOrderId())
                .fromStatus(h.getFromStatus())
                .toStatus(h.getToStatus())
                .note(h.getNote())
                .changedBy(h.getChangedBy())
                .createdAt(ldt)
                .build();
    }

    private String joinName(String first, String last) {
        String f = first == null ? "" : first.trim();
        String l = last == null ? "" : last.trim();
        String combined = (f + " " + l).trim();
        return combined.isEmpty() ? null : combined;
    }
}
