package kn.org.deliverybackend.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A size chart: a set of measurement {@code columns} (e.g. "Chest", "Length")
 * and one {@code row} per size, each row carrying a cell per column with both
 * centimetre and inch values.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SizeChartDTO {
    private List<String> columns;
    private List<SizeChartRowDTO> rows;
}
