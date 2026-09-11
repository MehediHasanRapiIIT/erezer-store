# Server-side search and paging

**Goal.** Lists are searched, filtered, sorted and paged by the server, which
sends back one page at a time with the total count. The browser no longer
downloads a whole list to filter it. Agreed 12 Sep 2026; work goes one item at
a time, each finished and checked before the next.

## Agreed

- **Shop paging stays "Load more"**: each press fetches the next page from the server.
- **Every list that can grow** gets server search and paging. Short lists that a
  shop keeps small (coupons, discounts, flash sales, bundles, banners, staff) stay
  as they are.
- **One format everywhere.** In: `page`, `size`, `q` (search text), filters, `sort`.
  Out: Spring's page (`content`, `totalElements`, `totalPages`, `number`, `size`).
- **Stable order.** Every sort ends with the id, so page 2 never repeats or skips
  anything from page 1.
- **Search waits for a short pause in typing**, then asks the server.

## Tasks

- [x] **1. Shop.** One endpoint for the shop list (search, category, gender,
  brand, maximum price, sort, page) and one for the filter choices. The shop
  and collection pages use them; "Load more" fetches the next page.
  *Done 12 Sep 2026.*
  - Endpoints: `GET /api/products/browse` and `GET /api/products/facets`, both public.
  - Pages hold 20 products; the server caps a page at 60.
  - Search now matches name, brand and description (it used to be the name only).
  - "Featured" really puts featured products first, then the newest. Price sorts
    use the price customers see.
  - Checks: `deploy/verify_paging.py shop` passes 53/53 against the database, and
    the browser check 14/14. The shop no longer downloads the whole catalogue.
- [x] **2. Admin: Products.** Search by name, SKU or category across all
  products, not just the page on screen. *Done 12 Sep 2026.*
  - Endpoint: `GET /admin/products`, which needs "See products". It searches
    name, SKU, brand and category name, has an optional category filter, and
    lists newest first.
  - The dead "Filter" button became a category dropdown.
  - Checks: `verify_paging.py admin-products` passes 14/14. With no search, the
    list and order are identical to before, and everything the old name search
    found is still found. The browser check passes 9/9: a product that only
    appears on page 2 is found by search.
- [x] **3. Admin: Orders.** Search by order number, customer name or phone
  across all orders. *Done 12 Sep 2026.*
  - `GET /admin/orders/paged` now takes `q` and `payment`. Search matches the
    order number (with or without "#"), the customer's name, phone and email,
    and the address.
  - The Payment filter is done by the server too; it used to filter only the 10
    orders on screen. "Cash" includes the 5 older orders saved as COD.
  - Date filters now use the Dhaka day. They used to use the browser's UTC day,
    so "Today" could show the wrong day early in the morning.
  - Newest first, with the id breaking ties.
  - Checks: `verify_paging.py admin-orders` passes 18/18 against the database.
    That includes an order placed at 18:54 UTC landing on its Dhaka day, and
    everything the old per-page search or Cash filter could show still being
    found.
- [x] **4. Admin: Returns, Support, Newsletter.** Page buttons (today only the
  first page shows) and search. *Done 12 Sep 2026.*
  - Each list endpoint takes `q`:
    - returns: customer email, reason, order and return numbers;
    - support: name, email, subject and message;
    - subscribers: email;
    - campaigns: subject.
  - Newest first, with the id breaking ties.
  - A shared pager (`shared/pager`) shows "Showing 21–40 of 312" with Previous
    and Next. Actions reload the current page.
  - These tables are empty in the local database. The checks ran on temporary
    marked rows (45 returns, 40 messages, 60 subscribers, 25 campaigns), removed
    afterwards: `verify_paging.py admin-inboxes` passes 31/31, and the browser
    check 17/17.
- [x] **5. Admin: Inventory, Categories.** Server search and paging instead of
  downloading everything. *Done 12 Sep 2026.*
  - `GET /admin/inventory` is now paged, 20 per page, and searches product name
    and SKU.
  - A new `GET /admin/inventory/alerts` lists every low or out-of-stock product,
    so the restock banner still covers the whole shop. Bulk update works on the
    page on screen.
  - A new `GET /admin/categories` (needs "See categories") pages and searches
    name and web address. The shop keeps its whole-list `/api/categories`.
  - Checks: `verify_paging.py` passes 109/109 across every item so far, the
    access sweep 420/420 (123 endpoints), and the browser check 10/10.
- [x] **6. Admin: Customers, Custom orders, Reviews.** Add server search.
  *Done 12 Sep 2026.*
  - Customers: `GET /admin/customers` and `/admin/customers/count` take `q`
    (name, email or phone). The page keeps "Load more" (50 at a time) and shows
    how many match. Ties in revenue are broken by customer id, so "Load more"
    never repeats anyone. The count now leaves out deleted accounts, the same
    as the list does.
  - Custom orders: `GET /admin/custom-orders` takes `q` (reference, customer
    name, phone, email, item). A search box sits next to the Active/History
    tabs; deleting a request reloads the page from the server.
  - Reviews: the product picker searches the shop list on the server, 20 at a
    time with "Load more", instead of downloading every product. It uses the
    public search, so "See reviews" alone is still enough.
  - Checks: `verify_paging.py` passes 132/132 across every item so far (with
    45 marked test requests added and then removed), the access sweep 420/420,
    and the browser check 19/19.
- [x] **7. Admin: product pickers** on Discounts, Flash sales and Bundles: type
  to search instead of downloading every product. *Done 12 Sep 2026.*
  - Flash sales and Bundles share one picker
    (`shared/product-picker/product-multi-picker.component.ts`). It searches the
    shop list on the server, 8 at a time, with the category filter sent to the
    server too. "Select all (filtered)" fetches every match, not just the page
    on screen. Categories come from the category list, so a category shows even
    when its products are on another page.
  - Discounts: the product dropdown is now a type-to-search box showing 8
    results; the chosen product is shown by name with a Change button. The
    table names each product discount by looking up just those products.
  - Nothing in the admin panel downloads the whole product list any more.
  - Checks: the browser check passes 29/29 (read-only: every form is
    cancelled). The server search these pickers use is the shop's, already
    covered by `verify_paging.py shop`.

Each item is checked two ways: the server's results must match what the old
in-browser filter showed, and paging through must never repeat or skip a row.
