package kn.org.deliverybackend.dto.response.review;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RatingSummaryDTO {
    private Long productId;
    private double avgRating;
    private int totalReviews;
    private Map<Integer, Long> starBreakdown;
}
