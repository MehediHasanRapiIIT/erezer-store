package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.ProductFitSizeChart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductFitSizeChartRepository extends JpaRepository<ProductFitSizeChart, ProductFitSizeChart.Key> {

    List<ProductFitSizeChart> findByProductId(Long productId);

    @Modifying
    @Query("DELETE FROM ProductFitSizeChart c WHERE c.productId = :productId AND c.fitCode = :fitCode")
    int deleteOne(@Param("productId") Long productId, @Param("fitCode") String fitCode);

    @Query("SELECT COUNT(DISTINCT c.productId) FROM ProductFitSizeChart c WHERE c.sizeChartId = :chartId")
    long countProductsUsing(@Param("chartId") Long chartId);

    @Modifying
    @Query("DELETE FROM ProductFitSizeChart c WHERE c.sizeChartId = :chartId")
    int clearChart(@Param("chartId") Long chartId);

    @Modifying
    @Query("DELETE FROM ProductFitSizeChart c WHERE c.fitCode = :fitCode")
    int clearFit(@Param("fitCode") String fitCode);
}
