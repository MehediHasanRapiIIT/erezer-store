#!/usr/bin/env python3
"""
Independent cross-check of the admin reporting numbers.

Pulls every order and order line straight out of PostgreSQL, recomputes each
report figure in plain Python (Asia/Dhaka calendar, Sunday-start weeks,
July–June fiscal year, cancelled/returned excluded from revenue), then calls
the real API and asserts the two agree to the paisa.

The Python side deliberately shares nothing with the SQL: no date_trunc, no
AT TIME ZONE, no FILTER clauses. If the two disagree, one of them is wrong.

Usage (stack running, from the repo root):
    python deploy/verify_reports.py                  # today, all period types
    python deploy/verify_reports.py --date 2026-03-15
    python deploy/verify_reports.py --dates 2026-09-04 2026-06-30 2026-03-20 2025-12-31

Exit status 0 means every figure matched.
"""
from __future__ import annotations

import argparse
import csv
import io
import json
import subprocess
import sys
import urllib.parse
import urllib.request
from collections import defaultdict
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal, ROUND_HALF_UP

API = "http://localhost:8080"
KEYCLOAK = "http://localhost:9090"
REALM = "delivery-admin"
CLIENT = "delivery-admin-ui"
USER = "admin"
PASSWORD = "admin"

# Dhaka is UTC+6 all year (Bangladesh has no daylight saving), so a fixed
# offset is exact and needs no tz database on the machine running this.
ZONE = timezone(timedelta(hours=6), "Asia/Dhaka")
WEEK_START = 6            # Python weekday(): Monday=0 … Sunday=6
FISCAL_START_MONTH = 7
NOT_COUNTED = {"CANCELLED", "RETURNED"}

PSQL = ["docker", "compose", "exec", "-T", "postgres", "psql",
        "-U", "postgres", "-d", "delivery_app_v1", "--csv", "-c"]


# ── data access ────────────────────────────────────────────────────────────────

def psql(sql: str) -> list[dict]:
    out = subprocess.run(PSQL + [sql], capture_output=True, text=True, check=True).stdout
    return list(csv.DictReader(io.StringIO(out)))


def token() -> str:
    body = urllib.parse.urlencode({
        "client_id": CLIENT, "username": USER, "password": PASSWORD, "grant_type": "password",
    }).encode()
    with urllib.request.urlopen(f"{KEYCLOAK}/realms/{REALM}/protocol/openid-connect/token", body) as r:
        return json.load(r)["access_token"]


def api(path: str, auth: str, **params) -> dict | list:
    url = f"{API}{path}"
    if params:
        url += "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {auth}"})
    with urllib.request.urlopen(req) as r:
        return json.load(r)


# ── raw rows ───────────────────────────────────────────────────────────────────

def utc_to_local(ts: str | None) -> datetime | None:
    if not ts:
        return None
    naive = datetime.fromisoformat(ts)
    return naive.replace(tzinfo=timezone.utc).astimezone(ZONE)


