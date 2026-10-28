-- Size stock can never go below zero (2026-10-28).
--
-- An order with the same size on two lines used to be checked line by line,
-- so it could sell more than there was and leave the size's stock negative.
-- The order now checks the lines together; this rule makes sure the database
-- never records less than nothing, whatever happens. Product and inventory
-- stock already have the same rule (V1).
--
-- A size already oversold shows as none in stock: it can't be sold again
-- until the admin adds stock.
UPDATE variant SET stock_quantity = 0 WHERE stock_quantity < 0;
ALTER TABLE variant ADD CONSTRAINT variant_stock_quantity_check CHECK (stock_quantity IS NULL OR stock_quantity >= 0);
