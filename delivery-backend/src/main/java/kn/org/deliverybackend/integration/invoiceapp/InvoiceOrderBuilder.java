package kn.org.deliverybackend.integration.invoiceapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kn.org.deliverybackend.dto.variant.VariantOptionDTO;
import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.OrderItem;
import kn.org.deliverybackend.entity.Payment;
import kn.org.deliverybackend.entity.PaymentRefund;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.enumeration.Fit;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.ShopCustomer;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.ShopItem;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.ShopOrder;
import kn.org.deliverybackend.repository.OrderItemRepository;
import kn.org.deliverybackend.repository.PaymentRefundRepository;
import kn.org.deliverybackend.repository.PaymentRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.VariantRepository;
import kn.org.deliverybackend.service.FileStorageService;
import kn.org.deliverybackend.service.FileStorageService.StoredFile;
import kn.org.deliverybackend.service.impl.OrderLinePicture;
import kn.org.deliverybackend.service.impl.ProductOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a shop order into what Erezer Invoice takes: the order, and one product
 * photo for each line (the picture of the colour that was ordered).
 *
 * <p>Erezer Invoice adds the totals up itself, so whatever the shop charged
 * beyond price × quantity (a custom size charge, tax) goes in as a line of its
 * own, and the totals match.
 */
@Component
@RequiredArgsConstructor
public class InvoiceOrderBuilder {

    /** Erezer Invoice keeps photos up to 10 MB. */
    static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;

    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository refundRepository;
    private final ProductRepository productRepository;
    private final VariantRepository variantRepository;
    private final OrderLinePicture linePicture;
    private final FileStorageService storage;
    private final ObjectMapper json = new ObjectMapper();

    /** The order ready to send. {@code photos.get(n)} goes with {@code order.items().get(n)}; may be empty. */
    public record Prepared(ShopOrder order, List<List<StoredFile>> photos, int missingPhotos) {
    }

    /** @throws IllegalArgumentException with a reason staff can act on, when the order can't be sent as it is */
    public Prepared build(Order order) {
        String phone = trim(order.getCustomerPhone());
        String address = trim(order.getDeliveryAddress());
        if (phone == null) throw new IllegalArgumentException("The order has no phone number.");
        if (address == null) throw new IllegalArgumentException("The order has no delivery address.");

        List<ShopItem> items = new ArrayList<>();
        List<List<StoredFile>> photos = new ArrayList<>();
        int missing = 0;

        for (OrderItem line : orderItemRepository.findByOrderId(order.getId())) {
            if (Boolean.TRUE.equals(line.getDeleted())) continue;
            Product product = line.getProductId() == null ? null : productRepository.findById(line.getProductId()).orElse(null);
            Variant variant = line.getVariantId() == null ? null : variantRepository.findById(line.getVariantId()).orElse(null);
            String name = firstOf(line.getProductName(), product == null ? null : product.getName(), "Product");
            String chosen = trim(line.getVariantName());

            items.add(new ShopItem(
                    cut(name, 160),
                    cut(firstOf(line.getProductCode(), product == null ? null : product.getProductCode()), 60),
                    cut(description(chosen, line.getCustomMeasurements()), 1000),
                    cut(colour(product, variant), 60),
                    cut(size(variant, chosen), 30),
                    money(line.getPriceAtOrder()),
                    line.getQuantity() == null || line.getQuantity() < 1 ? 1 : line.getQuantity(),
                    cut(chosen == null ? name : chosen.replace(" / ", " · "), 500)));

            Optional<StoredFile> photo = photoOf(line, product);
            if (photo.isPresent()) {
                photos.add(List.of(photo.get()));
            } else {
                photos.add(List.of());
                missing++;
            }

            BigDecimal surcharge = money(line.getCustomSurcharge());
            if (surcharge.signum() > 0) {
                items.add(new ShopItem(cut("Custom size charge: " + name, 160), null, null, null, null, surcharge, 1, null));
                photos.add(List.of());
            }
        }
        if (items.isEmpty()) throw new IllegalArgumentException("The order has no products.");

        BigDecimal tax = money(order.getTaxAmount());
        if (tax.signum() > 0) {
            items.add(new ShopItem("Tax", null, null, null, null, tax, 1, null));
            photos.add(List.of());
        }

        BigDecimal paid = paymentRepository.findByOrderId(order.getId()).stream()
                .filter(p -> "Completed".equalsIgnoreCase(p.getStatus()))
                .map(Payment::getAmount)
                .map(InvoiceOrderBuilder::money)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Money handed back by bKash isn't money the shop holds.
        BigDecimal refunded = refundRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                .filter(r -> PaymentRefund.COMPLETED.equalsIgnoreCase(r.getStatus()))
                .map(PaymentRefund::getAmount)
                .map(InvoiceOrderBuilder::money)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        paid = paid.subtract(refunded).max(BigDecimal.ZERO);

        BigDecimal delivery = order.getShippingFee() != null ? order.getShippingFee()
                : order.getDeliveryCharge() != null ? BigDecimal.valueOf(order.getDeliveryCharge()) : BigDecimal.ZERO;

        ShopOrder sent = new ShopOrder(
                order.getOrderNumber(),
                order.getCreatedAt() == null ? null : order.getCreatedAt().toInstant(),
                paymentMethod(order.getPaymentMethod(), paid),
                paid,
                new ShopCustomer(cut(firstOf(order.getCustomerName(), "Customer"), 120), cut(phone, 30),
                        email(order.getCustomerEmail()), cut(address, 500)),
                money(delivery),
                money(order.getDiscountAmount()),
                items);
        return new Prepared(sent, photos, missing);
    }

