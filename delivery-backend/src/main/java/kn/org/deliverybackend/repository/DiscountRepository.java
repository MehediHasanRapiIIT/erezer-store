package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.Discount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DiscountRepository extends JpaRepository<Discount, UUID> {

    @Query("SELECT d FROM Discount d WHERE d.isActive = true AND d.deleted = false")
    List<Discount> findActive();

    /**
     * The admin discount list, newest first. {@code q} is a lower-case "%text%"
     * pattern (or null) matched against the name and description.
     */
    String ADMIN_FILTERS = "AND (:q IS NULL OR LOWER(d.name) LIKE :q ESCAPE '\\' " +
            "  OR LOWER(d.description) LIKE :q ESCAPE '\\') ";

    @Query(value = "SELECT d FROM Discount d WHERE d.deleted = false " + ADMIN_FILTERS +
            "ORDER BY d.createdAt DESC, d.id",
            countQuery = "SELECT COUNT(d) FROM Discount d WHERE d.deleted = false " + ADMIN_FILTERS)
    Page<Discount> findForAdmin(@Param("q") String q, Pageable pageable);
}
