package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.BundleOffer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BundleOfferRepository extends JpaRepository<BundleOffer, UUID> {

    @Query("SELECT b FROM BundleOffer b WHERE b.deleted = false AND b.isActive = true " +
            "ORDER BY b.sortOrder ASC, b.createdAt DESC")
    List<BundleOffer> findActive();

    /**
     * The admin list, in display order. {@code q} is a lower-case "%text%"
     * pattern (or null) matched against the name, label and description.
     */
    String ADMIN_FILTERS = "AND (:q IS NULL OR LOWER(b.name) LIKE :q ESCAPE '\\' " +
            "  OR LOWER(b.label) LIKE :q ESCAPE '\\' OR LOWER(b.description) LIKE :q ESCAPE '\\') ";

    @Query(value = "SELECT b FROM BundleOffer b WHERE b.deleted = false " + ADMIN_FILTERS +
            "ORDER BY b.sortOrder ASC, b.createdAt DESC, b.id",
            countQuery = "SELECT COUNT(b) FROM BundleOffer b WHERE b.deleted = false " + ADMIN_FILTERS)
    Page<BundleOffer> findForAdmin(@Param("q") String q, Pageable pageable);
}
