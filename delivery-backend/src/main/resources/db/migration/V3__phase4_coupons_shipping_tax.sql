-- ============================================================================
-- V3 — Phase 4: coupons, shipping zones, tax rules, cancellation window flag
-- ============================================================================

-- ── coupon ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS coupon (
    id                  UUID         PRIMARY KEY,
    code                VARCHAR(64)  NOT NULL,
    discount_type       VARCHAR(16)  NOT NULL,   -- PERCENT | FLAT | FREE_SHIPPING
    discount_value      NUMERIC(12,2),           -- null for FREE_SHIPPING
    min_order_amount    NUMERIC(12,2),
    usage_limit         INTEGER,                 -- null = unlimited
    per_user_limit      INTEGER,                 -- null = unlimited
    times_used          INTEGER      NOT NULL DEFAULT 0,
    valid_from          TIMESTAMP,
    valid_to            TIMESTAMP,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    description         VARCHAR(255),

    -- Audit / soft-delete
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_by          BIGINT,
    deleted_at          TIMESTAMP,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    version             BIGINT       NOT NULL DEFAULT 0
);

-- Codes are unique among live (non-deleted) rows. Case-insensitive lookup.
CREATE UNIQUE INDEX IF NOT EXISTS uq_coupon_code_active
    ON coupon (LOWER(code)) WHERE deleted = FALSE;


-- ── coupon_redemption ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS coupon_redemption (
    id              UUID        PRIMARY KEY,
    coupon_id       UUID        NOT NULL,
    user_id         UUID,                    -- null for guest checkout
    order_id        UUID        NOT NULL,
    discount_amount NUMERIC(12,2) NOT NULL,
    redeemed_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_by      BIGINT,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_by      BIGINT,
    deleted_at      TIMESTAMP,
    deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    version         BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_redemption_coupon ON coupon_redemption (coupon_id);
CREATE INDEX IF NOT EXISTS idx_redemption_user   ON coupon_redemption (user_id);
-- One redemption per order (the order can only carry one coupon code).
CREATE UNIQUE INDEX IF NOT EXISTS uq_redemption_order ON coupon_redemption (order_id);


-- ── shipping_zone ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS shipping_zone (
    id                  BIGSERIAL    PRIMARY KEY,
    code                VARCHAR(64)  NOT NULL,
    display_name        VARCHAR(120) NOT NULL,
    country_code        VARCHAR(8)   NOT NULL DEFAULT 'BD',
    -- Comma-separated list of city / district keywords matched
    -- case-insensitively against the customer's delivery_address.
    region_keywords     VARCHAR(2000),
    flat_fee            NUMERIC(12,2) NOT NULL DEFAULT 0,
    free_above          NUMERIC(12,2),
    sort_order          INTEGER      NOT NULL DEFAULT 0,
    is_default          BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,

    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_by          BIGINT,
    deleted_at          TIMESTAMP,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    version             BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_shipping_zone_code
    ON shipping_zone (LOWER(code)) WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uq_shipping_zone_default
    ON shipping_zone (is_default) WHERE is_default = TRUE AND deleted = FALSE;


-- Seed three Bangladesh defaults that match Erezer's target market.
-- (Idempotent via WHERE NOT EXISTS so the seed is safe to re-run.)
INSERT INTO shipping_zone (code, display_name, country_code, region_keywords, flat_fee, free_above, sort_order, is_default, is_active)
SELECT 'INSIDE_DHAKA',  'Inside Dhaka',  'BD', 'dhaka',                              60,  2000, 1, FALSE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM shipping_zone WHERE LOWER(code) = 'inside_dhaka');

INSERT INTO shipping_zone (code, display_name, country_code, region_keywords, flat_fee, free_above, sort_order, is_default, is_active)
SELECT 'OUTSIDE_DHAKA', 'Outside Dhaka', 'BD', 'chittagong,sylhet,khulna,rajshahi,barisal,rangpur,mymensingh,comilla',
                                                                                    120, 3000, 2, TRUE,  TRUE
WHERE NOT EXISTS (SELECT 1 FROM shipping_zone WHERE LOWER(code) = 'outside_dhaka');

INSERT INTO shipping_zone (code, display_name, country_code, region_keywords, flat_fee, free_above, sort_order, is_default, is_active)
SELECT 'INTERNATIONAL', 'International', 'XX', NULL,                                1500, NULL, 9, FALSE, FALSE
WHERE NOT EXISTS (SELECT 1 FROM shipping_zone WHERE LOWER(code) = 'international');


-- ── tax_rule ───────────────────────────────────────────────────────────────
-- Phase 4 ships a single rule (zone-agnostic VAT). Schema supports per-zone
-- later by setting zone_id; null zone = global default.
CREATE TABLE IF NOT EXISTS tax_rule (
    id              BIGSERIAL    PRIMARY KEY,
    code            VARCHAR(64)  NOT NULL,
    display_name    VARCHAR(120) NOT NULL,
    zone_id         BIGINT,
    rate            NUMERIC(6,4) NOT NULL,        -- e.g. 0.0500 = 5%
    is_inclusive    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,

    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_by      BIGINT,
    deleted_at      TIMESTAMP,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_tax_zone ON tax_rule (zone_id);

INSERT INTO tax_rule (code, display_name, zone_id, rate, is_inclusive, is_active)
SELECT 'BD_VAT', 'Bangladesh VAT', NULL, 0.0000, FALSE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM tax_rule WHERE LOWER(code) = 'bd_vat');
-- Default rate is 0; admin sets the real percentage once policy is finalised.


-- ── Order columns for coupon & discount tracking ───────────────────────────
ALTER TABLE orders ADD COLUMN IF NOT EXISTS coupon_id        UUID;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS coupon_code      VARCHAR(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS discount_amount  NUMERIC(12,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS subtotal_amount  NUMERIC(12,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_zone_id BIGINT;


-- ── Payment columns for bKash flow ─────────────────────────────────────────
ALTER TABLE payment ADD COLUMN IF NOT EXISTS provider             VARCHAR(32);
ALTER TABLE payment ADD COLUMN IF NOT EXISTS provider_payment_id  VARCHAR(128);
ALTER TABLE payment ADD COLUMN IF NOT EXISTS provider_trx_id      VARCHAR(128);
ALTER TABLE payment ADD COLUMN IF NOT EXISTS payer_account        VARCHAR(64);
ALTER TABLE payment ADD COLUMN IF NOT EXISTS callback_url         VARCHAR(1024);
ALTER TABLE payment ADD COLUMN IF NOT EXISTS metadata             TEXT;

CREATE INDEX IF NOT EXISTS idx_payment_provider_id
    ON payment (provider, provider_payment_id);
