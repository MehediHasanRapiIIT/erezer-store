-- The size finder's rules ("Don't know your size?"), set on the Size Charts
-- page: one row per fit (by fit code), one for products with no fit (_GENERAL),
-- and one that switches the finder on or off for the whole shop (_SHOP).
-- A scope with no row uses the standard rules and is on.
CREATE TABLE IF NOT EXISTS size_finder (
    scope      VARCHAR(40) PRIMARY KEY,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    -- [{"size":"S","heightUpToIn":64,"weightUpToKg":54}, …]; null "up to" on the last row means "anything above".
    rules_json TEXT
);
