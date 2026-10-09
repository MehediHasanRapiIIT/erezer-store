package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.SizeChart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SizeChartRepository extends JpaRepository<SizeChart, Long> {

    /** The library, by name. */
    @Query("SELECT c FROM SizeChart c WHERE c.deleted = false OR c.deleted IS NULL ORDER BY LOWER(c.name), c.id")
    List<SizeChart> findLive();

    @Query("SELECT c FROM SizeChart c WHERE c.id = :id AND (c.deleted = false OR c.deleted IS NULL)")
    Optional<SizeChart> findLive(@Param("id") Long id);

    @Query("SELECT c FROM SizeChart c WHERE c.isDefault = true AND (c.deleted = false OR c.deleted IS NULL) ORDER BY c.id")
    List<SizeChart> findDefaults();

    // How many products and categories name a chart, and letting go of one that is deleted.

    @Query("SELECT COUNT(p) FROM Product p WHERE (p.deleted = false OR p.deleted IS NULL) "
            + "AND (p.sizeChartId = :id OR p.regularFitSizeChartId = :id)")
    long countProductsUsing(@Param("id") Long id);

    @Query("SELECT COUNT(c) FROM Category c WHERE (c.deleted = false OR c.deleted IS NULL) AND c.sizeChartId = :id")
    long countCategoriesUsing(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Product p SET p.sizeChartId = NULL WHERE p.sizeChartId = :id")
    int clearFromProducts(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Product p SET p.regularFitSizeChartId = NULL WHERE p.regularFitSizeChartId = :id")
    int clearRegularFitFromProducts(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Category c SET c.sizeChartId = NULL WHERE c.sizeChartId = :id")
    int clearFromCategories(@Param("id") Long id);
}