    // ── the pieces ───────────────────────────────────────────────────────────

    /**
     * bKash with money received: bKash, that amount paid in advance. Anything else
     * (cash on delivery, a bKash payment that never went through) is collected on delivery.
     */
    static String paymentMethod(String method, BigDecimal paid) {
        if (paid.signum() > 0 && method != null && method.equalsIgnoreCase("BKASH")) return "BKASH";
        if (paid.signum() > 0 && method != null && method.equalsIgnoreCase("NAGAD")) return "NAGAD";
        return "CASH";
    }

    /** "Black / Drop Shoulder / L", and the customer's measurements for a made-to-measure line. */
    String description(String chosen, String measurementsJson) {
        String measurements = measurements(measurementsJson);
        if (chosen == null) return measurements;
        return measurements == null ? chosen : chosen + "\nCustom size: " + measurements;
    }

    /** {"Chest":38,"Length":40,"comments":"loose"} → "Chest 38, Length 40, loose". */
    String measurements(String measurementsJson) {
        if (measurementsJson == null || measurementsJson.isBlank()) return null;
        try {
            JsonNode node = json.readTree(measurementsJson);
            List<String> parts = new ArrayList<>();
            String comments = null;
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                String value = e.getValue().asText("").trim();
                if (value.isEmpty()) continue;
                if (e.getKey().equalsIgnoreCase("comments")) comments = value;
                else parts.add(e.getKey() + " " + value);
            }
            if (comments != null) parts.add(comments);
            return parts.isEmpty() ? null : String.join(", ", parts);
        } catch (Exception e) {
            return measurementsJson;
        }
    }

    /** The colour chosen, from the product's options ("Colour"/"Color"), or null. */
    static String colour(Product product, Variant variant) {
        if (product == null || variant == null || variant.getOptionKey() == null || variant.getOptionKey().isBlank()) return null;
        return ProductOptions.describe(ProductOptions.parse(product.getOptionsJson()), variant.getOptionKey()).stream()
                .filter(c -> c.getOption() != null && c.getOption().trim().toLowerCase().matches("colou?rs?"))
                .map(VariantOptionDTO::getValue)
                .findFirst()
                .orElse(null);
    }

    /** The size chosen. Without the variant (since deleted), the last part of the name if it isn't a fit or colour. */
    static String size(Variant variant, String chosen) {
        if (variant != null) return trim(variant.getSize());
        if (chosen == null) return null;
        String[] parts = chosen.split(" / ");
        String last = parts[parts.length - 1].trim();
        boolean isFit = Arrays.stream(Fit.values()).anyMatch(f -> f.label().equalsIgnoreCase(last));
        return !isFit && last.length() <= 10 ? last : null;
    }

    /** The picture of the ordered choice, else the product's main one; read from this shop's own storage. */
    private Optional<StoredFile> photoOf(OrderItem line, Product product) {
        if (product == null) return Optional.empty();
        String url = linePicture.ofChoice(line);
        if (url == null) url = product.getImageUrl();
        return storage.read(url, MAX_PHOTO_BYTES);
    }

    // ── small helpers ────────────────────────────────────────────────────────

    private static String email(String raw) {
        String e = trim(raw);
        return e != null && e.length() <= 160 && e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") ? e : null;
    }

    static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String firstOf(String... values) {
        return Arrays.stream(values).map(InvoiceOrderBuilder::trim).filter(v -> v != null).findFirst().orElse(null);
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
