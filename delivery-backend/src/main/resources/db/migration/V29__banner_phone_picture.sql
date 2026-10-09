-- A second picture for phones.
--
-- A banner's picture is wide, made for a laptop. A phone's screen is tall, and a
-- wide picture can only fill it by losing its sides: the hero on a phone showed
-- the middle third of the photo. A banner can now carry a second, upright picture
-- that phones show instead. Null means the one picture is used everywhere, which
-- is every banner that exists today.

ALTER TABLE promotional_banner ADD COLUMN IF NOT EXISTS mobile_image_url VARCHAR(1000);
