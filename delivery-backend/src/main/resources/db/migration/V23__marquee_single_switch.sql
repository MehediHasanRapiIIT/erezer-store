-- One switch for the home page's scrolling text strip.
--
-- The strip used to be switched on or off in Settings (marquee_json.enabled).
-- Since V22 it is also a section on Admin -> Home page layout, which made two
-- switches for one thing. The layout's switch is now the only one; Settings
-- keeps the phrases.
--
-- A shop that had the strip switched OFF in Settings must not see it appear
-- because that switch went away, so its "off" is carried over to the layout.

DO $$
DECLARE
    -- The page as it was built (HomeSection's order), with the strip off.
    original_strip_off CONSTANT text :=
        '[{"key":"SPLIT_BAND","enabled":true},{"key":"TILE_GRID","enabled":true},'
        || '{"key":"CUSTOM_DESIGN_PROMO","enabled":true},{"key":"FLASH_SALE","enabled":true},'
        || '{"key":"FEATURED_BUNDLE","enabled":true},{"key":"SHOP_BY_CATEGORY","enabled":true},'
        || '{"key":"HIGHLIGHTS","enabled":true},{"key":"NEW_ARRIVALS","enabled":true},'
        || '{"key":"FEATURED_PRODUCTS","enabled":true},{"key":"CATEGORY_COLLECTIONS","enabled":true},'
        || '{"key":"MARQUEE","enabled":false},{"key":"RECENTLY_VIEWED","enabled":true},'
        || '{"key":"OUR_STORY","enabled":true},{"key":"NEWSLETTER","enabled":true}]';
    shop RECORD;
    moved text;
BEGIN
    FOR shop IN
        SELECT id, home_layout_json FROM store_settings WHERE marquee_json ~ '"enabled"\s*:\s*false'
    LOOP
        moved := NULL;
        IF shop.home_layout_json IS NOT NULL AND btrim(shop.home_layout_json) <> '' THEN
            BEGIN
                -- A layout the shop already saved: keep its order, switch the strip off in it.
                SELECT jsonb_agg(
                           CASE WHEN e ->> 'key' = 'MARQUEE' THEN jsonb_set(e, '{enabled}', 'false'::jsonb) ELSE e END
                           ORDER BY ord)::text
                  INTO moved
                  FROM jsonb_array_elements(shop.home_layout_json::jsonb) WITH ORDINALITY AS t(e, ord);
                -- A saved layout always names the strip; if this one somehow doesn't, add it, off.
                IF moved IS NULL OR moved !~ '"MARQUEE"' THEN
                    moved := (COALESCE(moved, '[]')::jsonb || '[{"key":"MARQUEE","enabled":false}]'::jsonb)::text;
                END IF;
            EXCEPTION WHEN OTHERS THEN
                moved := NULL;      -- unreadable: fall back to the original layout below
            END;
        END IF;
        UPDATE store_settings SET home_layout_json = COALESCE(moved, original_strip_off) WHERE id = shop.id;
    END LOOP;
END $$;

-- The Settings switch is no longer read. Leave it saying "on", so no stored
-- value still looks as though it were hiding the strip.
UPDATE store_settings
   SET marquee_json = regexp_replace(marquee_json, '"enabled"\s*:\s*false', '"enabled":true')
 WHERE marquee_json ~ '"enabled"\s*:\s*false';
