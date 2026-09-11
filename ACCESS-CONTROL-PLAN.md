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

- [ ] **0.1 Customer data isolation (A1).** One central check for every `/app/consumer/{userId}/**` request: the id in the address must equal the logged-in customer, otherwise 403. Covers addresses, cart, orders, order history, profile, returns and design drafts. Guest checkout (`/app/consumer/guest/orders`) stays open.
- [ ] **0.2 Reviews (A2, A3).** Take customer review endpoints out of the admin security chain and require a customer login instead. Take the reviewer's id from the login, never from the request. Admin review deletion stays under `/admin`.
- [ ] **0.3 API docs (A5).** Turn Swagger UI and the OpenAPI file off in the `prod` profile; keep them for local work.
- [ ] **0.4 Admin dead calls (A8).** Remove the admin panel's calls to customer endpoints, or replace them with admin endpoints if a screen needs the data.
- [ ] **0.5 Tests.** Customer A gets 403 for every one of customer B's endpoints, reads and writes; A still gets 200 for their own; a customer can post, edit and delete only their own review.

### Step 1 — Backend foundation

- [ ] **1.1 Keycloak.** Add realm role `moderator`. Turn on brute-force protection. Add a confidential client with a service account and `manage-users`, `view-users`, `query-users` so the backend can create staff. For production: `sslRequired: external`, and password login from scripts off on the admin client (A10). Update `keycloak/delivery-admin-realm.json` for fresh installs, and apply the same changes to the running Keycloak (the realm file is imported on first boot only).
- [ ] **1.2 Migration V11.** Tables: `staff_member` (Keycloak user id, username, email, name, role, active, audit columns), `permission` (key, area, label, description, sort), `staff_permission`, `permission_template`, `permission_template_item`, `admin_activity` (who, stored by name as well as id so entries survive the person being deleted; action; target type and id; summary; time; IP).
- [ ] **1.3 Permission registry in code.** One place listing every key with area and label. At startup it syncs into the `permission` table: adds new keys, never deletes existing grants.
- [ ] **1.4 Current staff resolver.** From the Keycloak token to a `staff_member` row, created on first login. The Keycloak `admin` role means Admin.
- [ ] **1.5 Enforcement.** `@RequiresPermission("orders.cancel")` on handler methods, checked by an interceptor. Admins always pass. Moderators are checked against the database through a short cache that is cleared whenever permissions change. A refusal returns 403 with the missing permission key.
- [ ] **1.6 Annotate every admin endpoint** per the catalogue, including product, category and banner changes under `/api`.
- [ ] **1.7 Field-level checks.** Product price; cancel via status change; each settings section; revenue fields blanked without `finance.revenue` in dashboard, analytics and customers.
- [ ] **1.8 Split the discount switches** into their own endpoint so they can be granted separately from settings.
- [ ] **1.9 Deny-by-default test.** Scan every admin handler; fail the build if any lacks a permission or an explicit admin-only marker.
- [ ] **1.10 `GET /admin/me`.** Returns name, role and permission keys for the admin panel.
- [ ] **1.11 Activity log writes.** Every successful change in the panel records who did what.
- [ ] **1.12 Tests.** Admin passes; moderator without permission gets 403; with it gets 200; a grant or revoke applies to the very next request.
- [ ] **1.13 Stock feed (A9).** Require a staff login on the `/ws/**` stock feed. Low priority.

### Step 2 — Admin panel follows permissions

- [ ] **2.1 Permission service.** Loads `/admin/me` after login and refreshes on window focus, periodically, and after any 403, so changes show without logging out.
- [ ] **2.2 Route guard per page** with its required permission, plus a friendly "No access" page.
- [ ] **2.3 Sidebar** shows only permitted items.
- [ ] **2.4 Buttons and fields** follow permissions: cancel, delete, save, send, refund; price fields read-only without `products.price`; each settings section read-only without its permission.
- [ ] **2.5 Global 403 handling.** A clear "You don't have permission for this" message and a permission refresh.
- [ ] **2.6 Header** shows the person's name and Admin or Moderator.
- [ ] **2.7 Landing page.** A moderator without `dashboard.view` lands on their first permitted page.
- [ ] **2.8 Money hidden without `finance.revenue`** on the dashboard, analytics and customers pages; Reports hidden entirely.

### Step 3 — Staff page and activity log

- [ ] **3.1 Keycloak admin client** in the backend, using the service account from 1.1.
- [ ] **3.2 Staff endpoints.** List; add moderator (name, email, username, temporary password that must be changed at first login); edit; deactivate and reactivate (also signs them out everywhere); reset password, again temporary; delete (removes the Keycloak account and all their permissions, signs them out, keeps their activity history by name).
- [ ] **3.3 Permission endpoints.** Catalogue grouped by area; read and replace one moderator's permissions; delegation rule enforced on the server.
- [ ] **3.4 Templates.** Create, rename, delete, apply to a moderator.
- [ ] **3.5 Safety rules.** Last admin protected; no self-demotion, self-deactivation or self-deletion; moderators cannot create, deactivate or delete admins, or grant what they do not hold; deleting asks for a typed confirmation because it cannot be undone.
- [ ] **3.6 Staff page UI.** Staff list with active and deactivated people; add-moderator form; permission checklist grouped by area with select-all and search; templates; deactivate and reactivate; reset password; delete with confirmation.
- [ ] **3.7 Activity log page.** Filter by person, area and date.

### Step 4 — Verify

- [ ] **4.1 Automated sweep.** Script creates a moderator, grants a subset, and checks every catalogue entry returns 403 or 200 as expected, then cleans up. A second sweep repeats the customer isolation checks from 0.5.
- [ ] **4.2 Browser walkthrough as a moderator.** Sidebar, hidden buttons, no-access page, and an instant revoke while logged in.
- [ ] **4.3 Docs.** Section in `TESTING.md`, and a short note on adding a permission when adding a feature.

## Open questions

None. All answered on 11 September 2026; the answers are recorded under Agreed decisions.
