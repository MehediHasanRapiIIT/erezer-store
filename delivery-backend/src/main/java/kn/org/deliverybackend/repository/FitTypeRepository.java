package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.FitType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FitTypeRepository extends JpaRepository<FitType, Long> {

    @Query("SELECT f FROM FitType f ORDER BY f.sortOrder, f.id")
    List<FitType> findAllInOrder();

    boolean existsByCode(String code);

    /** How many live variants are in this fit: a fit in use cannot be removed. */
    @Query("SELECT COUNT(v) FROM Variant v WHERE v.fit = :code AND (v.deleted = false OR v.deleted IS NULL)")
    long countVariantsIn(@Param("code") String code);

    /** How many live products have a size in this fit. */
    @Query("SELECT COUNT(DISTINCT v.productId) FROM Variant v WHERE v.fit = :code AND (v.deleted = false OR v.deleted IS NULL)")
    long countProductsIn(@Param("code") String code);

    @Query("SELECT COUNT(f) FROM FitType f WHERE f.sizeChartId = :chartId")
    long countUsingChart(@Param("chartId") Long chartId);

    @Modifying
    @Query("UPDATE FitType f SET f.sizeChartId = NULL WHERE f.sizeChartId = :chartId")
    int clearChart(@Param("chartId") Long chartId);
}
