package kn.org.deliverybackend.dto.payment;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One change to the ways to pay, made from the admin Products page: on the
 * products ticked there, or on one category (and so on everything in it that
 * sets nothing of its own).
 *
 * <p>For each method: ALLOW, BLOCK, FOLLOW (same as the category), or null to
 * leave it as it is.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodsChangeDTO {

    /** For a change to products. */
    @Size(max = 500, message = "Up to 500 products at a time")
    private List<Long> productIds;

    /** For a change to a category. */
    private Long categoryId;

    private String cod;
    private String bkash;
    private String card;
}
