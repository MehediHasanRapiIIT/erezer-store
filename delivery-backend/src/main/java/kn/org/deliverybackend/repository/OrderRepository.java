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

    /** One customer's order history, newest first; the id breaks ties so paging never repeats an order. */
    @Query(value = "SELECT o FROM Order o WHERE o.clientId = :clientId AND o.deleted = false " +
            "ORDER BY o.createdAt DESC, o.id",
            countQuery = "SELECT COUNT(o) FROM Order o WHERE o.clientId = :clientId AND o.deleted = false")
    Page<Order> findHistoryForClient(@Param("clientId") UUID clientId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.id = :orderId AND o.clientId = :clientId AND o.deleted = false")
    Optional<Order> findByIdAndClientId(UUID orderId, UUID clientId);

    @Query("SELECT o FROM Order o WHERE o.deleted = false ORDER BY o.createdAt DESC")
    List<Order> findAllOrders();

    /**
     * Filters for the admin order list; each is left out when its parameter is
     * null. {@code q} is a "%text%" pattern matched against the order id, the
     * customer's name, phone and email (as recorded on the order or on their
     * profile) and the delivery address. Dates are a UTC window [fromUtc, toUtc).
     */
    String ADMIN_ORDER_FILTERS =
            "AND (:status IS NULL OR o.order_status = :status) " +
            "AND (:excludeStatus IS NULL OR o.order_status <> :excludeStatus) " +
            "AND (:payment IS NULL OR o.payment_method = :payment OR o.payment_method = :paymentAlias) " +
            "AND (:fromUtc IS NULL OR o.created_at >= CAST(:fromUtc AS timestamp)) " +
            "AND (:toUtc IS NULL OR o.created_at < CAST(:toUtc AS timestamp)) " +
            "AND (:q IS NULL OR CAST(o.id AS text) ILIKE :q OR o.customer_name ILIKE :q " +
            "  OR o.customer_phone ILIKE :q OR o.customer_email ILIKE :q OR o.delivery_address ILIKE :q " +
            "  OR o.client_id IN (SELECT u.id FROM users u WHERE " +
            "    TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')) ILIKE :q " +
            "    OR u.phone_number ILIKE :q OR u.email ILIKE :q) " +
            // A product code in the order's lines: the code it was placed with.
            "  OR EXISTS (SELECT 1 FROM order_item oi WHERE oi.order_id = o.id AND oi.product_code ILIKE :q)) ";

    /** The admin order list, newest first; the id breaks ties so pages never overlap. */
    @Query(value = "SELECT * FROM orders o WHERE o.deleted = false " + ADMIN_ORDER_FILTERS
            + "ORDER BY o.created_at DESC, o.id",
            countQuery = "SELECT COUNT(*) FROM orders o WHERE o.deleted = false " + ADMIN_ORDER_FILTERS,
            nativeQuery = true)
    Page<Order> findOrdersFiltered(
            @Param("status") String status,
            @Param("excludeStatus") String excludeStatus,
            @Param("payment") String payment,
            @Param("paymentAlias") String paymentAlias,
            @Param("fromUtc") String fromUtc,
            @Param("toUtc") String toUtc,
            @Param("q") String q,
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
