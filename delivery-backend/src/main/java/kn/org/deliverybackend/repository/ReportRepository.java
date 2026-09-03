package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Cross-table aggregations for the admin reporting endpoints. All native SQL,
 * read-only and analytics-shaped; every figure the admin panel shows comes
 * from one of these queries, so the definitions live here.
 *
 * <h3>Time handling</h3>
 * {@code orders.created_at} is a {@code timestamp without time zone} holding
 * UTC wall-clock values. Windows are passed in as UTC strings already
 * converted from business-local midnight by {@code BusinessCalendar}
 * ({@code fromUtc}/{@code toUtc}, half-open), and bucketing converts each row
 * to business-local time first: {@code (created_at AT TIME ZONE 'UTC') AT TIME
 * ZONE :zone}. Strings rather than JDBC timestamps so no driver or Hibernate
 * time-zone setting can shift them.
 *
 * <h3>Counting rule</h3>
 * "Counted" orders are {@code order_status NOT IN ('CANCELLED','RETURNED')}.
 * Only those contribute to revenue, units and customers; placed-order counts
 * include everything so cancellation rates can be derived.
 */
@Repository
public interface ReportRepository extends JpaRepository<Order, UUID> {

    // ── shared SQL fragments (compile-time constants) ───────────────────────

    String WINDOW =
            " COALESCE(o.deleted, false) = false" +
            " AND o.created_at >= CAST(:fromUtc AS timestamp)" +
            " AND o.created_at <  CAST(:toUtc   AS timestamp) ";

    String COUNTED = " (o.order_status NOT IN ('CANCELLED','RETURNED')) ";

    String TOTAL = " COALESCE(o.total_amount, 0) ";

    /** shipping_fee is the modern column; delivery_charge the legacy double. */
    String SHIPPING = " COALESCE(o.shipping_fee, CAST(o.delivery_charge AS numeric), 0) ";

    /** Registered customer, else guest identified by email, else phone. */
    String CUSTOMER_KEY = " COALESCE(CAST(o.client_id AS text), lower(o.customer_email), o.customer_phone) ";

    /** Row timestamp in business-local time. */
    String LOCAL_TS = " ((o.created_at AT TIME ZONE 'UTC') AT TIME ZONE CAST(:zone AS text)) ";

    /** Per-order roll-up of line items: units and custom-size surcharges. */
    String ITEMS_JOIN =
            " LEFT JOIN (SELECT oi.order_id," +
            "                   SUM(COALESCE(oi.quantity, 0))         AS units," +
            "                   SUM(COALESCE(oi.custom_surcharge, 0)) AS surcharge" +
            "            FROM order_item oi" +
            "            WHERE COALESCE(oi.deleted, false) = false" +
            "            GROUP BY oi.order_id) s ON s.order_id = o.id ";

    /**
     * Merchandise value before discounts. Orders from before subtotal_amount
     * existed are reconstructed from the total so the breakdown still adds up.
     */
    String GROSS =
            " COALESCE(o.subtotal_amount," +
            "          " + TOTAL + " - " + SHIPPING + " - COALESCE(s.surcharge, 0)" +
            "          - COALESCE(o.tax_amount, 0) + COALESCE(o.discount_amount, 0)) ";

    // ── window metrics ──────────────────────────────────────────────────────