def load_orders() -> list[dict]:
    rows = psql("""
        SELECT o.id, o.created_at, o.delivered_at, o.cancelled_at, o.order_status, o.payment_method,
               o.total_amount, o.subtotal_amount, o.discount_amount, o.shipping_fee, o.delivery_charge,
               o.tax_amount, o.client_id, o.customer_email, o.customer_phone
        FROM orders o WHERE COALESCE(o.deleted, false) = false""")
    items = psql("""
        SELECT oi.order_id, oi.product_id, oi.quantity, oi.price_at_order, oi.custom_surcharge,
               p.name AS product_name, p.category_id, c.name AS category_name
        FROM order_item oi
        LEFT JOIN product p ON p.id = oi.product_id
        LEFT JOIN category c ON c.id = p.category_id
        WHERE COALESCE(oi.deleted, false) = false""")
    by_order: dict[str, list[dict]] = defaultdict(list)
    for it in items:
        by_order[it["order_id"]].append(it)

    orders = []
    for r in rows:
        lines = by_order.get(r["id"], [])
        units = sum(int(l["quantity"] or 0) for l in lines)
        surcharge = sum(dec(l["custom_surcharge"]) for l in lines)
        total = dec(r["total_amount"])
        shipping = dec(r["shipping_fee"]) if r["shipping_fee"] not in ("", None) else dec(r["delivery_charge"])
        tax = dec(r["tax_amount"])
        discount = dec(r["discount_amount"])
        gross = dec(r["subtotal_amount"]) if r["subtotal_amount"] not in ("", None) \
            else total - shipping - surcharge - tax + discount
        status = r["order_status"] or "UNKNOWN"
        if status == "PENDING":
            status = "PLACED"
        method = (r["payment_method"] or "UNKNOWN").upper()
        if method in ("COD", "CASH_ON_DELIVERY"):
            method = "CASH"
        key = r["client_id"] or (r["customer_email"] or "").lower() or r["customer_phone"] or None
        orders.append({
            "id": r["id"],
            "created": utc_to_local(r["created_at"]),
            "delivered": utc_to_local(r["delivered_at"]),
            "cancelled": utc_to_local(r["cancelled_at"]),
            "status": status,
            "raw_status": r["order_status"],
            "method": method,
            "total": total, "gross": gross, "discount": discount, "shipping": shipping,
            "surcharge": surcharge, "tax": tax,
            "units": units, "customer": key, "lines": lines,
            "counted": r["order_status"] not in NOT_COUNTED,
        })
    return orders


def dec(v) -> Decimal:
    if v in (None, ""):
        return Decimal("0")
    return Decimal(str(v))


def money(v: Decimal) -> Decimal:
    return v.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


# ── calendar (independent implementation) ─────────────────────────────────────

def period_bounds(ptype: str, anchor: date) -> tuple[date, date]:
    """[start, end_exclusive) in local dates."""
    if ptype == "DAY":
        return anchor, anchor + timedelta(days=1)
    if ptype == "WEEK":
        back = (anchor.weekday() - WEEK_START) % 7
        start = anchor - timedelta(days=back)
        return start, start + timedelta(days=7)
    if ptype == "MONTH":
        start = anchor.replace(day=1)
        nxt = (start.replace(day=28) + timedelta(days=4)).replace(day=1)
        return start, nxt
    if ptype == "YEAR":
        return date(anchor.year, 1, 1), date(anchor.year + 1, 1, 1)
    if ptype == "FISCAL_YEAR":
        start = date(anchor.year, FISCAL_START_MONTH, 1)
        if anchor < start:
            start = date(anchor.year - 1, FISCAL_START_MONTH, 1)
        return start, date(start.year + 1, FISCAL_START_MONTH, 1)
    raise ValueError(ptype)


def previous_bounds(ptype: str, start: date) -> tuple[date, date]:
    return period_bounds(ptype, start - timedelta(days=1))


# ── metrics (independent implementation) ──────────────────────────────────────

def in_window(ts: datetime | None, start: date, end: date) -> bool:
    return ts is not None and start <= ts.date() < end


