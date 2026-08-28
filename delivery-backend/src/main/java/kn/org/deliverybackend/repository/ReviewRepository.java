package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByUserIdAndProductIdAndOrderId(UUID userId, Long productId, UUID orderId);

    @Query("SELECT r FROM Review r WHERE r.productId = :productId AND r.deleted = false ORDER BY r.createdAt DESC")
    Page<Review> findByProductId(@Param("productId") Long productId, Pageable pageable);

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.productId = :productId AND r.deleted = false GROUP BY r.rating")
    List<Object[]> countByRatingForProduct(@Param("productId") Long productId);

    @Query("SELECT r FROM Review r WHERE r.deleted = false AND " +
            "LOWER(r.comment) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "ORDER BY r.createdAt DESC")
    Page<Review> searchAdmin(@Param("q") String q, Pageable pageable);
}
