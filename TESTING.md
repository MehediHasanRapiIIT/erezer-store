# Testing Erezer locally

A walkthrough for verifying the whole system on your machine. Every step below
was run against this stack and works.

---

## 1. Start it

```bash
cd d:/Erezer
cp .env.example .env        # only needed the first time
docker compose up -d --build
```

First run pulls images and builds all three apps — expect several minutes.
Afterwards it is much faster.

Wait until every service says `healthy`:

```bash
docker compose ps
```

```
NAME              STATUS
erezer-admin      Up (healthy)
erezer-backend    Up (healthy)
erezer-keycloak   Up (healthy)
erezer-mailpit    Up (healthy)
erezer-minio      Up (healthy)
erezer-postgres   Up (healthy)
erezer-redis      Up (healthy)
erezer-store      Up (healthy)
```

If the backend is stuck on `health: starting`, that is normal for up to ~90s on
a cold boot while Flyway builds the schema. Watch it with
`docker compose logs -f backend`.

---

## 2. Seed some data (do this first)

A fresh database is **completely empty** — no products, no categories. The
storefront will render a bare page and there is nothing to order, which makes it
look broken when it is not.

```bash
./deploy/seed-demo-data.sh
```

This creates a shop, 3 categories and 4 in-stock products with images. It is
safe to re-run — it skips whatever already exists.

> **Why a script and not the admin UI?** Products carry a required `shopId`, but
> there is no admin screen or API for creating shops, so the first product can
> never be created through the UI alone. The script inserts that row for you.

### A year of order history for the reports

The Reports, Dashboard and Analytics pages are empty until orders exist. This
writes ~2,100 realistic Bangladeshi orders (1 July 2025 → today) straight into
PostgreSQL: Dhaka ordering hours, Fri/Sat weekend pattern, Ramadan/Eid and
Pohela Boishakh peaks, cash-on-delivery vs bKash vs card, ৳60/৳120 shipping,
coupons, ~9 % cancellations, ~3 % returns, and 80 registered customers:

```bash
docker compose exec -T postgres psql -U postgres -d delivery_app_v1 < deploy/seed_demo_orders.sql
```

It needs products (run the product seeder first) and is idempotent — every row
carries an `@demo.erezer.local` email and the script removes those before
re-inserting. The header comment shows how to delete them again.

To prove the numbers, recompute every report from the raw rows in plain
Python and compare with the API:

```bash
python deploy/verify_reports.py --dates 2026-09-04 2026-06-30 2026-03-20 2025-12-31
```

It checks day / week / month / year / fiscal-year reports for each date plus
the dashboard tiles and analytics page — several thousand figures — and exits
non-zero on the first paisa of disagreement.

### Discount on/off switches

The Discounts screen has a master switch plus three per-scope switches, and a
product or a whole category can be marked "never discount". To prove they reach
the real checkout price rather than just the display:

```bash
python deploy/verify_discount_switches.py
```

It creates three throwaway rules, flips each switch, asserts the checkout quote
responds, then deletes the rules and restores your settings. Exits non-zero on
the first disagreement.

### Garment mockups for the custom-design studio

`/custom-design` draws whatever image is attached to the selected garment
colour, and draws **nothing** when that URL is null - so the canvas shows only
the dashed print-area guide until mockups are uploaded. This is normal on a
fresh database, not a bug.

Upload them in the admin panel: **Custom Design -> Colours & mockups -> pick a
colour -> Front / Back / Left sleeve / Right sleeve**. Each file goes to MinIO
and its URL is saved on the colour row, exactly like product images.

Transparent PNGs work best - the studio canvas is dark, so a photo with a light
grey background will show as a visible rectangle behind the garment.

## 3. Click through it

### Storefront — http://localhost:4200

| Check | What you should see |
|---|---|
| Home page | Categories and the seeded products |
| Product images load | Real images, not broken icons — this proves the MinIO public-URL wiring |
| Open a product | Detail page with price and stock |
| Add to cart → cart | Line total, shipping fee, grand total |
| Dark mode / language toggle | Theme flips; EN ⇄ BN switches copy |

### Admin panel — http://localhost:4300 — `admin` / `admin`

You are redirected to Keycloak to log in, then back. **If login works at all,
the Keycloak token/issuer wiring is correct** — that was the hardest part of the
Docker setup to get right.

The login page should show the **custom Erezer theme** — the two-panel
monochrome layout with the `EREZER` wordmark, not Keycloak's stock blue page. If
you see the stock page, the realm was imported before the theme was wired up:

```bash
docker compose up -d --force-recreate keycloak
```

You can edit `delivery-admin/keycloak-theme/erezer/login/` and just refresh —
theme caching is off locally.

| Check | What you should see |
|---|---|
| Dashboard | Today / this week / this month revenue with deltas, real 7-day and 12-month trend |
| Reports | Daily, Weekly (Sun–Sat), Monthly, Yearly and Fiscal-year (Jul–Jun) tabs; ‹ › steps periods; Export CSV downloads; all figures in ৳ |
| Analytics | Same numbers as Reports for the same window (cancelled/returned never count as revenue) |
| Discounts | Master switch flips ON/OFF and survives a reload; the three scope checkboxes suspend one scope each |
| Products → edit | "Never discount this product" saves and reloads checked |
| Categories → edit | "Never discount this category" saves and reloads on |
| Products | The 4 seeded products |
| Inventory | Stock quantities, low-stock thresholds |
| Categories | T-Shirts, Hoodies, Accessories |
| Orders | Empty until you place one (step 4) |

