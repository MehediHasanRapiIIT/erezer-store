-- One switch for every promo code: off hides the promo box in the shop and makes
-- the server refuse codes. Null on the existing row means on, so upgrading
-- changes nothing until an admin flips it.
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS coupons_enabled BOOLEAN;
