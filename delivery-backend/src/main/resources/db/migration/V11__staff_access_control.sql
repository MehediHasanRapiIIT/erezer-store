-- ============================================================================
-- Phase 13 — staff access control (Admin + Moderator, per-action permissions)
-- ============================================================================
-- Keycloak still handles login. These tables hold what each staff member may
-- do, so a change takes effect on the very next request instead of waiting
-- for a login token to expire. See ACCESS-CONTROL-PLAN.md.
--
-- New tables only; nothing existing is altered. Safe on a live database.
-- ============================================================================

-- The permission catalogue. Rows are written by the backend at startup from
-- the list in code; a key removed from code is marked inactive rather than
-- deleted, so no grant silently disappears.
CREATE TABLE IF NOT EXISTS permission (
    perm_key    VARCHAR(64)  PRIMARY KEY,
    area        VARCHAR(60)  NOT NULL,
    label       VARCHAR(160) NOT NULL,
    description VARCHAR(400),
    sort_order  INTEGER      NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE
);

-- One row per person who can open the admin panel.
CREATE TABLE IF NOT EXISTS staff_member (
    id               UUID         PRIMARY KEY,
    keycloak_user_id VARCHAR(64)  NOT NULL UNIQUE,
    username         VARCHAR(150) NOT NULL,
    email            VARCHAR(255),
    full_name        VARCHAR(200),
    role             VARCHAR(16)  NOT NULL CHECK (role IN ('ADMIN', 'MODERATOR')),
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    last_seen_at     TIMESTAMP(6),
    created_at       TIMESTAMP(6) NOT NULL,
    created_by       VARCHAR(150),
    updated_at       TIMESTAMP(6) NOT NULL
);

-- What each moderator may do. Admins need no rows: they may do everything.
CREATE TABLE IF NOT EXISTS staff_permission (
    staff_id UUID        NOT NULL REFERENCES staff_member (id) ON DELETE CASCADE,
    perm_key VARCHAR(64) NOT NULL REFERENCES permission (perm_key) ON DELETE CASCADE,
    PRIMARY KEY (staff_id, perm_key)
);

-- Named sets of permissions ("Order handler", "Packer") applied in one click.
CREATE TABLE IF NOT EXISTS permission_template (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP(6) NOT NULL,
    created_by VARCHAR(150),
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS permission_template_item (
    template_id UUID        NOT NULL REFERENCES permission_template (id) ON DELETE CASCADE,
    perm_key    VARCHAR(64) NOT NULL REFERENCES permission (perm_key) ON DELETE CASCADE,
    PRIMARY KEY (template_id, perm_key)
);

-- Who did what, and when. No foreign key to staff_member on purpose: deleting
-- a staff member must keep their history, shown by the name stored here.
CREATE TABLE IF NOT EXISTS admin_activity (
    id             UUID         PRIMARY KEY,
    occurred_at    TIMESTAMP(6) NOT NULL,
    staff_id       UUID,
    staff_name     VARCHAR(200) NOT NULL,
    staff_username VARCHAR(150),
    method         VARCHAR(10)  NOT NULL,
    path           VARCHAR(300) NOT NULL,
    perm_key       VARCHAR(64),
    area           VARCHAR(60),
    target_id      VARCHAR(100),
    summary        VARCHAR(400),
    status         INTEGER,
    ip_address     VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS ix_admin_activity_time  ON admin_activity (occurred_at DESC);
CREATE INDEX IF NOT EXISTS ix_admin_activity_staff ON admin_activity (staff_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS ix_admin_activity_area  ON admin_activity (area, occurred_at DESC);
