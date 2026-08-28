-- ============================================================================
-- V5 — Phase 6: Newsletter subscribers, campaigns, per-send logs
-- ============================================================================
-- Sales reporting and customer LTV are computed on the fly from existing
-- orders / order_items tables, so they need no schema changes.

-- ── newsletter_subscriber ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS newsletter_subscriber (
    id                  UUID         PRIMARY KEY,
    email               VARCHAR(255) NOT NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'SUBSCRIBED', -- SUBSCRIBED | UNSUBSCRIBED
    source              VARCHAR(64),                                  -- e.g. STOREFRONT_FOOTER, CHECKOUT, IMPORT
    unsubscribe_token   VARCHAR(64)  NOT NULL,
    subscribed_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unsubscribed_at     TIMESTAMP,

    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_by          BIGINT,
    deleted_at          TIMESTAMP,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    version             BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_newsletter_email
    ON newsletter_subscriber (LOWER(email)) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_newsletter_status
    ON newsletter_subscriber (status);
CREATE INDEX IF NOT EXISTS idx_newsletter_unsub_token
    ON newsletter_subscriber (unsubscribe_token);


-- ── newsletter_campaign ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS newsletter_campaign (
    id              UUID         PRIMARY KEY,
    subject         VARCHAR(255) NOT NULL,
    body_html       TEXT         NOT NULL,
    audience        VARCHAR(64)  NOT NULL DEFAULT 'ALL_SUBSCRIBERS', -- ALL_SUBSCRIBERS | REGISTERED_CUSTOMERS
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',           -- DRAFT | SENDING | SENT | FAILED
    sent_at         TIMESTAMP,
    sent_by         VARCHAR(200),
    sent_count      INTEGER      NOT NULL DEFAULT 0,
    fail_count      INTEGER      NOT NULL DEFAULT 0,

    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_by      BIGINT,
    deleted_at      TIMESTAMP,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_campaign_status
    ON newsletter_campaign (status, created_at DESC);


-- ── newsletter_send_log ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS newsletter_send_log (
    id              BIGSERIAL    PRIMARY KEY,
    campaign_id     UUID         NOT NULL,
    subscriber_id   UUID,
    email           VARCHAR(255) NOT NULL,
    status          VARCHAR(32)  NOT NULL,                            -- SENT | FAILED
    error_message   VARCHAR(2000),
    sent_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_by      BIGINT,
    deleted_at      TIMESTAMP,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_send_log_campaign
    ON newsletter_send_log (campaign_id);
