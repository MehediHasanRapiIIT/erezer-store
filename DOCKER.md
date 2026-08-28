# Erezer — Docker guide

Everything runs in containers: three application images plus Postgres, Redis,
MinIO, Keycloak and a mail catcher. There are two ways to run the system.

| Stack | Command | Use it when |
|---|---|---|
| **Full local** | `docker compose up -d --build` | Normal development and testing. Everything, published on localhost. |
| **Production** | `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d` | Deploying. TLS via Caddy, nothing exposed but 80/443. |

Each project also has a **standalone** stack for working on it alone — see
[Working on one project](#working-on-one-project).

---

## Quick start

```bash
cp .env.example .env      # defaults work as-is for local
docker compose up -d --build
```

First build takes a few minutes (Gradle and npm download their worlds). Later
builds are much faster — dependency layers and the Gradle/npm caches are reused.

Watch it come up:

```bash
docker compose ps          # every service should reach "healthy"
docker compose logs -f backend
```

| What | URL | Credentials |
|---|---|---|
| Storefront | http://localhost:4200 | register in the app |
| Admin panel | http://localhost:4300 | `admin` / `admin` |
| API + Swagger | http://localhost:8080/swagger-ui/index.html | — |
| API health | http://localhost:8080/actuator/health | — |
| Keycloak | http://localhost:9090 | `admin` / `admin` |
| MinIO console | http://localhost:9001 | `minioadmin` / `minioadmin` |
| Mailpit | http://localhost:8025 | — every outbound email lands here |

Everyday commands:

```bash
docker compose down            # stop; database and uploads survive
docker compose down -v         # stop and WIPE all data — full clean slate
docker compose up -d --build backend      # rebuild just one service
docker compose logs -f backend store      # tail several services
docker compose restart store              # re-run entrypoint, re-generate env.js
```

---

## The one thing to understand

Inside Docker, **a service has two addresses**, and almost every confusing
failure comes from using the wrong one.

| Who is connecting | Address to use | Example |
|---|---|---|
| One container → another | compose service name | `http://minio:9000` |
| A **browser** → a service | published host URL | `http://localhost:9000` |

The backend container reaches Postgres at `postgres:5432`, but a product image
URL saved in the database is loaded by *your browser*, so it must say
`localhost:9000`. Both values are configured, separately and deliberately:

```
MINIO_URL=http://minio:9000            # this JVM uploads through here
MINIO_PUBLIC_URL=http://localhost:9000 # this is what the browser is given
```

The same split applies to Keycloak, and it is what removed the old
`127.0.0.1 keycloak` hosts-file requirement:

```
KEYCLOAK_ISSUER_URI=http://localhost:9090/realms/delivery-admin   # public: matches the token's `iss`
KEYCLOAK_JWK_SET_URI=http://keycloak:9090/realms/.../certs        # internal: where keys are fetched
```

Setting both makes Spring Boot skip OIDC discovery — it validates the issuer the
browser actually used while fetching signing keys over the internal network. The
browser and the backend can disagree about the address without disagreeing about
the token.

**In `.env`, the rule is:** `*_HOST_PORT` are ports on your machine; `PUBLIC_*`
are URLs a browser will use. If you change a port, change the matching
`PUBLIC_*` URL too.

---

## How the frontends are configured

The two Angular apps are **not** rebuilt per environment. Each container writes
`/env.js` at startup from its environment variables, and the app reads
`window.__ENV__` before bootstrapping:

```
docker-entrypoint.sh  ──writes──▶  /env.js  ──read by──▶  core/runtime-config.ts
```

So the image you test locally is byte-for-byte the image you deploy; only the
environment differs. To repoint a frontend, change the variable and restart —
no rebuild:

```bash
docker compose up -d --force-recreate store
```

Verify what a container is actually serving:

```bash
curl http://localhost:4300/env.js
```

`ng serve` uses the checked-in `public/env.js` instead, so local development is
unaffected.

Adding a new setting takes three steps: add the key to `RuntimeEnv` in
`core/runtime-config.ts`, emit it in `docker-entrypoint.sh`, and pass it in
compose.

---

## The custom Keycloak login theme

The admin panel's branded login page lives in
[delivery-admin/keycloak-theme/](delivery-admin/keycloak-theme/). Applying it
takes **two independent things**, and it silently falls back to Keycloak's stock
page if either is missing:

1. **The theme files** must exist at `/opt/keycloak/themes/erezer` inside
   Keycloak — handled by
   [delivery-admin/keycloak-theme/Dockerfile](delivery-admin/keycloak-theme/Dockerfile),
   which bakes them into a custom `erezer/keycloak` image. Baking rather than
   mounting is what makes it work in production: the theme travels with the
   image instead of depending on a directory being present on the host.
2. **The realm must select it** via `"loginTheme": "erezer"` in
   [delivery-backend/keycloak/delivery-admin-realm.json](delivery-backend/keycloak/delivery-admin-realm.json).

Locally the theme directory is *also* bind-mounted over the baked-in copy, with
theme caching disabled — so you can edit `login.ftl` or `login.css` and just
refresh the browser. No rebuild, no restart. Production has no mount and
caching on.

```bash
# after editing the theme locally: just reload the login page
# after changing the Dockerfile or adding files:
docker compose build keycloak && docker compose up -d --force-recreate keycloak
```

### The gotcha: realm import only runs once

Keycloak imports the realm JSON **only when the realm does not already exist**.
That has different consequences in each environment:

- **Locally**, Keycloak stores its data inside the container with no volume, so
  `docker compose up -d --force-recreate keycloak` gives it a clean database and
  the realm is re-imported. Any change you make to the realm JSON — including
  `loginTheme` — applies on the next recreate. The flip side: users or clients
  you created by hand in the Keycloak console are **lost** on recreate. Treat the
  realm JSON as the source of truth.
- **In production**, Keycloak persists to Postgres, so the realm survives and the
  JSON is *not* re-read. Editing `loginTheme` there changes nothing on an
  existing deployment. Set it in the admin console instead:
  **Realm settings → Themes → Login theme → `erezer` → Save**.

Verify which theme a realm is actually using:

```bash
curl -s http://localhost:9090/realms/delivery-admin/protocol/openid-connect/auth\
'?client_id=delivery-admin-ui&redirect_uri=http%3A%2F%2Flocalhost%3A4300%2F&response_type=code&scope=openid' \
  | grep -o 'login/[a-z]*/css/login.css'
```

`login/erezer/css/login.css` means the custom theme is live; anything else means
it fell back to the default.

---

## Database schema

Flyway owns the schema. `V1__baseline.sql` is a complete baseline, so a brand
new volume is built by migrations alone — there is no two-pass first boot.

The local stack runs `DDL_AUTO=validate`, the same contract production uses:
Hibernate verifies the entity model matches the migrated schema and **fails
startup on drift**. That is deliberate — it catches a missing migration here
rather than in production.

If startup fails with a schema validation error, an entity changed without a
migration. Write the migration. To unblock yourself while iterating:

```bash
DDL_AUTO=update docker compose up -d --force-recreate backend
```

Check what has been applied:

```bash
docker compose exec postgres psql -U postgres -d delivery_app_v1 \
  -c "select version, description, success from flyway_schema_history order by installed_rank"
```

---

## Working on one project

Each project has a self-contained stack, run from inside its own directory.
They use separate compose project names, so their data never collides with the
full stack — but they compete for the same host ports, so run one at a time.

**Backend** (`delivery-backend/`) — the API plus every datastore, without
building the two Angular apps. This is the fast loop for backend work:

```bash
cd delivery-backend
docker compose up -d --build
```

**Admin panel** (`delivery-admin/`) and **Storefront** (`erezer-store/`) — each
is a static SPA with no dependencies of its own. Point it at whichever backend
you are already running:

```bash
cd erezer-store
PUBLIC_API_URL=http://localhost:8080 docker compose up -d --build
```

---

## Production

```bash
cp .env.example .env
$EDITOR .env          # set every value marked required, and real hostnames
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

What the overlay changes:

- **Only Caddy is exposed** (80/443, plus HTTP/3). Postgres, Redis, MinIO,
  Keycloak and the backend are unreachable from outside the Docker network.
- **TLS is automatic.** Caddy provisions and renews Let's Encrypt certificates.
  Point every hostname's DNS at the host *before* first boot or validation fails.
- **The backend runs the `prod` profile** — no default values for secrets, so a
  missing variable fails at startup instead of quietly using a dev fallback.
  Hibernate runs `validate`; Flyway alone may change the schema.
- **Keycloak runs in production mode** against its own Postgres database
  (created on first boot by `deploy/postgres-init/01-keycloak-db.sh`) rather
  than the throwaway dev store.
- **Mailpit does not start.** Set the real `MAIL_*` values.
- **Nightly `pg_dump`** with retention, and optional off-host S3 upload.

Before going live, at minimum:

1. Set a strong `EREZER_JWT_SECRET` (≥32 chars), `POSTGRES_PASSWORD`,
   `MINIO_ROOT_PASSWORD` and `KEYCLOAK_ADMIN_PASSWORD`.
2. Change the seeded Keycloak `admin` / `admin` login.
3. Set `BACKUP_S3_*` — backups on the same host do not survive losing the host.
4. Clear `CADDY_GLOBAL_EXTRA` so real certificates are issued instead of
   Caddy's internal CA.
5. Narrow CORS. `CorsConfig.java` currently allows any origin, which is
   convenient locally and too permissive in production.

Rehearse the whole overlay locally first by leaving the `*.localhost` hostnames
and `CADDY_GLOBAL_EXTRA=local_certs` in place; your browser will warn about the
self-signed CA, which is expected.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Images upload but render broken in the browser | `MINIO_PUBLIC_URL` points at an address only containers can resolve | It must be browser-reachable — `http://localhost:9000` locally, `https://${MEDIA_HOST}` in prod |
| Admin login loops, or the API returns 401 with a valid token | The `iss` in the token does not match the backend's `issuer-uri` | `PUBLIC_KEYCLOAK_URL`, the admin's `KEYCLOAK_URL` and the backend's `KEYCLOAK_ISSUER_URI` must all be the same address |
| Backend exits with a schema validation error | An entity changed without a matching migration | Write the migration, or `DDL_AUTO=update` to unblock temporarily |
| Backend never becomes healthy | Usually Postgres — check it first | `docker compose logs backend`, `docker compose ps` |
| Port already in use | Something else holds 4200/8080/5432 | Change the matching `*_HOST_PORT` **and** its `PUBLIC_*` URL in `.env` |
| Frontend still calls the old API URL | Stale `env.js` in the browser cache, or the container was not recreated | `docker compose up -d --force-recreate store`, then hard-reload |
| Changed `.env` but nothing happened | Compose only reads it at container creation | `docker compose up -d --force-recreate` |
| Emails never arrive | Expected locally — Mailpit catches everything | Read them at http://localhost:8025 |

Useful checks:

```bash
docker compose ps                                  # health of everything
docker compose exec backend env | sort             # what the backend actually received
curl -s http://localhost:8080/actuator/health      # readiness
curl -s http://localhost:4300/env.js               # what the admin panel was told
```

---

## Note on `delivery-admin/deploy/`

That folder is the **previous** deployment stack. Its build paths
(`context: ../delivery-backend`) assume an older repo layout where the backend
lived inside `delivery-admin/`, so it no longer resolves and cannot build. It
has been superseded by `docker-compose.prod.yml` and `deploy/` at the repo root.
It was left in place rather than deleted — remove it once you are satisfied with
the new stack.
