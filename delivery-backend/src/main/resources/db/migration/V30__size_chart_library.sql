-- A library of size charts.
--
-- The shop had three fixed charts kept in its settings: one general chart and
-- one each for Drop Shoulder and Regular Fit. Every product showed the general
-- one, or its fit's. A cap, a kids' hoodie and a men's t-shirt could not each
-- have their own.
--
-- Charts are now rows of their own. A product can name one, a category can name
-- one for everything in and under it, and one chart is the default for the rest.
-- A product that comes in two fits can name a second chart for Regular Fit.

CREATE TABLE IF NOT EXISTS size_chart (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by BIGINT,
    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    updated_at TIMESTAMP,
    updated_by BIGINT,
    version BIGINT DEFAULT 0,
    name VARCHAR(120) NOT NULL,
    -- {"columns":["Chest","Length"],"rows":[{"size":"M","cells":[{"cm":101,"inch":39.8}, ...]}]}
    chart_json TEXT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE
);

ALTER TABLE product ADD COLUMN IF NOT EXISTS size_chart_id BIGINT;
ALTER TABLE product ADD COLUMN IF NOT EXISTS regular_fit_size_chart_id BIGINT;
ALTER TABLE category ADD COLUMN IF NOT EXISTS size_chart_id BIGINT;

-- The charts the shop already has move into the library, so nothing typed is lost.
INSERT INTO size_chart (name, chart_json, is_default, created_at, updated_at, deleted, version)
SELECT 'General', size_chart_json, TRUE, now(), now(), FALSE, 0
FROM store_settings
WHERE size_chart_json IS NOT NULL AND size_chart_json LIKE '{%'
  AND NOT EXISTS (SELECT 1 FROM size_chart)
ORDER BY id
LIMIT 1;

INSERT INTO size_chart (name, chart_json, is_default, created_at, updated_at, deleted, version)
SELECT fit.name, (s.fit_size_charts_json::jsonb -> fit.code)::text, FALSE, now(), now(), FALSE, 0
FROM (SELECT fit_size_charts_json FROM store_settings
      WHERE fit_size_charts_json IS NOT NULL AND fit_size_charts_json LIKE '{%' ORDER BY id LIMIT 1) s
CROSS JOIN (VALUES ('DROP_SHOULDER', 'Drop Shoulder'), ('REGULAR_FIT', 'Regular Fit')) AS fit(code, name)
WHERE jsonb_array_length(COALESCE(s.fit_size_charts_json::jsonb -> fit.code -> 'rows', '[]'::jsonb)) > 0
  AND NOT EXISTS (SELECT 1 FROM size_chart WHERE name = fit.name);

-- Products that come in fits keep showing what they show today: the Drop
-- Shoulder chart, and for Regular Fit its own.
UPDATE product p SET size_chart_id = c.id
FROM size_chart c
WHERE c.name = 'Drop Shoulder' AND p.size_chart_id IS NULL
  AND EXISTS (SELECT 1 FROM variant v WHERE v.product_id = p.id AND v.fit = 'DROP_SHOULDER' AND COALESCE(v.deleted, FALSE) = FALSE);

UPDATE product p SET regular_fit_size_chart_id = c.id
FROM size_chart c
WHERE c.name = 'Regular Fit' AND p.regular_fit_size_chart_id IS NULL
  AND EXISTS (SELECT 1 FROM variant v WHERE v.product_id = p.id AND v.fit = 'REGULAR_FIT' AND COALESCE(v.deleted, FALSE) = FALSE);

-- A product sold in Regular Fit only shows that chart as its one chart.
UPDATE product p SET size_chart_id = p.regular_fit_size_chart_id
WHERE p.size_chart_id IS NULL AND p.regular_fit_size_chart_id IS NOT NULL;
