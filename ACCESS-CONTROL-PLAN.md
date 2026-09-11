# Access control — implementation plan

Planned for **12 September 2026**. Tick items off as they land. Audited against the whole project on 11 September 2026: every backend endpoint, every call the admin panel makes, the security rules, and the production proxy.

## Agreed decisions

- **Two roles.** *Admin* can do everything and can never be restricted. *Moderator* starts with no access; an admin grants permissions per moderator.
- **Per-action permissions.** Every action in the panel is its own permission, e.g. change an order's status but not cancel it, edit a product but not its price.
- **Dynamic.** Permissions live in the app database and are checked on every admin request. A change takes effect on the next click: no code change, no restart, no re-login.
- **Templates.** An admin can save a set of ticks under a name ("Order handler", "Packer") and apply it to a moderator in one click.
- **Delegation is safe.** An admin may let a moderator manage staff, but a moderator can only grant permissions they hold themselves and can never create an admin. The last admin cannot be removed or demoted.
- **Activity log.** Every change made in the panel records who, what and when.
- **Customer details are not a separate permission.** Anyone allowed to see orders sees the whole order, including the customer's name, phone number and address. Decided 11 Sep 2026.
- **Money is the admin's call per moderator.** Seeing revenue anywhere is the single permission `finance.revenue`, granted or not like any other. Decided 11 Sep 2026.
- **Staff can be deactivated or deleted.** Deactivating keeps the account for later; deleting removes the login and permissions for good. Either way their activity history stays, shown by name. Decided 11 Sep 2026.
- **New moderators get a temporary password** set by the admin, and must choose their own at their first login. Decided 11 Sep 2026.
- **Keycloak stays the login system.** It answers who the person is and whether they are admin or moderator. Permissions are *not* stored as Keycloak roles, because token lifetime would delay changes by minutes.

## Audit findings (11 Sep 2026)

The first two were proven against the running stack with two existing demo customers, using read-only requests.

| # | Finding | Evidence | Fixed by |
|---|---|---|---|
| A1 | **Any logged-in customer can read another customer's data** by putting that customer's id in the address. Orders, profile and cart were returned. The same endpoints also change data (cart, addresses, profile, design drafts, cancelling, returns), so those are very likely open too; writes were not tested to avoid changing data. | Customer A's login fetched customer B's orders, email and cart with HTTP 200. Without a login the same request is refused. | 0.1 |
| A2 | **Customers cannot post, edit or delete reviews at all.** The admin security chain claims every `POST`/`PUT`/`DELETE` under `/api/products/**`, which swallows `/api/products/{id}/reviews`, so a customer's login is rejected as an invalid admin token. | A real customer reviewing a product they bought and received got HTTP 401 `invalid_token`. | 0.2 |
| A3 | **Reviews trust the customer id sent in the request** (body for create and edit, address for delete) instead of the login. Harmless today only because of A2. | `ReviewController`, `ReviewServiceImpl`. | 0.2 |
| A4 | **The admin backend never checks a role or permission.** Any logged-in Keycloak user may do everything under `/admin/**` and change products, categories and banners. | `SecurityConfig`: `anyRequest().authenticated()`; no `@PreAuthorize` anywhere. | Step 1 |
| A5 | **The API documentation is public in production.** Swagger UI and the OpenAPI file are permitted to everyone and nothing turns them off in the `prod` profile, so the full map of admin endpoints would be published. | `SecurityConfig` permits `/swagger-ui/**`, `/v3/api-docs/**`; springdoc 2.6.0 on the classpath; no `springdoc.*` setting in `application-prod.properties`. | 0.3 |
| A6 | **Revenue is visible from more places than the dashboard:** analytics totals and payment split, customer lifetime value on the Customers page, and every report. | `AnalyticsDTO.totalRevenue`, `CustomerLifetimeValueDTO.lifetimeRevenue`. | `finance.revenue` in the catalogue |
| A7 | **Store settings is one big document** covering policies, support contacts, size chart, home-page content, footer, payment methods and the discount switches, saved in a single `PUT`. | `StoreSettingsDTO`. | 1.7, 1.8 |
| A8 | The admin panel contains calls to customer endpoints (`/app/consumer/{userId}/orders…` in `order.service.ts`) that the backend rejects for an admin login. | Admin HTTP call sweep. | 0.4 |
| A9 | The live stock feed WebSocket (`/ws/**`) is open to anyone. It carries stock levels only, so this is low risk. | `SecurityConfig`, `WebSocketConfig`. | 1.13 |
| A10 | Keycloak hardening for production: brute-force protection off, `sslRequired: none`, and the admin client allows password login from scripts (`directAccessGrantsEnabled`). | Realm file. | 1.1 |

