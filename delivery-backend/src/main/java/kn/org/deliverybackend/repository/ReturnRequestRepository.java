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

    @Query(value = "SELECT r FROM ReturnRequest r WHERE r.deleted = false " +
            "AND (:status IS NULL OR r.status = :status) " +
            "ORDER BY r.requestedAt DESC",
            countQuery = "SELECT COUNT(r) FROM ReturnRequest r WHERE r.deleted = false " +
                    "AND (:status IS NULL OR r.status = :status)")
    Page<ReturnRequest> findForAdmin(@Param("status") String status, Pageable pageable);
}
