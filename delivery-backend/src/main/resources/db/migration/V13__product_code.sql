-- Product code (PRODUCT-CODE-PLAN.md): typed by staff, required, and may be
-- shared by several products. The automatic SKU stays as the system's own ID.
ALTER TABLE product ADD COLUMN IF NOT EXISTS product_code VARCHAR(40);

-- Every existing product (deleted ones too, so old orders show a code) starts
-- with its SKU; staff can change it on the product's edit page.
UPDATE product
   SET product_code = LEFT(COALESCE(NULLIF(btrim(sku), ''), 'PR-' || lpad(id::text, 5, '0')), 40)
 WHERE product_code IS NULL OR btrim(product_code) = '';

ALTER TABLE product ALTER COLUMN product_code SET NOT NULL;

-- Searches match the code ignoring capital letters.
CREATE INDEX IF NOT EXISTS ix_product_product_code ON product (lower(product_code));

-- Orders keep the code the product had when it was ordered, as they keep the
-- price. Existing orders take the product's code today.
ALTER TABLE order_item ADD COLUMN IF NOT EXISTS product_code VARCHAR(40);

UPDATE order_item oi
   SET product_code = p.product_code
  FROM product p
 WHERE p.id = oi.product_id AND oi.product_code IS NULL;
