#!/usr/bin/env python3
"""
Fills a fresh Erezer install with realistic demo content.

A brand-new database is completely empty, so every storefront section either
hides itself or shows a placeholder — which reads as broken rather than
unconfigured. This seeds enough for the whole site to look finished:

  * categories with real photography, one of them promoted to its own
    landing-page band and page
  * products spread across those categories, priced, in stock, with a
    sensible mix flagged featured / new-arrival
  * banners for all eight landing-page slots, with working call-to-actions

Safe to re-run: everything is matched by name or slot and skipped if present.

    python deploy/seed_demo_data.py            # seed
    python deploy/seed_demo_data.py --reset    # remove seeded content first

Requires the stack to be running (docker compose up -d).
"""
from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

API = "http://localhost:8080"
KEYCLOAK = "http://localhost:9090"
REALM = "delivery-admin"
CLIENT = "delivery-admin-ui"
USER = "admin"
PASSWORD = "admin"

# Unsplash serves these directly; the storefront already uses the same source
# for its built-in fallback imagery, so nothing new is introduced by them.
UNSPLASH = "https://images.unsplash.com/photo-{id}?auto=format&fit=crop&w={w}&q=70"


def photo(pid: str, w: int = 900) -> str:
    return UNSPLASH.format(id=pid, w=w)


# --- content ----------------------------------------------------------------

CATEGORIES = [
    # (name, hero photo, promoted to its own home band?)
    ("T-Shirts",    "1521572163474-6864f9cf17ab", False),
    ("Hoodies",     "1556821840-3a63f95609a7", False),
    ("Jackets",     "1551028719-00167b16eac5", False),
    ("Denim",       "1542272604-787c3835535d", False),
    ("Accessories", "1523293182086-7651a899d37f", False),
    ("Bags",        "1553062407-98eeb64c6a62", False),
    ("Caps",        "1588850561407-ed78c282e89b", False),
    ("Erezer Pink", "1595777457583-95e059d581b8", True),
]

# (category, product, photo id, price, stock, featured, new arrival)
PRODUCTS = [
    ("T-Shirts", "Classic Black Tee",        "1521572163474-6864f9cf17ab", 1200, 60, True,  True),
    ("T-Shirts", "Essential White Tee",      "1583743814966-8936f5b7be1a", 1100, 45, True,  False),
    ("T-Shirts", "Oversized Drop Shoulder",  "1503341504253-dff4815485f1",  1450, 30, False, True),
    ("T-Shirts", "Striped Crew Neck",        "1576566588028-4147f3842f27", 1350, 25, False, False),
    ("T-Shirts", "Heavyweight Pocket Tee",   "1622470953794-aa9c70b0fb9d", 1550, 18, False, False),

    ("Hoodies",  "Erezer Pullover Hoodie",   "1556821840-3a63f95609a7",    2800, 25, True,  True),
    ("Hoodies",  "Zip-Through Hoodie",       "1620799140408-edc6dcb6d633", 3200, 15, False, False),
    ("Hoodies",  "Cropped Fleece Hoodie",    "1618354691373-d851c5c3a990", 2600, 20, False, True),
    ("Hoodies",  "Heavyweight Boxy Hoodie",  "1509942774463-acf339cf87d5", 3400, 12, True,  False),

    ("Jackets",  "Denim Trucker Jacket",     "1551028719-00167b16eac5",    4200, 14, True,  False),
    ("Jackets",  "Quilted Bomber",           "1591047139829-d91aecb6caea", 4800, 10, False, True),
    ("Jackets",  "Lightweight Windbreaker",  "1544022613-e87ca75a784a",    3900, 16, False, False),

    ("Denim",    "Straight Leg Jeans",       "1542272604-787c3835535d",    2900, 22, False, True),
    ("Denim",    "Relaxed Tapered Jeans",    "1541099649105-f69ad21f3246", 3100, 18, False, False),
    ("Denim",    "Denim Shorts",             "1591195853828-11db59a44f6b", 1900, 26, False, False),

    ("Accessories", "Leather Card Holder",   "1523293182086-7651a899d37f",  950, 40, False, False),
    ("Accessories", "Woven Belt",            "1624222247344-550fb60583dc", 1200, 30, False, True),
    ("Accessories", "Everyday Socks (3-pack)","1586350977771-b3b0abd50c82", 650, 80, False, False),

    ("Bags",     "Canvas Tote Bag",          "1553062407-98eeb64c6a62",     850, 50, True,  False),
    ("Bags",     "Daily Backpack",           "1548036328-c9fa89d128fa",    3600, 12, False, True),
    ("Bags",     "Compact Crossbody",        "1590874103328-eac38a683ce7", 2400, 18, False, False),

    ("Caps",     "Six-Panel Cap",            "1588850561407-ed78c282e89b",  900, 45, False, False),
    ("Caps",     "Corduroy Cap",             "1534215754734-18e55d13e346", 1100, 28, False, True),

    ("Erezer Pink", "Pink Oversized Tee",    "1595777457583-95e059d581b8", 1400, 30, True,  True),
    ("Erezer Pink", "Pink Cropped Hoodie",   "1618354691229-88d47f285158", 2900, 18, False, True),
    ("Erezer Pink", "Pink Knit Cardigan",    "1434389677669-e08b4cac3105", 3300, 12, False, False),
    ("Erezer Pink", "Pink Canvas Tote",      "1591561954557-26941169b49e",  950, 35, False, False),
    ("Erezer Pink", "Pink Ribbed Top",       "1515886657613-9f3515b0c78f", 1650, 24, False, False),
    ("Erezer Pink", "Pink Bucket Hat",       "1521369909029-2afed882baee",  850, 40, False, False),
]

