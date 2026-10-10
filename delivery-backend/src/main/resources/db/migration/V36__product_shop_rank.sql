-- The place a product is given in the shop (1 is first), set on the admin
-- "Shop Order" page. Null: not ranked; it comes after the ranked products.
ALTER TABLE product ADD COLUMN IF NOT EXISTS shop_rank INTEGER;
CREATE INDEX IF NOT EXISTS idx_product_shop_rank ON product (shop_rank) WHERE shop_rank IS NOT NULL;