Already fine, no action needed: in production only Caddy is exposed (ports 80 and 443); Caddy blocks Keycloak's admin console, the actuator, and every write to the storage server; self-registration in the admin realm is off.

## What exists today

- The backend accepts **any** logged-in Keycloak user for every `/admin/**` action and for product, category and banner changes (A4). Safe only because one admin account exists.
- Keycloak realm `delivery-admin`: one realm role `admin`, one user `admin`.
- Admin app route guard checks only "logged in". The sidebar has 20 items, the app 27 pages.
- Order status changes already record the Keycloak username (`changed_by`).
- No Keycloak admin client in the backend yet (needed to create staff accounts).
- Each app folder has its own standalone `docker-compose.yml`. Always run `docker compose` from the repo root, or a second, conflicting stack starts.

## Permission catalogue (draft)

Keys are stable identifiers; labels are what the Staff page shows. Endpoint paths are relative to the controller base shown.

| Area | Key | Action | Endpoints |
|---|---|---|---|
| Finance | `finance.revenue` | See money totals anywhere | revenue fields in dashboard, analytics, customer lifetime value; required by `reports.view` |
| Dashboard | `dashboard.view` | See the dashboard | `GET /admin/dashboard/stats` |
| Analytics | `analytics.view` | See analytics | `GET /admin/dashboard/analytics` |
| Reports | `reports.view` | See business reports | `GET /admin/reports/*` (also needs `finance.revenue`) |
| Reports | `reports.export` | Export CSV / print | UI only (export is built in the browser) |
| Orders | `orders.view` | See orders | `GET /admin/orders`, `/paged`, `/status/{s}`, `/{id}`, `/{id}/track`, `/statuses`, `/summary` |
| Orders | `orders.status` | Change order status | `PATCH /admin/orders/{id}/status` (not to CANCELLED) |
| Orders | `orders.cancel` | Cancel an order | `PATCH /admin/orders/{id}/status` → CANCELLED |
| Orders | `orders.invoice.print` | Download invoice | `GET /admin/orders/{id}/invoice/pdf` |
| Orders | `orders.invoice.send` | Email invoice | `POST /admin/orders/{id}/invoice/send` |
| Orders | `orders.notes.view` | Read order notes | `GET …/notes` |
| Orders | `orders.notes.add` | Add order notes | `POST …/notes` |
| Orders | `orders.notes.delete` | Delete order notes | `DELETE …/notes/{noteId}` |
| Returns | `returns.view` | See returns | `GET /admin/returns`, `/{id}` |
| Returns | `returns.decide` | Approve or reject | `POST /{id}/approve`, `/{id}/reject` |
| Returns | `returns.pickup` | Mark picked up | `POST /{id}/picked-up` |
| Returns | `returns.refund` | Refund | `POST /{id}/refund` |
| Custom orders | `custom_orders.view` | See custom orders | `GET /admin/custom-orders`, `/{id}` |
| Custom orders | `custom_orders.update` | Update status / details | `PATCH /{id}` |
| Custom orders | `custom_orders.delete` | Delete | `DELETE /{id}` |
| Customers | `customers.view` | See customers | `GET /admin/customers`, `/count` (lifetime value needs `finance.revenue`) |
| Support | `support.view` | Read messages | `GET /admin/support/messages`, `/{id}` |
| Support | `support.update` | Mark handled | `PATCH /{id}` |
| Support | `support.delete` | Delete messages | `DELETE /{id}` |
| Products | `products.view` | See products in admin | UI only (product GETs are public for the storefront) |
| Products | `products.create` | Add products | `POST /api/products` |
| Products | `products.edit` | Edit products | `PUT /api/products/{id}` |
| Products | `products.price` | Change price / sale price | field check inside create and edit |
| Products | `products.delete` | Delete products | `DELETE /api/products/{id}` |
| Products | `products.feature` | Feature on home page | `PATCH /admin/products/{id}/featured` |
| Products | `products.images` | Manage photos | `POST/PUT/DELETE /admin/products/{id}/images…` |
| Products | `products.variants` | Manage sizes and colours | `POST/PUT/DELETE /admin/products/{id}/variants…` |
| Inventory | `inventory.view` | See stock | `GET /admin/inventory`, `/inventory/summary`, `/products/{id}/stock` |
| Inventory | `inventory.edit` | Change stock | `PUT /admin/products/{id}/stock`, `PUT /admin/inventory/bulk` |
| Categories | `categories.view` | See categories in admin | UI only |
| Categories | `categories.create` | Add categories | `POST /api/categories` |
| Categories | `categories.edit` | Edit categories | `PUT /api/categories/{id}` |
| Categories | `categories.delete` | Delete categories | `DELETE /api/categories/{id}` |
| Reviews | `reviews.view` | See reviews | `GET /admin/products/{id}/reviews`, `/summary` |
| Reviews | `reviews.delete` | Delete reviews | `DELETE /admin/products/{id}/reviews/{reviewId}` |
| Banners | `banners.view` | See banners in admin | UI only |
| Banners | `banners.create` | Add banners | `POST /api/banners` |
| Banners | `banners.edit` | Edit banners | `PUT /api/banners/{id}` |
| Banners | `banners.delete` | Delete banners | `DELETE /api/banners/{id}` |
| Coupons | `coupons.view` / `.create` / `.edit` / `.delete` | Coupons | `GET/POST/PUT/DELETE /admin/coupons…` |
| Discounts | `discounts.view` / `.create` / `.edit` / `.delete` | Discount rules | `GET/POST/PUT/DELETE /admin/discounts…` |
| Discounts | `discounts.switches` | Master and scope on/off switches | new endpoint, split out of store settings |
| Flash sales | `flash_sales.view` / `.create` / `.edit` / `.delete` | Flash sales | `GET/POST/PUT/DELETE /admin/flash-sales…` |
| Bundles | `bundles.view` / `.create` / `.edit` / `.delete` | Bundles | `GET/POST/PUT/DELETE /admin/bundles…` |
| Newsletter | `newsletter.subscribers` | See subscribers | `GET /admin/newsletter/subscribers`, `/count` |
| Newsletter | `newsletter.campaigns.view` | See campaigns | `GET /campaigns`, `/campaigns/{id}` |
| Newsletter | `newsletter.campaigns.edit` | Write campaigns | `POST/PUT/DELETE /campaigns…` |
| Newsletter | `newsletter.campaigns.send` | Send campaigns | `POST /campaigns/{id}/send` |
| Design library | `design.view` | See garments and designs | `GET /admin/custom-design/items`, `/logos` |
| Design library | `design.items` | Manage garments and mockups | `POST/DELETE /items…`, `POST /uploads/image` |
| Design library | `design.logos` | Manage the design library | `POST/DELETE /logos…` |
| Settings | `settings.view` | See store settings | `GET /admin/store-settings` |
| Settings | `settings.store` | Return policy, exchange window, support contacts | field check inside settings save |
| Settings | `settings.homepage` | Brand story, trust strip, highlights | field check inside settings save |
| Settings | `settings.footer` | Footer columns, promises, outlets | field check inside settings save |
| Settings | `settings.sizechart` | Size chart | field check inside settings save |
| Settings | `settings.payments` | Turn payment methods on/off | field check inside settings save |
| Staff | `staff.view` | See staff | new |
| Staff | `staff.manage` | Add, deactivate, reactivate, reset passwords | new |
| Staff | `staff.delete` | Delete staff for good | new |
| Staff | `staff.permissions` | Grant permissions (only ones held) | new |
| Activity | `activity.view` | See the activity log | new |