# (slot, photo id, title, subtitle, button label, button link)
BANNERS = [
    ("HERO",         "1483985988355-763728e1935b", "READY TO WEAR",
     "Premium essentials, made to last.", "SHOP NOW", "/shop"),
    ("SPLIT_LEFT",   "1490481651871-ab68de25d43d", "NEW ARRIVALS",
     "This season's cuts", "SHOP T-SHIRTS", "/t-shirts"),
    ("SPLIT_RIGHT",  "1539109136881-3be0616acf4b", "THE PINK EDIT",
     "Erezer Pink collection", "SHOP THE EDIT", "/erezer-pink"),
    ("GRID_1",       "1553062407-98eeb64c6a62", "Bags", "", "BAGS", "/bags"),
    ("GRID_2",       "1588850561407-ed78c282e89b", "Caps", "", "CAPS", "/caps"),
    ("GRID_3",       "1523293182086-7651a899d37f", "Accessories", "", "ACCESSORIES", "/accessories"),
    ("GRID_4",       "1551028719-00167b16eac5", "Jackets", "", "JACKETS", "/jackets"),
    ("CUSTOM_PROMO", "1521572163474-6864f9cf17ab", "CUSTOMIZE YOUR APPAREL, YOUR WAY.",
     "Design your own t-shirts, hoodies and more in our online studio — no minimum order, even a single piece.",
     "TRY IT NOW", "/custom-design"),
]


# --- http helpers -----------------------------------------------------------

def fail(message: str) -> None:
    print(f"\nerror: {message}", file=sys.stderr)
    sys.exit(1)


def token() -> str:
    body = urllib.parse.urlencode({
        "client_id": CLIENT, "username": USER,
        "password": PASSWORD, "grant_type": "password",
    }).encode()
    try:
        with urllib.request.urlopen(f"{KEYCLOAK}/realms/{REALM}/protocol/openid-connect/token", body) as r:
            return json.load(r)["access_token"]
    except Exception as exc:
        fail(f"could not authenticate with Keycloak at {KEYCLOAK} ({exc})")


def request(method: str, path: str, auth: str | None = None,
            payload: dict | list | None = None, raw: bytes | None = None,
            content_type: str | None = None):
    headers = {}
    if auth:
        headers["Authorization"] = f"Bearer {auth}"
    data = raw
    if payload is not None:
        data = json.dumps(payload).encode()
        headers["Content-Type"] = "application/json"
    if content_type:
        headers["Content-Type"] = content_type
    req = urllib.request.Request(f"{API}{path}", data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req) as r:
            text = r.read().decode()
            return json.loads(text) if text.strip() else None
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f"{method} {path} -> HTTP {exc.code}: {exc.read().decode()[:200]}") from exc


def multipart(auth: str, path: str, fields: dict[str, str],
              file_field: str | None = None, filename: str | None = None,
              file_bytes: bytes | None = None, file_type: str = "image/jpeg"):
    """Builds a multipart body by hand.

    Deliberately not shelling out to curl: on Git Bash a leading-slash form value
    like "/shop" is rewritten to a Windows path, which silently corrupts every
    call-to-action link.
    """
    boundary = "----" + uuid.uuid4().hex
    parts: list[bytes] = []
    for key, value in fields.items():
        # @RequestPart deserialises a JSON part only when that part declares
        # application/json. Without it Spring sees application/octet-stream and
        # rejects the whole request with a 500.
        part_type = "\r\nContent-Type: application/json" if value.lstrip().startswith("{") else ""
        parts.append(
            f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"{part_type}\r\n\r\n{value}\r\n'.encode()
        )
    if file_bytes is not None:
        parts.append(
            f'--{boundary}\r\nContent-Disposition: form-data; name="{file_field}"; '
            f'filename="{filename}"\r\nContent-Type: {file_type}\r\n\r\n'.encode()
            + file_bytes + b"\r\n"
        )
    parts.append(f"--{boundary}--\r\n".encode())
    return request("POST", path, auth=auth, raw=b"".join(parts),
                   content_type=f"multipart/form-data; boundary={boundary}")


def download(url: str) -> bytes:
    with urllib.request.urlopen(url) as r:
        return r.read()


# --- seeding ----------------------------------------------------------------

