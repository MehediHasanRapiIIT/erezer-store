# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

This repo contains **two separate Angular 21 applications** that talk to the same backend:

| Path | App | Purpose |
|------|-----|---------|
| `/` (root) | `delivery-admin` | Internal **admin panel** (catalog, orders, customers, reports…) |
| `erezer-store/` | `erezer-store` | Customer-facing **storefront** |
| `deploy/` | — | Single-host `docker-compose` production stack + Caddy + Keycloak realm |

Each Angular app is an independent npm workspace with its own `package.json`, `angular.json`, `node_modules`, `Dockerfile`, and `dist/`. Run npm/ng commands **from inside the relevant app directory**, not the repo root (root commands operate on the admin app only).

The Spring backend (`delivery-backend`) and its Keycloak setup are **not in this repo** — they are sibling projects referenced by `deploy/docker-compose.yml`. Both frontends call the backend at `http://localhost:8080` in dev.

### Naming caveat — "delivery" is legacy

The project was a food-delivery app being **repurposed into "Erezer", a clothing e-commerce brand**. Folder names, the `delivery-admin` project name, and some domain types (e.g. `deliveryAddress`, `OUT_FOR_DELIVERY`) are kept for continuity but the product is now apparel retail. Don't infer rider/courier/delivery-logistics features from the names — that functionality has been removed from the admin.

## Commands

`ng test` uses the **Vitest** runner (jsdom, no browser). Both apps default `ng serve` to **port 4200**, so to run them simultaneously give one an explicit port.

```bash
# Admin (run from repo root)
npm start                              # ng serve → http://localhost:4200
npm run build                          # production build → dist/
npm test                               # Vitest, all *.spec.ts
npx ng test --watch                    # watch mode
npx ng test --include src/app/app.spec.ts      # single file
npx ng test --filter "^App"            # tests whose name matches a regex
npx ng test --list-tests               # list discovered specs without running
npx ng test --coverage

# Storefront (run from erezer-store/)
cd erezer-store
npx ng serve --port 4300               # avoid clashing with the admin on 4200
npm run build
npm test
```

There is no separate lint script; formatting is via **Prettier** (`npx prettier --write .`). Production CI is `.github/workflows/{admin,storefront}-ci.yml` → `npm ci && npm run build -- --configuration production` on Node 22.

## Configuration that is hardcoded

There is no `.env` for the frontends; key endpoints live in source and must be edited there:

- **Admin API base:** `src/environments/environment.ts` (`apiBaseUrl`).
- **Storefront API base:** `const BASE` at the top of `erezer-store/src/app/core/api.service.ts`.
- **Admin Keycloak:** `url` / `realm` / `clientId` consts at the top of `src/app/core/services/keycloak.service.ts` (dev: `localhost:9090`, realm `delivery-admin`, client `delivery-admin-ui`). Requires `public/silent-check-sso.html` for `check-sso`.
- **Storefront Meta Pixel:** `erezer-store/src/app/core/config.ts` (or `window.__EREZER_META_PIXEL_ID__` at deploy time).

## Architecture

Both apps are **standalone-component** Angular (no NgModules), Tailwind CSS **v4** (`@import "tailwindcss"` in `styles.css`, no JS theme config), Angular **signals** for state, and **Sentry** initialized before bootstrap in `app.config.ts`. Routes are lazy via `loadComponent`.

### Admin (`delivery-admin`, root)

- **Auth chain:** `KeycloakService` (wraps `keycloak-js`) ← `AuthService` (thin facade) ← `authGuard`/`guestGuard` on every route + `authInterceptor`. Keycloak is initialized in an `APP_INITIALIZER` (`check-sso`) before the app renders. The interceptor attaches `Authorization: Bearer <token>` (refreshing within 30s of expiry) and triggers a re-login on any `401`. All feature routes are behind `authGuard`; `/` redirects to `/login`.
- **Feature-per-folder:** each domain is a folder under `src/app/features/` (orders, products, categories, coupons, discounts, returns, reports, customers, newsletter, …) loaded lazily in `app.routes.ts`.
- **One service per backend resource:** `src/app/core/services/*.service.ts` (e.g. `order.service.ts`, `product.service.ts`, `return.service.ts`). Shared DTOs/interfaces live in `src/app/core/models/api.models.ts`. When adding a backend integration, add a typed service here and a DTO in `api.models.ts` rather than calling `HttpClient` from components.

### Storefront (`erezer-store/`)

- **Single HTTP gateway:** `core/api.service.ts` (`ApiService`) is the only place that calls `HttpClient`; every backend DTO is typed in `core/api.models.ts`. Components/stores depend on `ApiService`, never raw HTTP.
- **Signals stores:** app state lives in `core/store/*.store.ts` — `EcommerceStore` (products, cart, wishlist; cart persisted to `localStorage` under `erezer-cart`), `DiscountsStore`, `SettingsStore` (admin-managed store/footer settings). Pricing helpers in `core/discount-pricing.ts`.
- **Auth:** `core/auth.service.ts` uses **Spring JWT** (access + refresh tokens in `localStorage` under `erezer-*` keys, exposed as signals), distinct from the admin's Keycloak. `core/auth.interceptor.ts` attaches the token. This split is intentional: customers use JWT, admins use Keycloak.
- **Cross-cutting:** custom i18n in `core/i18n/` (EN/BN `dictionaries.ts` + `TranslateService`/`TranslatePipe`, no ngx-translate); `theme.service.ts` toggles a `dark` class on `<html>`; plus `pixel.service.ts` (Meta Pixel), `seo.service.ts`, `recently-viewed.service.ts`, and the `reveal`/`count-up` scroll directives. Inline templates are the norm; shared UI lives in `components/` (layout header/footer, shared product-card and the reusable `theme-toggle`).

## Deploy stack

`deploy/docker-compose.yml` stands up the full system on one host: `postgres`, `redis`, `minio`, `keycloak`, `delivery-backend`, `erezer-store`, `delivery-admin`, and `caddy` (TLS/HTTP3 reverse proxy by hostname), plus `mailpit` and a `pg-backup` cron. Each Angular app builds via its multi-stage `Dockerfile` (Node 22 build → nginx-alpine static serve with SPA fallback). See `deploy/README.md` for first-run, migrations, backups, and observability. Images are published to `ghcr.io/<owner>/{delivery-admin,erezer-store}` by `.github/workflows/`.
