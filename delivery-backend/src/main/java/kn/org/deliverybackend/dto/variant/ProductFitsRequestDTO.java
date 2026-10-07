package kn.org.deliverybackend.dto.variant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * The fits a product comes in: Drop Shoulder, Regular Fit, both, or — an empty
 * list — neither.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductFitsRequestDTO {

    @NotNull(message = "Say which fits the product comes in")
    @Size(max = 2, message = "A product comes in at most two fits")
    @Valid
    private List<Choice> fits;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {

        /** DROP_SHOULDER or REGULAR_FIT. */
        @NotNull(message = "Which fit?")
        private String fit;

        /**
         * This fit's own price, set on each of its sizes. Null with
         * {@link #changePrice} means "the product's price".
         */
        @PositiveOrZero(message = "A fit's price can't be negative")
        private BigDecimal price;

        /** False leaves the prices of this fit's sizes exactly as they are. */
        private boolean changePrice;
    }
}
