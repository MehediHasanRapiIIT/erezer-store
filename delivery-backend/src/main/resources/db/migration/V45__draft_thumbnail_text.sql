-- A saved design's small preview is a picture held as text, longer than the 1000
-- characters allowed until now, so every save of a Custom Design was refused.
ALTER TABLE custom_design_draft ALTER COLUMN thumbnail_url TYPE TEXT;
