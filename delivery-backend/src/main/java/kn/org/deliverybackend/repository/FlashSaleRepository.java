package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.FlashSale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FlashSaleRepository extends JpaRepository<FlashSale, UUID> {

    /** Active, non-deleted campaigns; window filtering is applied in the service. */
    @Query("SELECT f FROM FlashSale f WHERE f.isActive = true AND f.deleted = false")
    List<FlashSale> findActive();

    /**
     * The admin list, latest-ending first. {@code q} is a lower-case "%text%"
     * pattern (or null) matched against the name, label and coupon code.
     */
    String ADMIN_FILTERS = "AND (:q IS NULL OR LOWER(f.name) LIKE :q ESCAPE '\\' " +
            "  OR LOWER(f.label) LIKE :q ESCAPE '\\' OR LOWER(f.couponCode) LIKE :q ESCAPE '\\') ";

    @Query(value = "SELECT f FROM FlashSale f WHERE f.deleted = false " + ADMIN_FILTERS +
            "ORDER BY f.endsAt DESC NULLS LAST, f.id",
            countQuery = "SELECT COUNT(f) FROM FlashSale f WHERE f.deleted = false " + ADMIN_FILTERS)
    Page<FlashSale> findForAdmin(@Param("q") String q, Pageable pageable);
}
