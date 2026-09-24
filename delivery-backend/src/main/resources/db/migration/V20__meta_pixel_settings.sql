-- Meta (Facebook) Pixel settings the shop owner can change from the admin panel,
-- instead of editing the server's .env and redeploying.
--
-- meta_capi_token holds the Conversions API access token encrypted (AES-GCM,
-- see util/SecretBox); it is never sent back to any browser.
ALTER TABLE store_settings
    ADD COLUMN IF NOT EXISTS meta_enabled          BOOLEAN,
    ADD COLUMN IF NOT EXISTS meta_pixel_id         VARCHAR(64),
    ADD COLUMN IF NOT EXISTS meta_capi_token       TEXT,
    ADD COLUMN IF NOT EXISTS meta_test_event_code  VARCHAR(64);
