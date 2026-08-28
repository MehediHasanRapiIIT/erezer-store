package kn.org.deliverybackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import kn.org.deliverybackend.entity.Order;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cross-table aggregations for the admin reporting endpoints. All native SQL
 * to keep the JPA layer simple — these are read-only and analytics-shaped.
 *
 * Extends OrderRepository's entity {@code Order} to satisfy Spring Data's bean
 * requirement, but every method here is a native query that returns
 * {@code Object[]} or scalar.
 */
@Repository
public interface ReportRepository extends JpaRepository<Order, java.util.UUID> {

    /**
     * Daily revenue + order count, including all non-deleted orders.
     * Returns rows: [date(java.sql.Date), revenue(BigDecimal), orderCount(Long)].
     */
    @Query(value =
            "SELECT date_trunc('day', o.created_at)::date AS bucket, " +
            "       COALESCE(SUM(o.total_amount), 0) AS revenue, " +
            "       COUNT(*) AS order_count " +
            "FROM orders o " +
            "WHERE o.deleted = false " +
            "  AND o.created_at >= :from AND o.created_at <  :to " +
            "GROUP BY bucket " +
            "ORDER BY bucket ASC",
            nativeQuery = true)
    List<Object[]> revenueDaily(@Param("from") LocalDateTime from,
                                @Param("to")   LocalDateTime to);

    /**
     * Weekly revenue (ISO week start, Monday). Rows: [date, revenue, count].
     */
    @Query(value =
            "SELECT date_trunc('week', o.created_at)::date AS bucket, " +
            "       COALESCE(SUM(o.total_amount), 0) AS revenue, " +
            "       COUNT(*) AS order_count " +
            "FROM orders o " +
            "WHERE o.deleted = false " +
            "  AND o.created_at >= :from AND o.created_at <  :to " +
            "GROUP BY bucket " +
            "ORDER BY bucket ASC",
            nativeQuery = true)
    List<Object[]> revenueWeekly(@Param("from") LocalDateTime from,
                                 @Param("to")   LocalDateTime to);

    /**
     * Monthly revenue. Rows: [date(first-of-month), revenue, count].
     */
    @Query(value =
            "SELECT date_trunc('month', o.created_at)::date AS bucket, " +
            "       COALESCE(SUM(o.total_amount), 0) AS revenue, " +
            "       COUNT(*) AS order_count " +
            "FROM orders o " +
            "WHERE o.deleted = false " +
            "  AND o.created_at >= :from AND o.created_at <  :to " +
            "GROUP BY bucket " +
            "ORDER BY bucket ASC",
            nativeQuery = true)
    List<Object[]> revenueMonthly(@Param("from") LocalDateTime from,
                                  @Param("to")   LocalDateTime to);

    /**
     * Top products by units sold within the window. Joins order_item → product;
     * excludes orders in cancelled / returned terminal states.
     * Rows: [productId(Long), name(String), imageUrl(String), units(Long), revenue(BigDecimal)].
     */
    @Query(value =
            "SELECT p.id AS product_id, p.name AS product_name, p.image_url AS image_url, " +
            "       COALESCE(SUM(oi.quantity), 0) AS units_sold, " +
            "       COALESCE(SUM(oi.quantity * oi.price_at_order), 0) AS revenue " +
            "FROM order_item oi " +
            "JOIN orders  o ON o.id = oi.order_id AND o.deleted = false " +
            "JOIN product p ON p.id = oi.product_id " +
            "WHERE o.created_at >= :from AND o.created_at < :to " +
            "  AND o.order_status NOT IN ('CANCELLED','RETURNED') " +
            "GROUP BY p.id, p.name, p.image_url " +
            "ORDER BY units_sold DESC, revenue DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Object[]> topProducts(@Param("from") LocalDateTime from,
                                @Param("to")   LocalDateTime to,
                                @Param("limit") int limit);

    /**
     * Top categories aggregated through product. Rows: [categoryId, categoryName, units, revenue].
     */
    @Query(value =
            "SELECT c.id AS category_id, c.name AS category_name, " +
            "       COALESCE(SUM(oi.quantity), 0) AS units_sold, " +
            "       COALESCE(SUM(oi.quantity * oi.price_at_order), 0) AS revenue " +
            "FROM order_item oi " +
            "JOIN orders   o ON o.id = oi.order_id AND o.deleted = false " +
            "JOIN product  p ON p.id = oi.product_id " +
            "JOIN category c ON c.id = p.category_id " +
            "WHERE o.created_at >= :from AND o.created_at < :to " +
            "  AND o.order_status NOT IN ('CANCELLED','RETURNED') " +
            "GROUP BY c.id, c.name " +
            "ORDER BY revenue DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Object[]> topCategories(@Param("from") LocalDateTime from,
                                  @Param("to")   LocalDateTime to,
                                  @Param("limit") int limit);

    /**
     * One-row summary. [totalOrders, deliveredOrders, cancelledOrders, returnedOrders,
     *                    grossRevenue, netRevenue, uniqueCustomers]
     */
    @Query(value =
            "SELECT " +
            "  COUNT(*) AS total_orders, " +
            "  SUM(CASE WHEN o.order_status = 'DELIVERED' THEN 1 ELSE 0 END) AS delivered, " +
            "  SUM(CASE WHEN o.order_status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled, " +
            "  SUM(CASE WHEN o.order_status = 'RETURNED'  THEN 1 ELSE 0 END) AS returned, " +
            "  COALESCE(SUM(o.total_amount), 0) AS gross_revenue, " +
            "  COALESCE(SUM(CASE WHEN o.order_status NOT IN ('CANCELLED','RETURNED') " +
            "                    THEN o.total_amount ELSE 0 END), 0) AS net_revenue, " +
            "  COUNT(DISTINCT o.client_id) AS unique_customers " +
            "FROM orders o " +
            "WHERE o.deleted = false " +
            "  AND o.created_at >= :from AND o.created_at < :to",
            nativeQuery = true)
    Object[] salesSummary(@Param("from") LocalDateTime from,
                          @Param("to")   LocalDateTime to);

    /**
     * Customer LTV — only counts orders that actually contributed revenue
     * (not cancelled, not returned). Rows:
     * [userId(UUID), firstName, lastName, email,
     *  orderCount(Long), lifetimeRevenue(BigDecimal),
     *  firstOrderAt(Timestamp), lastOrderAt(Timestamp)].
     */
    @Query(value =
            "SELECT u.id AS user_id, u.first_name, u.last_name, u.email, " +
            "       COUNT(o.id) AS order_count, " +
            "       COALESCE(SUM(o.total_amount), 0) AS lifetime_revenue, " +
            "       MIN(o.created_at) AS first_order_at, " +
            "       MAX(o.created_at) AS last_order_at " +
            "FROM users u " +
            "JOIN orders o ON o.client_id = u.id AND o.deleted = false " +
            "                AND o.order_status NOT IN ('CANCELLED','RETURNED') " +
            "WHERE u.deleted = false " +
            "GROUP BY u.id, u.first_name, u.last_name, u.email " +
            "ORDER BY lifetime_revenue DESC " +
            "LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Object[]> customerLifetimeValue(@Param("limit") int limit,
                                          @Param("offset") int offset);

    @Query(value =
            "SELECT COUNT(DISTINCT o.client_id) " +
            "FROM orders o " +
            "WHERE o.deleted = false " +
            "  AND o.order_status NOT IN ('CANCELLED','RETURNED')",
            nativeQuery = true)
    long countCustomersWithOrders();
}