def metrics(orders: list[dict], start: date, end: date) -> dict:
    placed = [o for o in orders if in_window(o["created"], start, end)]
    counted = [o for o in placed if o["counted"]]
    delivered = [o for o in placed if o["status"] == "DELIVERED"]
    cancelled = [o for o in placed if o["status"] == "CANCELLED"]
    returned = [o for o in placed if o["status"] == "RETURNED"]
    pending = [o for o in placed if o["status"] == "PLACED"]
    in_progress = [o for o in counted if o["status"] not in ("PLACED", "DELIVERED")]
    net = sum((o["total"] for o in counted), Decimal("0"))

    # New customers: first counted order ever falls inside the window.
    first: dict[str, datetime] = {}
    for o in orders:
        if o["counted"] and o["customer"] and o["created"]:
            if o["customer"] not in first or o["created"] < first[o["customer"]]:
                first[o["customer"]] = o["created"]
    new_customers = sum(1 for ts in first.values() if in_window(ts, start, end))

    delivered_in = [o for o in orders if o["status"] == "DELIVERED" and in_window(o["delivered"], start, end)]
    cancelled_in = [o for o in orders if o["status"] == "CANCELLED" and in_window(o["cancelled"], start, end)]

    def pct(part, whole):
        return 0.0 if whole == 0 else round(part * 1000 / whole) / 10

    return {
        "placedOrders": len(placed),
        "orders": len(counted),
        "deliveredOrders": len(delivered),
        "cancelledOrders": len(cancelled),
        "returnedOrders": len(returned),
        "pendingOrders": len(pending),
        "inProgressOrders": len(in_progress),
        "unitsSold": sum(o["units"] for o in counted),
        "grossSales": money(sum((o["gross"] for o in counted), Decimal("0"))),
        "discounts": money(sum((o["discount"] for o in counted), Decimal("0"))),
        "shipping": money(sum((o["shipping"] for o in counted), Decimal("0"))),
        "surcharges": money(sum((o["surcharge"] for o in counted), Decimal("0"))),
        "vat": money(sum((o["tax"] for o in counted), Decimal("0"))),
        "netRevenue": money(net),
        "deliveredRevenue": money(sum((o["total"] for o in delivered), Decimal("0"))),
        "averageOrderValue": money(net / len(counted)) if counted else Decimal("0.00"),
        "cancelledValue": money(sum((o["total"] for o in cancelled), Decimal("0"))),
        "returnedValue": money(sum((o["total"] for o in returned), Decimal("0"))),
        "uniqueCustomers": len({o["customer"] for o in counted if o["customer"]}),
        "newCustomers": new_customers,
        "cancellationRate": pct(len(cancelled), len(placed)),
        "returnRate": pct(len(returned), len(placed)),
        "deliveryRate": pct(len(delivered), len(placed)),
        "deliveredInPeriodOrders": len(delivered_in),
        "deliveredInPeriodRevenue": money(sum((o["total"] for o in delivered_in), Decimal("0"))),
        "cancelledInPeriodOrders": len(cancelled_in),
        "cancelledInPeriodValue": money(sum((o["total"] for o in cancelled_in), Decimal("0"))),
    }


def bucket_key(ts: datetime, unit: str) -> datetime:
    if unit == "hour":
        return ts.replace(minute=0, second=0, microsecond=0, tzinfo=None)
    if unit == "day":
        return datetime.combine(ts.date(), datetime.min.time())
    if unit == "month":
        return datetime(ts.year, ts.month, 1)
    raise ValueError(unit)


def breakdown(orders: list[dict], start: date, end: date, unit: str) -> dict[str, dict]:
    out: dict[str, dict] = {}
    for o in orders:
        if not in_window(o["created"], start, end):
            continue
        k = bucket_key(o["created"], unit).isoformat()
        b = out.setdefault(k, {"placedOrders": 0, "orders": 0, "cancelledOrders": 0, "netRevenue": Decimal("0")})
        b["placedOrders"] += 1
        if o["counted"]:
            b["orders"] += 1
            b["netRevenue"] += o["total"]
        if o["status"] == "CANCELLED":
            b["cancelledOrders"] += 1
    for b in out.values():
        b["netRevenue"] = money(b["netRevenue"])
    return out


def by_status(orders, start, end) -> dict[str, tuple[int, Decimal]]:
    out: dict[str, list] = defaultdict(lambda: [0, Decimal("0")])
    for o in orders:
        if in_window(o["created"], start, end):
            out[o["status"]][0] += 1
            out[o["status"]][1] += o["total"]
    return {k: (v[0], money(v[1])) for k, v in out.items()}


