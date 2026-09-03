#!/usr/bin/env python3
"""
End-to-end check of the discount on/off switches against the running stack.

Creates three throwaway discount rules (one per scope), then flips each switch
and asserts both the public discount list and the real checkout quote respond.
The quote is the number that becomes the customer's bill, so it is the one that
matters. Everything created here is removed at the end.
"""
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

API = "http://localhost:8080"
KEYCLOAK = "http://localhost:9090"
REALM, CLIENT, USER, PASSWORD = "delivery-admin", "delivery-admin-ui", "admin", "admin"

CRLF = "\r\n"

failures: list[str] = []
checks = 0


def token() -> str:
    body = urllib.parse.urlencode({
        "client_id": CLIENT, "username": USER, "password": PASSWORD, "grant_type": "password",
    }).encode()
    with urllib.request.urlopen(f"{KEYCLOAK}/realms/{REALM}/protocol/openid-connect/token", body) as r:
        return json.load(r)["access_token"]


def call(method: str, path: str, auth: str | None = None, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(f"{API}{path}", data=data, method=method)
    if auth:
        req.add_header("Authorization", f"Bearer {auth}")
    if data:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        print(f"HTTP {e.code} on {method} {path}: {e.read()[:300].decode(errors='replace')}")
        raise


def put_form(path: str, auth: str, fields: dict):
    """PUT multipart/form-data. The product update binds flat form fields."""
    boundary = uuid.uuid4().hex
    chunks = []
    for key, value in fields.items():
        if value is None:
            continue
        chunks.append("--" + boundary + CRLF)
        chunks.append('Content-Disposition: form-data; name="' + key + '"' + CRLF + CRLF)
        chunks.append(str(value) + CRLF)
    chunks.append("--" + boundary + "--" + CRLF)
    body = "".join(chunks).encode()

    req = urllib.request.Request(f"{API}{path}", data=body, method="PUT")
    req.add_header("Content-Type", "multipart/form-data; boundary=" + boundary)
    req.add_header("Authorization", f"Bearer {auth}")
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        print(f"HTTP {e.code} on PUT {path}: {e.read()[:400].decode(errors='replace')}")
        raise


def eq(label: str, expected, actual):
    global checks
    checks += 1
    if expected != actual:
        failures.append(f"{label}: expected {expected!r}, got {actual!r}")


def set_switches(auth: str, **flags):
    settings = call("GET", "/admin/store-settings", auth)
    settings.update(flags)
    call("PUT", "/admin/store-settings", auth, settings)


def quote_discount(auth: str, product_id: int) -> float:
    """The automatic discount the server would apply to a one-unit cart."""
    q = call("POST", "/api/checkout/quote", auth,
             {"items": [{"productId": product_id, "quantity": 1}]})
    return float(q.get("discountAmount") or 0)


def active_scopes(auth: str) -> list[str]:
    return sorted(d["scope"] for d in call("GET", "/api/discounts/active", auth)
                  if d["name"].startswith("ZZ-switch-test"))


def update_product(auth: str, product: dict, excluded: bool):
    """Send the product back the same way the admin edit form does."""
    return put_form("/api/products/" + str(product["id"]), auth, {
        "categoryId": product["categoryId"],
        "name": product["name"],
        "description": product["description"],
        "price": product["price"],
        "shopId": product.get("shopId") or 1,
        "isAvailable": str(bool(product.get("isAvailable"))).lower(),
        "discountExcluded": str(excluded).lower(),
    })


def fetch_product(auth: str, pid: int) -> dict:
    return call("GET", "/api/products/" + str(pid), auth)


def main() -> int:
    auth = token()

    products = call("GET", "/api/products", auth)
    products = products if isinstance(products, list) else products.get("content", [])
    product = next(p for p in products if p.get("isAvailable") and p.get("categoryId"))
    pid, cid = product["id"], product["categoryId"]
    print("using product " + str(pid) + " (" + product["name"] + ") in category " + str(cid))

    original_settings = call("GET", "/admin/store-settings", auth)
    original_category = call("GET", "/api/categories/" + str(cid), auth)
    created: list[str] = []

    all_on = {"discountsEnabled": True, "discountsGlobalEnabled": True,
              "discountsCategoryEnabled": True, "discountsProductEnabled": True}

    def rule(scope: str, target):
        d = call("POST", "/admin/discounts", auth, {
            "name": "ZZ-switch-test " + scope, "scope": scope, "discountType": "FLAT",
            "discountValue": 10, "targetId": target, "stackable": True,
            "priority": 0, "isActive": True,
        })
        created.append(d["id"])

    try:
        rule("GLOBAL", None)
        rule("CATEGORY", cid)
        rule("PRODUCT", pid)

        # ── everything on: three stackable flat rules of 10 = 30 off ────────
        set_switches(auth, **all_on)
        eq("all on: active scopes", ["CATEGORY", "GLOBAL", "PRODUCT"], active_scopes(auth))
        eq("all on: quote discount", 30.0, quote_discount(auth, pid))

        # ── master switch off ──────────────────────────────────────────────
        set_switches(auth, **{**all_on, "discountsEnabled": False})
        eq("master off: active scopes", [], active_scopes(auth))
        eq("master off: quote discount", 0.0, quote_discount(auth, pid))

        # ── each scope switched off on its own ─────────────────────────────
        for flag, suspended, remaining in (
            ("discountsGlobalEnabled", "GLOBAL", ["CATEGORY", "PRODUCT"]),
            ("discountsCategoryEnabled", "CATEGORY", ["GLOBAL", "PRODUCT"]),
            ("discountsProductEnabled", "PRODUCT", ["CATEGORY", "GLOBAL"]),
        ):
            set_switches(auth, **{**all_on, flag: False})
            eq(suspended + " scope off: active scopes", remaining, active_scopes(auth))
            eq(suspended + " scope off: quote discount", 20.0, quote_discount(auth, pid))

        # ── per-product exclusion, with every switch on ────────────────────
        set_switches(auth, **all_on)
        update_product(auth, product, True)
        eq("product excluded: quote discount", 0.0, quote_discount(auth, pid))
        after = fetch_product(auth, pid)
        eq("product excluded: flag round-trips to the form", True, after.get("discountExcluded"))
        update_product(auth, product, False)
        eq("product un-excluded: quote discount", 30.0, quote_discount(auth, pid))

        # ── per-category exclusion ─────────────────────────────────────────
        call("PUT", "/api/categories/" + str(cid), auth,
             {**original_category, "discountExcluded": True})
        eq("category excluded: quote discount", 0.0, quote_discount(auth, pid))
        after_p = fetch_product(auth, pid)
        eq("category excluded: surfaced on the product", True, after_p.get("categoryDiscountExcluded"))
        eq("category excluded: product's own flag untouched", False, bool(after_p.get("discountExcluded")))
        call("PUT", "/api/categories/" + str(cid), auth,
             {**original_category, "discountExcluded": False})
        eq("category un-excluded: quote discount", 30.0, quote_discount(auth, pid))

        # ── a sale price is not a discount rule, so it must survive ────────
        set_switches(auth, **{**all_on, "discountsEnabled": False})
        priced = fetch_product(auth, pid)
        eq("master off: sale price untouched", product.get("discountPrice"), priced.get("discountPrice"))

    finally:
        for did in created:
            try:
                call("DELETE", "/admin/discounts/" + did, auth)
            except Exception as exc:
                print("cleanup failed for discount", did, exc)
        try:
            update_product(auth, product, False)
            call("PUT", "/api/categories/" + str(cid), auth, original_category)
            call("PUT", "/admin/store-settings", auth, original_settings)
            print("cleaned up: rules deleted, product/category/settings restored")
        except Exception as exc:
            print("cleanup warning:", exc)

    if failures:
        print("\n" + str(len(failures)) + " MISMATCH(ES):")
        for f in failures:
            print("  -", f)
        return 1
    print("\nOK: all " + str(checks) + " checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
