-- ============================================================================
-- V2 — Phase 3: Product variants (size/color/SKU/stock) and multi-image gallery
-- ============================================================================

-- ── Extend the existing `variant` table with clothing-specific fields ──────
-- All columns are nullable so existing rows (which had only quantity/name) stay
-- valid. Application code treats missing size/color as "single-variant" mode.
ALTER TABLE variant ADD COLUMN IF NOT EXISTS size            VARCHAR(16);
ALTER TABLE variant ADD COLUMN IF NOT EXISTS color           VARCHAR(64);
ALTER TABLE variant ADD COLUMN IF NOT EXISTS color_hex       VARCHAR(9);
ALTER TABLE variant ADD COLUMN IF NOT EXISTS sku             VARCHAR(64);
ALTER TABLE variant ADD COLUMN IF NOT EXISTS stock_quantity  INTEGER;
ALTER TABLE variant ADD COLUMN IF NOT EXISTS price_override  NUMERIC(12,2);

-- Backfill the new per-variant stock from the legacy `quantity` column where
-- present. New code reads stock_quantity exclusively.
UPDATE variant SET stock_quantity = quantity WHERE stock_quantity IS NULL AND quantity IS NOT NULL;

-- SKU is unique-per-product when populated; partial unique index keeps NULLs
-- free.
CREATE UNIQUE INDEX IF NOT EXISTS uq_variant_product_sku
    ON variant (product_id, sku)
    WHERE sku IS NOT NULL;

-- Common access pattern: list variants of a product.
CREATE INDEX IF NOT EXISTS idx_variant_product
    ON variant (product_id);


-- ── product_image (new) ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS product_image (
    id              BIGSERIAL    PRIMARY KEY,
    product_id      BIGINT       NOT NULL,
    url             VARCHAR(1024) NOT NULL,
    alt_text        VARCHAR(255),
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    is_primary      BOOLEAN       NOT NULL DEFAULT FALSE,

    -- Audit / soft-delete columns matching AbstractBaseEntity
    created_by      BIGINT,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_by      BIGINT,
    deleted_at      TIMESTAMP,
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    version         BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_product_image_product
    ON product_image (product_id, sort_order);

-- Exactly one primary image per product (when present).
CREATE UNIQUE INDEX IF NOT EXISTS uq_product_image_primary
    ON product_image (product_id)
    WHERE is_primary = TRUE AND deleted = FALSE;


-- ── product: clothing-brand fields (additive) ───────────────────────────────
-- Optional metadata that the storefront wants to display on the PDP. All
-- nullable to leave existing rows valid.
ALTER TABLE product ADD COLUMN IF NOT EXISTS brand              VARCHAR(120);
ALTER TABLE product ADD COLUMN IF NOT EXISTS gender             VARCHAR(16);
ALTER TABLE product ADD COLUMN IF NOT EXISTS material           VARCHAR(255);
ALTER TABLE product ADD COLUMN IF NOT EXISTS care_instructions  VARCHAR(2000);
