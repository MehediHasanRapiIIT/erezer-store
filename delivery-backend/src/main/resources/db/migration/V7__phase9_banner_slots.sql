-- ============================================================================
-- Phase 9 — editorial landing page: banner slots + call-to-action
-- ============================================================================
-- The home page grew from "one hero carousel" into several distinct picture
-- bands (hero, a two-up split, a 2x2 tile grid, a custom-design promo). A
-- banner now says which band it belongs to and carries its own button, so the
-- whole landing page is editable from the admin without a deploy.
--
-- Every column is NULLABLE and has no default on purpose:
--   * existing rows keep working untouched — the storefront treats a NULL slot
--     as HERO, which is the only place banners were rendered before;
--   * a NULL cta_label or cta_link simply hides the button rather than
--     rendering a link to nowhere.
--
-- Additive only: no data is rewritten and nothing is dropped, so this is safe
-- to apply to a live database.
-- ============================================================================

ALTER TABLE promotional_banner
    ADD COLUMN IF NOT EXISTS slot       VARCHAR(32),
    ADD COLUMN IF NOT EXISTS cta_label  VARCHAR(60),
    ADD COLUMN IF NOT EXISTS cta_link   VARCHAR(500),
    ADD COLUMN IF NOT EXISTS sort_order INTEGER;

COMMENT ON COLUMN promotional_banner.slot IS
    'Home-page band this banner fills: HERO, SPLIT_LEFT, SPLIT_RIGHT, GRID_1..GRID_4, CUSTOM_PROMO. NULL is treated as HERO.';
COMMENT ON COLUMN promotional_banner.cta_label IS
    'Button text, e.g. SHOP NOW. NULL hides the button.';
COMMENT ON COLUMN promotional_banner.cta_link IS
    'Button destination, e.g. /shop?category=2. NULL hides the button.';
COMMENT ON COLUMN promotional_banner.sort_order IS
    'Ordering within a slot, lowest first. Drives HERO carousel order.';

-- The storefront's only query shape is "active banners for slot X, in order",
-- so index exactly that. Partial on deleted=false to match the soft-delete
-- filter every read applies.
CREATE INDEX IF NOT EXISTS idx_banner_slot_sort
    ON promotional_banner (slot, sort_order)
    WHERE deleted = false;
