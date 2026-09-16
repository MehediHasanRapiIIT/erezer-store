package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    @Query("SELECT c FROM Coupon c WHERE LOWER(c.code) = LOWER(:code) AND c.deleted = false")
    Optional<Coupon> findByCodeIgnoreCase(@Param("code") String code);

    /**
     * The admin coupon list, newest first. {@code q} is a lower-case "%text%"
     * pattern (or null) matched against the code and description.
     */
    String ADMIN_FILTERS = "AND (:q IS NULL OR LOWER(c.code) LIKE :q ESCAPE '\\' " +
            "  OR LOWER(c.description) LIKE :q ESCAPE '\\') ";

    @Query(value = "SELECT c FROM Coupon c WHERE c.deleted = false " + ADMIN_FILTERS +
            "ORDER BY c.createdAt DESC, c.id",
            countQuery = "SELECT COUNT(c) FROM Coupon c WHERE c.deleted = false " + ADMIN_FILTERS)
    Page<Coupon> findForAdmin(@Param("q") String q, Pageable pageable);
}
