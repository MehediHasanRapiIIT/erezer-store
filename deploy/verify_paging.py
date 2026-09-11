#!/usr/bin/env python3
"""
Checks the server-side search and paging endpoints against the database.

For each list, the expected rows are worked out here in plain Python straight
from the database. They are then fetched from the API a small page at a time,
so paging is really exercised. The two must match exactly, in order: no row
repeated or skipped, and the right total. Where a list replaced an in-browser
filter, the old endpoint's results filtered the old way must agree as well.

Usage (stack running, from the repo root):
    python deploy/verify_paging.py            # every list
    python deploy/verify_paging.py shop       # just one

Exit status 0 means every check matched.
"""
from __future__ import annotations

import json
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API = "http://localhost:8080"
PAGE = 5  # small on purpose: most checks span several pages


def psql_json(sql: str) -> list[dict]:
    """Rows as dicts, via json_agg so text with newlines or separators survives."""
    r = subprocess.run(["docker", "compose", "exec", "-T", "postgres", "psql", "-U", "postgres",
                        "-d", "delivery_app_v1", "-At", "-c", f"SELECT coalesce(json_agg(t), '[]') FROM ({sql}) t"],
                       capture_output=True, text=True, cwd=ROOT, encoding="utf-8")
    if r.returncode != 0:
        raise SystemExit(r.stderr.strip())
    return json.loads(r.stdout.strip() or "[]")


def get(path: str, params: dict | None = None, token: str | None = None):
    query = urllib.parse.urlencode({k: v for k, v in (params or {}).items() if v is not None and v != ""})
    req = urllib.request.Request(f"{API}{path}{'?' + query if query else ''}")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")


class Results:
    def __init__(self):
        self.checks = 0
        self.fails: list[str] = []

    def expect(self, label: str, ok: bool, detail: str = ""):
        self.checks += 1
        print(f"  {'ok ' if ok else 'BAD'} {label}{(' ' + detail) if detail and not ok else ''}")
        if not ok:
            self.fails.append(f"{label} {detail}".strip())


def all_pages(path: str, params: dict, token: str | None = None, size: int = PAGE) -> tuple[list, int]:
    """Every item of a paged list, fetched page by page, and the reported total."""
    items: list = []
    page, total = 0, None
    while True:
        status, body = get(path, {**params, "page": page, "size": size}, token)
        if status != 200:
            raise SystemExit(f"{path} {params} page {page}: HTTP {status} {str(body)[:200]}")
        total = body["totalElements"]
        items += body["content"]
        page += 1
        if page >= max(body["totalPages"], 1):
            return items, total


def check_list(results: Results, label: str, got: list, total: int, expected: list):
    dupes = len(got) - len(set(got))
    results.expect(f"{label}: {len(expected)} rows in the right order, no repeats",
                   got == expected and dupes == 0 and total == len(expected),
                   f"got {len(got)} (total {total}, {dupes} repeated), expected {len(expected)}"
                   + ("" if set(got) == set(expected) else f"; missing {sorted(set(expected) - set(got))[:5]}"
                      f", extra {sorted(set(got) - set(expected))[:5]}"))


# ── 1. Shop ──────────────────────────────────────────────────────────────────