    /**
     * Everything about the orders placed inside a window, in one row:
     * <pre>
     *  0 placed_orders      1 orders (counted)   2 delivered_orders
     *  3 cancelled_orders   4 returned_orders    5 pending_orders
     *  6 in_progress_orders 7 gross_sales        8 discounts
     *  9 shipping          10 vat               11 net_revenue
     * 12 delivered_revenue 13 cancelled_value   14 returned_value
     * 15 unique_customers  16 surcharges        17 units_sold
     * </pre>
     */
    @Query(value =
            "SELECT" +
            "  COUNT(*)                                                         AS placed_orders," +
            "  COUNT(*) FILTER (WHERE " + COUNTED + ")                          AS orders," +
            "  COUNT(*) FILTER (WHERE o.order_status = 'DELIVERED')             AS delivered_orders," +
            "  COUNT(*) FILTER (WHERE o.order_status = 'CANCELLED')             AS cancelled_orders," +
            "  COUNT(*) FILTER (WHERE o.order_status = 'RETURNED')              AS returned_orders," +
            "  COUNT(*) FILTER (WHERE o.order_status IN ('PLACED','PENDING'))   AS pending_orders," +
            "  COUNT(*) FILTER (WHERE " + COUNTED +
            "                     AND o.order_status NOT IN ('PLACED','PENDING','DELIVERED')) AS in_progress_orders," +
            "  COALESCE(SUM(" + GROSS + ") FILTER (WHERE " + COUNTED + "), 0)                     AS gross_sales," +
            "  COALESCE(SUM(COALESCE(o.discount_amount, 0)) FILTER (WHERE " + COUNTED + "), 0)   AS discounts," +
            "  COALESCE(SUM(" + SHIPPING + ") FILTER (WHERE " + COUNTED + "), 0)                  AS shipping," +
            "  COALESCE(SUM(COALESCE(o.tax_amount, 0)) FILTER (WHERE " + COUNTED + "), 0)        AS vat," +
            "  COALESCE(SUM(" + TOTAL + ") FILTER (WHERE " + COUNTED + "), 0)                     AS net_revenue," +
            "  COALESCE(SUM(" + TOTAL + ") FILTER (WHERE o.order_status = 'DELIVERED'), 0)        AS delivered_revenue," +
            "  COALESCE(SUM(" + TOTAL + ") FILTER (WHERE o.order_status = 'CANCELLED'), 0)        AS cancelled_value," +
            "  COALESCE(SUM(" + TOTAL + ") FILTER (WHERE o.order_status = 'RETURNED'), 0)         AS returned_value," +
            "  COUNT(DISTINCT " + CUSTOMER_KEY + ") FILTER (WHERE " + COUNTED + ")                AS unique_customers," +
            "  COALESCE(SUM(COALESCE(s.surcharge, 0)) FILTER (WHERE " + COUNTED + "), 0)          AS surcharges," +
            "  COALESCE(SUM(COALESCE(s.units, 0)) FILTER (WHERE " + COUNTED + "), 0)              AS units_sold " +
            "FROM orders o " +
            ITEMS_JOIN +
            "WHERE " + WINDOW,
            nativeQuery = true)
    List<Object[]> windowMetrics(@Param("fromUtc") String fromUtc,
                                 @Param("toUtc") String toUtc);

    /**
     * Customers whose first ever counted order falls inside the window.
     * Looks at all history, not just the window, so a returning customer is
     * never mistaken for a new one.
     */
    @Query(value =
            "SELECT COUNT(*) FROM (" +
            "  SELECT " + CUSTOMER_KEY + " AS customer_key, MIN(o.created_at) AS first_at" +
            "  FROM orders o" +
            "  WHERE COALESCE(o.deleted, false) = false AND " + COUNTED +
            "    AND " + CUSTOMER_KEY + " IS NOT NULL" +
            "  GROUP BY 1" +
            ") f " +
            "WHERE f.first_at >= CAST(:fromUtc AS timestamp) " +
            "  AND f.first_at <  CAST(:toUtc   AS timestamp)",
            nativeQuery = true)
    long newCustomers(@Param("fromUtc") String fromUtc,
                      @Param("toUtc") String toUtc);

    /** Orders whose delivery happened inside the window: [count, value]. */
    @Query(value =
            "SELECT COUNT(*), COALESCE(SUM(" + TOTAL + "), 0) " +
            "FROM orders o " +
            "WHERE COALESCE(o.deleted, false) = false " +
            "  AND o.order_status = 'DELIVERED' " +
            "  AND o.delivered_at >= CAST(:fromUtc AS timestamp) " +
            "  AND o.delivered_at <  CAST(:toUtc   AS timestamp)",
            nativeQuery = true)
    List<Object[]> deliveredInWindow(@Param("fromUtc") String fromUtc,
                                     @Param("toUtc") String toUtc);

