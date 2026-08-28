package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.TaxRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaxRuleRepository extends JpaRepository<TaxRule, Long> {

    /**
     * Best-match tax rule for the given zone: zone-specific row wins; otherwise
     * the global (zone_id IS NULL) row is used. Both must be active.
     */
    @Query("SELECT t FROM TaxRule t WHERE t.deleted = false AND t.isActive = true " +
            "AND (t.zoneId = :zoneId OR t.zoneId IS NULL) " +
            "ORDER BY CASE WHEN t.zoneId IS NULL THEN 1 ELSE 0 END ASC, t.id ASC")
    Optional<TaxRule> findApplicableForZone(@Param("zoneId") Long zoneId);
}
