package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.request.review.ReviewRequestDTO;
import kn.org.deliverybackend.dto.request.review.ReviewUpdateRequestDTO;
import kn.org.deliverybackend.dto.response.review.RatingSummaryDTO;
import kn.org.deliverybackend.dto.response.review.ReviewResponseDTO;
import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Review;
import kn.org.deliverybackend.entity.Users;
import kn.org.deliverybackend.exception.DuplicateResourceException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.exception.UnauthorizedReviewException;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.ReviewRepository;
import kn.org.deliverybackend.repository.UsersRepository;
import kn.org.deliverybackend.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UsersRepository usersRepository;

    @Override
    @Transactional
    public ReviewResponseDTO submitReview(Long productId, ReviewRequestDTO request) {
        // 1. Verify product exists
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        // 2. Verify order exists and belongs to user
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + request.getOrderId()));

        if (!order.getClientId().equals(request.getUserId())) {
            throw new UnauthorizedReviewException(
                    "Order does not belong to user: " + request.getUserId());
        }

        // 3. Verify order is DELIVERED
        if (!"DELIVERED".equalsIgnoreCase(order.getOrderStatus())) {
            throw new UnauthorizedReviewException(
                    "Reviews can only be submitted for delivered orders. Current status: " + order.getOrderStatus());
        }

        // 4. Check for duplicate review
        if (reviewRepository.existsByUserIdAndProductIdAndOrderId(
                request.getUserId(), productId, request.getOrderId())) {
            throw new DuplicateResourceException(
                    "You have already reviewed this product for this order");
        }

        // 5. Save review
        Review review = new Review();
        review.setProductId(productId);
        review.setUserId(request.getUserId());
        review.setOrderId(request.getOrderId());
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        Review saved = reviewRepository.save(review);

        // 6. Update product rating with pessimistic lock
        Product lockedProduct = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        double newAvg = ((lockedProduct.getAvgRating() * lockedProduct.getTotalReviews()) + request.getRating())
                / (lockedProduct.getTotalReviews() + 1);
        lockedProduct.setAvgRating(newAvg);
        lockedProduct.setTotalReviews(lockedProduct.getTotalReviews() + 1);
        productRepository.save(lockedProduct);

        return toResponseDTO(saved, request.getUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponseDTO> getReviews(Long productId, Pageable pageable) {
        productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        return reviewRepository.findByProductId(productId, pageable)
                .map(review -> toResponseDTO(review, review.getUserId()));
    }

    @Override
    @Transactional(readOnly = true)
    public RatingSummaryDTO getRatingSummary(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        // Initialize all star counts to 0
        Map<Integer, Long> breakdown = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            breakdown.put(i, 0L);
        }

        // Populate from GROUP BY query
        List<Object[]> counts = reviewRepository.countByRatingForProduct(productId);
        for (Object[] row : counts) {
            int star = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            breakdown.put(star, count);
        }

        return new RatingSummaryDTO(productId, product.getAvgRating(), product.getTotalReviews(), breakdown);
    }

    @Override
    @Transactional
    public ReviewResponseDTO updateReview(Long productId, UUID reviewId, ReviewUpdateRequestDTO request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + reviewId));

        if (review.getDeleted()) {
            throw new ResourceNotFoundException("Review not found with id: " + reviewId);
        }

        if (!review.getUserId().equals(request.getUserId())) {
            throw new UnauthorizedReviewException("You are not authorized to edit this review");
        }

        int oldRating = review.getRating();
        int newRating = request.getRating();

        review.setRating(newRating);
        review.setComment(request.getComment());
        reviewRepository.save(review);

        // Recalculate avg: newAvg = ((currentAvg * totalReviews) - oldRating + newRating) / totalReviews
        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        if (product.getTotalReviews() > 0) {
            double newAvg = ((product.getAvgRating() * product.getTotalReviews()) - oldRating + newRating)
                    / product.getTotalReviews();
            product.setAvgRating(newAvg);
            productRepository.save(product);
        }

        return toResponseDTO(review, review.getUserId());
    }

    @Override
    @Transactional
    public void deleteReview(Long productId, UUID reviewId, UUID requestingUserId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + reviewId));

        if (review.getDeleted()) {
            throw new ResourceNotFoundException("Review not found with id: " + reviewId);
        }

        if (!review.getUserId().equals(requestingUserId)) {
            throw new UnauthorizedReviewException("You are not authorized to delete this review");
        }

        int oldRating = review.getRating();

        // Soft delete
        review.setDeleted(true);
        reviewRepository.save(review);

        // Recalculate avg
        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        int newTotal = product.getTotalReviews() - 1;
        double newAvg = newTotal > 0
                ? ((product.getAvgRating() * product.getTotalReviews()) - oldRating) / newTotal
                : 0.0;
        product.setAvgRating(newAvg);
        product.setTotalReviews(newTotal);
        productRepository.save(product);
    }

    @Override
    @Transactional
    public void adminDeleteReview(Long productId, UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + reviewId));

        if (review.getDeleted()) {
            throw new ResourceNotFoundException("Review not found with id: " + reviewId);
        }

        int oldRating = review.getRating();
        review.setDeleted(true);
        reviewRepository.save(review);

        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        int newTotal = product.getTotalReviews() - 1;
        double newAvg = newTotal > 0
                ? ((product.getAvgRating() * product.getTotalReviews()) - oldRating) / newTotal
                : 0.0;
        product.setAvgRating(newAvg);
        product.setTotalReviews(newTotal);
        productRepository.save(product);
    }

    private ReviewResponseDTO toResponseDTO(Review review, UUID userId) {
        String userName = usersRepository.findById(userId)
                .map(u -> {
                    String first = u.getFirstName() != null ? u.getFirstName() : "";
                    String last = u.getLastName() != null ? u.getLastName() : "";
                    return (first + " " + last).trim();
                })
                .orElse("Unknown User");

        return new ReviewResponseDTO(
                review.getId(),
                review.getUserId(),
                userName,
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