    /** Orders whose cancellation happened inside the window: [count, value]. */
    @Query(value =
            "SELECT COUNT(*), COALESCE(SUM(" + TOTAL + "), 0) " +
            "FROM orders o " +
            "WHERE COALESCE(o.deleted, false) = false " +
            "  AND o.order_status = 'CANCELLED' " +
            "  AND o.cancelled_at >= CAST(:fromUtc AS timestamp) " +
            "  AND o.cancelled_at <  CAST(:toUtc   AS timestamp)",
            nativeQuery = true)
    List<Object[]> cancelledInWindow(@Param("fromUtc") String fromUtc,
                                     @Param("toUtc") String toUtc);

    // ── time series ─────────────────────────────────────────────────────────

    /**
     * Orders bucketed in business-local time. {@code unit} is a
     * {@code date_trunc} field ('hour', 'day', 'week', 'month', 'year').
     * PostgreSQL weeks start on Monday, so a configured Sunday/Saturday start
     * is handled by shifting {@code shift} days before truncating and back
     * after ({@code BusinessCalendar#weekShiftDays}).
     * Rows: [bucket 'YYYY-MM-DD HH24:MI:SS' local, placed, counted, cancelled, net_revenue].
     */
    @Query(value =
            "SELECT to_char(x.b, 'YYYY-MM-DD HH24:MI:SS')                       AS bucket," +
            "       COUNT(*)                                                    AS placed," +
            "       COUNT(*) FILTER (WHERE x.counted)                           AS orders," +
            "       COUNT(*) FILTER (WHERE x.status = 'CANCELLED')              AS cancelled," +
            "       COALESCE(SUM(x.total) FILTER (WHERE x.counted), 0)          AS net_revenue " +
            "FROM (" +
            "  SELECT o.order_status AS status," +
            "         " + COUNTED + " AS counted," +
            "         " + TOTAL + " AS total," +
            "         CASE WHEN CAST(:unit AS text) = 'week'" +
            "              THEN date_trunc('week', " + LOCAL_TS + " + CAST(:shift AS int) * INTERVAL '1 day')" +
            "                   - CAST(:shift AS int) * INTERVAL '1 day'" +
            "              ELSE date_trunc(CAST(:unit AS text), " + LOCAL_TS + ")" +
            "         END AS b" +
            "  FROM orders o" +
            "  WHERE " + WINDOW +
            ") x " +
            "GROUP BY x.b " +
            "ORDER BY x.b",
            nativeQuery = true)
    List<Object[]> buckets(@Param("fromUtc") String fromUtc,
                           @Param("toUtc") String toUtc,
                           @Param("zone") String zone,
                           @Param("unit") String unit,
                           @Param("shift") int shift);

    // ── distributions ───────────────────────────────────────────────────────

    /** Rows: [status, count, value]. */
    @Query(value =
            "SELECT o.order_status, COUNT(*), COALESCE(SUM(" + TOTAL + "), 0) " +
            "FROM orders o " +
            "WHERE " + WINDOW +
            "GROUP BY o.order_status " +
            "ORDER BY COUNT(*) DESC, o.order_status",
            nativeQuery = true)
    List<Object[]> ordersByStatus(@Param("fromUtc") String fromUtc,
                                  @Param("toUtc") String toUtc);

    /**
     * Rows: [method, orders, revenue, delivered_orders, delivered_revenue,
     *        undelivered_orders, undelivered_value, cancelled_orders].
     */
    @Query(value =
            "SELECT COALESCE(o.payment_method, 'UNKNOWN')                                        AS method," +
            "       COUNT(*) FILTER (WHERE " + COUNTED + ")                                       AS orders," +
            "       COALESCE(SUM(" + TOTAL + ") FILTER (WHERE " + COUNTED + "), 0)                AS revenue," +
            "       COUNT(*) FILTER (WHERE o.order_status = 'DELIVERED')                          AS delivered_orders," +
            "       COALESCE(SUM(" + TOTAL + ") FILTER (WHERE o.order_status = 'DELIVERED'), 0)   AS delivered_revenue," +
            "       COUNT(*) FILTER (WHERE " + COUNTED + " AND o.order_status <> 'DELIVERED')     AS undelivered_orders," +
            "       COALESCE(SUM(" + TOTAL + ") FILTER (WHERE " + COUNTED +
            "                                          AND o.order_status <> 'DELIVERED'), 0)     AS undelivered_value," +
            "       COUNT(*) FILTER (WHERE o.order_status = 'CANCELLED')                          AS cancelled_orders " +
            "FROM orders o " +
            "WHERE " + WINDOW +
            "GROUP BY 1 " +
            "ORDER BY 3 DESC, 1",
            nativeQuery = true)
    List<Object[]> ordersByPayment(@Param("fromUtc") String fromUtc,
                                   @Param("toUtc") String toUtc);

