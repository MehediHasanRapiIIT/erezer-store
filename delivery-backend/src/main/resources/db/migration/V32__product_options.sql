-- Options a shop defines itself: colour, and anything else.
--
-- A product's variants were its sizes, optionally in two fixed fits. A t-shirt
-- sold in black, white and red could only be three separate products.
--
-- A product can now carry any number of options, each with its own choices
-- ("Colour: Black, White, Red"; "Sleeve: Short, Long"). A variant is then one
-- combination of choices, in a fit and a size where the product has those,
-- with its own stock, price and SKU as before.
--
--   product.options_json   the options and their choices, each with a short id
--                          that never changes, so renaming "Navy" to "Navy Blue"
--                          touches nothing else:
--                          [{"id":"a1b2","name":"Colour","kind":"COLOUR",
--                            "values":[{"id":"c3d4","value":"Black","hex":"#000000"}]}]
--   variant.option_key     this variant's combination, as "optionId=valueId"
--                          pairs joined by "|" in option-id order. Null on a
--                          product that has no options - every variant today.
--   product_image.option_value_id
--                          the choice (a colour, usually) a picture belongs to;
--                          null for a picture that suits all of them.

ALTER TABLE product ADD COLUMN IF NOT EXISTS options_json TEXT;
ALTER TABLE variant ADD COLUMN IF NOT EXISTS option_key VARCHAR(500);
ALTER TABLE product_image ADD COLUMN IF NOT EXISTS option_value_id VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_variant_product_option ON variant (product_id, option_key);