def shop(results: Results):
    print("== shop list (/api/products/browse) ==")
    products = psql_json("SELECT id, name, brand, description, gender, category_id, price, discount_price, "
                         "coalesce(is_featured, false) AS featured, coalesce(deleted, false) AS deleted FROM product")

    def shown(p) -> Decimal:
        return Decimal(str(p["discount_price"] if p["discount_price"] is not None else p["price"]))

    def matches(p, q=None, category=None, gender=None, brand=None, max_price=None) -> bool:
        if p["deleted"]:
            return False
        if q and not any(q.lower() in (p[k] or "").lower() for k in ("name", "brand", "description")):
            return False
        if category is not None and p["category_id"] != category:
            return False
        if gender and p["gender"] != gender:
            return False
        if brand and p["brand"] != brand:
            return False
        if max_price is not None and shown(p) > Decimal(str(max_price)):
            return False
        return True

    def ordered(rows, sort):
        if sort == "price-asc":
            return sorted(rows, key=lambda p: (shown(p), p["id"]))
        if sort == "price-desc":
            return sorted(rows, key=lambda p: (-shown(p), p["id"]))
        return sorted(rows, key=lambda p: (not p["featured"], -p["id"]))

    live = [p for p in products if not p["deleted"]]
    categories = sorted({p["category_id"] for p in live if p["category_id"] is not None})
    genders = sorted({p["gender"] for p in live if p["gender"]})
    brands = sorted({p["brand"] for p in live if p["brand"]})
    prices = sorted(shown(p) for p in live)
    median = prices[len(prices) // 2] if prices else Decimal("0")
    words = sorted({w for p in live for w in (p["name"] or "").lower().split() if len(w) > 3})[:3]

    scenarios = [({}, s) for s in ("featured", "price-asc", "price-desc")]
    scenarios += [({"category": c}, "featured") for c in categories]
    scenarios += [({"gender": g}, "price-asc") for g in genders]
    scenarios += [({"brand": b}, "featured") for b in brands[:3]]
    scenarios += [({"max_price": median}, "price-desc")]
    scenarios += [({"q": w}, "featured") for w in words]
    if brands:
        scenarios += [({"q": brands[0][:4]}, "price-asc")]
    scenarios += [({"q": "zzzz-nothing-matches"}, "featured"), ({"q": "%"}, "featured")]
    if categories and genders:
        scenarios += [({"category": categories[0], "gender": genders[0]}, "price-asc")]

    old_all = get("/api/products")[1]
    for filters, sort in scenarios:
        expected = [p["id"] for p in ordered([p for p in products if matches(p, **filters)], sort)]
        params = {"q": filters.get("q"), "categoryId": filters.get("category"), "gender": filters.get("gender"),
                  "brand": filters.get("brand"), "maxPrice": filters.get("max_price"), "sort": sort}
        items, total = all_pages("/api/products/browse", params)
        got = [x["id"] for x in items]
        check_list(results, f"{filters or 'everything'} / {sort}", got, total, expected)

        # The old shop: a whole list, filtered in the browser.
        old = old_all if "category" not in filters else get(f"/api/categories/{filters['category']}/products")[1]
        if "q" in filters:
            old = get("/api/products/search", {"name": filters["q"]})[1]
        old_ids = {x["id"] for x in old if
                   (not filters.get("gender") or x.get("gender") == filters["gender"])
                   and (not filters.get("brand") or x.get("brand") == filters["brand"])
                   and ("max_price" not in filters or
                        Decimal(str(x["discountPrice"] if x["discountPrice"] is not None else x["price"]))
                        <= Decimal(str(filters["max_price"])))}
        if "q" in filters:
            # The old search looked at names only; the new one adds brand and description.
            results.expect(f"  old name-only search results all still found for {filters['q']!r}",
                           old_ids <= set(got), f"missing {sorted(old_ids - set(got))[:5]}")
        else:
            results.expect("  same products as the old in-browser filter", old_ids == set(got),
                           f"old {len(old_ids)}, new {len(got)}")

    print("== shop filter choices (/api/products/facets) ==")
    for scope in [{}] + [{"category": c} for c in categories] + [{"q": w} for w in words[:1]]:
        rows = [p for p in products if matches(p, q=scope.get("q"), category=scope.get("category"))]
        status, facets = get("/api/products/facets", {"q": scope.get("q"), "categoryId": scope.get("category")})
        want_max = max((shown(p) for p in rows), default=None)
        got_max = Decimal(str(facets["maxPrice"])) if status == 200 and facets["maxPrice"] is not None else None
        results.expect(f"{scope or 'everything'}: genders, brands and highest price",
                       status == 200
                       and set(facets["genders"]) == {p["gender"] for p in rows if p["gender"] and p["gender"].strip()}
                       and set(facets["brands"]) == {p["brand"] for p in rows if p["brand"] and p["brand"].strip()}
                       and got_max == want_max,
                       f"got {facets if status == 200 else status}")

    status, body = get("/api/products/browse", {"size": 1000})
    results.expect("page size is capped at 60", status == 200 and body["size"] <= 60, f"size {body.get('size')}")


# ── 2. Admin: Products ───────────────────────────────────────────────────────

def admin_token() -> str:
    body = urllib.parse.urlencode({"client_id": "delivery-admin-ui", "grant_type": "password",
                                   "username": "admin", "password": "admin"}).encode()
    with urllib.request.urlopen("http://localhost:9090/realms/delivery-admin/protocol/openid-connect/token",
                                body, timeout=30) as r:
        return json.load(r)["access_token"]


def admin_products(results: Results):
    print("== admin products (/admin/products) ==")
    token = admin_token()
    products = psql_json("SELECT p.id, p.name, p.sku, p.brand, p.category_id, coalesce(p.deleted, false) AS deleted, "
                         "c.name AS category_name FROM product p LEFT JOIN category c ON c.id = p.category_id")

    def matches(p, q=None, category=None) -> bool:
        if p["deleted"]:
            return False
        if q and not any(q.lower() in (p[k] or "").lower() for k in ("name", "sku", "brand", "category_name")):
            return False
        return category is None or p["category_id"] == category

    live = [p for p in products if not p["deleted"]]
    categories = sorted({p["category_id"] for p in live if p["category_id"] is not None})
    words = sorted({w for p in live for w in (p["name"] or "").lower().split() if len(w) > 3})[:2]
    skus = sorted(p["sku"] for p in live if p["sku"])
    category_names = sorted({p["category_name"] for p in live if p["category_name"]})
    scenarios = [{}] + [{"q": w} for w in words]
    if skus:
        scenarios.append({"q": skus[0][:5]})
    if category_names:
        scenarios.append({"q": category_names[0][:4]})
    scenarios += [{"category": c} for c in categories[:3]]
    if words and categories:
        scenarios.append({"q": words[0], "category": categories[0]})
    scenarios.append({"q": "zzzz-nothing-matches"})

    for f in scenarios:
        expected = [p["id"] for p in sorted((p for p in products if matches(p, **f)), key=lambda p: -p["id"])]
        items, total = all_pages("/admin/products", {"q": f.get("q"), "categoryId": f.get("category")}, token)
        check_list(results, f"{f or 'everything'}", [x["id"] for x in items], total, expected)

    old, _ = all_pages("/api/products/paged", {})
    new, _ = all_pages("/admin/products", {}, token)
    results.expect("with no search: the same list and order as the old paged list",
                   [x["id"] for x in old] == [x["id"] for x in new])
    for w in words:
        old_ids = {x["id"] for x in get("/api/products/search", {"name": w})[1]}
        new_ids = {x["id"] for x in all_pages("/admin/products", {"q": w}, token)[0]}
        results.expect(f"everything the old search found for {w!r} is still found", old_ids <= new_ids,
                       f"missing {sorted(old_ids - new_ids)[:5]}")
    results.expect("needs a staff login", get("/admin/products")[0] == 401)


# ── 3. Admin: Orders ─────────────────────────────────────────────────────────

def admin_orders(results: Results):
    from datetime import datetime, timedelta, timezone

    print("== admin orders (/admin/orders/paged) ==")
    token = admin_token()
    orders = psql_json(
        "SELECT o.id::text AS id, o.order_status AS status, o.payment_method AS payment, "
        "to_char(o.created_at, 'YYYY-MM-DD\"T\"HH24:MI:SS.US') AS created, "
        "o.customer_name AS name, o.customer_phone AS phone, o.customer_email AS email, "
        "o.delivery_address AS address, "
        "trim(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')) AS profile_name, "
        "u.phone_number AS profile_phone, u.email AS profile_email "
        "FROM orders o LEFT JOIN users u ON u.id = o.client_id WHERE coalesce(o.deleted, false) = false")
    dhaka = timedelta(hours=6)  # Bangladesh has no daylight saving
    for o in orders:
        o["created_dt"] = datetime.fromisoformat(o["created"])
        o["day"] = (o["created_dt"] + dhaka).date().isoformat()  # the shop day it belongs to

    def matches(o, status=None, exclude=None, payment=None, frm=None, to=None, q=None) -> bool:
        if status and o["status"] != status:
            return False
        if exclude and o["status"] == exclude:
            return False
        if payment and not (o["payment"] == payment or (payment == "CASH" and o["payment"] == "COD")):
            return False
        if frm and o["day"] < frm:
            return False
        if to and o["day"] > to:
            return False
        if q:
            t = q.strip().lstrip("#").strip().lower()
            fields = ("id", "name", "phone", "email", "address", "profile_name", "profile_phone", "profile_email")
            if not any(t in (o[f] or "").lower() for f in fields):
                return False
        return True

    def expected(**f):
        rows = sorted((o for o in orders if matches(o, **f)), key=lambda o: o["id"])
        return [o["id"] for o in sorted(rows, key=lambda o: o["created_dt"], reverse=True)]

    today = (datetime.now(timezone.utc) + dhaka).date().isoformat()
    week_ago = (datetime.fromisoformat(today) - timedelta(days=6)).date().isoformat()
    sample = sorted(orders, key=lambda o: o["id"])[len(orders) // 3]
    # An order placed late in the UTC evening belongs to the next shop day in Dhaka.
    late = next((o for o in orders if o["created_dt"].hour >= 18), None)
    phone = next((o["phone"] for o in orders if o["phone"] and len(o["phone"]) >= 6), "")
    name_part = (sample["name"] or "").split()[0][:4] if sample["name"] else ""
    scenarios = [
        {"exclude": "DELIVERED"},
        {"status": "DELIVERED"},
        {"status": "CANCELLED", "exclude": "DELIVERED"},
        {"payment": "CASH", "exclude": "DELIVERED"},
        {"payment": "BKASH"},
        {"q": "#" + sample["id"][:8].upper()},
        {"q": name_part},
        {"q": phone[-5:]},
        {"q": name_part, "payment": "CARD"},
        {"frm": today, "to": today},
        {"frm": week_ago, "to": today, "exclude": "DELIVERED"},
        {"q": "zzzz-nothing-matches"},
    ]
    if late:
        scenarios.append({"frm": late["day"], "to": late["day"]})
    for f in scenarios:
        params = {"status": f.get("status"), "excludeStatus": f.get("exclude"), "payment": f.get("payment"),
                  "fromDate": f.get("frm"), "toDate": f.get("to"), "q": f.get("q")}
        items, total = all_pages("/admin/orders/paged", params, token, size=100)
        check_list(results, str(f), [x["id"] for x in items], total, expected(**f))
    if late:
        got = {x["id"] for x in all_pages("/admin/orders/paged", {"fromDate": late["day"], "toDate": late["day"]},
                                          token, size=100)[0]}
        results.expect(f"an order placed at {late['created'][11:16]} UTC is on its Dhaka day {late['day']}",
                       late["id"] in got)

    # The old page searched and filtered by payment only within the 10 orders on
    # screen. Everything it could ever have shown, page by page, must still be found.
    active, _ = all_pages("/admin/orders/paged", {"excludeStatus": "DELIVERED"}, token, size=100)
    for q in (name_part, phone[-5:]):
        old = {x["id"] for x in active if any(q.lower() in (x.get(k) or "").lower()
                                              for k in ("id", "customerName", "customerPhone", "deliveryAddress"))}
        new = {x["id"] for x in all_pages("/admin/orders/paged", {"excludeStatus": "DELIVERED", "q": q},
                                          token, size=100)[0]}
        results.expect(f"everything the old per-page search could show for {q!r} is found", old <= new,
                       f"missing {sorted(old - new)[:3]}")
    old_cash = {x["id"] for x in active if x.get("paymentMethod") == "CASH"}
    new_cash = {x["id"] for x in all_pages("/admin/orders/paged", {"excludeStatus": "DELIVERED", "payment": "CASH"},
                                           token, size=100)[0]}
    results.expect("the old Cash filter's orders are all still found (and COD joins them)", old_cash <= new_cash)
    results.expect("needs a staff login", get("/admin/orders/paged")[0] == 401)


# ── 4. Admin: Returns, Support, Newsletter ───────────────────────────────────

def admin_inboxes(results: Results):
    """Returns, support messages, subscribers and campaigns: search and paging against the database."""
    token = admin_token()

    def run(label: str, path: str, rows: list[dict], fields: tuple, order_field: str,
            statuses: list, terms: list, extra: dict | None = None):
        print(f"== {label} ({path}) ==")

        def expected(status=None, q=None):
            keep = [r for r in rows if (not status or r["status"] == status)
                    and (not q or any(q.lower() in (r[f] or "").lower() for f in fields))]
            keep.sort(key=lambda r: r["id"])
            keep.sort(key=lambda r: r[order_field] or "", reverse=True)
            return [r["id"] for r in keep]

        scenarios = [{}] + [{"status": s} for s in statuses] + [{"q": t} for t in terms if t] + [{"q": "zzzz-nothing"}]
        if statuses and terms and terms[0]:
            scenarios.append({"status": statuses[0], "q": terms[0]})
        for f in scenarios:
            items, total = all_pages(path, {**(extra or {}), "status": f.get("status"), "q": f.get("q")}, token, size=3)
            check_list(results, f"{label} {f or 'everything'}", [str(x["id"]) for x in items], total, expected(**f))
        results.expect(f"{label}: needs a staff login", get(path)[0] == 401)

    def words(rows, field):
        return sorted({w for r in rows for w in (r[field] or "").lower().split() if len(w) > 3})[:2]

    returns = psql_json("SELECT id::text AS id, status, customer_email, reason, order_id::text AS order_id, "
                        "to_char(requested_at, 'YYYY-MM-DD HH24:MI:SS.US') AS at FROM return_request "
                        "WHERE coalesce(deleted, false) = false")
    run("returns", "/admin/returns", returns, ("customer_email", "reason", "order_id", "id"), "at",
        sorted({r["status"] for r in returns})[:2],
        words(returns, "reason") + [(returns[0]["order_id"] or "")[:8] if returns else ""])

    messages = psql_json("SELECT id::text AS id, status, name, email, subject, message, "
                         "to_char(created_at, 'YYYY-MM-DD HH24:MI:SS.US') AS at FROM contact_message "
                         "WHERE coalesce(deleted, false) = false")
    run("support messages", "/admin/support/messages", messages, ("name", "email", "subject", "message"), "at",
        sorted({m["status"] for m in messages})[:2], words(messages, "subject") + words(messages, "name")[:1])

    subscribers = psql_json("SELECT id::text AS id, status, email, "
                            "to_char(subscribed_at, 'YYYY-MM-DD HH24:MI:SS.US') AS at FROM newsletter_subscriber "
                            "WHERE coalesce(deleted, false) = false")
    domains = sorted({(s["email"] or "").split("@")[-1] for s in subscribers if "@" in (s["email"] or "")})[:1]
    run("subscribers", "/admin/newsletter/subscribers", subscribers, ("email",), "at",
        sorted({s["status"] for s in subscribers})[:2], domains + [(subscribers[0]["email"] or "")[:4] if subscribers else ""])

    campaigns = psql_json("SELECT id::text AS id, status, subject, "
                          "to_char(created_at, 'YYYY-MM-DD HH24:MI:SS.US') AS at FROM newsletter_campaign "
                          "WHERE coalesce(deleted, false) = false")
    # Campaigns have no status filter on the endpoint; statuses are left out.
    run("campaigns", "/admin/newsletter/campaigns", campaigns, ("subject",), "at", [], words(campaigns, "subject"))


# ── 5. Admin: Inventory, Categories ──────────────────────────────────────────

def admin_stock_categories(results: Results):
    token = admin_token()

    print("== inventory (/admin/inventory) ==")
    products = [p for p in psql_json("SELECT id, name, sku, coalesce(deleted, false) AS deleted FROM product")
                if not p["deleted"]]

    def stock_expected(q=None):
        keep = [p for p in products if not q or any(q.lower() in (p[k] or "").lower() for k in ("name", "sku"))]
        return [p["id"] for p in sorted(keep, key=lambda p: p["id"])]

    words = sorted({w for p in products for w in (p["name"] or "").lower().split() if len(w) > 3})[:2]
    skus = sorted(p["sku"] for p in products if p["sku"])
    for f in [{}] + [{"q": w} for w in words] + ([{"q": skus[0][:5]}] if skus else []) + [{"q": "zzzz-nothing"}]:
        items, total = all_pages("/admin/inventory", {"q": f.get("q")}, token, size=7)
        check_list(results, f"inventory {f or 'everything'}", [x["productId"] for x in items], total,
                   stock_expected(**f))
    rows, _ = all_pages("/admin/inventory", {}, token, size=50)
    status, alerts = get("/admin/inventory/alerts", token=token)
    results.expect("restock alerts are exactly the low and out-of-stock products",
                   status == 200 and sorted(a["productId"] for a in alerts)
                   == sorted(r["productId"] for r in rows if r["stockStatus"] != "IN_STOCK"))
    results.expect("inventory needs a staff login", get("/admin/inventory")[0] == 401)

    print("== categories (/admin/categories) ==")
    categories = [c for c in psql_json("SELECT id, name, slug, coalesce(deleted, false) AS deleted FROM category")
                  if not c["deleted"]]

    def category_expected(q=None):
        keep = [c for c in categories if not q or any(q.lower() in (c[k] or "").lower() for k in ("name", "slug"))]
        return [c["id"] for c in sorted(keep, key=lambda c: c["id"])]

    names = sorted(c["name"] for c in categories if c["name"])
    for f in [{}] + ([{"q": names[0][:3]}] if names else []) + [{"q": "zzzz-nothing"}]:
        items, total = all_pages("/admin/categories", {"q": f.get("q")}, token, size=3)
        check_list(results, f"categories {f or 'everything'}", [x["id"] for x in items], total, category_expected(**f))
    public = {c["id"]: c["productCount"] for c in get("/api/categories")[1]}
    admin = {c["id"]: c["productCount"] for c in all_pages("/admin/categories", {}, token, size=50)[0]}
    results.expect("same categories and product counts as the public list", public == admin)
    results.expect("categories need a staff login", get("/admin/categories")[0] == 401)


# ── 6. Admin: Customers, Custom orders ───────────────────────────────────────

def admin_customers_custom(results: Results):
    token = admin_token()

    print("== customers (/admin/customers) ==")
    customers = psql_json(
        "SELECT u.id::text AS id, lower(trim(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, ''))) AS name, "
        "u.email, u.phone_number AS phone, coalesce(sum(o.total_amount), 0) AS revenue "
        "FROM users u JOIN orders o ON o.client_id = u.id AND coalesce(o.deleted, false) = false "
        "AND o.order_status NOT IN ('CANCELLED','RETURNED') WHERE coalesce(u.deleted, false) = false "
        "GROUP BY u.id, u.first_name, u.last_name, u.email, u.phone_number")

    def customer_expected(q=None):
        t = (q or "").strip().lower()
        keep = [c for c in customers if not t or t in c["name"] or t in (c["email"] or "").lower()
                or t in (c["phone"] or "")]
        keep.sort(key=lambda c: c["id"])
        keep.sort(key=lambda c: Decimal(str(c["revenue"])), reverse=True)
        return [c["id"] for c in keep]

    def customer_pages(q=None, size=7):
        """The list is fetched the way the page does it: limit/offset until a short page."""
        got, offset = [], 0
        while True:
            status, body = get("/admin/customers", {"q": q, "limit": size, "offset": offset}, token)
            if status != 200:
                raise SystemExit(f"/admin/customers q={q!r} offset {offset}: HTTP {status} {str(body)[:200]}")
            got += [c["userId"] for c in body]
            offset += size
            if len(body) < size:
                return got

    sample = sorted(customers, key=lambda c: c["id"])[len(customers) // 2] if customers else None
    terms = []
    if sample:
        terms += [sample["name"].split()[0][:4] if sample["name"] else None,
                  (sample["email"] or "").split("@")[-1][:6], (sample["phone"] or "")[-4:]]
    for q in [None] + [t for t in terms if t] + ["zzzz-nothing", "%", "_"]:
        expected = customer_expected(q)
        count = get("/admin/customers/count", {"q": q}, token)[1]
        check_list(results, f"customers {q or 'everyone'}", customer_pages(q), count, expected)
    results.expect("customers need a staff login", get("/admin/customers")[0] == 401
                   and get("/admin/customers/count")[0] == 401)

    print("== custom orders (/admin/custom-orders) ==")
    orders = psql_json(
        "SELECT id::text AS id, reference, lower(coalesce(first_name, '') || ' ' || coalesce(last_name, '')) AS name, "
        "phone, email, item_name, status, to_char(created_at, 'YYYY-MM-DD HH24:MI:SS.US') AS at, "
        "coalesce(deleted, false) AS deleted FROM custom_order")

    def order_expected(status=None, exclude=None, q=None):
        t = (q or "").strip().lower()
        keep = [o for o in orders if not o["deleted"] and (not status or o["status"] == status)
                and (not exclude or o["status"] != exclude)
                and (not t or t in (o["reference"] or "").lower() or t in o["name"] or t in (o["phone"] or "")
                     or t in (o["email"] or "").lower() or t in (o["item_name"] or "").lower())]
        keep.sort(key=lambda o: o["id"])
        keep.sort(key=lambda o: o["at"] or "", reverse=True)
        return [o["id"] for o in keep]

    live = [o for o in orders if not o["deleted"]]
    sample = sorted(live, key=lambda o: o["id"])[len(live) // 2] if live else None
    terms = []
    if sample:
        terms += [(sample["reference"] or "")[:4], sample["name"].split()[0][:4] if sample["name"].strip() else None,
                  (sample["phone"] or "")[-4:], (sample["item_name"] or "").split()[0].lower() if sample["item_name"] else None]
    scenarios = [({"history": "true"}, {"status": "DELIVERED"}), ({}, {"exclude": "DELIVERED"})]
    scenarios += [({"status": s}, {"status": s}) for s in ("NEW", "QUOTED", "CLOSED")]
    scenarios += [({"q": t}, {"exclude": "DELIVERED", "q": t}) for t in terms if t]
    if terms and terms[-1]:
        scenarios += [({"q": terms[-1], "history": "true"}, {"status": "DELIVERED", "q": terms[-1]}),
                      ({"q": terms[-1], "status": "NEW"}, {"status": "NEW", "q": terms[-1]})]
    scenarios += [({"q": "zzzz-nothing"}, {"exclude": "DELIVERED", "q": "zzzz-nothing"}),
                  ({"q": "%"}, {"exclude": "DELIVERED", "q": "%"})]
    for params, want in scenarios:
        items, total = all_pages("/admin/custom-orders", params, token, size=4)
        check_list(results, f"custom orders {params or 'active'}", [x["id"] for x in items], total,
                   order_expected(**want))
    status, body = get("/admin/custom-orders", {"size": 1000}, token)
    results.expect("custom orders page size is capped at 100", status == 200 and body["size"] <= 100,
                   f"size {body.get('size') if status == 200 else status}")
    results.expect("custom orders need a staff login", get("/admin/custom-orders")[0] == 401)


# ── 7. Shop: a customer's own orders ─────────────────────────────────────────

def customer_token(user_id: str, email: str) -> str:
    """A short-lived shop login for one customer, signed with the secret in .env (never printed)."""
    import base64, hashlib, hmac, time
    secret = next((line.split("=", 1)[1].strip().strip("\"'")
                   for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines()
                   if line.startswith("EREZER_JWT_SECRET=")), "")
    if not secret:
        raise SystemExit("EREZER_JWT_SECRET is not in .env")

    def b64(d: bytes) -> str:
        return base64.urlsafe_b64encode(d).rstrip(b"=").decode()

    now = int(time.time())
    head = b64(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
    claims = b64(json.dumps({"iss": "erezer-store", "sub": user_id, "email": email, "tt": "access",
                             "iat": now, "exp": now + 600}).encode())
    return f"{head}.{claims}.{b64(hmac.new(secret.encode(), f'{head}.{claims}'.encode(), hashlib.sha256).digest())}"


def shop_orders(results: Results):
    print("== a customer's own orders (/app/consumer/{id}/orders/paged) ==")
    people = psql_json("SELECT u.id::text AS id, u.email, count(o.id) AS n FROM users u "
                       "JOIN orders o ON o.client_id = u.id GROUP BY u.id, u.email ORDER BY n DESC, u.id")
    if len(people) < 2:
        raise SystemExit("needs at least two customers with orders")
    for who in (people[0], people[-1]):
        token = customer_token(who["id"], who["email"])
        rows = psql_json("SELECT id::text AS id, to_char(created_at, 'YYYY-MM-DD HH24:MI:SS.US') AS at, "
                         f"deleted FROM orders WHERE client_id = '{who['id']}'")
        live = [r for r in rows if r["deleted"] is False]  # the same rule as opening one order
        live.sort(key=lambda r: r["id"])
        live.sort(key=lambda r: r["at"] or "", reverse=True)
        items, total = all_pages(f"/app/consumer/{who['id']}/orders/paged", {}, token, size=7)
        check_list(results, f"customer with {who['n']} orders: newest first",
                   [x["id"] for x in items], total, [r["id"] for r in live])
        status, old = get(f"/app/consumer/{who['id']}/orders", token=token)
        results.expect("  the same orders as the old whole list, less deleted ones",
                       status == 200 and {x["id"] for x in items} == {x["id"] for x in old} - {
                           r["id"] for r in rows if r["deleted"] is not False})
    a, b = people[0], people[1]
    token = customer_token(a["id"], a["email"])
    results.expect("another customer's orders are refused",
                   get(f"/app/consumer/{b['id']}/orders/paged", token=token)[0] == 403)
    results.expect("needs a login", get(f"/app/consumer/{a['id']}/orders/paged")[0] == 401)
    status, body = get(f"/app/consumer/{a['id']}/orders/paged", {"size": 1000}, token)
    results.expect("page size is capped at 100", status == 200 and body["size"] <= 100,
                   f"size {body.get('size') if status == 200 else status}")


SECTIONS = {"shop": shop, "admin-products": admin_products, "admin-orders": admin_orders,
            "admin-inboxes": admin_inboxes, "admin-stock-categories": admin_stock_categories,
            "admin-customers-custom": admin_customers_custom, "shop-orders": shop_orders}


def main() -> int:
    wanted = sys.argv[1:] or list(SECTIONS)
    results = Results()
    for name in wanted:
        if name not in SECTIONS:
            raise SystemExit(f"Unknown list {name!r}; choose from {', '.join(SECTIONS)}")
        SECTIONS[name](results)
    print()
    if results.fails:
        print(f"{len(results.fails)} FAILED of {results.checks}:")
        for f in results.fails:
            print("  -", f)
        return 1
    print(f"OK: all {results.checks} checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
