package kn.org.deliverybackend.dto.returns;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Used by admin approve / reject / refund endpoints — all fields optional. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReturnDecisionDTO {

    @Size(max = 2000)
    private String adminNotes;

    @PositiveOrZero
    private BigDecimal refundAmount;
}
