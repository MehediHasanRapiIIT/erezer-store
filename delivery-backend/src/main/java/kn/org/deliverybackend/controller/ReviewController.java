package kn.org.deliverybackend.controller;

import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.request.review.ReviewRequestDTO;
import kn.org.deliverybackend.dto.request.review.ReviewUpdateRequestDTO;
import kn.org.deliverybackend.dto.response.review.RatingSummaryDTO;
import kn.org.deliverybackend.dto.response.review.ReviewResponseDTO;
import kn.org.deliverybackend.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/products/{productId}")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/reviews")
    public ResponseEntity<ReviewResponseDTO> submitReview(
            @PathVariable Long productId,
            @Valid @RequestBody ReviewRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.submitReview(productId, request));
    }

    @GetMapping("/reviews")
    public ResponseEntity<Page<ReviewResponseDTO>> getReviews(
            @PathVariable Long productId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(reviewService.getReviews(productId, pageable));
    }

    @GetMapping("/rating-summary")
    public ResponseEntity<RatingSummaryDTO> getRatingSummary(@PathVariable Long productId) {
        return ResponseEntity.ok(reviewService.getRatingSummary(productId));
    }

    @PutMapping("/reviews/{reviewId}")
    public ResponseEntity<ReviewResponseDTO> updateReview(
            @PathVariable Long productId,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewUpdateRequestDTO request) {
        return ResponseEntity.ok(reviewService.updateReview(productId, reviewId, request));
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long productId,
            @PathVariable UUID reviewId,
            @RequestParam UUID userId) {
        reviewService.deleteReview(productId, reviewId, userId);
        return ResponseEntity.noContent().build();
    }
}
