-- ============================================================================
-- Phase 11 — reporting indexes
-- ============================================================================
-- Every business report (day / week / month / year) filters orders by
-- created_at and joins order_item by order_id. Without these the report
-- queries scan the whole orders table on each dashboard load.
--
-- Partial on deleted = false because every report excludes soft-deleted rows.
-- Index-only, so safe to apply to a live database (no locks beyond the usual
-- CREATE INDEX share lock; the tables are small enough not to need
-- CONCURRENTLY, which Flyway cannot run inside a transaction anyway).
-- ============================================================================

CREATE INDEX IF NOT EXISTS ix_orders_created_at_live
    ON orders (created_at)
    WHERE deleted = false;

CREATE INDEX IF NOT EXISTS ix_orders_delivered_at_live
    ON orders (delivered_at)
    WHERE deleted = false AND delivered_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_orders_cancelled_at_live
    ON orders (cancelled_at)
    WHERE deleted = false AND cancelled_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_order_item_order_id
    ON order_item (order_id);
