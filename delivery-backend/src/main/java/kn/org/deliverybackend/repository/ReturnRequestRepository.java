package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.ReturnRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, UUID> {

    @Query("SELECT r FROM ReturnRequest r WHERE r.userId = :userId AND r.deleted = false " +
            "ORDER BY r.requestedAt DESC")
    List<ReturnRequest> findByUser(@Param("userId") UUID userId);

    @Query("SELECT r FROM ReturnRequest r WHERE r.orderId = :orderId AND r.deleted = false " +
            "ORDER BY r.requestedAt DESC")
    List<ReturnRequest> findByOrder(@Param("orderId") UUID orderId);

    @Query("SELECT r FROM ReturnRequest r WHERE r.orderId = :orderId AND r.deleted = false " +
            "ORDER BY r.requestedAt DESC")
    Optional<ReturnRequest> findLatestByOrder(@Param("orderId") UUID orderId);

    /**
     * The admin returns list, newest first. {@code q} is a lower-case "%text%"
     * pattern (or null) matched against the customer email, the reason, and
     * the order and return numbers.
     */
    String ADMIN_FILTERS = "AND (:status IS NULL OR r.status = :status) " +
            "AND (:q IS NULL OR LOWER(r.customerEmail) LIKE :q ESCAPE '\\' " +
            "  OR LOWER(r.reason) LIKE :q ESCAPE '\\' " +
            "  OR LOWER(CAST(r.orderId AS String)) LIKE :q ESCAPE '\\' " +
            "  OR LOWER(CAST(r.id AS String)) LIKE :q ESCAPE '\\') ";

    @Query(value = "SELECT r FROM ReturnRequest r WHERE r.deleted = false " + ADMIN_FILTERS +
            "ORDER BY r.requestedAt DESC, r.id",
            countQuery = "SELECT COUNT(r) FROM ReturnRequest r WHERE r.deleted = false " + ADMIN_FILTERS)
    Page<ReturnRequest> findForAdmin(@Param("status") String status, @Param("q") String q, Pageable pageable);
}
