# Erezer deploy

Single-host docker-compose stack for production or beefy dev machines.

## What this stands up

| Service           | Image / build                          | Exposed via      |
|-------------------|----------------------------------------|------------------|
| `postgres`        | postgres:16-alpine                     | internal only    |
| `redis`           | redis:7-alpine                         | internal only    |
| `minio`           | minio/minio                            | internal only (or `minio:9001` console if you publish it) |
| `keycloak`        | quay.io/keycloak/keycloak:26.0         | via `${KEYCLOAK_HOSTNAME}` through Caddy |
| `delivery-backend`| built from `../delivery-backend`       | via `${API_HOST}` through Caddy |
| `erezer-store`    | built from `../erezer-store`           | via `${STOREFRONT_HOST}` through Caddy |
| `delivery-admin`  | built from `..` (root Angular project) | via `${ADMIN_HOST}` through Caddy |
| `caddy`           | caddy:2.8-alpine                       | host ports 80 + 443 (HTTP/3 included) |
| `pg-backup`       | postgres:16-alpine + cron loop         | writes to the `backup-data` volume |

## First run

```bash
cd deploy
cp .env.example .env
$EDITOR .env       # fill in every value flagged ":?required" at minimum

docker compose --env-file .env up -d --build
```

Caddy will provision certs automatically:
* On `*.localhost` hostnames it uses Caddy's internal CA (set
  `CADDY_GLOBAL_EXTRA=local_certs` in `.env`).
* On real public hostnames it talks to Let's Encrypt (omit
  `CADDY_GLOBAL_EXTRA`, set `ACME_EMAIL`).

Browse:
* https://${STOREFRONT_HOST}
* https://${ADMIN_HOST}
* https://${API_HOST}/swagger-ui/index.html

## Routine ops

```bash
# Tail JSON logs for the backend (SPRING_PROFILES_ACTIVE=prod activates JSON).
docker compose logs -f delivery-backend

# Apply migrations only (no service restart).
docker compose exec delivery-backend java -jar /app/app.jar --spring.flyway.repair=true

# Trigger an on-demand backup.
docker compose exec pg-backup /usr/local/bin/pg-backup.sh

# Restore from a dump.
gunzip -c backups/erezer-YYYYMMDD-HHMM.sql.gz | \
  docker compose exec -T postgres psql -U "${POSTGRES_USER}" "${POSTGRES_DB}"
```

## Observability

* **Logs:** prod profile emits Logstash-JSON on stdout — ship to Loki / ELK /
  Datadog via the docker logging driver (`--log-driver=fluentd` or similar).
* **Correlation IDs:** every request is tagged with `X-Correlation-Id` (echoed
  back to the client). The id flows through MDC into every log line and into
  Sentry breadcrumbs.
* **Sentry:** set `SENTRY_DSN_BACKEND`, `SENTRY_DSN_STORE`, `SENTRY_DSN_ADMIN`
  in `.env` to enable. Sample rates default to 10% — bump via `SENTRY_TRACES_SAMPLE_RATE`.
* **Rate limit headers:** when a client trips the limiter the response includes
  `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Window`, `X-RateLimit-Reset`.

## Going to staging / prod

* Build & push images through the GitHub Actions workflows (Phase 7 ships
  `backend-image.yml`, `storefront-ci.yml`, `admin-ci.yml`) instead of building
  on the host. Set `IMAGE_REPO_OWNER` + `IMAGE_TAG` in `.env` and remove the
  `build:` blocks from `docker-compose.yml` so the runtime pulls pre-built
  images.
* Mount the `pg-data` and `minio-data` volumes on a separate disk; back the
  Caddy data dir up so cert state survives container churn.
* Off-host backups: fill `BACKUP_S3_*` to copy dumps to S3 / R2 / Wasabi.
* Rotate `EREZER_JWT_SECRET` and `POSTGRES_PASSWORD` via your secret manager,
  not a checked-in `.env`.
