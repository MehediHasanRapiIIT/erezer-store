# Change prices by category

Set prices for a whole category at once, as well as per product (which works
today on the product form). Status: **built and checked, 12 Sep 2026.**

## Decided (12 Sep 2026)

- **Option A:** a category change writes the new price into each product, as if
  each had been edited by hand. Afterwards any product can still be changed on
  its own. The category does not keep a price of its own, so products added
  later are not affected.
- **The sale discount % is covered too.**
- **Sizes with their own price change too.**

## How it will work

A **Change prices** button on the Products page opens a page where you:

1. **Pick a category.**
2. **Choose the price change:**
   - set to an exact price (৳1,500)
   - raise or lower by an amount (৳100)
   - raise or lower by a percentage (10%)

   New prices are rounded to whole taka.
3. **Choose what happens to the sale discount:**
   - keep each product's current sale %, recalculated on the new price
   - set one sale % for every product in the category
   - remove the sale

   The sale price is worked out exactly as the product form does it today.
4. **See a preview before anything is saved:** every product in the category
   with its old and new price, old and new sale price, and old and new size
   prices. You can untick any product to leave it out. A change that would make
   a price ৳0 or less is refused.
5. **Apply.** Everything is saved together, or nothing is. The Activity log gets
   one entry listing each product's old and new prices, so a mistake can be
   found and put back by hand.

**Sizes with their own price** get the same change:
- raise or lower by an amount: the same amount;
- by a percentage: the same percentage;
- set to an exact price: each size keeps its gap from the product price, so an
  XXL that was ৳100 above stays ৳100 above the new price.

## What does not change

- **Past orders** keep the price they were placed at.
- **Carts** show the new price straight away, the same as editing one product
  today.
- **Discounts, flash sales, coupons and bundles** keep their own rules. A
  bundle's fixed price is not touched.
- **"Never discount" products** keep that setting.
- **Sale discounts on sizes with their own price:** as today, such a size
  sells at its own price, without the product's sale discount.
- **Deleted products** are skipped.

## Who can use it

Needs both "Edit products" and "Change prices". These are the same permissions
needed to change one product's price today, so nobody gains anything new.

## Tasks

- [x] **1. Backend.** A preview endpoint and an apply endpoint.
  - They take the same request, and apply saves in one transaction.
  - The price and sale arithmetic sits in one place, with tests covering amounts,
    percentages, rounding, the size gap, keep/set/remove sale, and the ৳0 guard.
  - Both endpoints are protected by the two permissions, and apply writes to
    the Activity log.
- [x] **2. Admin page.** The steps above, with the button on Products shown only
  to people with both permissions.
- [x] **3. Checks.**
  - Backend tests.
  - The permission sweep includes the new endpoints.
  - A live check on a temporary test category with marked test products (never
    real products), removed afterwards.
  - A browser check.

## Notes (done 12 Sep 2026)

- **Endpoints:**
  - `GET /admin/products/price-change/preview` is a read, so trying options
    doesn't fill the Activity log.
  - `POST /admin/products/price-change` takes the ticked `productIds` and
    refuses the whole change if a ticked product has left the category or would
    reach ৳0.
  - The arithmetic is in `service/CategoryPriceChange.java`.
- **Page:** `/products/prices`, from a **Change prices** button on Products (it
  carries over the category chosen in the list). Also added while building:
  - "Keep prices as they are", for changing only the sale.
  - Products that can't be changed are shown in red and can't be ticked.
  - Changing any setting throws the preview away, so an out-of-date preview
    can't be applied.
- **Preview pages (added 12 Sep 2026, for categories of 100+ products):**
  - The preview shows 20 products at a time, with a search by name or SKU.
    Ticks are remembered by product across pages and searches.
  - "Tick all", "Untick all" and "Apply to N products" cover every page. The
    server works out the whole category and sends the counts and the list of
    changeable products with every page.
  - Checks: service tests for paging and search; live check 35/35; browser
    check 21/21 on 25 test products.
- **Activity log:** entries can now carry longer details (new column
  `admin_activity.details`, migration V12), shown under **Show details** on the
  Activity log page. A price change keeps every old and new price there.
- **Checks:**
  - Backend tests: 11 for the arithmetic, 6 for the service rules, and 2 for
    permissions and logging.
  - Live check: 28/28.
  - Access sweep: 428/428 (125 endpoints).
  - Browser check: 12/12.
  - The test category and products, and their log lines, were removed afterwards.
