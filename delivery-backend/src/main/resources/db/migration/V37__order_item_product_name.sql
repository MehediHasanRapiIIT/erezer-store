-- The product's name as it was when the order was placed, kept on the order
-- line like its price and code. An order then still says what was bought after
-- the product is renamed or deleted (it used to read "Unknown Product").
ALTER TABLE order_item ADD COLUMN IF NOT EXISTS product_name VARCHAR(255);

-- Lines placed before this: take the name from the product, where it still exists.
UPDATE order_item oi
   SET product_name = p.name
  FROM product p
 WHERE p.id = oi.product_id
   AND oi.product_name IS NULL;
