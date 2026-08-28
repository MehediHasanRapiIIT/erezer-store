package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    @Query("SELECT i FROM ProductImage i WHERE i.productId = :productId AND i.deleted = false " +
            "ORDER BY i.isPrimary DESC, i.sortOrder ASC, i.id ASC")
    List<ProductImage> findByProductId(@Param("productId") Long productId);

    @Query("SELECT i FROM ProductImage i WHERE i.productId = :productId AND i.isPrimary = true " +
            "AND i.deleted = false")
    Optional<ProductImage> findPrimaryByProductId(@Param("productId") Long productId);

    @Modifying
    @Query("UPDATE ProductImage i SET i.isPrimary = false " +
            "WHERE i.productId = :productId AND i.isPrimary = true")
    void clearPrimaryFlag(@Param("productId") Long productId);
}
