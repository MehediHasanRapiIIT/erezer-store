package kn.org.deliverybackend.dto.flashsale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Admin create/update payload for a {@code FlashSale}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleRequestDTO {

    @NotBlank
    @Size(max = 120)
    private String name;

    @Size(max = 120)
    private String label;

    /** PERCENT | FLAT */
    @NotBlank
    private String discountType;

    @NotNull
    @Positive
    private BigDecimal discountValue;

    private LocalDateTime startsAt;

    @NotNull
    private LocalDateTime endsAt;

    @Size(max = 40)
    private String couponCode;

    private BigDecimal minSpend;

    private Boolean isActive;

    /** Feature this sale in the landing-page widget. */
    private Boolean featured;

    /** Participating product ids (curated, ordered). */
    private List<Long> productIds;
}