Shared endpoints:

- `POST /admin/uploads/image` is allowed to anyone holding at least one edit permission that uploads images.
- `GET /admin/search` is allowed to everyone; results are filtered to the areas the person can view.

## Tasks

### Step 0 — Fix the audit findings first (security, independent of roles)

Done 11 Sep 2026. Verified by 14 web-layer tests and a 24-check live run against the stack. Also fixed on the way: a refused request came back as 401 instead of 403, because the internal error page was itself blocked; that would have logged moderators out on every refusal.

- [x] **0.1 Customer data isolation (A1).** One central check for every `/app/consumer/{userId}/**` request: the id in the address must equal the logged-in customer, otherwise 403. Covers addresses, cart, orders, order history, profile, returns and design drafts. Guest checkout (`/app/consumer/guest/orders`) stays open.
- [x] **0.2 Reviews (A2, A3).** Take customer review endpoints out of the admin security chain and require a customer login instead. Take the reviewer's id from the login, never from the request. Admin review deletion stays under `/admin`.
- [x] **0.3 API docs (A5).** Turn Swagger UI and the OpenAPI file off in the `prod` profile; keep them for local work.
- [x] **0.4 Admin dead calls (A8).** Remove the admin panel's calls to customer endpoints, or replace them with admin endpoints if a screen needs the data.
- [x] **0.5 Tests.** Customer A gets 403 for every one of customer B's endpoints, reads and writes; A still gets 200 for their own; a customer can post, edit and delete only their own review.

