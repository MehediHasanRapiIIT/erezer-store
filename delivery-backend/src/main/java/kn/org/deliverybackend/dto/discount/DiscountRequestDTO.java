package kn.org.deliverybackend.dto.discount;

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
public class DiscountRequestDTO {

    @NotBlank
    @Size(max = 120)
    private String name;

    /** PRODUCT | CATEGORY | GLOBAL */
    @NotBlank
    private String scope;

    /** PERCENT | FLAT */
    @NotBlank
    private String discountType;

    @PositiveOrZero
    private BigDecimal discountValue;

    /** productId (PRODUCT) or categoryId (CATEGORY); ignored for GLOBAL. */
    private Long targetId;

    private Boolean stackable;
    private Integer priority;

    private LocalDateTime validFrom;
    private LocalDateTime validTo;

    private Boolean isActive;

    @Size(max = 255)
    private String description;
}
