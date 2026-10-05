-- Which sections the shop's home page shows, and in what order
-- (Admin -> Home page layout).
--
-- A JSON list of {"key": "...", "enabled": true|false}, top to bottom. Empty
-- means "as the page was built": every section on, in its original order, so
-- nothing about the home page changes until an admin arranges it.
--
-- The big top banner is not in the list: it is always first and always shown.
ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS home_layout_json TEXT;
