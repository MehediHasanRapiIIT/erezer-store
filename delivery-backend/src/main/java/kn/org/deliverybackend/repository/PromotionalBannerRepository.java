package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.PromotionalBanner;
import kn.org.deliverybackend.enumeration.BannerSlot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PromotionalBannerRepository extends JpaRepository<PromotionalBanner, UUID> {

    @Query("SELECT b FROM PromotionalBanner b WHERE b.deleted = false AND (" +
            "LOWER(b.promotionTitle) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(b.promotionDetails) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "ORDER BY b.fromDate DESC")
    Page<PromotionalBanner> searchAdmin(@Param("q") String q, Pageable pageable);

    @Query("SELECT b FROM PromotionalBanner b WHERE b.deleted = false AND (" +
            "LOWER(b.promotionTitle) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(b.promotionDetails) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
            "(b.fromDate IS NULL OR b.fromDate <= :today) AND " +
            "(b.toDate IS NULL OR b.toDate >= :today) " +
            "ORDER BY b.fromDate DESC")
    Page<PromotionalBanner> searchCustomerActive(@Param("q") String q, @Param("today") LocalDate today, Pageable pageable);

    /**
     * The admin Banners page. {@code slot} (or null for every slot) picks one
     * band; {@code hero} must be true when that band is HERO, because rows saved
     * before slots existed have no slot and show there. {@code q} is a lower-case
     * "%text%" pattern (or null) matched against the headline, short line and
     * button words. Deleted rows are left out.
     */
    String ADMIN_FILTERS = "WHERE (b.deleted = false OR b.deleted IS NULL) " +
            "AND (:slot IS NULL OR b.slot = :slot OR (:hero = true AND b.slot IS NULL)) " +
            "AND (:q IS NULL OR LOWER(b.promotionTitle) LIKE :q ESCAPE '\\' " +
            "  OR LOWER(b.promotionDetails) LIKE :q ESCAPE '\\' OR LOWER(b.ctaLabel) LIKE :q ESCAPE '\\') ";

    /** Home-page order: top picture, the two tall ones, the tiles, the design-your-own photo; then Order within a band. */
    String ADMIN_ORDER = "ORDER BY CASE " +
            "WHEN b.slot IS NULL OR b.slot = kn.org.deliverybackend.enumeration.BannerSlot.HERO THEN 0 " +
            "WHEN b.slot = kn.org.deliverybackend.enumeration.BannerSlot.SPLIT_LEFT THEN 1 " +
            "WHEN b.slot = kn.org.deliverybackend.enumeration.BannerSlot.SPLIT_RIGHT THEN 2 " +
            "WHEN b.slot = kn.org.deliverybackend.enumeration.BannerSlot.GRID_1 THEN 3 " +
            "WHEN b.slot = kn.org.deliverybackend.enumeration.BannerSlot.GRID_2 THEN 4 " +
            "WHEN b.slot = kn.org.deliverybackend.enumeration.BannerSlot.GRID_3 THEN 5 " +
            "WHEN b.slot = kn.org.deliverybackend.enumeration.BannerSlot.GRID_4 THEN 6 " +
            "ELSE 7 END, COALESCE(b.sortOrder, 0), b.createdAt, b.id";

    @Query(value = "SELECT b FROM PromotionalBanner b " + ADMIN_FILTERS + ADMIN_ORDER,
            countQuery = "SELECT COUNT(b) FROM PromotionalBanner b " + ADMIN_FILTERS)
    Page<PromotionalBanner> findForAdmin(@Param("slot") BannerSlot slot, @Param("hero") boolean hero,
                                         @Param("q") String q, Pageable pageable);

    /** The first few banners in one band, in display order, without a count query. */
    @Query("SELECT b FROM PromotionalBanner b " + ADMIN_FILTERS + ADMIN_ORDER)
    List<PromotionalBanner> findFirstInSlot(@Param("slot") BannerSlot slot, @Param("hero") boolean hero,
                                            @Param("q") String q, Pageable pageable);

    /** How many banners each band holds; a null slot is counted by the caller as HERO. */
    @Query("SELECT b.slot, COUNT(b) FROM PromotionalBanner b WHERE (b.deleted = false OR b.deleted IS NULL) GROUP BY b.slot")
    List<Object[]> countBySlot();
}
