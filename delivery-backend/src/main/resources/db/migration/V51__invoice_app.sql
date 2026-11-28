-- Erezer Invoice: the owner's separate order system. Staff tick orders here and
-- send them there, with their product photos.

-- Where it is and the key it gave us (locked with SecretBox, like the Meta token).
ALTER TABLE store_settings ADD COLUMN invoice_app_url VARCHAR(300);
ALTER TABLE store_settings ADD COLUMN invoice_app_key TEXT;

-- Orders that reached it: one row per order, the latest send. Sending again is
-- harmless (it answers "already there"), so this only feeds the "In Invoice" badge.
CREATE TABLE order_invoice_send (
    order_id         UUID PRIMARY KEY REFERENCES orders (id) ON DELETE CASCADE,
    invoice_order_id BIGINT,
    sent_at          TIMESTAMPTZ NOT NULL,
    sent_by_name     VARCHAR(150),
    missing_photos   INT NOT NULL DEFAULT 0
);