### Step 1 — Backend foundation

- [x] **1.1 Keycloak.** Add realm role `moderator`. Turn on brute-force protection. Add a confidential client with a service account and `manage-users`, `view-users`, `query-users` so the backend can create staff. For production: `sslRequired: external`, and password login from scripts off on the admin client (A10). Update `keycloak/delivery-admin-realm.json` for fresh installs, and apply the same changes to the running Keycloak (the realm file is imported on first boot only).
- [x] **1.2 Migration V11.** Tables: `staff_member` (Keycloak user id, username, email, name, role, active, audit columns), `permission` (key, area, label, description, sort), `staff_permission`, `permission_template`, `permission_template_item`, `admin_activity` (who, stored by name as well as id so entries survive the person being deleted; action; target type and id; summary; time; IP).
- [x] **1.3 Permission registry in code.** One place listing every key with area and label. At startup it syncs into the `permission` table: adds new keys, never deletes existing grants.
- [x] **1.4 Current staff resolver.** From the Keycloak token to a `staff_member` row, created on first login. The Keycloak `admin` role means Admin.
- [x] **1.5 Enforcement.** `@RequiresPermission("orders.cancel")` on handler methods, checked by an interceptor. Admins always pass. Moderators are checked against the database through a short cache that is cleared whenever permissions change. A refusal returns 403 with the missing permission key.
- [x] **1.6 Annotate every admin endpoint** per the catalogue, including product, category and banner changes under `/api`.
- [x] **1.7 Field-level checks.** Product price; cancel via status change; each settings section; revenue fields blanked without `finance.revenue` in dashboard, analytics and customers.
- [x] **1.8 Split the discount switches** into their own endpoint so they can be granted separately from settings.
- [x] **1.9 Deny-by-default test.** Scan every admin handler; fail the build if any lacks a permission or an explicit admin-only marker.
- [x] **1.10 `GET /admin/me`.** Returns name, role and permission keys for the admin panel.
- [x] **1.11 Activity log writes.** Every successful change in the panel records who did what.
- [x] **1.12 Tests.** Admin passes; moderator without permission gets 403; with it gets 200; a grant or revoke applies to the very next request.
- [x] **1.13 Stock feed (A9).** Require a staff login on the `/ws/**` stock feed. Low priority.

**Step 1 notes (done 11 Sep 2026).** 88 backend tests pass; live check 43/43 against the running stack.

