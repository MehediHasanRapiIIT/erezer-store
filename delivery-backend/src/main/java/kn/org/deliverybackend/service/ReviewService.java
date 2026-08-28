package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.request.review.ReviewRequestDTO;
import kn.org.deliverybackend.dto.request.review.ReviewUpdateRequestDTO;
import kn.org.deliverybackend.dto.response.review.RatingSummaryDTO;
import kn.org.deliverybackend.dto.response.review.ReviewResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReviewService {
    ReviewResponseDTO submitReview(Long productId, ReviewRequestDTO request);
    Page<ReviewResponseDTO> getReviews(Long productId, Pageable pageable);
    RatingSummaryDTO getRatingSummary(Long productId);
    ReviewResponseDTO updateReview(Long productId, UUID reviewId, ReviewUpdateRequestDTO request);
    void deleteReview(Long productId, UUID reviewId, UUID requestingUserId);
    void adminDeleteReview(Long productId, UUID reviewId);
}
