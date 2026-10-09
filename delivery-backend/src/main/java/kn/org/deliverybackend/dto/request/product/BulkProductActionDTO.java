package kn.org.deliverybackend.dto.request.product;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kn.org.deliverybackend.enumeration.BulkProductAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** One action for the products ticked on the Products page. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkProductActionDTO {
    @NotNull(message = "Choose what to do")
    private BulkProductAction action;
    @NotEmpty(message = "Tick at least one product")
    @Size(max = 500, message = "Up to 500 products at a time")
    private List<Long> productIds;
    /** Where to, for MOVE_CATEGORY. */
    private Long categoryId;
    /** Which chart, for SET_SIZE_CHART. Null or 0: none of their own, so they follow their category. */
    private Long sizeChartId;

    public BulkProductActionDTO(kn.org.deliverybackend.enumeration.BulkProductAction action, List<Long> productIds, Long categoryId) {
        this(action, productIds, categoryId, null);
    }
}
