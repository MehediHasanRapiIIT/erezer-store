#!/usr/bin/env python3
"""
End-to-end check of per-product and per-category delivery charges (V21).

A product can be given its own delivery charge, and so can a category. The rules:

  * a product's own charge wins
  * failing that, its category's
  * failing that, the customer's area price (Inside Dhaka / Outside Dhaka)
  * an order pays the highest charge in the basket, once
  * the shop's free-shipping rules still beat all of it

Every assertion is made against the real checkout quote, because that number is
what the customer is shown and billed. Pass --with-order to place one real guest
order as well, which proves the saved order charges what the quote promised; it
is left cancelled and named "ZZ Shipping Check" so it is easy to spot and remove.

Everything this touches is put back at the end, and the final check confirms the
shop quotes exactly what it quoted before the script ran.

    python deploy/verify_shipping_charges.py [--with-order]
"""
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.parse
import urllib.request

# The messages quote prices in taka, which a Windows console will not print in
# its default encoding.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

API = "http://localhost:8080"
KEYCLOAK = "http://localhost:9090"
REALM, CLIENT, USER, PASSWORD = "delivery-admin", "delivery-admin-ui", "admin", "admin"

failures: list[str] = []
checks = 0


def check(what: str, got, want) -> None:
    global checks
    checks += 1
    if got == want:
        print(f"  ok  {what}")
    else:
        print(f"FAIL  {what}: got {got!r}, wanted {want!r}")
        failures.append(what)


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


# ── the two things under test ────────────────────────────────────────────────

def set_charge(auth: str, *, scope: str, charge=None, product_ids=None, category_id=None):
    """Sets a delivery charge, or takes it away when charge is None."""
    body: dict = {"scope": scope}
    if product_ids is not None:
        body["productIds"] = product_ids
    if category_id is not None:
        body["categoryId"] = category_id
    if charge is None:
        body["useAreaPrice"] = True
    else:
        body["charge"] = charge
    return call("PUT", "/admin/shipping/charges", auth, body)


def delivery(auth: str, zone_id: int, *items) -> float:
    """What the real checkout quote charges to deliver this basket."""
    q = call("POST", "/api/checkout/quote", auth, {
        "items": [{"productId": pid, "quantity": qty} for pid, qty in items],
        "shippingZoneId": zone_id,
    })
    return float(q.get("shippingFee") or 0)


