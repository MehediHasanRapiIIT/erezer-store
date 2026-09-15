package kn.org.deliverybackend.dto.shipping;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** A new shipping price for one zone. */
public record ZoneFeeChangeDTO(@NotNull @PositiveOrZero @DecimalMax("1000000") BigDecimal flatFee) {
}
