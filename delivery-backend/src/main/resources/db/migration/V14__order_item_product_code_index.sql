-- The Orders page search also matches a product code in an order's lines
-- (PRODUCT-CODE-PLAN.md, part 2), so the codes need an index of their own.
CREATE INDEX IF NOT EXISTS ix_order_item_product_code ON order_item (lower(product_code));
