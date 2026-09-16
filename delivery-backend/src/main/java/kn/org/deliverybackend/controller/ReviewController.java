package kn.org.deliverybackend.controller;

import org.springframework.security.core.Authentication;
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
    private final kn.org.deliverybackend.service.RateLimiterService rateLimiter;

    @PostMapping("/reviews")
    public ResponseEntity<ReviewResponseDTO> submitReview(
            @PathVariable Long productId,
            @Valid @RequestBody ReviewRequestDTO request,
            Authentication authentication) {
        request.setUserId(customerId(authentication));
        rateLimiter.enforce("review:" + request.getUserId(), 10, java.time.Duration.ofHours(1));
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
            @Valid @RequestBody ReviewUpdateRequestDTO request,
            Authentication authentication) {
        request.setUserId(customerId(authentication));
        rateLimiter.enforce("review:" + request.getUserId(), 10, java.time.Duration.ofHours(1));
        return ResponseEntity.ok(reviewService.updateReview(productId, reviewId, request));
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long productId,
            @PathVariable UUID reviewId,
            @RequestParam(required = false) UUID userId,
            Authentication authentication) {
        // Any userId sent by the client is ignored: the author is the login.
        reviewService.deleteReview(productId, reviewId, customerId(authentication));
        return ResponseEntity.noContent().build();
    }

    /**
     * The logged-in customer. Reviews never trust an id sent in the request:
     * security only lets authenticated customers reach the write endpoints,
     * and their token's subject is their user id.
     */
    private static UUID customerId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
