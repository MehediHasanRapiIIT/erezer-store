-- Three kinds of bundle offer.
--
-- A bundle was always "pick N of these products, pay one fixed price", written
-- on the form as Buy X Get Y. The shop also wants a plain fixed-price bundle
-- ("Any 3 for 999") said as such, and a discount that grows with the quantity
-- ("buy 2 save 10%, buy 3 save 15%").
--
--   FIXED_PRICE        buy_count items for bundle_price.
--   BUY_X_GET_Y        buy_count + get_count items for bundle_price.
--   QUANTITY_DISCOUNT  any number of items from the smallest step up; the
--                      percentage of the highest step reached comes off.
--                      tiers_json holds the steps: [{"quantity":2,"percentOff":10}, ...]

ALTER TABLE bundle_offer ADD COLUMN IF NOT EXISTS offer_type VARCHAR(30);
ALTER TABLE bundle_offer ADD COLUMN IF NOT EXISTS tiers_json TEXT;

-- What exists today keeps its meaning and its price.
UPDATE bundle_offer
SET offer_type = CASE WHEN COALESCE(get_count, 0) > 0 THEN 'BUY_X_GET_Y' ELSE 'FIXED_PRICE' END
WHERE offer_type IS NULL;
