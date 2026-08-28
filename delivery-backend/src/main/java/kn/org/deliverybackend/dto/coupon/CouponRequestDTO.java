package kn.org.deliverybackend.dto.coupon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponRequestDTO {

    @NotBlank
    @Size(max = 64)
    private String code;

    /** PERCENT | FLAT | FREE_SHIPPING */
    @NotBlank
    private String discountType;

    @PositiveOrZero
    private BigDecimal discountValue;

    @PositiveOrZero
    private BigDecimal minOrderAmount;

    private Integer usageLimit;
    private Integer perUserLimit;

    private LocalDateTime validFrom;
    private LocalDateTime validTo;

    private Boolean isActive;

    @Size(max = 255)
    private String description;
}