- Decided while building: adding a product also needs `products.price` (a new product always has a price). "Never discount" on a product or category needs `discounts.switches`. A size's own price needs `products.price`, and its stock needs `inventory.edit`. Riders in admin search are admin-only. An endpoint with no rule counts as admin-only, and the build fails until one is added.
- For Step 2: revenue fields now come back empty (`null`) without `finance.revenue` on the dashboard, analytics and customers pages. The panel must show "hidden" rather than 0 or break. The discounts page already uses the new `/admin/discounts/switches` endpoint.
- For Step 3: saving grants on the Staff page must call `StaffDirectory.evict(...)` so changes apply on the next click. Without that, a direct database edit takes up to 15 seconds. The permission checklist should tick an area's "see" permission when any of its edit permissions is ticked (e.g. `settings.footer` needs `settings.view` to open the page). The backend client secret for staff accounts is in `.env` (`KEYCLOAK_ADMIN_CLIENT_*`, written by `deploy/keycloak_setup.py`).
- For production: run `deploy/keycloak_setup.py` once against the live Keycloak, and turn off password login from scripts (`directAccessGrantsEnabled`) on `delivery-admin-ui`. It stays on locally for the checks.
- Fixed on the way: the edit-product form reset the sale discount to 0 on every load, so saving any product quietly removed its sale price.

### Step 2 — Admin panel follows permissions

- [x] **2.1 Permission service.** Loads `/admin/me` after login and refreshes on window focus, periodically, and after any 403, so changes show without logging out.
- [x] **2.2 Route guard per page** with its required permission, plus a friendly "No access" page.
- [x] **2.3 Sidebar** shows only permitted items.
- [x] **2.4 Buttons and fields** follow permissions: cancel, delete, save, send, refund; price fields read-only without `products.price`; each settings section read-only without its permission.
- [x] **2.5 Global 403 handling.** A clear "You don't have permission for this" message and a permission refresh.
- [x] **2.6 Header** shows the person's name and Admin or Moderator.
- [x] **2.7 Landing page.** A moderator without `dashboard.view` lands on their first permitted page.
- [x] **2.8 Money hidden without `finance.revenue`** on the dashboard, analytics and customers pages; Reports hidden entirely.

**Step 2 notes (done 12 Sep 2026).** Browser check 32/32, logged in as a throwaway moderator whose permissions were granted and taken away during the run. 89 backend tests pass. The admin production build passes, but the first-load bundle is 536 kB: over the 500 kB warning level, under the 1 MB limit.

- Where things live: page rules and sidebar order in `delivery-admin/src/app/core/access/admin-pages.ts`; the `PermissionService` (`can`, `canAny`, `allows`); the `accessGuard`; the "No access" page; a pop-up for refusals. A new `GET /admin/permissions` endpoint gives permission names in plain words, and the Staff page in Step 3 will use it too.
- Decided while building:
  - "Edit products" also lets the person see a product's photos and sizes; the backend rule was widened to match.
  - The Analytics CSV export follows "Export and print reports", like Reports.
  - The dashboard's trend chart and best-sellers card need Reports access.
  - Only admins see riders in search.
- Fixed on the way:
  - On nine pages, a refused or failed delete removed the row from the screen while it still existed on the server.
  - The category list's Active switch wiped the category's image, home-page section and "Never discount". It also let someone without the permission turn "Never discount" off. The backend now compares the value that will actually be stored.
  - The products list's Available switch removed the product's sale price.
  - The dashboard header was hard-coded to "Admin User / Super Admin".
- Known and left alone:
  - Analytics still ranks top categories by revenue when the figures are hidden, so the order hints at relative sales.
  - Some buttons have always done nothing: More Filters on Orders; Filter and Export on Products and Categories; Sort on Categories.
  - The banners empty state invites people who can't create banners to upload one.
  - A moderator who can add order notes but not read them gets no confirmation after saving one.
  - Order notes, and dashboard cards that need Reports, only appear after a page reload when access is granted while the page is open.


### Step 3 — Staff page and activity log

- [x] **3.1 Keycloak admin client** in the backend, using the service account from 1.1.
- [x] **3.2 Staff endpoints.** List; add moderator (name, email, username, temporary password that must be changed at first login); edit; deactivate and reactivate (also signs them out everywhere); reset password, again temporary; delete (removes the Keycloak account and all their permissions, signs them out, keeps their activity history by name).
- [x] **3.3 Permission endpoints.** Catalogue grouped by area; read and replace one moderator's permissions; delegation rule enforced on the server.
- [x] **3.4 Templates.** Create, rename, delete, apply to a moderator.
- [x] **3.5 Safety rules.** Last admin protected; no self-demotion, self-deactivation or self-deletion; moderators cannot create, deactivate or delete admins, or grant what they do not hold; deleting asks for a typed confirmation because it cannot be undone.
- [x] **3.6 Staff page UI.** Staff list with active and deactivated people; add-moderator form; permission checklist grouped by area with select-all and search; templates; deactivate and reactivate; reset password; delete with confirmation.
- [x] **3.7 Activity log page.** Filter by person, area and date.

