-- Which ways to pay a product may be bought with, set by an admin on the
-- Products page for chosen products or a whole category. For each method:
-- TRUE allowed, FALSE not allowed, NULL follow the category (and in the end
-- the shop-wide switch in Settings, which always comes first).
ALTER TABLE product  ADD COLUMN IF NOT EXISTS pay_cod   BOOLEAN;
ALTER TABLE product  ADD COLUMN IF NOT EXISTS pay_bkash BOOLEAN;
ALTER TABLE product  ADD COLUMN IF NOT EXISTS pay_card  BOOLEAN;
ALTER TABLE category ADD COLUMN IF NOT EXISTS pay_cod   BOOLEAN;
ALTER TABLE category ADD COLUMN IF NOT EXISTS pay_bkash BOOLEAN;
ALTER TABLE category ADD COLUMN IF NOT EXISTS pay_card  BOOLEAN;
