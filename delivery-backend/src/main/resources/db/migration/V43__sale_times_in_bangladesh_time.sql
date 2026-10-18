-- The server now runs on Bangladesh time (UTC+6) and stores UTC (2026-10-18).
--
-- Until now the start and end times typed on the admin pages (Flash Sales,
-- coupons, discounts) were saved as typed, then read as UTC: a sale set to
-- start at 10:00 started at 16:00 Bangladesh time. Now they are converted to
-- UTC when saved, so the ones already saved are converted here, once, and keep
-- the times the admin meant.
UPDATE flash_sale SET starts_at = starts_at - INTERVAL '6 hours' WHERE starts_at IS NOT NULL;
UPDATE flash_sale SET ends_at   = ends_at   - INTERVAL '6 hours' WHERE ends_at   IS NOT NULL;
UPDATE coupon     SET valid_from = valid_from - INTERVAL '6 hours' WHERE valid_from IS NOT NULL;
UPDATE coupon     SET valid_to   = valid_to   - INTERVAL '6 hours' WHERE valid_to   IS NOT NULL;
UPDATE discount   SET valid_from = valid_from - INTERVAL '6 hours' WHERE valid_from IS NOT NULL;
UPDATE discount   SET valid_to   = valid_to   - INTERVAL '6 hours' WHERE valid_to   IS NOT NULL;
