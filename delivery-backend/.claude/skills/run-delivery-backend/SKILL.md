---
name: run-delivery-backend
description: Build, launch, smoke-test, and stop the delivery-backend Spring Boot service. Use when the user asks to run, start, build, test, smoke, or probe the backend / delivery API / Spring Boot app, hit /api/products or /admin endpoints, or check that the backend is up.
---

`delivery-backend/` is a Spring Boot 3.3.5 / Java 21 / Gradle app that serves the delivery-admin REST API on `:8080`. It needs PostgreSQL at `localhost:5432` to start; Keycloak (`:9090`) and MinIO (`:9000`) are referenced in config but **not** required for the server to come up — only for protected (`/admin/**`) endpoints and image upload.

The driver here is [smoke.sh](smoke.sh) — a curl-based smoke test that can also build, launch, and stop the server. It's what you should run; do not start the app manually unless you specifically need to keep it running.

All paths below are relative to `delivery-backend/`.

## Prerequisites

- **JDK 21** on `PATH` — `java --version` must report `21.x`. The build's toolchain is locked to 21.
- **PostgreSQL** listening on `localhost:5432` with database `delivery_app_v1`, user `postgres`, password `root`. Hibernate creates/updates tables via `ddl-auto=update` on first run. Credentials live in [src/main/resources/application.properties](src/main/resources/application.properties).
- **curl** (used by the smoke script).
- Optional for full auth flow: **Keycloak** at `http://localhost:9090` with realm `delivery-admin`. Without it, public endpoints still serve; `/admin/**` returns 401.

The Gradle wrapper (`./gradlew`) handles its own Gradle install — no separate Gradle needed.

## Build

```bash
./gradlew assemble --no-daemon -q
```

Produces `build/libs/delivery-backend-0.0.1-SNAPSHOT.jar`. MapStruct/Lombok annotation processors emit ~20 "Unmapped target properties" warnings — these are not errors, ignore them.

## Run (agent path — preferred)

One command that builds (if needed), launches the jar, waits for `Started DeliveryBackendApplication`, probes 8 endpoints, then stops the server:

```bash
.claude/skills/run-delivery-backend/smoke.sh --launch
```

Probe-only (server already running):

```bash
.claude/skills/run-delivery-backend/smoke.sh
```

The script prints `ALL PROBES PASSED` and exits 0 on success. It covers:

| Endpoint | Expected |
|---|---|
| `GET /v3/api-docs` | 200 (OpenAPI JSON) |
| `GET /swagger-ui/index.html` | 200 |
| `GET /api/categories` | 200 |
| `GET /api/products` | 200 |
| `GET /api/banners` | 200 |
| `GET /app/home` | 200 |
| `GET /admin/dashboard` | 401 (refuses without JWT) |
| `GET /api/cart` | 401 |

Env overrides: `BASE_URL`, `JAR`, `STARTUP_TIMEOUT_SEC`. Logs go to `/tmp/delivery-backend.log`; PID lives in `/tmp/delivery-backend.pid` while the script is running.

## Run (human path — long-running)

If you actually want the server up for manual exploration:

```bash
./gradlew bootRun --no-daemon          # or:
java -jar build/libs/delivery-backend-0.0.1-SNAPSHOT.jar
```

Open Swagger UI at <http://localhost:8080/swagger-ui/index.html>. Ctrl-C to stop. Cold start takes ~25–30 s on a fresh JVM (Hibernate scans 19 repositories and reconciles the schema).

## Test

```bash
./gradlew test --no-daemon
```

Unit tests don't need PostgreSQL running.

## Gotchas

- **Process tree kill on Windows.** `nohup java -jar … &` under Git Bash spawns a bash helper whose child is `java.exe`. Killing the bash PID alone leaves `java.exe` bound to 8080. The driver uses `taskkill //F //T //PID <pid>` (kill tree) plus a netstat-based fallback that targets whatever is still listening on 8080. If you write your own runner on Windows, do the same — a plain `kill` will leak the server.
- **`application-local.properties` has a malformed line.** Line 4 is `` Database Configuration - PostgreSQL`` — missing the leading `#`, but it parses as whitespace-prefixed text that Spring ignores rather than a property. Don't "fix" it by uncommenting; the real properties live below.
- **`spring.jpa.hibernate.ddl-auto=update`** means the first run against an empty database will create all tables. Subsequent schema drift gets applied automatically. Don't point this at a production DB.
- **JWT issuer-uri does not block startup.** Despite the README/security docs suggesting `jwt.issuer-uri` triggers OIDC discovery at boot, the app starts cleanly even with Keycloak down — discovery is deferred to first protected request. Public endpoints (`/api/products`, `/api/categories`, `/api/banners`, `/app/home`, `/swagger-ui/**`, `/v3/api-docs/**`, `/ws/**`) work without Keycloak; `/admin/**` returns 401.
- **No `pom.xml`.** This is Gradle, not Maven. Use `./gradlew`, not `mvn`.
- **Annotation-processor warnings are noise.** ~20 MapStruct "Unmapped target properties" warnings on every build — entity audit fields (`createdBy`, `updatedAt`, etc.) intentionally aren't mapped. Don't chase them.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `HikariPool-1 - Exception during pool initialization` / `Connection refused` | PostgreSQL not running, wrong port, or DB `delivery_app_v1` doesn't exist | Start Postgres on 5432; `createdb -U postgres delivery_app_v1` (password `root`) |
| `FATAL: password authentication failed for user "postgres"` | Postgres password isn't `root` | Either change the postgres password to `root` or edit `application-local.properties` to match your local creds |
| `Started DeliveryBackendApplication` never appears in log | Usually a port conflict on 8080 from a prior crashed run | `netstat -ano \| grep :8080` then `taskkill //F //PID <pid>` |
| Smoke script reports `/admin/dashboard -> 401` as FAIL | You changed the script and expected 200 without supplying a JWT | The 401 is the correct, expected outcome — protected endpoints must refuse anonymous requests |
| `./gradlew` reports `Unsupported class file major version` | JDK < 21 on PATH | Install JDK 21, set `JAVA_HOME` |
