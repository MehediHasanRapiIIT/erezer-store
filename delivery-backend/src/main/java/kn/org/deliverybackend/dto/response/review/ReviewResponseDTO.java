package kn.org.deliverybackend.dto.response.review;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponseDTO {
    private UUID reviewId;
    private UUID userId;
    private String userName;
    private int rating;
    private String comment;
    private Date createdAt;
}
