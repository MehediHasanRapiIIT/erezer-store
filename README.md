# Erezer

An apparel shop for Erezer, Dhaka: the storefront customers use, the admin panel
the shop runs on, and the backend they share. Everything runs in Docker.

> The `delivery-` folder names are left over from an earlier project. This is an
> apparel shop — there is no rider or delivery-logistics feature.

| Folder | What it is |
|---|---|
| `erezer-store` | The shop customers see (Angular) |
| `delivery-admin` | The admin panel: products, orders, stock, staff (Angular) |
| `delivery-backend` | The API, database migrations and Keycloak setup (Spring Boot, Java 21) |
| `deploy` | Production compose files, Caddy, and the check scripts |

## What you need first

Nothing is installed on your machine except these — Java, Node and Postgres all
live inside the containers.

| | Windows | Linux |
|---|---|---|
| Docker | [Docker Desktop](https://docs.docker.com/desktop/install/windows-install/) | Docker Engine + the Compose plugin (`docker compose version` must work) |
| Git | [git-scm.com](https://git-scm.com/download/win) | your package manager |
| Python 3 | [python.org](https://www.python.org/downloads/) | usually already there |

Python is only needed for the Keycloak step below and the check scripts, and
uses nothing outside its standard library.

## Start it

```bash
git clone https://github.com/MehediHasanRapiIIT/erezer-store.git
cd erezer-store
cp .env.example .env             # works as-is for local; keep your own secrets out of git
docker compose up -d --build     # the first build takes a few minutes
docker compose ps                # every service should reach "healthy"
```

On Windows use Git Bash or WSL for those commands. In PowerShell the copy is
`Copy-Item .env.example .env`; the rest is the same.

| What | Address | Sign in |
|---|---|---|
| Storefront | http://localhost:4200 | register in the shop |
| Admin panel | http://localhost:4300 | `admin` / `admin` |
| API (Swagger) | http://localhost:8080/swagger-ui/index.html | — |
| Keycloak | http://localhost:9090 | `admin` / `admin` |
| MinIO (uploads) | http://localhost:9001 | `minioadmin` / `minioadmin` |
| Mailpit (email) | http://localhost:8025 | every outgoing email lands here |

Before using the **Staff** page, create Keycloak's admin client. The script
writes its secret into `.env` for you; then restart the backend so it picks it up:

```bash
python deploy/keycloak_setup.py
docker compose up -d backend
```

`docker compose down` stops everything and keeps your data; `down -v` wipes it.

A fresh start has an empty catalogue — add categories and products from the
admin panel. If you change `ADMIN_HOST_PORT` or `STORE_HOST_PORT` in `.env`,
also add the new addresses to the `delivery-admin-ui` client in Keycloak
(Clients → Valid redirect URIs and Web origins), or admin sign-in is refused.

## Where the documentation is

| File | What it covers |
|---|---|
| `DOCKER.md` | How the containers fit together, everyday commands, troubleshooting |
| `TESTING.md` | How to check each area by hand; section 9 covers staff and permissions |
| `ACCESS-CONTROL-PLAN.md` | Admin and moderator permissions |
| `PAGINATION-PLAN.md` | Searching and paging done by the server |
| `PRODUCT-CODE-PLAN.md` | Product codes, per product and per category |
| `CATEGORY-PRICE-PLAN.md` | Changing prices for a whole category |
| `delivery-admin/README.md`, `erezer-store/README.md` | Running one app on its own |

## Checks

With the stack running, from this folder:

```bash
python deploy/verify_paging.py          # every list pages and searches correctly, against the database
python deploy/verify_access_control.py  # every admin endpoint enforces its permission
```

Backend tests: `cd delivery-backend && ./gradlew test`.
Admin panel build: `cd delivery-admin && npm ci && npm run build`.
