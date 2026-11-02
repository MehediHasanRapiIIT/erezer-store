-- The in-store showcase screen (/showcase), set on the Settings page: whether it
-- plays, how long each slide stays, which products it shows (new arrivals, all,
-- or the ones ticked) and whether each product carries a QR code to its page.
CREATE TABLE IF NOT EXISTS showcase_settings (
    id              BIGINT PRIMARY KEY,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    slide_seconds   INTEGER NOT NULL DEFAULT 6,
    product_mode    VARCHAR(20) NOT NULL DEFAULT 'NEW',
    product_ids     TEXT,
    show_qr         BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO showcase_settings (id) VALUES (1) ON CONFLICT (id) DO NOTHING;
