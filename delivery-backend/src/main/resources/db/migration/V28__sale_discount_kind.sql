-- How a product's sale discount was given: a percentage, or an amount in taka.
--
-- Only the sale price was stored, which is all a product with one price needs.
-- A product that comes in fits (or has sizes with their own price) sells at
-- several prices, and the sale has to come off each of them the way the shop
-- owner typed it: "10% off" is 10% off every one, "100 taka off" is 100 off every
-- one. The sale price alone can't say which. Null means a percentage, which is
-- what every existing sale is taken to be.

ALTER TABLE product ADD COLUMN IF NOT EXISTS sale_by_amount BOOLEAN;
