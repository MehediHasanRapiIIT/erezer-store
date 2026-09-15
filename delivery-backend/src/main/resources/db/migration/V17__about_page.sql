-- The storefront's About Us page, edited under Admin -> Settings -> About page.
-- Stored like the brand story: one JSON document on the settings row.
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS about_page_json TEXT;
