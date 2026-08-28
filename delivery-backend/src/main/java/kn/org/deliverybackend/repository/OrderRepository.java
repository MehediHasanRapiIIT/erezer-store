package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.Order;
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
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("SELECT o FROM Order o WHERE o.clientId = :clientId AND o.deleted = false ORDER BY o.createdAt DESC")
    List<Order> findByClientId(UUID clientId);

    @Query("SELECT o FROM Order o WHERE o.id = :orderId AND o.clientId = :clientId AND o.deleted = false")
    Optional<Order> findByIdAndClientId(UUID orderId, UUID clientId);

    @Query("SELECT o FROM Order o WHERE o.deleted = false ORDER BY o.createdAt DESC")
    List<Order> findAllOrders();

    @Query(value = "SELECT * FROM orders o WHERE o.deleted = false " +
            "AND (:status IS NULL OR o.order_status = :status) " +
            "AND (:excludeStatus IS NULL OR o.order_status <> :excludeStatus) " +
            "AND (:fromDate IS NULL OR o.created_at >= CAST(:fromDate AS timestamp)) " +
            "AND (:toDate IS NULL OR o.created_at <= CAST(:toDate AS timestamp)) " +
            "ORDER BY o.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM orders o WHERE o.deleted = false " +
                    "AND (:status IS NULL OR o.order_status = :status) " +
                    "AND (:excludeStatus IS NULL OR o.order_status <> :excludeStatus) " +
                    "AND (:fromDate IS NULL OR o.created_at >= CAST(:fromDate AS timestamp)) " +
                    "AND (:toDate IS NULL OR o.created_at <= CAST(:toDate AS timestamp))",
            nativeQuery = true)
    Page<Order> findOrdersFiltered(
            @Param("status") String status,
            @Param("excludeStatus") String excludeStatus,
            @Param("fromDate") String fromDate,
            @Param("toDate") String toDate,
            Pageable pageable);

    @Query(value = "SELECT o FROM Order o WHERE o.deleted = false ORDER BY o.createdAt DESC",
            countQuery = "SELECT COUNT(o) FROM Order o WHERE o.deleted = false")
    Page<Order> findAllOrdersPaged(Pageable pageable);

    @Query(value = "SELECT o FROM Order o WHERE o.orderStatus = :status AND o.deleted = false ORDER BY o.createdAt DESC",
            countQuery = "SELECT COUNT(o) FROM Order o WHERE o.orderStatus = :status AND o.deleted = false")
    Page<Order> findByOrderStatusPaged(@Param("status") String status, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.orderStatus = :status AND o.deleted = false ORDER BY o.createdAt DESC")
    List<Order> findByOrderStatus(@Param("status") String status);

    @Query(value = "SELECT * FROM orders o WHERE o.deleted = false AND (" +
            "CAST(o.id AS text) ILIKE CONCAT('%', :q, '%') OR " +
            "LOWER(o.order_status) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(o.delivery_address) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "ORDER BY o.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM orders o WHERE o.deleted = false AND (" +
                    "CAST(o.id AS text) ILIKE CONCAT('%', :q, '%') OR " +
                    "LOWER(o.order_status) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                    "LOWER(o.delivery_address) LIKE LOWER(CONCAT('%', :q, '%')))",
            nativeQuery = true)
    Page<Order> searchAdmin(@Param("q") String q, Pageable pageable);
}
