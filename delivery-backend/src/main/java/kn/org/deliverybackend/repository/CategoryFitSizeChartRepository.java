package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.CategoryFitSizeChart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryFitSizeChartRepository extends JpaRepository<CategoryFitSizeChart, CategoryFitSizeChart.Key> {

    List<CategoryFitSizeChart> findByCategoryId(Long categoryId);

    @Modifying
    @Query("DELETE FROM CategoryFitSizeChart c WHERE c.categoryId = :categoryId AND c.fitCode = :fitCode")
    int deleteOne(@Param("categoryId") Long categoryId, @Param("fitCode") String fitCode);

    @Query("SELECT COUNT(DISTINCT c.categoryId) FROM CategoryFitSizeChart c WHERE c.sizeChartId = :chartId")
    long countCategoriesUsing(@Param("chartId") Long chartId);

    @Modifying
    @Query("DELETE FROM CategoryFitSizeChart c WHERE c.sizeChartId = :chartId")
    int clearChart(@Param("chartId") Long chartId);

    @Modifying
    @Query("DELETE FROM CategoryFitSizeChart c WHERE c.fitCode = :fitCode")
    int clearFit(@Param("fitCode") String fitCode);
}
