-- Subcategories.
--
-- A subcategory is a category that sits under another one: Hoodies -> Zip
-- Hoodies. There are two levels only (the application refuses a subcategory of
-- a subcategory), so one nullable column says it all. Every category that
-- exists today stays a main category.

ALTER TABLE category ADD COLUMN IF NOT EXISTS parent_id BIGINT;

ALTER TABLE category DROP CONSTRAINT IF EXISTS fk_category_parent;
ALTER TABLE category ADD CONSTRAINT fk_category_parent
    FOREIGN KEY (parent_id) REFERENCES category (id);

CREATE INDEX IF NOT EXISTS idx_category_parent ON category (parent_id);