def by_payment(orders, start, end) -> dict[str, dict]:
    out: dict[str, dict] = {}
    for o in orders:
        if not in_window(o["created"], start, end):
            continue
        p = out.setdefault(o["method"], {"orders": 0, "revenue": Decimal("0"), "deliveredOrders": 0,
                                         "deliveredRevenue": Decimal("0"), "undeliveredOrders": 0,
                                         "undeliveredValue": Decimal("0"), "cancelledOrders": 0})
        if o["counted"]:
            p["orders"] += 1
            p["revenue"] += o["total"]
            if o["status"] != "DELIVERED":
                p["undeliveredOrders"] += 1
                p["undeliveredValue"] += o["total"]
        if o["status"] == "DELIVERED":
            p["deliveredOrders"] += 1
            p["deliveredRevenue"] += o["total"]
        if o["status"] == "CANCELLED":
            p["cancelledOrders"] += 1
    for p in out.values():
        for k in ("revenue", "deliveredRevenue", "undeliveredValue"):
            p[k] = money(p[k])
    return out


def top_products(orders, start, end) -> dict[int, dict]:
    out: dict[int, dict] = {}
    for o in orders:
        if not (in_window(o["created"], start, end) and o["counted"]):
            continue
        for l in o["lines"]:
            if not l["product_id"]:
                continue
            pid = int(l["product_id"])
            p = out.setdefault(pid, {"units": 0, "value": Decimal("0"), "orders": set()})
            q = int(l["quantity"] or 0)
            p["units"] += q
            p["value"] += q * dec(l["price_at_order"]) + dec(l["custom_surcharge"])
            p["orders"].add(o["id"])
    return out


def top_categories(orders, start, end) -> dict[int, dict]:
    out: dict[int, dict] = {}
    for o in orders:
        if not (in_window(o["created"], start, end) and o["counted"]):
            continue
        for l in o["lines"]:
            if not l["category_id"]:
                continue
            cid = int(l["category_id"])
            c = out.setdefault(cid, {"units": 0, "value": Decimal("0"), "orders": set()})
            q = int(l["quantity"] or 0)
            c["units"] += q
            c["value"] += q * dec(l["price_at_order"]) + dec(l["custom_surcharge"])
            c["orders"].add(o["id"])
    return out


# ── comparison ─────────────────────────────────────────────────────────────────

class Checker:
    def __init__(self):
        self.failures: list[str] = []
        self.checks = 0

    def eq(self, label: str, expected, actual):
        self.checks += 1
        if isinstance(expected, Decimal):
            ok = money(expected) == money(dec(actual))
        elif isinstance(expected, float):
            ok = abs(expected - float(actual)) < 1e-9
        else:
            ok = expected == actual
        if not ok:
            self.failures.append(f"{label}: expected {expected!r}, API returned {actual!r}")