def ensure_shop(auth: str) -> None:
    """Products carry a NOT NULL shop_id but there is no admin screen for shops."""
    import subprocess
    subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres", "psql", "-U", "postgres",
         "-d", "delivery_app_v1", "-q", "-c",
         "insert into shop (id, name, location, deleted, created_at, updated_at, version) "
         "values (1, 'Erezer Flagship', 'Dhaka', false, now(), now(), 0) "
         "on conflict (id) do nothing;"],
        capture_output=True, check=False,
    )
    print("  shop #1 ready")


def seed_categories(auth: str) -> dict[str, dict]:
    existing = {c["name"]: c for c in request("GET", "/api/categories")}
    result: dict[str, dict] = {}
    for order, (name, pid, on_home) in enumerate(CATEGORIES):
        if name in existing:
            result[name] = existing[name]
            print(f"  skip     {name}")
            continue
        created = request("POST", "/api/categories", auth=auth, payload={
            "name": name,
            "isActive": True,
            "imageUrl": photo(pid),
            "showOnHome": on_home,
            "homeSortOrder": order,
        })
        result[name] = created
        print(f"  created  {name}  /{created.get('slug')}" + ("  [home band]" if on_home else ""))
    return result


def seed_products(auth: str, categories: dict[str, dict]) -> None:
    existing = {p["name"] for p in request("GET", "/api/products")}
    created = 0
    for cat_name, name, pid, price, stock, featured, new_arrival in PRODUCTS:
        if name in existing:
            continue
        category = categories.get(cat_name)
        if not category:
            continue
        body = {
            "categoryId": category["id"],
            "name": name,
            "description": f"{name} from the Erezer {cat_name} collection. "
                           "Premium fabric, considered fit, made to last.",
            "price": price,
            "shopId": 1,
            "isAvailable": True,
            "unit": "items",
            "brand": "Erezer",
            "gender": "UNISEX",
            "isFeatured": featured,
            "isNewArrival": new_arrival,
            "lowStockThreshold": 5,
            # No file part: the mapper takes imageUrl straight from the payload,
            # so demo products can point at hosted photography.
            "imageUrl": photo(pid, 800),
        }
        saved = multipart(auth, "/api/products",
                          {"productRequestDTO": json.dumps(body)})
        # A new product starts OUT_OF_STOCK, so it cannot be added to a cart
        # until stock is set — without this the checkout flow is untestable.
        request("PUT", f"/admin/products/{saved['id']}/stock", auth=auth, payload={
            "operation": "SET", "quantity": stock, "unit": "items", "lowStockThreshold": 5,
        })
        created += 1
    print(f"  {created} products created, {len(existing)} already present")


def seed_banners(auth: str) -> None:
    existing = {b.get("slot") for b in request("GET", "/api/banners")}
    created = 0
    for slot, pid, title, details, label, link in BANNERS:
        if slot in existing:
            continue
        image = download(photo(pid, 1600))
        multipart(auth, "/api/banners", {
            "slot": slot, "promotionTitle": title, "promotionDetails": details,
            "ctaLabel": label, "ctaLink": link, "sortOrder": "0",
        }, file_field="image", filename=f"{slot.lower()}.jpg", file_bytes=image)
        created += 1
        print(f"  {slot}")
    print(f"  {created} banners uploaded, {len(existing)} already present")


def reset(auth: str) -> None:
    seeded_products = {p[1] for p in PRODUCTS}
    for product in request("GET", "/api/products"):
        if product["name"] in seeded_products:
            request("DELETE", f"/api/products/{product['id']}", auth=auth)
    for banner in request("GET", "/api/banners"):
        request("DELETE", f"/api/banners/{banner['id']}", auth=auth)
    seeded_categories = {c[0] for c in CATEGORIES}
    for category in request("GET", "/api/categories"):
        if category["name"] in seeded_categories:
            try:
                request("DELETE", f"/api/categories/{category['id']}", auth=auth)
            except RuntimeError:
                pass  # still referenced by a product someone else added
    print("  seeded content removed")


def main() -> None:
    parser = argparse.ArgumentParser(description="Seed Erezer demo content.")
    parser.add_argument("--reset", action="store_true",
                        help="remove previously seeded content instead of adding it")
    args = parser.parse_args()

    try:
        request("GET", "/actuator/health")
    except Exception:
        fail(f"backend not reachable at {API} — run: docker compose up -d")

    auth = token()
    print("\nAuthenticated as admin")

    if args.reset:
        print("\nRemoving seeded content")
        reset(auth)
        return

    print("\nShop")
    ensure_shop(auth)
    print("\nCategories")
    categories = seed_categories(auth)
    print("\nProducts")
    seed_products(auth, categories)
    print("\nLanding-page banners")
    seed_banners(auth)

    print("\nDone.")
    print("  Storefront   http://localhost:4200")
    print("  Collections  http://localhost:4200/categories")
    print("  Admin        http://localhost:4300  (admin / admin)\n")


if __name__ == "__main__":
    main()
