package kn.org.deliverybackend.dto.bundle;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One step of a quantity discount: this many items or more get this percentage off. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BundleTierDTO {
    private Integer quantity;
    private BigDecimal percentOff;
}
