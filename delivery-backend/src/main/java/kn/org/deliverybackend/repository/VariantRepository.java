package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.Variant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VariantRepository extends JpaRepository<Variant, Long> {

    @Query("SELECT v FROM Variant v WHERE v.productId = :productId AND v.deleted = false " +
            "ORDER BY v.size ASC, v.id ASC")
    List<Variant> findByProductId(@Param("productId") Long productId);

    @Query("SELECT v FROM Variant v WHERE v.productId = :productId AND v.sku = :sku AND v.deleted = false")
    Optional<Variant> findByProductIdAndSku(@Param("productId") Long productId, @Param("sku") String sku);

    @Query("SELECT COALESCE(SUM(v.stockQuantity), 0) FROM Variant v " +
            "WHERE v.productId = :productId AND v.deleted = false")
    long sumStockByProduct(@Param("productId") Long productId);
}
