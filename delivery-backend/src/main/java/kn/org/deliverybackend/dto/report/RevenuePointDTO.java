package kn.org.deliverybackend.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenuePointDTO {
    /** Bucket date (always the start of the day/week/month depending on granularity). */
    private LocalDate date;
    private BigDecimal revenue;
    private long orderCount;
}