**Step 3 notes (done 12 Sep 2026).** Live backend check 51/51. Browser check 18/18: a moderator was added on the Staff page, made to choose a new password at first login, given a permission, deactivated, and deleted, and the activity log showed each step. 100 backend tests pass, and the Step 1 and Step 2 checks were run again.

- How it works: the backend manages staff logins through Keycloak's admin API, as the `erezer-backend-admin` service account. Its settings are `KEYCLOAK_ADMIN_SERVER_URL`, `KEYCLOAK_ADMIN_CLIENT_ID` and `KEYCLOAK_ADMIN_CLIENT_SECRET`, and `deploy/keycloak_setup.py` writes the secret to `.env`. Every change clears the person's cached access once it is saved, so the server applies it on their next click. Their open panel catches up within a minute, or straight away on reload.
- Decided while building:
  - Only admins can change someone's role, and a role change clears that person's permissions.
  - People reset their own password from their Keycloak account page, not from the Staff page.
  - Every staff member needs an email.
  - Picking a template fills the checklist, and you save it yourself, so the ticks can be checked first.
  - The checklist also ticks what a permission depends on. For example, "Cancel orders" ticks "See orders", and "See business reports" ticks "See money totals".
  - A deleted person's still-valid login token is refused and cannot re-create them.
- For production: run `deploy/keycloak_setup.py` against the live Keycloak and put `KEYCLOAK_ADMIN_CLIENT_SECRET` in the server's `.env` before using the Staff page. Without it, the page explains that staff logins can't be managed yet.
- Known and left alone:
  - Someone given the admin or moderator role directly in Keycloak appears on the Staff page only after their first login.
  - The activity log covers changes made in the admin panel, not customer actions on the storefront.


### Step 4 — Verify

- [x] **4.1 Automated sweep.** Script creates a moderator, grants a subset, and checks every catalogue entry returns 403 or 200 as expected, then cleans up. A second sweep repeats the customer isolation checks from 0.5.
- [x] **4.2 Browser walkthrough as a moderator.** Sidebar, hidden buttons, no-access page, and an instant revoke while logged in.
- [x] **4.3 Docs.** Section in `TESTING.md`, and a short note on adding a permission when adding a feature.

**Step 4 notes (done 12 Sep 2026).**

- **Sweep:** `python deploy/verify_access_control.py` passes all 408 checks.
  - It reads the 120 staff endpoints and their rules from the running code, through the admin-only `GET /admin/access/endpoints`, so new endpoints are included by themselves.
  - It adds two throwaway moderators: one with the 25 "see" permissions, one with the other 54.
  - Every endpoint refuses or lets in each moderator exactly as its rule says, and none answers without a login. The admin reaches all 54 reads.
  - The customer-isolation checks pass again.
  - It never changes real data, and it deletes both moderators at the end.
- **Browser walkthroughs:** 21/21 and 32/32. They covered:
  - the sidebar, hidden buttons and hidden money;
  - the "No access" page;
  - a new moderator made to choose their own password;
  - a permission removed on the Staff page while the moderator was working, with their very next click refused.

  These ran from throwaway scripts that are not in the repo. The lasting check is the sweep script.
- **Docs:** `TESTING.md` section 9 covers one-time setup, trying it by hand, the automated check, and the steps for adding an admin feature. `Perm.java` points to it.
- **Fixed on the way:** a malformed request (an id in the wrong format, a missing required parameter, or an unreadable body) answered 500. It now answers 400 with a plain message.
- **Fixed after review:**
  - Editing or deleting a banner, category or product that doesn't exist answered 500; it now answers 404. The sweep now runs without warnings.
  - The Staff page's add form moved to its own page, `/staff/new`, which needs "Add staff". The Staff list page shows only the list.


## Open questions

None. All answered on 11 September 2026; the answers are recorded under Agreed decisions.
