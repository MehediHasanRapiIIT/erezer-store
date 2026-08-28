package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.dto.response.review.RatingSummaryDTO;
import kn.org.deliverybackend.dto.response.review.ReviewResponseDTO;
import kn.org.deliverybackend.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/products/{productId}/reviews")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AdminReviewController {

    private final ReviewService reviewService;

    /** Paginated reviews for a product — admin view */
    @GetMapping
    public ResponseEntity<Page<ReviewResponseDTO>> getReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(
                reviewService.getReviews(productId,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    /** Rating summary for a product */
    @GetMapping("/summary")
    public ResponseEntity<RatingSummaryDTO> getSummary(@PathVariable Long productId) {
        return ResponseEntity.ok(reviewService.getRatingSummary(productId));
    }

    /** Admin hard-delete (no userId check) */
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long productId,
            @PathVariable UUID reviewId) {
        reviewService.adminDeleteReview(productId, reviewId);
        return ResponseEntity.noContent().build();
    }
}
