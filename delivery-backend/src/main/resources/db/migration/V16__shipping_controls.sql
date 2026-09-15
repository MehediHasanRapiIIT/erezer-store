-- Shipping is charged on every order unless an admin says otherwise
-- (Admin -> Shipping): a "free shipping for all orders" switch, and a free-shipping
-- offer above an amount the admin types. Both start off.
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS shipping_free_all      BOOLEAN;
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS shipping_offer_enabled BOOLEAN;
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS shipping_offer_min     NUMERIC(12,2);

-- V3 seeded each zone with a free-above threshold (2000 / 3000) that no admin
-- screen showed or could change, so large orders shipped free without anyone
-- choosing that. The store-wide offer above replaces it; the column stays only
-- so older rows keep their history, and nothing reads it any more.
UPDATE shipping_zone SET free_above = NULL WHERE free_above IS NOT NULL;
