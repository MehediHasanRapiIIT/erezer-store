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

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    List<Product> findTop10ByOrderByCreatedAtDesc();
    List<Product> findTop8ByOrderByCreatedAtDesc();
    List<Product> findTop9ByOrderByCreatedAtDesc();
    List<Product> findTop9ByIsNewArrivalTrueOrderByCreatedAtDesc();
    List<Product> findTop9ByIsFeaturedTrueOrderByCreatedAtDesc();
    List<Product> findByNameContainingIgnoreCase(String name);

    @Query(value = "SELECT * FROM product WHERE category_id = :categoryId", nativeQuery = true)
    List<Product> findByCategoryId(@Param("categoryId") Long categoryId);

    /** A category's products, leaving out deleted ones, by name: the rows of a category price change. */
    @Query("SELECT p FROM Product p WHERE p.categoryId = :categoryId AND p.deleted = false ORDER BY p.name, p.id")
    List<Product> findLiveByCategory(@Param("categoryId") Long categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT p FROM Product p WHERE p.deleted = false AND (" +
            "LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(p.productCode) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "ORDER BY p.createdAt DESC")
    Page<Product> searchAdmin(@Param("q") String q, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.deleted = false AND p.isAvailable = true AND p.stockQuantity > 0 AND (" +
            "LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "ORDER BY p.avgRating DESC, p.createdAt DESC")
    Page<Product> searchCustomer(@Param("q") String q, Pageable pageable);

    /**
     * The Inventory page: products not deleted, optionally matching a
     * lower-case "%text%" pattern on name, SKU or product code, in id order.
     */
    @Query(value = "SELECT p FROM Product p WHERE (p.deleted = false OR p.deleted IS NULL) " +
            "AND (:q IS NULL OR LOWER(p.name) LIKE :q ESCAPE '\\' OR LOWER(p.sku) LIKE :q ESCAPE '\\' " +
            "     OR LOWER(p.productCode) LIKE :q ESCAPE '\\') " +
            "ORDER BY p.id",
            countQuery = "SELECT COUNT(p) FROM Product p WHERE (p.deleted = false OR p.deleted IS NULL) " +
                    "AND (:q IS NULL OR LOWER(p.name) LIKE :q ESCAPE '\\' OR LOWER(p.sku) LIKE :q ESCAPE '\\' " +
                    "     OR LOWER(p.productCode) LIKE :q ESCAPE '\\')")
    Page<Product> searchForInventory(@Param("q") String q, Pageable pageable);

    /**
     * Restock alerts: products not deleted that are out of stock (0) or at or
     * below their low-stock threshold. Uses the inventory record's figures when
     * the product has one, else the product's own, exactly like each stock row.
     * Same search as the Inventory page, in id order.
     */
    String LOW_STOCK_FILTERS = "WHERE (p.deleted = false OR p.deleted IS NULL) " +
            "AND ((i.id IS NOT NULL AND (i.stockQuantity = 0 " +
            "        OR (i.lowStockThreshold IS NOT NULL AND i.stockQuantity <= i.lowStockThreshold))) " +
            "  OR (i.id IS NULL AND (p.stockQuantity = 0 " +
            "        OR (p.lowStockThreshold IS NOT NULL AND p.stockQuantity <= p.lowStockThreshold)))) " +
            "AND (:q IS NULL OR LOWER(p.name) LIKE :q ESCAPE '\\' OR LOWER(p.sku) LIKE :q ESCAPE '\\' " +
            "     OR LOWER(p.productCode) LIKE :q ESCAPE '\\') ";

    @Query(value = "SELECT p FROM Product p LEFT JOIN Inventory i ON i.productId = p.id " + LOW_STOCK_FILTERS +
            "ORDER BY p.id",
            countQuery = "SELECT COUNT(p) FROM Product p LEFT JOIN Inventory i ON i.productId = p.id " + LOW_STOCK_FILTERS)
    Page<Product> findLowStock(@Param("q") String q, Pageable pageable);

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
