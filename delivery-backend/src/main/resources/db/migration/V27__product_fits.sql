-- Fits: Drop Shoulder and Regular Fit.
--
-- A product can come in one fit, both, or neither. Each fit has its own sizes,
-- and so its own stock and (optionally) its own price, which is exactly what a
-- row of "variant" already holds per size. So a fit is one more thing a variant
-- is: Drop Shoulder M and Regular Fit M are two rows. Null means the product has
-- no fits, which is every product that exists today.

ALTER TABLE variant ADD COLUMN IF NOT EXISTS fit VARCHAR(30);

-- The two fits measure differently, so each gets its own size chart beside the
-- shop's general one. One JSON object keyed by fit: {"DROP_SHOULDER": {...}, ...}.
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS fit_size_charts_json TEXT;
