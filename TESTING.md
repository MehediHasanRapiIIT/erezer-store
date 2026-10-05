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

### Meta Pixel and Conversions API

The storefront pixel is off until `META_PIXEL_ID` is set in `.env`. To see it
fire locally without a real Meta account, put any number there and restart the
store, then install the "Meta Pixel Helper" Chrome extension and browse: it
lists PageView, ViewContent, AddToCart, AddToWishlist, Search, InitiateCheckout,
AddPaymentInfo, Purchase, CompleteRegistration, Lead and Contact as you trigger
them. Purchase carries the product ids and quantities, and for bKash it fires
on the return page only after the payment completes.

The backend sends its own copy of each Purchase to Meta's Conversions API when
`META_CAPI_ACCESS_TOKEN` is also set, using the same event id as the browser so
Meta counts the sale once. Cash-on-delivery orders are sent when placed; bKash
orders when the payment is confirmed. Paste a code from Events Manager ->
Test events into `META_TEST_EVENT_CODE` to watch them arrive, then clear it.

The product feed for Meta catalog ads is at
`http://localhost:8080/api/meta/catalog.csv`. In Commerce Manager, add a data
source of type "scheduled feed" pointing at that URL on your production domain.

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
| Staff | You, marked "You", as Admin; **+ Add** adds a moderator (see section 9) |
| Activity log | Every change made in the panel, newest first, with who and when |

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

## 9. Staff and permissions

Two roles: an **Admin** can do everything; a **Moderator** can do only what an
admin ticked for them on the **Staff** page. Changes apply on the person's next
click. The rules and decisions are in `ACCESS-CONTROL-PLAN.md`.

### One-time setup

The Staff page manages Keycloak logins through a service account. Create it
(safe to run again), then restart the backend so it reads the new secret:

```bash
python deploy/keycloak_setup.py
docker compose up -d backend
```

Without it, the Staff page lists people but explains that logins can't be
managed yet. Do the same once on the live server.

### Try it by hand

1. As `admin`, open **Staff → + Add moderator**. The form opens on its own
   page. Fill in the name, username and email, keep the generated password,
   tick **See orders**, and save. Copy the password from the card, then
   choose **Back to staff**.
2. In a private window, log in as the new moderator. Keycloak asks for a new
   password first. Afterwards the sidebar shows only **Orders**.
3. Back as admin, open **Permissions** on their row, tick **See coupons**, and
   save. The moderator gets Coupons on their next page load.
4. Untick it again while the moderator is still on the panel. Their next click
   on Coupons is refused with a "Not allowed" message, and they are moved to a
   page that explains what's missing.
5. **Activity log** lists each of those changes, with who did it and when.

A moderator without "See money totals" sees 🔒 Hidden instead of revenue on
the dashboard, analytics and customers pages.

### Automated check

```bash
python deploy/verify_access_control.py
```

This adds two throwaway moderators through the Staff API: one with every
"see" permission, one with every other permission. It then calls every staff
endpoint as each of them:

- Endpoints they weren't given must refuse with 403.
- Endpoints they were given must let them through.
- Nothing works without a login.
- The customer-isolation checks from `ACCESS-CONTROL-PLAN.md` run again: one
  customer can't read another's data.

It never changes real data. Refusals are tested on every endpoint, and a
refused request never reaches the code that would act. On the "allowed" side
it only reads, or acts on a record id that doesn't exist. It deletes both
moderators at the end; the activity log keeps the "Added…" and "Deleted…"
lines, as it would for anyone. Exit status 0 means everything matched.

### When you add an admin feature

1. **Add a permission** for each new action to
   `delivery-backend/.../access/Perm.java`: key, area, label in plain words,
   and an optional description. It is written to the database at startup and
   appears on the Staff page by itself. Admins get it automatically.
2. **Mark every new endpoint** with `@RequiresPermission(Perm.X)`, or with
   `@AdminOnly` or `@AnyStaff`. The build fails until you do
   (`AdminEndpointCoverageTest`). For a check that depends on the data sent,
   such as the price inside a product edit, call `StaffAccess.require(Perm.X)`
   in the controller.