def verify_period(chk: Checker, orders, auth, ptype: str, anchor: date):
    rep = api("/admin/reports/period", auth, type=ptype, date=anchor.isoformat())
    start, end = period_bounds(ptype, anchor)
    pstart, pend = previous_bounds(ptype, start)
    tag = f"{ptype}@{anchor}"

    chk.eq(f"{tag} start", start.isoformat(), rep["start"])
    chk.eq(f"{tag} end", (end - timedelta(days=1)).isoformat(), rep["end"])
    chk.eq(f"{tag} previousStart", pstart.isoformat(), rep["previousStart"])
    chk.eq(f"{tag} previousEnd", (pend - timedelta(days=1)).isoformat(), rep["previousEnd"])
    chk.eq(f"{tag} zone", "Asia/Dhaka", rep["zone"])
    chk.eq(f"{tag} weekStart", "SUNDAY", rep["weekStart"])

    for name, (s, e) in (("current", (start, end)), ("previous", (pstart, pend))):
        exp = metrics(orders, s, e)
        got = rep[name]
        for k, v in exp.items():
            chk.eq(f"{tag} {name}.{k}", v, got[k])

    # Sales breakdown must reconcile: net = gross − discounts + shipping + surcharges + VAT.
    cur = rep["current"]
    recon = dec(cur["grossSales"]) - dec(cur["discounts"]) + dec(cur["shipping"]) + dec(cur["surcharges"]) + dec(cur["vat"])
    chk.eq(f"{tag} reconciliation gross-discount+shipping+surcharge+vat == net", money(recon), cur["netRevenue"])

    # Breakdown buckets: every expected bucket present, zero-filled, values equal.
    unit = {"DAY": "hour", "WEEK": "day", "MONTH": "day", "YEAR": "month", "FISCAL_YEAR": "month"}[ptype]
    chk.eq(f"{tag} bucketUnit", unit, rep["bucketUnit"])
    exp_b = breakdown(orders, start, end, unit)
    got_b = {b["bucketStart"][:19]: b for b in rep["breakdown"]}
    expected_count = {"hour": 24, "day": (end - start).days, "month": 12}[unit]
    chk.eq(f"{tag} breakdown bucket count", expected_count, len(rep["breakdown"]))
    for k, eb in exp_b.items():
        gb = got_b.get(k)
        if gb is None:
            chk.failures.append(f"{tag} breakdown bucket {k} missing from API")
            continue
        for f in ("placedOrders", "orders", "cancelledOrders", "netRevenue"):
            chk.eq(f"{tag} breakdown[{k}].{f}", eb[f], gb[f])
    for k, gb in got_b.items():
        if k not in exp_b:
            chk.eq(f"{tag} breakdown[{k}] should be empty", 0, gb["placedOrders"])
    chk.eq(f"{tag} breakdown sums to placed", sum(b["placedOrders"] for b in rep["breakdown"]), cur["placedOrders"])
    chk.eq(f"{tag} breakdown sums to net", money(sum((dec(b["netRevenue"]) for b in rep["breakdown"]), Decimal("0"))), cur["netRevenue"])

    # Status distribution.
    exp_s = by_status(orders, start, end)
    got_s = {s["status"]: s for s in rep["byStatus"]}
    chk.eq(f"{tag} byStatus keys", sorted(exp_s), sorted(got_s))
    for st, (n, v) in exp_s.items():
        if st in got_s:
            chk.eq(f"{tag} byStatus[{st}].count", n, got_s[st]["count"])
            chk.eq(f"{tag} byStatus[{st}].value", v, got_s[st]["value"])

    # Payment split.
    exp_p = by_payment(orders, start, end)
    got_p = {p["method"]: p for p in rep["byPayment"]}
    chk.eq(f"{tag} byPayment keys", sorted(exp_p), sorted(got_p))
    for m, ep in exp_p.items():
        if m in got_p:
            for f, v in ep.items():
                chk.eq(f"{tag} byPayment[{m}].{f}", v, got_p[m][f])

    # Top products / categories (values for whichever the API listed; ranking is by units then value).
    exp_tp = top_products(orders, start, end)
    for row in rep["topProducts"]:
        e = exp_tp.get(row["productId"])
        if e is None:
            chk.failures.append(f"{tag} topProducts lists product {row['productId']} with no counted sales")
            continue
        chk.eq(f"{tag} topProducts[{row['productId']}].unitsSold", e["units"], row["unitsSold"])
        chk.eq(f"{tag} topProducts[{row['productId']}].revenue", money(e["value"]), row["revenue"])
        chk.eq(f"{tag} topProducts[{row['productId']}].orderCount", len(e["orders"]), row["orderCount"])
    if exp_tp:
        best_units = max(p["units"] for p in exp_tp.values())
        chk.eq(f"{tag} topProducts[0] has the most units", best_units, rep["topProducts"][0]["unitsSold"] if rep["topProducts"] else None)
    exp_tc = top_categories(orders, start, end)
    for row in rep["topCategories"]:
        e = exp_tc.get(row["categoryId"])
        if e is None:
            chk.failures.append(f"{tag} topCategories lists category {row['categoryId']} with no counted sales")
            continue
        chk.eq(f"{tag} topCategories[{row['categoryId']}].unitsSold", e["units"], row["unitsSold"])
        chk.eq(f"{tag} topCategories[{row['categoryId']}].revenue", money(e["value"]), row["revenue"])
        chk.eq(f"{tag} topCategories[{row['categoryId']}].orderCount", len(e["orders"]), row["orderCount"])


