package kn.org.deliverybackend.dto.coupon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponValidateRequestDTO {

    @NotBlank
    private String code;

    @PositiveOrZero
    private BigDecimal cartSubtotal;

    /** Optional — when provided, per-user usage limit is enforced. */
    private UUID userId;
}
