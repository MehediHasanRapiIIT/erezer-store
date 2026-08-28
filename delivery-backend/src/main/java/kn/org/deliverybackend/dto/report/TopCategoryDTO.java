package kn.org.deliverybackend.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopCategoryDTO {
    private Long categoryId;
    private String categoryName;
    private long unitsSold;
    private BigDecimal revenue;
}
