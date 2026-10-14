-- Fewer, clearer customer emails (2026-10-15).
--
-- "In production" is no longer a step: orders standing there move on to
-- Processing. Their history keeps the old step as it happened.
UPDATE orders SET order_status = 'PROCESSING' WHERE order_status = 'IN_PRODUCTION';

-- The parcel's tracking page at the courier, given when the order is shipped.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tracking_url VARCHAR(500);

-- What the customer emails show, set on the Settings page: where a customer
-- can leave a review once the order is delivered, the shop's address for the
-- footer, and the two marketing emails (off until they move to their own service).
CREATE TABLE IF NOT EXISTS customer_email_settings (
    id                     BIGINT PRIMARY KEY,
    review_on_website      BOOLEAN NOT NULL DEFAULT TRUE,
    facebook_url           VARCHAR(300),
    instagram_url          VARCHAR(300),
    whatsapp_number        VARCHAR(40),
    business_address       VARCHAR(300),
    cart_reminder_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
    newsletter_enabled     BOOLEAN NOT NULL DEFAULT FALSE
);
INSERT INTO customer_email_settings (id) VALUES (1) ON CONFLICT (id) DO NOTHING;
