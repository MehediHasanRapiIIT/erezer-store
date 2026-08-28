-- ============================================================================
-- V6 — Phase 8: Abandoned-cart tracking
--
-- Per-row last_emailed_at lets the scheduled job know whether the customer
-- has already been pinged for the *current* cart state (we re-set
-- last_emailed_at to NULL whenever the cart row is touched in app code via
-- the normal UPDATE — see CartService).
-- ============================================================================

ALTER TABLE cart ADD COLUMN IF NOT EXISTS last_emailed_at TIMESTAMP;

-- The abandoned-cart scan filters on (user_id, updated_at, last_emailed_at).
-- A composite index keeps the nightly job O(log n) even as the cart grows.
CREATE INDEX IF NOT EXISTS idx_cart_abandoned_scan
    ON cart (user_id, updated_at)
    WHERE deleted = FALSE;
