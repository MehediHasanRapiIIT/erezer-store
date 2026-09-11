# Product code

A **product code** that staff type for every product, next to the automatic
SKU. Status: **built and checked, 12 Sep 2026.**

## Decided (12 Sep 2026)

- **Add a new field; keep the SKU.**
  - The SKU stays the system's own automatic ID (PI-00036). Size SKUs, stock
    and the product data sent to Google depend on it.
  - The product code is typed by staff and can be changed at any time.
- **Required.** Every existing product gets a code first, so nothing breaks
  when the field becomes required.
- **Two products may share a code.** Codes are not unique.
- **Who can set it:**
  - anyone with "Edit products";
  - when adding a product, "Add products" (as today). No new permission.
- **Shown on every admin page where products appear**, and on order invoices.
- **The order page shows a bigger product picture**, so it's clear what the
  customer ordered.

## How it will work

**Existing products:** each one starts with its SKU as its product code (for
example PI-00036). Deleted products get one too, so old orders show a code.
Staff can change any of them from the product's edit page.

**The code itself:** required, 1 to 40 characters, with spaces trimmed from the
ends, shown exactly as typed. Search ignores capital versus small letters.

**Orders keep the code the product had when it was ordered**, the same way
they keep the price. Changing a product's code later doesn't rewrite old
invoices. Existing orders take the product's code on the day this is released.

## Where it shows

| Page | What changes |
|---|---|
| Add product, Edit product | New required **Product code** field |
| Products list | Code shown under the name; the search finds codes |
| Inventory (stock list and restock alerts) | Code shown; the search finds codes |
| Change prices | Code shown in the preview; the search finds codes |
| Order details | Each item shows its code and size, with a bigger picture that opens full size when clicked |
| Invoice (download, and the email to the customer) | Code shown with each item |
| Returns | Code shown with each returned item |
| Dashboard, Analytics, Reports (top products) | Code next to each product name |
| Discounts, Flash sales, Bundles, Reviews (product pickers) | Code shown; typing a code finds the product |
| Discounts list | Code next to the target product's name |
| Admin search box (sidebar) | Finds products by code and shows it |
| Activity log (price changes) | Each line names the product code |

**The shop's own search** will also find a product by its code, because the
admin pickers use that search. Customers who know a code, say from a tag, can
find the product. The code isn't shown anywhere on the shop's pages. The one
place customers see it is the invoice they receive.

## Tasks

- [x] **1. Backend.**
  - A migration adds the code to products, fills it from the SKU, and then
    makes it required. It also adds the code to order items and fills those in.
  - The product form requires the code. New orders keep a copy of it.
  - Everything the table above lists sends the code, and every product search
    also finds codes.
- [x] **2. Admin panel.** Every page in the table, and the bigger order picture.
- [x] **3. Invoice.** The code with each item.
- [x] **4. Checks.**
  - Backend tests, the permission sweep and `verify_paging.py`.
  - A live check with marked test products: two products sharing a code, a
    blank code refused, a code change leaving an old order's invoice as it was.
    The test products are removed afterwards.
  - A browser walk-through of every page in the table.

## Part 2 — codes by category (built and checked, 12 Sep 2026)

Give a whole category its codes at once, as well as per product. Asked for on
12 Sep 2026, after Part 1 was in use.

### Decided

- **The same code for every product** is the normal way: you type `EP-1001` and
  every ticked product in the category gets exactly that code. Products may
  share a code, so this is allowed.
  - *Changed on 12 Sep 2026, after seeing the first build.* It was first built
    as numbering only; that is now the second choice below.
- **A prefix with running numbers** stays available: `EP` gives `EP-001`,
  `EP-002`, `EP-003`… in name order (A → Z).
- **Every ticked product** is changed, including ones whose code was typed by
  hand. Everything is ticked by default; untick what you want left alone.

### How it will work

A **Change codes** button on Products, next to Change prices, opens a page that
works the same way:

1. **Pick a category**, then choose what each product gets:
   - **the same code for every product** (the normal way): type it, e.g.
     `EP-1001`, up to 40 characters — anything the product form accepts; or
   - **a prefix with numbers**: type the prefix (letters, numbers, dashes and
     underscores, up to 20 characters).
2. **See a preview** of every product in the category, 20 at a time, with a
   search box: old code → new code. Untick any product to leave it out; ticks
   are remembered across pages.
3. **Apply.** Everything is saved together or not at all, and the Activity log
   keeps every old and new code under "Show details".

