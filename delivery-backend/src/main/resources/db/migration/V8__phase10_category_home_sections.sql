-- ============================================================================
-- Phase 10 — admin-promotable category sections
-- ============================================================================
-- A category can now claim its own band on the landing page and its own
-- storefront page (e.g. "Erezer Pink" at /erezer-pink), decided entirely by the
-- admin rather than by adding code per collection.
--
-- All columns are NULLABLE with no default: existing categories keep behaving
-- exactly as before (no home section) until an admin opts them in. Additive
-- only, so this is safe to apply to a live database.
-- ============================================================================

ALTER TABLE category
    ADD COLUMN IF NOT EXISTS slug            VARCHAR(140),
    ADD COLUMN IF NOT EXISTS show_on_home    BOOLEAN,
    ADD COLUMN IF NOT EXISTS home_sort_order INTEGER;

COMMENT ON COLUMN category.slug IS
    'URL-safe name for the category page, e.g. erezer-pink serves /erezer-pink.';
COMMENT ON COLUMN category.show_on_home IS
    'True to give this category its own product section on the landing page.';
COMMENT ON COLUMN category.home_sort_order IS
    'Ordering among home sections, lowest first.';

-- Backfill BEFORE the unique index exists, so an existing collision cannot
-- abort the migration.
--
-- Two categories can normalise to the same slug ("Erezer Pink" and
-- "erezer-pink!"), so the first keeps the clean slug and later ones get their
-- id appended. Deterministic, and the admin can rename either afterwards.
UPDATE category AS c
SET slug = derived.candidate
FROM (
    SELECT id,
           CASE
               WHEN row_number() OVER (PARTITION BY base ORDER BY id) = 1 THEN base
               ELSE base || '-' || id
           END AS candidate
    FROM (
        SELECT id,
               trim(BOTH '-' FROM regexp_replace(lower(name), '[^a-z0-9]+', '-', 'g')) AS base
        FROM category
        WHERE slug IS NULL
          AND name IS NOT NULL
    ) normalised
    WHERE base <> ''
) AS derived
WHERE c.id = derived.id;

-- Slugs address a page, so they must be unique. Partial: soft-deleted rows keep
-- their slug and must not stop a new category reusing that name.
CREATE UNIQUE INDEX IF NOT EXISTS ux_category_slug
    ON category (slug)
    WHERE slug IS NOT NULL AND deleted = false;
