package kn.org.deliverybackend.dto.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One chart of the size chart library, as it is listed, and as it is sent to be
 * saved (the id and the counts are then ignored).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SizeChartLibraryDTO {
    private Long id;
    @NotBlank(message = "Give the chart a name")
    @Size(max = 120, message = "The name can be up to 120 letters")
    private String name;
    @NotNull(message = "The chart needs its columns and sizes")
    private SizeChartDTO chart;
    /** Shown by a product when neither it nor a category above it names a chart. */
    private Boolean isDefault;
    /** Products that name this chart themselves. */
    private long productCount;
    /** Categories that name this chart. */
    private long categoryCount;
}
