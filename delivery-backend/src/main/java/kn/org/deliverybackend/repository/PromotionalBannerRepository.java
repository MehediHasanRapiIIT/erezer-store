package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.PromotionalBanner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
}
