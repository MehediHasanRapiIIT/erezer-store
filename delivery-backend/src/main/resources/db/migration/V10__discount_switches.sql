-- ============================================================================
-- Phase 12 — discount on/off switches
-- ============================================================================
-- Lets the admin suspend automatic discounts without deleting the rules, at
-- three levels:
--
--   1. A master switch that stops every automatic discount at once.
--   2. Per-scope switches that stop all store-wide, all category-level or all
--      product-level rules.
--   3. Per-product and per-category exclusions, so one product or one whole
--      collection is never automatically discounted even by a store-wide rule.
--
-- Only automatic discounts (the `discount` table) are affected. A product's own
-- sale price, coupon codes, flash sales and bundle offers are deliberately left
-- alone; they are separate mechanisms an admin turns off in their own screens.
--
-- Every column is NULLABLE with no default so existing rows keep behaving
-- exactly as before: a NULL switch reads as "enabled" and a NULL exclusion
-- reads as "not excluded". Additive only, safe on a live database.
-- ============================================================================

ALTER TABLE store_settings
    ADD COLUMN IF NOT EXISTS discounts_enabled          BOOLEAN,
    ADD COLUMN IF NOT EXISTS discounts_global_enabled   BOOLEAN,
    ADD COLUMN IF NOT EXISTS discounts_category_enabled BOOLEAN,
    ADD COLUMN IF NOT EXISTS discounts_product_enabled  BOOLEAN;

COMMENT ON COLUMN store_settings.discounts_enabled IS
    'Master switch. False suspends every automatic discount. NULL = enabled.';
COMMENT ON COLUMN store_settings.discounts_global_enabled IS
    'False suspends discounts whose scope is GLOBAL. NULL = enabled.';
COMMENT ON COLUMN store_settings.discounts_category_enabled IS
    'False suspends discounts whose scope is CATEGORY. NULL = enabled.';
COMMENT ON COLUMN store_settings.discounts_product_enabled IS
    'False suspends discounts whose scope is PRODUCT. NULL = enabled.';

ALTER TABLE product
    ADD COLUMN IF NOT EXISTS discount_excluded BOOLEAN;

COMMENT ON COLUMN product.discount_excluded IS
    'True to keep this product at full price, ignoring every automatic discount '
    'including store-wide ones. NULL = not excluded.';

ALTER TABLE category
    ADD COLUMN IF NOT EXISTS discount_excluded BOOLEAN;

COMMENT ON COLUMN category.discount_excluded IS
    'True to keep every product in this category at full price, ignoring every '
    'automatic discount including store-wide ones. NULL = not excluded.';

-- The discount engine asks "which categories are excluded?" on every priced
-- line. Partial index so it only holds the few excluded rows.
CREATE INDEX IF NOT EXISTS ix_category_discount_excluded
    ON category (id)
    WHERE discount_excluded = true;