def verify_dashboard(chk: Checker, orders, auth, today: date):
    stats = api("/admin/dashboard/stats", auth)
    epoch = date(1970, 1, 1)
    all_m = metrics(orders, epoch, today + timedelta(days=1))
    t = metrics(orders, today, today + timedelta(days=1))
    y = metrics(orders, today - timedelta(days=1), today)
    ws, we = period_bounds("WEEK", today)
    w = metrics(orders, ws, we)
    lws, lwe = previous_bounds("WEEK", ws)
    lw = metrics(orders, lws, lwe)
    ms, me = period_bounds("MONTH", today)
    m = metrics(orders, ms, me)
    lms, lme = previous_bounds("MONTH", ms)
    lm = metrics(orders, lms, lme)

    chk.eq("stats.totalOrders", all_m["placedOrders"], stats["totalOrders"])
    chk.eq("stats.validOrders", all_m["orders"], stats["validOrders"])
    chk.eq("stats.totalRevenue", all_m["netRevenue"], stats["totalRevenue"])
    chk.eq("stats.pendingOrders", all_m["pendingOrders"], stats["pendingOrders"])
    chk.eq("stats.cancelledOrders", all_m["cancelledOrders"], stats["cancelledOrders"])
    chk.eq("stats.todayOrders", t["orders"], stats["todayOrders"])
    chk.eq("stats.todayRevenue", t["netRevenue"], stats["todayRevenue"])
    chk.eq("stats.yesterdayOrders", y["orders"], stats["yesterdayOrders"])
    chk.eq("stats.yesterdayRevenue", y["netRevenue"], stats["yesterdayRevenue"])
    chk.eq("stats.weekOrders", w["orders"], stats["weekOrders"])
    chk.eq("stats.weekRevenue", w["netRevenue"], stats["weekRevenue"])
    chk.eq("stats.lastWeekOrders", lw["orders"], stats["lastWeekOrders"])
    chk.eq("stats.lastWeekRevenue", lw["netRevenue"], stats["lastWeekRevenue"])
    chk.eq("stats.monthOrders", m["orders"], stats["monthOrders"])
    chk.eq("stats.monthRevenue", m["netRevenue"], stats["monthRevenue"])
    chk.eq("stats.lastMonthOrders", lm["orders"], stats["lastMonthOrders"])
    chk.eq("stats.lastMonthRevenue", lm["netRevenue"], stats["lastMonthRevenue"])

    # Analytics, all-time and this month.
    for label, params, (s, e) in (
        ("analytics[all]", {}, (epoch, today + timedelta(days=1))),
        ("analytics[month]", {"fromDate": ms.isoformat(), "toDate": today.isoformat()}, (ms, today + timedelta(days=1))),
    ):
        a = api("/admin/dashboard/analytics", auth, **params)
        em = metrics(orders, s, e)
        chk.eq(f"{label}.totalRevenue", em["netRevenue"], a["totalRevenue"])
        chk.eq(f"{label}.totalOrders", em["placedOrders"], a["totalOrders"])
        chk.eq(f"{label}.completedOrders", em["deliveredOrders"], a["completedOrders"])
        chk.eq(f"{label}.cancelledOrders", em["cancelledOrders"], a["cancelledOrders"])
        chk.eq(f"{label}.pendingOrders", em["pendingOrders"], a["pendingOrders"])
        chk.eq(f"{label}.completionRate", em["deliveryRate"], a["completionRate"])
        chk.eq(f"{label}.cancellationRate", em["cancellationRate"], a["cancellationRate"])
        chk.eq(f"{label}.avgOrderValue", em["averageOrderValue"], a["avgOrderValue"])
        # 7-day trend ending today.
        trend_start = max(today - timedelta(days=6), s)
        exp_daily = breakdown(orders, trend_start, today + timedelta(days=1), "day")
        chk.eq(f"{label}.dailyOrders length", (today - trend_start).days + 1, len(a["dailyOrders"]))
        for dpt in a["dailyOrders"]:
            k = datetime.combine(date.fromisoformat(dpt["date"]), datetime.min.time()).isoformat()
            eb = exp_daily.get(k, {"orders": 0, "netRevenue": Decimal("0")})
            chk.eq(f"{label}.dailyOrders[{dpt['date']}].count", eb["orders"], dpt["count"])
            chk.eq(f"{label}.dailyOrders[{dpt['date']}].revenue", eb["netRevenue"], dpt["revenue"])
        exp_s = by_status(orders, s, e)
        got_s = {x["status"]: x["count"] for x in a["ordersByStatus"]}
        chk.eq(f"{label}.ordersByStatus", {k: v[0] for k, v in exp_s.items()}, got_s)
        exp_p = by_payment(orders, s, e)
        for pm in a["ordersByPayment"]:
            ep = exp_p.get(pm["method"])
            if ep is None:
                chk.failures.append(f"{label}.ordersByPayment has unknown method {pm['method']}")
                continue
            chk.eq(f"{label}.ordersByPayment[{pm['method']}].count", ep["orders"], pm["count"])
            chk.eq(f"{label}.ordersByPayment[{pm['method']}].revenue", ep["revenue"], pm["revenue"])
        exp_tc = top_categories(orders, s, e)
        for c in a["topCategories"]:
            match = [v for k, v in exp_tc.items()]
            # categoryName only in this DTO; match by value.
            chk.eq(f"{label}.topCategories[{c['categoryName']}] value known",
                   True, any(money(v["value"]) == money(dec(c["revenue"])) and len(v["orders"]) == c["orderCount"] for v in match))

    # Legacy revenue series: weekly buckets must start on Sundays and exclude cancelled orders.
    series = api("/admin/reports/revenue", auth, **{"from": ms.isoformat(), "to": today.isoformat(), "granularity": "WEEK"})
    for pt in series:
        d = date.fromisoformat(pt["date"])
        chk.eq(f"revenue[WEEK] bucket {d} is a Sunday", 6, d.weekday())
        ws_, we_ = period_bounds("WEEK", d)
        em = metrics(orders, max(ws_, ms), min(we_, today + timedelta(days=1)))
        chk.eq(f"revenue[WEEK][{d}].orderCount", em["orders"], pt["orderCount"])
        chk.eq(f"revenue[WEEK][{d}].revenue", em["netRevenue"], pt["revenue"])


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--date", help="anchor date yyyy-MM-dd (default: today in Dhaka)")
    ap.add_argument("--dates", nargs="*", help="several anchor dates")
    args = ap.parse_args()

    today = datetime.now(ZONE).date()
    anchors = [date.fromisoformat(x) for x in (args.dates or [])]
    if args.date:
        anchors.append(date.fromisoformat(args.date))
    if not anchors:
        anchors = [today]

    print("loading orders from PostgreSQL ...", flush=True)
    orders = load_orders()
    print(f"  {len(orders)} orders, {sum(len(o['lines']) for o in orders)} lines")
    auth = token()

    chk = Checker()
    for anchor in anchors:
        for ptype in ("DAY", "WEEK", "MONTH", "YEAR", "FISCAL_YEAR"):
            verify_period(chk, orders, auth, ptype, anchor)
            print(f"  checked {ptype:<11} @ {anchor}  ({chk.checks} comparisons so far)", flush=True)
    verify_dashboard(chk, orders, auth, today)
    print(f"  checked dashboard + analytics ({chk.checks} comparisons)")

    if chk.failures:
        print(f"\n{len(chk.failures)} MISMATCH(ES):")
        for f in chk.failures[:80]:
            print("  -", f)
        return 1
    print(f"\nOK: all {chk.checks} figures match the independent recomputation.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
