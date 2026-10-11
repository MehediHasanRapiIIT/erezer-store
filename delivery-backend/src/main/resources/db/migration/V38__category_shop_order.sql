-- The place a category is given among its siblings (1 is first), set on the
-- admin "Shop Order" page. Null: not arranged; it follows the arranged ones, by name.
ALTER TABLE category ADD COLUMN IF NOT EXISTS shop_order INTEGER;