### Mailpit — http://localhost:8025

Every outbound email is caught here; nothing is sent to the real world.

---

## 4. The full customer journey

This is the test that exercises everything at once.

1. **Register** on the storefront (any email — it does not need to be real).
2. **Open Mailpit** at http://localhost:8025. You will see *Welcome to Erezer*
   and *Verify your Erezer email*.
3. **Click the verification link** in that email.
   ⚠️ This step is not optional — placing an order without it fails with
   `EMAIL_NOT_VERIFIED`.
4. **Add a product to your cart** and check out (choose **Cash on Delivery** —
   bKash runs in STUB mode locally).
5. **Confirm the results:**
   - Mailpit shows *Your Erezer order is confirmed*
   - The admin panel's **Orders** page lists the order as `PLACED`
   - The product's stock has dropped by the quantity you ordered

If all of that works, the entire system is functioning: both auth systems,
the database, object storage, email, and the pricing/inventory logic.

---

## 5. Quick API smoke test

Paste this to check the backend without touching a browser:

```bash
# Public endpoints - should all be 200
for p in /actuator/health /api/products /api/categories /app/home /v3/api-docs; do
  printf "%-20s %s\n" "$p" "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080$p)"
done

# Protected endpoint without a token - should be 401
curl -s -o /dev/null -w "admin without token: %{http_code}\n" \
  http://localhost:8080/admin/dashboard/stats

# With a real Keycloak token - should be 200
TOKEN=$(curl -s -X POST "http://localhost:9090/realms/delivery-admin/protocol/openid-connect/token" \
  -d "client_id=delivery-admin-ui" -d "username=admin" -d "password=admin" \
  -d "grant_type=password" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
curl -s -o /dev/null -w "admin with token:    %{http_code}\n" \
  -H "Authorization: Bearer $TOKEN" http://localhost:8080/admin/dashboard/stats
```

Also worth a look: **Swagger UI** at
http://localhost:8080/swagger-ui/index.html — every endpoint, browsable.

---

## 6. Verifying the Docker-specific fixes

These are the things that were broken before and are worth confirming yourself.

**Images are browser-reachable** (not the internal container hostname):

```bash
curl -s http://localhost:8080/api/products | grep -o 'http://[^"]*product-images[^"]*' | head -1
```
Must print `http://localhost:9000/...`. If it says `minio:9000`, images will not
load in a browser.

**Runtime config is injected, not compiled in:**

```bash
curl -s http://localhost:4200/env.js     # storefront
curl -s http://localhost:4300/env.js     # admin
```

**One image, any environment** — same image, different config, no rebuild:

```bash
docker run --rm -d --name t -p 4999:80 \
  -e API_BASE_URL="https://api.example.com" erezer/delivery-admin:local
sleep 3 && curl -s http://localhost:4999/env.js && docker stop t
```

**Data survives a restart:**

```bash
docker compose down && docker compose up -d
# your products, orders and users are all still there
```

---

## 7. Resetting

```bash
docker compose down            # stop, keep all data
docker compose down -v         # stop and WIPE everything - full clean slate
docker compose up -d --build && ./deploy/seed-demo-data.sh
```

Use `down -v` whenever you want to prove a first-time setup works from scratch.

---

## 8. When something looks wrong

```bash
docker compose ps                              # who is unhealthy
docker compose logs -f backend                 # most problems show up here
docker compose logs --tail=50 store admin
docker compose exec backend env | sort         # what the backend actually got
```

| Symptom | Likely cause | Fix |
|---|---|---|
| Storefront looks empty | No data seeded | `./deploy/seed-demo-data.sh` |
| Product images are broken icons | `MINIO_PUBLIC_URL` not browser-reachable | Must be `http://localhost:9000` in `.env` |
| Admin login loops or bounces | Keycloak URL mismatch | `PUBLIC_KEYCLOAK_URL` and the admin's `KEYCLOAK_URL` must be the same address |
| Checkout rejected | Email not verified | Click the link in Mailpit first |
| Port already in use | Something else holds 4200/8080/5432 | Change `*_HOST_PORT` **and** its matching `PUBLIC_*` in `.env` |
| Backend won't start, schema error | Entity changed without a migration | Write the migration, or `DDL_AUTO=update` to unblock |
| Frontend calls the old API URL | Stale container or browser cache | `docker compose up -d --force-recreate store` then hard-reload |
| Changed `.env`, nothing happened | Compose reads it at container creation | `docker compose up -d --force-recreate` |

---

## What is not wired up locally

These are stubbed on purpose, and are **not** signs of a broken setup:

- **bKash payments** run in `STUB` mode — use Cash on Delivery.
- **SMS** logs to the backend console instead of sending.
- **Sentry** is off (blank DSN).
- **Real email** never leaves your machine — everything lands in Mailpit.