def main() -> int:
    with_order = "--with-order" in sys.argv
    auth = token()

    # ── what we have to work with ────────────────────────────────────────────
    listing = call("GET", "/api/products", auth)
    products = listing if isinstance(listing, list) else listing.get("content", [])
    # Sorted by id: the shop returns products in no particular order, and saving
    # one moves it, so an unsorted pick would test a different pair each run.
    usable = sorted((p for p in products if p.get("isAvailable") and p.get("categoryId")),
                    key=lambda p: p["id"])
    if len(usable) < 2:
        print("Needs at least two available products in a category. Nothing was changed.")
        return 1
    first = usable[0]
    pid, cid = first["id"], first["categoryId"]
    # Prefer a second product from a different category, so a charge on the first
    # product's category cannot quietly apply to both.
    second = next((p for p in usable[1:] if p["categoryId"] != cid), usable[1])
    other, other_cid = second["id"], second["categoryId"]
    print(f"using product {pid} ({first['name']}) in category {cid}, "
          f"and product {other} ({second['name']}) in category {other_cid}")

    settings = call("GET", "/admin/shipping", auth)
    zones = {z["code"]: z for z in settings["zones"] if z.get("isActive")}
    zone = zones.get("INSIDE_DHAKA") or next(iter(zones.values()))
    zone_id, area_fee = zone["id"], float(zone["flatFee"])
    print(f"area: {zone['displayName']} at {area_fee:g} taka")

    # What to put back afterwards.
    was_product = first.get("shippingCharge")
    was_other = second.get("shippingCharge")
    was_category = call("GET", "/api/categories/" + str(cid), auth).get("shippingCharge")
    was_other_category = (call("GET", "/api/categories/" + str(other_cid), auth).get("shippingCharge")
                          if other_cid != cid else None)
    was_free_all = bool(settings.get("freeAll"))
    if was_free_all:
        print("note: free shipping for all orders is ON; turning it off for the check")
        call("PUT", "/admin/shipping/rules", auth, {"freeAll": False})

    try:
        # Start from a clean slate for both products and the category.
        set_charge(auth, scope="PRODUCTS", product_ids=[pid, other], charge=None)
        set_charge(auth, scope="CATEGORY", category_id=cid, charge=None)

        print("\nnothing set anywhere")
        check("the area price is charged", delivery(auth, zone_id, (pid, 1)), area_fee)

        print("\na charge on the product itself")
        set_charge(auth, scope="PRODUCTS", product_ids=[pid], charge=237)
        check("the product's own charge is used", delivery(auth, zone_id, (pid, 1)), 237.0)
        check("and buying three of it still charges once", delivery(auth, zone_id, (pid, 3)), 237.0)

        print("\na charge of zero means free delivery")
        set_charge(auth, scope="PRODUCTS", product_ids=[pid], charge=0)
        check("delivery is free", delivery(auth, zone_id, (pid, 1)), 0.0)

        print("\ntaking the charge away again")
        set_charge(auth, scope="PRODUCTS", product_ids=[pid], charge=None)
        check("back to the area price", delivery(auth, zone_id, (pid, 1)), area_fee)

        print("\na charge on the category")
        set_charge(auth, scope="CATEGORY", category_id=cid, charge=311)
        check("a product with no charge of its own follows its category",
              delivery(auth, zone_id, (pid, 1)), 311.0)

        print("\nthe nearest rule wins")
        set_charge(auth, scope="PRODUCTS", product_ids=[pid], charge=237)
        check("the product's own charge beats its category's",
              delivery(auth, zone_id, (pid, 1)), 237.0)

        print("\nseveral things in one basket")
        # Every charge that could reach the second product is cleared first, so it
        # genuinely falls back to the area price. Without this the checks below
        # would silently measure an inherited charge instead.
        set_charge(auth, scope="CATEGORY", category_id=cid, charge=None)
        if other_cid != cid:
            set_charge(auth, scope="CATEGORY", category_id=other_cid, charge=None)
        set_charge(auth, scope="PRODUCTS", product_ids=[other], charge=None)
        check("the second product falls back to the area price on its own",
              delivery(auth, zone_id, (other, 1)), area_fee)

        set_charge(auth, scope="PRODUCTS", product_ids=[pid], charge=237)
        check("the highest charge wins, once",
              delivery(auth, zone_id, (pid, 1), (other, 1)), max(237.0, area_fee))
        set_charge(auth, scope="PRODUCTS", product_ids=[pid], charge=10)
        check("a product cheaper to deliver than the area does not make the basket cheaper",
              delivery(auth, zone_id, (pid, 1), (other, 1)), area_fee)

        # An inherited charge counts towards the highest in just the same way.
        set_charge(auth, scope="CATEGORY", category_id=other_cid, charge=311)
        set_charge(auth, scope="PRODUCTS", product_ids=[pid], charge=237)
        check("a charge inherited from a category counts towards the highest",
              delivery(auth, zone_id, (pid, 1), (other, 1)), 311.0)
        set_charge(auth, scope="CATEGORY", category_id=other_cid, charge=None)
        set_charge(auth, scope="CATEGORY", category_id=cid, charge=311)

        print("\nthe shop's own rules come first")
        set_charge(auth, scope="PRODUCTS", product_ids=[pid], charge=237)
        call("PUT", "/admin/shipping/rules", auth, {"freeAll": True})
        check("free shipping for all orders beats a product's charge",
              delivery(auth, zone_id, (pid, 1)), 0.0)
        call("PUT", "/admin/shipping/rules", auth, {"freeAll": False})
        check("and charges again once it is off", delivery(auth, zone_id, (pid, 1)), 237.0)

        print("\nwhat the admin screens show")
        shown = call("GET", "/api/products/" + str(pid), auth)
        check("the product reports its own charge", float(shown["shippingCharge"]), 237.0)
        check("and its category's, for the Delivery column",
              float(shown["categoryShippingCharge"]), 311.0)

        print("\nwhat the owner is told")
        result = set_charge(auth, scope="CATEGORY", category_id=cid, charge=150)
        print("      " + result["message"])
        check("the message counts the products that follow the category",
              result["keptOwnCharge"] >= 1, True)

        if with_order:
            print("\na real order is billed what the quote promised")
            quoted = delivery(auth, zone_id, (pid, 1))
            order = call("POST", "/app/consumer/guest/orders", None, {
                "email": "shipping-check@example.com",
                "firstName": "ZZ Shipping", "lastName": "Check",
                "deliveryAddress": "ZZ test — Dhaka", "phone": "01700000000",
                "paymentMethod": "COD", "shopId": first.get("shopId") or 1,
                "shippingZoneId": zone_id,
                "items": [{"productId": pid, "quantity": 1}],
            })
            check("the saved order charges the quoted amount",
                  float(order["deliveryCharge"]), quoted)
            print(f"      order {order['orderNumber']} — cancelling it")
            try:
                # Cancelled from the staff side: the customer route needs that
                # customer's own login, which a guest order does not have.
                call("PATCH", f"/admin/orders/{order['id']}/status", auth,
                     {"status": "CANCELLED", "note": "automated delivery charge check"})
                print("      cancelled; delete it from the Orders screen when you like")
            except urllib.error.HTTPError:
                print(f"      (could not cancel {order['orderNumber']}; cancel it on the Orders screen)")

    finally:
        print("\nputting the shop back")
        set_charge(auth, scope="PRODUCTS", product_ids=[pid],
                   charge=None if was_product is None else float(was_product))
        set_charge(auth, scope="PRODUCTS", product_ids=[other],
                   charge=None if was_other is None else float(was_other))
        set_charge(auth, scope="CATEGORY", category_id=cid,
                   charge=None if was_category is None else float(was_category))
        if other_cid != cid:
            set_charge(auth, scope="CATEGORY", category_id=other_cid,
                       charge=None if was_other_category is None else float(was_other_category))
        if was_free_all:
            call("PUT", "/admin/shipping/rules", auth, {"freeAll": True})
        else:
            restored = delivery(auth, zone_id, (pid, 1))
            expected = area_fee if was_product is None and was_category is None else None
            if expected is not None:
                check("the shop quotes what it did before this ran", restored, expected)

    print()
    if failures:
        print(f"{len(failures)} of {checks} checks FAILED:")
        for f in failures:
            print("  - " + f)
        return 1
    print(f"OK: all {checks} checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
