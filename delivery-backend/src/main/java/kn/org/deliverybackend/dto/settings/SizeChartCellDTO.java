package kn.org.deliverybackend.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** A single measurement, carried in both centimetres and inches. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SizeChartCellDTO {
    private BigDecimal cm;
    private BigDecimal inch;
}
