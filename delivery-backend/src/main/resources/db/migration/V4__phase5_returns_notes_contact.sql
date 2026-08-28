-- ============================================================================
-- V4 — Phase 5: Returns / RMA, internal order notes, customer-care contact
-- ============================================================================

-- ── orders: delivered_at (used to enforce the return window) ────────────────
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP;


-- ── return_request ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS return_request (
    id                UUID         PRIMARY KEY,
    order_id          UUID         NOT NULL,
    user_id           UUID,                          -- null for guest orders
    customer_email    VARCHAR(255),                  -- snapshot for guest returns

    status            VARCHAR(32)  NOT NULL DEFAULT 'REQUESTED',
    reason            VARCHAR(64)  NOT NULL,         -- WRONG_SIZE | DEFECTIVE | NOT_AS_DESCRIBED | CHANGED_MIND | OTHER
    customer_notes    VARCHAR(2000),
    admin_notes       VARCHAR(2000),

    refund_amount     NUMERIC(12,2),                 -- set when APPROVED / REFUNDED

    requested_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at        TIMESTAMP,
    decided_by        VARCHAR(200),
    picked_up_at      TIMESTAMP,
    refunded_at       TIMESTAMP,

    created_by        BIGINT,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        BIGINT,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_by        BIGINT,
    deleted_at        TIMESTAMP,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    version           BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_return_order  ON return_request (order_id);
CREATE INDEX IF NOT EXISTS idx_return_user   ON return_request (user_id);
CREATE INDEX IF NOT EXISTS idx_return_status ON return_request (status);


-- ── return_item ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS return_item (
    id                  UUID         PRIMARY KEY,
    return_request_id   UUID         NOT NULL,
    order_item_id       UUID         NOT NULL,
    product_id          BIGINT,
    quantity            INTEGER      NOT NULL,
    condition           VARCHAR(32),                 -- SEALED | OPENED | DAMAGED | OTHER
    line_refund_amount  NUMERIC(12,2),

    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_by          BIGINT,
    deleted_at          TIMESTAMP,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    version             BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_return_item_request ON return_item (return_request_id);


-- ── return_photo ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS return_photo (
    id                  BIGSERIAL    PRIMARY KEY,
    return_request_id   UUID         NOT NULL,
    url                 VARCHAR(1024) NOT NULL,
    caption             VARCHAR(255),

    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_by          BIGINT,
    deleted_at          TIMESTAMP,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    version             BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_return_photo_request ON return_photo (return_request_id);


-- ── order_note  (admin-internal notes attached to an order) ────────────────
CREATE TABLE IF NOT EXISTS order_note (
    id            UUID         PRIMARY KEY,
    order_id      UUID         NOT NULL,
    body          VARCHAR(4000) NOT NULL,
    author        VARCHAR(200),                       -- admin email / username

    created_by    BIGINT,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_by    BIGINT,
    deleted_at    TIMESTAMP,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_order_note_order ON order_note (order_id, created_at DESC);


-- ── contact_message  (storefront contact form submissions) ─────────────────
CREATE TABLE IF NOT EXISTS contact_message (
    id            UUID         PRIMARY KEY,
    name          VARCHAR(200) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    subject       VARCHAR(255),
    message       VARCHAR(4000) NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'NEW',  -- NEW | READ | RESOLVED
    order_id      UUID,                                 -- optional context

    created_by    BIGINT,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_by    BIGINT,
    deleted_at    TIMESTAMP,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_contact_status ON contact_message (status, created_at DESC);
