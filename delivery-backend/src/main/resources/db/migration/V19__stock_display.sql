-- Product page stock: labels (In stock / Only a few left / Out of stock) or the
-- real quantity ("12 in stock"). Switched on per category; a product can follow
-- its category or choose for itself. Null = the default (labels / follow category).
ALTER TABLE category ADD COLUMN IF NOT EXISTS show_stock_quantity BOOLEAN;
ALTER TABLE product  ADD COLUMN IF NOT EXISTS stock_display VARCHAR(20);