3. **Admin panel:**
   - Add the page rule to `ACCESS` and `ADMIN_PAGES` in
     `core/access/admin-pages.ts`, and the route with `page(...)` in
     `app.routes.ts`.
   - Show each button only when `perms.can('x')` is true.
   - Don't call an endpoint the person can't use.
4. If the new permission only makes sense with another one (editing usually
   needs seeing), add that to `features/staff/permission-deps.ts` so the
   checklist ticks both.
5. Run `python deploy/verify_access_control.py`. It picks up new endpoints by
   itself.

---

## 10. Delivery charges on products and categories

Most products are delivered at their area's price — ৳60 inside Dhaka, ৳120
outside — set on **Admin → Shipping**. A product or a whole category can be given
its own charge instead, on **Admin → Products → Delivery charge**.

Which charge applies to a line, nearest rule first:

1. the charge set on the product
2. the charge set on its category
3. the area's price

An order pays the **highest charge in the basket, once**: one delivery, one
charge. A charge of **0** means delivered free. The free-shipping switches on the
Shipping page and a free-shipping coupon still beat all of it.

### Checking it by hand

1. Open **Products**, press **Delivery charge**, and the panel appears with three
   tabs: chosen products, a category, the whole shop.
2. On **Chosen products**, tick one product, type `237`, press apply. Its row in
   the **Delivery** column now reads `৳237`.
3. Put that product in a cart and go to checkout: delivery is ৳237 whether the
   address is in Dhaka or outside it.
4. Add a second product that shows `Area charge`. Delivery stays ৳237 — the
   highest in the basket, not the sum.
5. Back on the panel, tick **Use the area price instead** and apply. The row reads
   `Area charge` again and checkout goes back to ৳60.
6. On **A category**, choose a category and type `150`. Every product in it that
   has no charge of its own shows `৳150` in violet — inherited. Add a new product
   to that category afterwards and it shows `৳150` too, with nothing to set.
7. Set a product to `0`: its page in the shop says **Free delivery**, and
   checkout charges nothing for delivery — as long as it is alone in the basket.

### Checking it with the script

```bash
python deploy/verify_shipping_charges.py              # 17 checks against real quotes
python deploy/verify_shipping_charges.py --with-order # and one real order, cancelled after
```

It puts every charge back as it found it, and the last check confirms the shop
quotes what it quoted before the script ran. The quote endpoint is rate-limited,
so leave a minute between runs or the checks fail with HTTP 429.

### Permissions

Changing a delivery charge needs **Change shipping prices and free shipping**
(`shipping.edit`) — the same permission as the Shipping page, because it is the
same decision. A Moderator without it does not see the **Delivery charge** button
at all, still sees the **Delivery** column, and is refused by the API if the call
is made directly. See §9.

---

## 11. Adding a product in one click

**Products → Add New Product** takes everything on one form: the details, the
price and discount, the pictures and the sizes. **Save Product** sends it all in
one request, and the server saves all of it or none of it — a product never
appears in the shop half-made, and a refused save leaves no picture in storage.

- **Pictures:** choose or drop several at once (up to 10, 15 MB each). The first
  is the main one; **Make main** and the arrows change the order.
- **Discount:** the **% / ৳** switch gives it as a percentage or a fixed amount
  off. The line under it says what customers pay. Switching keeps that the same.
  Only the sale price is stored, so the Edit page reopens a whole percentage as
  a percentage and anything else as the amount off.
- **Sizes:** tick several and type the stock for each. On the Edit page,
  **+ Add several sizes** does the same, with the sizes the product already has
  shown but not tickable; **+ Add one size** is the old one-at-a-time form. A
  product can never have the same size twice.

### Checking it

```bash
python deploy/verify_product_upload.py   # who may send pictures, sizes, stock — as real moderators
```

By hand: add a product with three pictures, four sizes and a ৳ discount; it
lands at the top of the Products list with all of it. Then try again with a
text file renamed `.png` among the pictures: the form says which picture is
wrong, keeps everything you typed, and nothing is saved.

---

## 12. Adding several products at once

