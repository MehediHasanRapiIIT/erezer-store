package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findTop10ByOrderByCreatedAtDesc();
    List<Product> findTop8ByOrderByCreatedAtDesc();
    List<Product> findTop9ByOrderByCreatedAtDesc();
    List<Product> findTop9ByIsNewArrivalTrueOrderByCreatedAtDesc();
    List<Product> findTop9ByIsFeaturedTrueOrderByCreatedAtDesc();
    List<Product> findByNameContainingIgnoreCase(String name);

    @Query(value = "SELECT * FROM product WHERE category_id = :categoryId", nativeQuery = true)
    List<Product> findByCategoryId(@Param("categoryId") Long categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT p FROM Product p WHERE p.deleted = false AND (" +
            "LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "ORDER BY p.createdAt DESC")
    Page<Product> searchAdmin(@Param("q") String q, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.deleted = false AND p.isAvailable = true AND p.stockQuantity > 0 AND (" +
            "LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "ORDER BY p.avgRating DESC, p.createdAt DESC")
    Page<Product> searchCustomer(@Param("q") String q, Pageable pageable);

    /**
     * Up to {@code limit} products from the same category, excluding the
     * caller and any soft-deleted/unavailable rows. Ordered by rating then
     * recency. Used by the "you may also like" carousel.
     */
    @Query("SELECT p FROM Product p WHERE p.deleted = false " +
            "AND p.isAvailable = true " +
            "AND p.categoryId = :categoryId " +
            "AND p.id <> :excludeId " +
            "ORDER BY p.avgRating DESC, p.createdAt DESC")
    List<Product> findRelated(@Param("categoryId") Long categoryId,
                              @Param("excludeId") Long excludeId,
                              Pageable pageable);
}
