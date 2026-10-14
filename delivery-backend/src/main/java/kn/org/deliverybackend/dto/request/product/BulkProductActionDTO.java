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
    /**
     * For SET_SIZE_CHART: a chart for each fit, by fit (DROP_SHOULDER → chart id).
     * 0 takes the products' own chart for that fit away; a fit left out is left as it is.
     */
    private java.util.Map<String, Long> fitSizeChartIds;
    /** For SET_SIZE_CHART: true leaves the products' general chart as it is (only the fits' charts change). */
    private Boolean keepGeneralChart;

    public BulkProductActionDTO(kn.org.deliverybackend.enumeration.BulkProductAction action, List<Long> productIds, Long categoryId, Long sizeChartId) {
        this(action, productIds, categoryId, sizeChartId, null, null);
    }

    public BulkProductActionDTO(kn.org.deliverybackend.enumeration.BulkProductAction action, List<Long> productIds, Long categoryId) {
        this(action, productIds, categoryId, null, null, null);
    }
}