**Products → Add several products** is for a delivery or a photo shoot: up to
20 products that share a category, description, price, discount and sizes,
each with its own name, code and pictures. Opened while the Products list is
showing a category, it starts in that category.

- **Drop the shoot's photos** and they are dealt out into products — 4 per
  product unless you change the number — in name order, as the camera numbered
  them. The first of each is the main picture. Each product is named from its
  first photo (`01-pink-floral.jpg` → "Pink Floral"); camera names like
  `IMG_2041` are left blank to type. **+ Add a row** adds one by hand.
- **Codes are suggested** from the category's initials and the next free number
  (Erezer Pink → EP-1003, EP-1004…), and can be changed. A code you typed is
  never replaced. A number is never offered again once an order has carried it,
  even if the product was deleted. The single Add page suggests a code the same
  way. **SKUs** are automatic, as always.
- **Photos are stored exactly as uploaded** — same size, same format, byte for
  byte. One save can carry up to 240 MB (15 MB per photo); the footer shows the
  running total, and the Save button shows how much has gone up. A bigger batch
  is refused before anything is sent: save part of it, then the rest.
- **One click saves all of them, or none.** A mistake names its row
  ("Row 2: Picture 1: …"), marks it, and keeps everything you typed.

### Checking it

```bash
python deploy/verify_product_upload.py   # covers this page's permissions too
```

By hand: drop eight photos numbered 01–08 with 4 per product, check you get two
rows named from the files with consecutive codes, set one row's own price, and
save. Both land in the category's Products list with four pictures each.

---

## 13. Pictures are stored exactly as uploaded

The shop never shrinks, re-compresses or converts an uploaded picture. What is
stored, and what the shop serves, is the same file byte for byte, so its size
and quality are what they were on the uploader's computer.

```bash
python deploy/verify_images_unchanged.py   # a real JPEG, PNG and WebP through every upload route
```

**Custom orders.** The pictures shown on an order are flattened previews of the
garment at screen size. Under them, **Original files** lists what the design was
made from — the customer's uploads and the shop's logos — each at its real pixel
size with **Open original file**. Print from those. A picture whose background
the customer removed in the studio is listed too, kept at full size inside the
design.

---

## 14. Home page layout

**Admin → Home Page** chooses which sections the shop's home page shows, and in
what order. The big top banner is fixed: always first, always shown. Under it
are 14 sections, each with an on/off switch and up/down arrows:

two-panel band · category tile grid · custom design promotion · flash sale ·
featured bundle · shop by category · highlights · new arrivals · featured
products · category collections · scrolling text strip · recently viewed · our
story · newsletter sign-up

- Changes stay on the page until **Save layout**; the shop keeps the old layout
  until then. **Undo changes** drops them. The link under the list puts
  everything back to the original order, all on.
- A section that is on still only appears when it has something to show — a
  flash sale only while one is running, "Our story" once it is written. Each
  row says so.
- The **scrolling text strip** is switched here and nowhere else. Settings
  holds only its phrases. (It used to have its own switch there; a shop that
  had it off was carried over as "off" here.)
- **Our story** can show several social handles (Settings → Brand story →
  Social handles: a name and a link each, up to 8). Each gets its network's
  mark on the shop — Instagram, Facebook, TikTok, YouTube, X — and any other
  link a general one. A link typed without `https://` is completed.
- **Category collections** (categories marked "Show on home") move as one
  block; the order inside it is set on each category.
- Until a shop saves a layout, the home page is exactly as it was built. A
  section added to the shop later appears at the end, switched on.
- Seeing the page needs "See store settings" or "Edit home page content";
  changing it needs **Edit home page content** (`settings.homepage`). Saving
  the Settings page never touches the layout.

By hand: switch "Our story" off, move "Shop by category" to the top, save, and
reload the shop's home page.

---

## What is not wired up locally

These are stubbed on purpose, and are **not** signs of a broken setup:

- **bKash payments** run in `STUB` mode — use Cash on Delivery.
- **SMS** logs to the backend console instead of sending.
- **Sentry** is off (blank DSN).
- **Real email** never leaves your machine — everything lands in Mailpit.