    // ── rankings ────────────────────────────────────────────────────────────

    /**
     * Top products by units sold on counted orders in the window.
     * Rows: [productId, name, imageUrl, units, salesValue, orderCount].
     */
    @Query(value =
            "SELECT p.id, p.name, p.image_url," +
            "       COALESCE(SUM(oi.quantity), 0)                                            AS units_sold," +
            "       COALESCE(SUM(oi.quantity * COALESCE(oi.price_at_order, 0)" +
            "                    + COALESCE(oi.custom_surcharge, 0)), 0)                     AS sales_value," +
            "       COUNT(DISTINCT o.id)                                                     AS order_count " +
            "FROM order_item oi " +
            "JOIN orders  o ON o.id = oi.order_id " +
            "JOIN product p ON p.id = oi.product_id " +
            "WHERE " + WINDOW +
            "  AND " + COUNTED +
            "  AND COALESCE(oi.deleted, false) = false " +
            "GROUP BY p.id, p.name, p.image_url " +
            "ORDER BY units_sold DESC, sales_value DESC, p.name " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Object[]> topProducts(@Param("fromUtc") String fromUtc,
                               @Param("toUtc") String toUtc,
                               @Param("limit") int limit);

    /**
     * Top categories by sales value on counted orders in the window.
     * Rows: [categoryId, name, units, salesValue, orderCount].
     */
    @Query(value =
            "SELECT c.id, c.name," +
            "       COALESCE(SUM(oi.quantity), 0)                                            AS units_sold," +
            "       COALESCE(SUM(oi.quantity * COALESCE(oi.price_at_order, 0)" +
            "                    + COALESCE(oi.custom_surcharge, 0)), 0)                     AS sales_value," +
            "       COUNT(DISTINCT o.id)                                                     AS order_count " +
            "FROM order_item oi " +
            "JOIN orders   o ON o.id = oi.order_id " +
            "JOIN product  p ON p.id = oi.product_id " +
            "JOIN category c ON c.id = p.category_id " +
            "WHERE " + WINDOW +
            "  AND " + COUNTED +
            "  AND COALESCE(oi.deleted, false) = false " +
            "GROUP BY c.id, c.name " +
            "ORDER BY sales_value DESC, units_sold DESC, c.name " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Object[]> topCategories(@Param("fromUtc") String fromUtc,
                                 @Param("toUtc") String toUtc,
                                 @Param("limit") int limit);

    // ── customers ───────────────────────────────────────────────────────────

    /**
     * Customer lifetime value over all history. Only counted orders. Rows:
     * [userId, firstName, lastName, email, orderCount, lifetimeRevenue,
     *  firstOrderAt, lastOrderAt].
     */
    @Query(value =
            "SELECT u.id AS user_id, u.first_name, u.last_name, u.email, " +
            "       COUNT(o.id) AS order_count, " +
            "       COALESCE(SUM(o.total_amount), 0) AS lifetime_revenue, " +
            "       MIN(o.created_at) AS first_order_at, " +
            "       MAX(o.created_at) AS last_order_at " +
            "FROM users u " +
            "JOIN orders o ON o.client_id = u.id AND COALESCE(o.deleted, false) = false " +
            "                AND o.order_status NOT IN ('CANCELLED','RETURNED') " +
            "WHERE COALESCE(u.deleted, false) = false " +
            "GROUP BY u.id, u.first_name, u.last_name, u.email " +
            "ORDER BY lifetime_revenue DESC " +
            "LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Object[]> customerLifetimeValue(@Param("limit") int limit,
                                         @Param("offset") int offset);

    @Query(value =
            "SELECT COUNT(DISTINCT o.client_id) " +
            "FROM orders o " +
            "WHERE COALESCE(o.deleted, false) = false " +
            "  AND o.order_status NOT IN ('CANCELLED','RETURNED')",
            nativeQuery = true)
    long countCustomersWithOrders();
}
