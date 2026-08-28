package kn.org.deliverybackend.dto.checkout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutQuoteResponseDTO {

    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal customSurcharge; // flat custom-size fee(s), added after discounts
    private BigDecimal total;

    private Long shippingZoneId;
    private String shippingZoneName;

    private String couponCode;
    private String couponDiscountType;
    private String couponMessage; // null on success; rejection reason otherwise
    private boolean couponApplied;
}
