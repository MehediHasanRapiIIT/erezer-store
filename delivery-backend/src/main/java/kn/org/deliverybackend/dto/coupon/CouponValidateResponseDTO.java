package kn.org.deliverybackend.dto.coupon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponValidateResponseDTO {
    private boolean valid;
    private String code;
    private String discountType;
    /** Currency-amount discount that would apply to {@code cartSubtotal}. */
    private BigDecimal discountAmount;
    /** True for FREE_SHIPPING coupons; checkout should also zero the shipping fee. */
    private boolean removesShipping;
    /** Free-text rejection reason when {@code valid=false}; null otherwise. */
    private String reason;
}
