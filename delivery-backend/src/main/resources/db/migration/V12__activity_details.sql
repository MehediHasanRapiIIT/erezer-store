-- Longer detail kept with an activity log line, shown under "Show details" on
-- the Activity log page. Used first by category price changes, which list
-- every product's old and new prices (too long for the 400-character summary).
ALTER TABLE admin_activity ADD COLUMN IF NOT EXISTS details TEXT;
