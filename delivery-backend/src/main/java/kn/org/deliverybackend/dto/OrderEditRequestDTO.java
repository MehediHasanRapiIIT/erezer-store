package kn.org.deliverybackend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * An admin's correction to an order. Anything left null is not changed.
 *
 * <p>{@code items}, when given, is the whole order as it should now be: a line
 * with an id is one already on the order (its quantity or price may differ), a
 * line without one is being added, and a line of the order that is missing from
 * the list is being removed.
 */
@Data
public class OrderEditRequestDTO {

    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String deliveryAddress;

    private BigDecimal shippingFee;
    private BigDecimal discountAmount;

    private List<Line> items;

    @Data
    public static class Line {
        /** The order line being changed; null for a product being added. */
        private UUID id;
        /** For a product being added. */
        private Long productId;
        /** The size/colour/fit of a product being added, when it has any. */
        private Long variantId;
        private Integer quantity;
        /** The price of one; null keeps the line's price (or, for a new line, the shop's price today). */
        private BigDecimal unitPrice;
    }
}
