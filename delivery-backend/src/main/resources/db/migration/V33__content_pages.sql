-- Pages the shop writes itself: "Our Mission", "Our Values", and any other.
--
-- The footer linked to a Journal and a Careers page that never existed. The
-- shop wants pages of its own words there instead, and to add, change and
-- remove such pages from the admin panel.
--
-- A page is a title, an opening, a list of sections (a heading and its text,
-- with an optional picture) and a closing line. It is read at /pages/<slug>.

CREATE TABLE IF NOT EXISTS content_page (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by BIGINT,
    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    updated_at TIMESTAMP,
    updated_by BIGINT,
    version BIGINT DEFAULT 0,
    slug VARCHAR(140) NOT NULL,
    title VARCHAR(160) NOT NULL,
    -- A small line above the title, e.g. "EREZER".
    eyebrow VARCHAR(80),
    intro TEXT,
    hero_image_url VARCHAR(1000),
    -- [{"heading":"Originality","body":"We value ...","imageUrl":null}, ...]
    sections_json TEXT,
    closing TEXT,
    -- A button under the closing line; both empty for none.
    cta_label VARCHAR(80),
    cta_link VARCHAR(300),
    show_in_footer BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_content_page_slug ON content_page (LOWER(slug)) WHERE deleted = FALSE;

INSERT INTO content_page (slug, title, eyebrow, intro, sections_json, closing, cta_label, cta_link, show_in_footer, is_active, sort_order, created_at, updated_at, deleted, version)
SELECT 'our-mission', 'Our Mission', 'EREZER',
$txt$Our mission is simple: to make fashion more expressive, more accessible, and impossible to ignore.

EREZER was built for people who don't want their style to feel ordinary. We create premium fashionwear that combines distinctive design, quality materials, and modern silhouettes to give people the freedom to express themselves through what they wear.$txt$,
$json$[
 {"heading":"Creating Beyond the Ordinary","body":"We challenge conventional ideas of everyday fashion by creating pieces that carry character, attitude, and individuality.","imageUrl":null},
 {"heading":"Making Quality Accessible","body":"Premium should not have to mean unreachable. We aim to deliver thoughtfully designed, well-made clothing at prices that allow more people to experience quality fashion.","imageUrl":null},
 {"heading":"Designing for Self-Expression","body":"Your style is personal. Our goal is to create clothing that gives you more ways to express your personality, confidence, and perspective.","imageUrl":null},
 {"heading":"Always Moving Forward","body":"Fashion evolves, and so do we. We continuously experiment with new designs, techniques, and ideas to keep EREZER fresh, relevant, and forward-thinking.","imageUrl":null},
 {"heading":"Building a Brand With Meaning","body":"EREZER is more than a clothing label. We want to build a brand that people connect with—a brand that represents individuality, confidence, creativity, and the courage to stand apart.","imageUrl":null}
]$json$,
$txt$EREZER exists to help you erase the ordinary and wear something that feels unmistakably you.$txt$,
'Shop the collection', '/shop', TRUE, TRUE, 0, now(), now(), FALSE, 0
WHERE NOT EXISTS (SELECT 1 FROM content_page WHERE LOWER(slug) = 'our-mission');

INSERT INTO content_page (slug, title, eyebrow, intro, sections_json, closing, cta_label, cta_link, show_in_footer, is_active, sort_order, created_at, updated_at, deleted, version)
SELECT 'our-values', 'Our Values', 'EREZER',
$txt$At EREZER, we believe clothing is more than something you wear. It is a reflection of who you are, what you believe, and how you choose to be seen.$txt$,
$json$[
 {"heading":"Originality","body":"We value individuality over imitation. Every design is created to bring something distinctive to your wardrobe and help you express your own identity.","imageUrl":null},
 {"heading":"Quality Without Compromise","body":"From fabric selection to stitching and finishing, we pay attention to the details that make a difference. We believe premium quality should be something you can feel, not just something you can see.","imageUrl":null},
 {"heading":"Confidence","body":"What you wear should make you feel like yourself—only more confident. EREZER creates pieces designed to help you own your presence wherever you go.","imageUrl":null},
 {"heading":"Creativity","body":"Fashion should never stand still. We continuously explore new ideas, silhouettes, graphics, and styles to keep EREZER evolving with the people who wear it.","imageUrl":null},
 {"heading":"Authenticity","body":"We stay true to who we are. No unnecessary noise, no forced trends—just distinctive fashion made with a clear identity.","imageUrl":null},
 {"heading":"Continuous Improvement","body":"We believe there is always room to do better. From our products to your experience with EREZER, we are constantly learning, improving, and moving forward.","imageUrl":null}
]$json$,
$txt$Wear what represents you. Stay original.$txt$,
'Shop the collection', '/shop', TRUE, TRUE, 1, now(), now(), FALSE, 0
WHERE NOT EXISTS (SELECT 1 FROM content_page WHERE LOWER(slug) = 'our-values');

-- The footer: Journal and Careers become the two new pages, in the same place.
UPDATE store_settings
SET footer_json = jsonb_set(footer_json::jsonb, '{columns}', (
        SELECT COALESCE(jsonb_agg(jsonb_set(col, '{links}', (
                SELECT COALESCE(jsonb_agg(CASE l ->> 'url'
                           WHEN '/journal' THEN '{"label":"Our Mission","url":"/pages/our-mission"}'::jsonb
                           WHEN '/careers' THEN '{"label":"Our Values","url":"/pages/our-values"}'::jsonb
                           ELSE l END ORDER BY li), '[]'::jsonb)
                FROM jsonb_array_elements(COALESCE(col -> 'links', '[]'::jsonb)) WITH ORDINALITY AS x(l, li))) ORDER BY ci), '[]'::jsonb)
        FROM jsonb_array_elements(footer_json::jsonb -> 'columns') WITH ORDINALITY AS y(col, ci)))::text
WHERE footer_json IS NOT NULL AND footer_json LIKE '{%'
  AND jsonb_typeof(footer_json::jsonb -> 'columns') = 'array';

-- A footer that had already dropped those two links still gets the new pages, in its first column.
UPDATE store_settings
SET footer_json = jsonb_insert(footer_json::jsonb, '{columns,0,links,-1}', '{"label":"Our Mission","url":"/pages/our-mission"}'::jsonb, TRUE)::text
WHERE footer_json IS NOT NULL AND footer_json LIKE '{%'
  AND jsonb_typeof(footer_json::jsonb -> 'columns' -> 0 -> 'links') = 'array'
  AND footer_json NOT LIKE '%/pages/our-mission%';

UPDATE store_settings
SET footer_json = jsonb_insert(footer_json::jsonb, '{columns,0,links,-1}', '{"label":"Our Values","url":"/pages/our-values"}'::jsonb, TRUE)::text
WHERE footer_json IS NOT NULL AND footer_json LIKE '{%'
  AND jsonb_typeof(footer_json::jsonb -> 'columns' -> 0 -> 'links') = 'array'
  AND footer_json NOT LIKE '%/pages/our-values%';
