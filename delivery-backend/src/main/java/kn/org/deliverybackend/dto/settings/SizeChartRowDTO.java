package kn.org.deliverybackend.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SizeChartRowDTO {
    /** Size label, e.g. "S", "M", "L", "XL", "XXL". */
    private String size;
    /** One cell per column in {@link SizeChartDTO#getColumns()} (aligned by index). */
    private List<SizeChartCellDTO> cells;
}
