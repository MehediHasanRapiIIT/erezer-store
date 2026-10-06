-- A product's own stock is the total of its sizes.
--
-- A product sold in sizes has stock in two places: each size's, which is what
-- an order takes from, and the product's own, which is what the shop and the
-- admin lists show. Until now adding a size, or changing its stock, left the
-- product's own figure where it was: a product given 100 of each size could
-- still say 12, or "out of stock". The application now keeps the two in step
-- whenever a size changes; this brings the products that already exist into
-- line.
--
-- Only products whose sizes hold some stock are touched. One whose sizes are
-- all at zero keeps its figure until someone next changes a size, so nothing
-- flips to "out of stock" on its own because of this file.

WITH totals AS (
    SELECT product_id, SUM(COALESCE(stock_quantity, 0)) AS total
    FROM variant
    WHERE deleted = false
    GROUP BY product_id
    HAVING SUM(COALESCE(stock_quantity, 0)) > 0
)
UPDATE inventory i
SET stock_quantity = t.total, last_updated = now()
FROM totals t
WHERE i.product_id = t.product_id AND i.stock_quantity <> t.total;

WITH totals AS (
    SELECT product_id, SUM(COALESCE(stock_quantity, 0)) AS total
    FROM variant
    WHERE deleted = false
    GROUP BY product_id
    HAVING SUM(COALESCE(stock_quantity, 0)) > 0
)
UPDATE product p
SET stock_quantity = t.total
FROM totals t
WHERE p.id = t.product_id AND p.stock_quantity IS DISTINCT FROM t.total;