With the numbered way:
- Numbers start at 001 and are three digits (`EP-001`); past 999 they simply
  grow (`EP-1000`).
- Products are numbered in name order. An unticked product keeps its own code,
  and a number already used by an unticked product with the same prefix is
  skipped, so applying twice doesn't create accidental twins. Numbers can
  therefore have gaps, which is normal.
- Products keep their own edit page, so any single code can still be corrected
  afterwards.
- A code that would end up longer than 40 characters is refused, as elsewhere.

### Who can use it

"Edit products", the same permission as changing one product's code today. (The
price tool needs "Change prices" as well; codes are not money, so that one
doesn't apply here.)

### Searching by code

Already built in Part 1, and already done by the server, not the browser:
Products, Inventory, Change prices, the sidebar search box, and the product
pickers on Discounts, Flash sales, Bundles and Reviews. The checks for Part 2
will exercise it again.

**New in Part 2:** the **Orders** page search also finds orders by a product
code in their lines, alongside the order number, customer name, phone, email
and address it already searches. Each order keeps the code it was placed with,
so searching an old code finds the orders that were placed with it.

### Tasks

- [x] **1. Backend.** A preview endpoint and an apply endpoint for codes, with
  the numbering in one tested place (name order, skipped numbers, length limit).
- [x] **2. Admin page.** As above, reusing the Change prices layout, pages and
  ticking.
- [x] **3. Checks.** Backend tests, the permission sweep, a live check on marked
  test products (removed afterwards), and a browser walk-through.

### Part 2 notes (done 12 Sep 2026)

- **Endpoints:** `GET /admin/products/code-change/preview` (a read, so trying
  codes isn't logged) and `POST /admin/products/code-change`. Both take a
  `mode` (SAME or NUMBERED) with the code or the prefix, and both need
  "Edit products". The rules live in `service/CategoryCodeChange.java`.
- **Page:** `/products/codes`, from a **# Change codes** button on Products that
  carries over the category chosen in the list. Two choices, with "the same code
  for every product" picked to start with. It shows 20 products at a time with a
  search on name or code, and the preview is worked out again whenever you tick
  or untick, because with numbers the result depends on it.
- **Orders search:** the order list also matches a product code in an order's
  lines (`ix_order_item_product_code`, migration V14). Since each order keeps
  the code it was placed with, searching an old code finds the old orders.
- **Worth knowing:** an unticked product always keeps its code. With numbers, a
  number it already holds with that prefix is skipped, so no two products end up
  sharing a numbered code by accident; numbers can therefore have gaps. Applying
  the very same thing twice changes nothing the second time.
- **Checks:**
  - Backend tests: 5 for the code and prefix rules, 9 for the service (both
    ways of giving codes).
  - Live check: 33/33.
  - Browser check: 15/15.
  - Access sweep: 434/434 (127 endpoints, the two new ones included).
  - `verify_paging.py`: 144/144, now including searches by product code on the
    shop, the Products page, Inventory and Orders.
  - The test category, products, test order and their log lines were removed.

## Part 1 notes (done 12 Sep 2026)

- **Database:**
  - Migration `V13__product_code.sql` adds `product.product_code`, filled from
    the SKU and then made required, with a search index on the lower-case code.
  - It also adds `order_item.product_code`, filled from each product.
  - New orders copy the code in `OrderServiceImpl.persistItems`. An order line
    without its own copy shows the product's current code.
- **Found while building:**
  - The Available switch on the Products list re-saves the whole product.
    It now sends the code back unchanged; without that, every switch would have
    been refused.
  - On the order page, "Code: …" replaces the internal product number that used
    to sit under the name (the "18").
- **Checked end to end too (12 Sep 2026):** a real order placed through the
  shop's guest checkout kept the product's code on its line; changing the code
  afterwards left the order page and the invoice showing the old one. 8/8.
  - Run safely by pointing the backend's mail at Mailpit for the duration
    (a temporary compose override; `.env` untouched, settings restored after).
    The script refuses to run unless mail points at Mailpit, and deletes its own
    test email afterwards. Facebook reporting stays off: it needs a pixel id and
    an access token, and neither is set.
- **Checks:**
  - Backend tests pass.
  - Live check: 19/19.
  - Browser check: 13/13, including the invoice preview showing
    "Code: PC-SHARED-1" and the full-size order picture.
  - `verify_paging.py`: 139/139.
  - Access sweep: 428/428.
  - The test data and its log lines were removed.
