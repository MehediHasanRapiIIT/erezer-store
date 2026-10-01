-- A delivery charge an admin can set on one product, or on a whole category
-- (Admin -> Products -> Delivery charge).
--
-- Both start empty, so nothing about the shop changes until someone types a
-- number: every product keeps using its area's price (Inside Dhaka / Outside
-- Dhaka) exactly as before. A charge of 0 means that product is delivered free.
--
-- Which one applies to a line: the product's own charge, else its category's,
-- else the area price. An order pays the highest charge in the basket, once --
-- one delivery, one charge. The free-shipping rules on the Shipping page, and a
-- free-shipping coupon, still beat all of it.
ALTER TABLE product  ADD COLUMN IF NOT EXISTS shipping_charge NUMERIC(12,2);
ALTER TABLE category ADD COLUMN IF NOT EXISTS shipping_charge NUMERIC(12,2);

-- Nothing in the shop may charge a negative amount to deliver.
ALTER TABLE product  DROP CONSTRAINT IF EXISTS product_shipping_charge_non_negative;
ALTER TABLE product  ADD  CONSTRAINT product_shipping_charge_non_negative
    CHECK (shipping_charge IS NULL OR shipping_charge >= 0);
ALTER TABLE category DROP CONSTRAINT IF EXISTS category_shipping_charge_non_negative;
ALTER TABLE category ADD  CONSTRAINT category_shipping_charge_non_negative
    CHECK (shipping_charge IS NULL OR shipping_charge >= 0);
