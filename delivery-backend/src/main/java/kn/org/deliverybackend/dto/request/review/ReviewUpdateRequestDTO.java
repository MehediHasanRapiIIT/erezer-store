package kn.org.deliverybackend.dto.request.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ReviewUpdateRequestDTO {

    /** Set from the logged-in customer by the controller; any value sent is ignored. */
    private UUID userId;

    @NotNull(message = "rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating;


    @jakarta.validation.constraints.Size(max = 2000, message = "A review can be up to 2000 characters")
    private String comment;
}
