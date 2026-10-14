-- A category's chart for one fit: every product in it (and in the categories
-- under it) shows this chart for that fit, unless the product has its own.
CREATE TABLE IF NOT EXISTS category_fit_size_chart (
    category_id   BIGINT      NOT NULL,
    fit_code      VARCHAR(40) NOT NULL,
    size_chart_id BIGINT      NOT NULL,
    PRIMARY KEY (category_id, fit_code)
);
