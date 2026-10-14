-- The fits a product can come in, as a list the shop manages (Size Charts page)
-- instead of two fits fixed in the program. A variant keeps naming its fit by
-- code (DROP_SHOULDER), so nothing already stored changes meaning.
CREATE TABLE IF NOT EXISTS fit (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(40)  NOT NULL UNIQUE,
    name          VARCHAR(60)  NOT NULL,
    -- Two or three letters for a SKU, e.g. DS.
    sku_code      VARCHAR(6)   NOT NULL,
    -- The chart this fit's sizes show, from the size chart library; null to follow the product's chart.
    size_chart_id BIGINT,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    -- Hidden: kept for products that already use it, not offered for new ones.
    hidden        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    DEFAULT NOW(),
    updated_at    TIMESTAMP    DEFAULT NOW()
);

-- The two fits the shop has always had, each with the library chart of the same name when there is one.
INSERT INTO fit (code, name, sku_code, size_chart_id, sort_order)
SELECT 'DROP_SHOULDER', 'Drop Shoulder', 'DS',
       (SELECT id FROM size_chart WHERE LOWER(TRIM(name)) = 'drop shoulder' AND COALESCE(deleted, FALSE) = FALSE ORDER BY id LIMIT 1), 1
WHERE NOT EXISTS (SELECT 1 FROM fit WHERE code = 'DROP_SHOULDER');

INSERT INTO fit (code, name, sku_code, size_chart_id, sort_order)
SELECT 'REGULAR_FIT', 'Regular Fit', 'RF',
       (SELECT id FROM size_chart WHERE LOWER(TRIM(name)) = 'regular fit' AND COALESCE(deleted, FALSE) = FALSE ORDER BY id LIMIT 1), 2
WHERE NOT EXISTS (SELECT 1 FROM fit WHERE code = 'REGULAR_FIT');

-- A product's own chart for one of its fits, when it should differ from the fit's chart.
CREATE TABLE IF NOT EXISTS product_fit_size_chart (
    product_id    BIGINT      NOT NULL,
    fit_code      VARCHAR(40) NOT NULL,
    size_chart_id BIGINT      NOT NULL,
    PRIMARY KEY (product_id, fit_code)
);

-- "Different chart for Regular Fit" chosen on a product until now becomes its own chart for Regular Fit.
INSERT INTO product_fit_size_chart (product_id, fit_code, size_chart_id)
SELECT id, 'REGULAR_FIT', regular_fit_size_chart_id FROM product
 WHERE regular_fit_size_chart_id IS NOT NULL
ON CONFLICT DO NOTHING;

ALTER TABLE product DROP COLUMN IF EXISTS regular_fit_size_chart_id;
