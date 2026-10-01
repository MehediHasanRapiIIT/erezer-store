#!/usr/bin/env python3
"""
Live check of "Add product" and "Add several products": who may send what.

The one-click save takes the product, its pictures and its sizes together, so
it must ask for exactly what the separate screens would:

  * pictures need "Manage photos"              (products.images)
  * sizes need "Manage sizes and colours"      (products.variants)
  * a size's stock needs "Change stock"        (inventory.edit)
  * a size's own price needs "Change prices"   (products.price)

verify_access_control.py proves the endpoint's own rule; this proves the rules
that depend on what is sent. Each refusal is also checked to have saved nothing.

It signs in as throwaway moderators (through the Staff page's own API) and
removes them, and everything they made, at the end.

    python deploy/verify_product_upload.py
"""
from __future__ import annotations

import json
import os
import struct
import subprocess
import sys
import uuid
import urllib.error
import urllib.request
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import verify_access_control as vac  # noqa: E402  the moderator machinery lives there

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

API = "http://localhost:8080"
MODERATOR = "verify-editor"   # a name vac.remove_moderators knows how to clear up
NAME = "ZZ Upload Permission Check"

failures: list[str] = []
checks = 0


def check(what: str, ok: bool, detail: str = "") -> None:
    global checks
    checks += 1
    print(f"  {'ok' if ok else 'FAIL'}  {what}{'' if ok else '  -> ' + detail}")
    if not ok:
        failures.append(what)


def tiny_png() -> bytes:
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + b"\xc8\x32\x50" * 8 for _ in range(8))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 8, 8, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def add_product(token: str, category_id: int, *, sizes=None, pictures=0) -> tuple[int, dict | str]:
    """POST /admin/products/full the way the Add Product page does."""
    boundary = uuid.uuid4().hex
    parts: list[bytes] = []

    def part(name: str, content_type: str, data: bytes, filename: str | None = None):
        disposition = f'form-data; name="{name}"' + (f'; filename="{filename}"' if filename else "")
        parts.append(f"--{boundary}\r\nContent-Disposition: {disposition}\r\n"
                     f"Content-Type: {content_type}\r\n\r\n".encode() + data + b"\r\n")

    product = {"name": NAME, "productCode": "ZZ-PERM", "description": "Permission check.",
               "price": 900, "categoryId": category_id, "shopId": 1, "isAvailable": True}
    part("product", "application/json", json.dumps(product).encode())
    if sizes:
        part("sizes", "application/json", json.dumps(sizes).encode())
    for i in range(pictures):
        part("pictures", "image/png", tiny_png(), f"p{i}.png")
    body = b"".join(parts) + f"--{boundary}--\r\n".encode()

    req = urllib.request.Request(f"{API}/admin/products/full", data=body, method="POST")
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, e.read()[:200].decode(errors="replace")


def add_batch(token: str, category_id: int, rows: list[dict], *, sizes=None) -> tuple[int, dict | str]:
    """POST /admin/products/batch the way "Add several products" does. A row's
    "pictures" is how many to attach to it."""
    boundary = uuid.uuid4().hex
    parts: list[bytes] = []

    def part(name: str, content_type: str, data: bytes, filename: str | None = None):
        disposition = f'form-data; name="{name}"' + (f'; filename="{filename}"' if filename else "")
        parts.append(f"--{boundary}\r\nContent-Disposition: {disposition}\r\n"
                     f"Content-Type: {content_type}\r\n\r\n".encode() + data + b"\r\n")

    batch = {
        "shared": {"name": "", "productCode": "", "description": "Permission check.", "price": 900,
                   "categoryId": category_id, "shopId": 1, "isAvailable": True},
        "sizes": sizes or [],
        "items": [{"name": NAME, "productCode": f"ZZ-PERM-{i}"} for i in range(len(rows))],
    }
    part("batch", "application/json", json.dumps(batch).encode())
    for i, row in enumerate(rows):
        for p in range(row.get("pictures", 0)):
            part(f"pictures-{i}", "image/png", tiny_png(), f"r{i}p{p}.png")
    body = b"".join(parts) + f"--{boundary}--\r\n".encode()

    req = urllib.request.Request(f"{API}/admin/products/batch", data=body, method="POST")
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, e.read()[:200].decode(errors="replace")


def ours(admin: str) -> list[dict]:
    s, page = vac.http("GET", f"{API}/admin/products?q={urllib.request.quote(NAME)}&size=50", admin)
    return [p for p in (page.get("content", []) if s == 200 else []) if p["name"] == NAME]


def remove_ours(admin: str) -> None:
    """Every product this made, and its picture files."""
    for p in ours(admin):
        s, images = vac.http("GET", f"{API}/api/products/{p['id']}/images", admin)
        for img in images if s == 200 else []:
            name = img["url"].rsplit("/", 1)[-1]
            subprocess.run(["docker", "exec", "erezer-minio", "sh", "-c",
                            "mc alias set here http://localhost:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD >/dev/null"
                            f" && mc rm here/product-images/{name} >/dev/null"], check=False)
        vac.http("DELETE", f"{API}/api/products/{p['id']}", admin)


def as_moderator(stack, admin: str, permissions: list[str]) -> str:
    vac.remove_moderators(stack, admin)
    return vac.add_moderator(stack, admin, MODERATOR, permissions)


def main() -> int:
    stack = vac.Stack(API, vac.ENV.get("PUBLIC_KEYCLOAK_URL") or "http://localhost:9090")
    admin = stack.login("admin", "admin")
    s, cats = vac.http("GET", f"{API}/api/categories", admin)
    category_id = cats[0]["id"]
    remove_ours(admin)

    no_stock = [{"size": "M", "stockQuantity": 0}]
    try:
        print("as a moderator who may add products and set prices, and nothing else")
        mod = as_moderator(stack, admin, ["products.view", "products.create", "products.price"])

        s, body = add_product(mod, category_id)
        check("a product on its own is allowed", s == 201, f"HTTP {s} {body}")
        remove_ours(admin)

        s, body = add_product(mod, category_id, pictures=1)
        check("with a picture it is refused: no “Manage photos”", s == 403, f"HTTP {s} {body}")
        check("and nothing was saved", not ours(admin))

        s, body = add_product(mod, category_id, sizes=no_stock)
        check("with a size it is refused: no “Manage sizes and colours”", s == 403, f"HTTP {s} {body}")
        check("and nothing was saved", not ours(admin))

        s, body = add_batch(mod, category_id, [{}, {}])
        check("several products with no pictures are allowed", s == 201, f"HTTP {s} {body}")
        remove_ours(admin)

        s, body = add_batch(mod, category_id, [{}, {"pictures": 1}])
        check("several, with a picture in row 2, are refused: no “Manage photos”", s == 403, f"HTTP {s} {body}")
        check("and not even row 1 was saved", not ours(admin))

        s, body = add_batch(mod, category_id, [{}], sizes=no_stock)
        check("several, with sizes, are refused: no “Manage sizes and colours”", s == 403, f"HTTP {s} {body}")
        check("and nothing was saved", not ours(admin))

        print("\nas one who may also manage photos and sizes, but not stock")
        mod = as_moderator(stack, admin, ["products.view", "products.create", "products.price",
                                          "products.images", "products.variants"])

        s, body = add_product(mod, category_id, pictures=1, sizes=no_stock)
        check("pictures and a size with no stock are allowed", s == 201, f"HTTP {s} {body}")
        remove_ours(admin)

        s, body = add_product(mod, category_id, sizes=[{"size": "M", "stockQuantity": 5}])
        check("a size with stock is refused: no “Change stock”", s == 403, f"HTTP {s} {body}")
        check("and nothing was saved", not ours(admin))

        s, body = add_batch(mod, category_id, [{"pictures": 2}, {"pictures": 1}], sizes=no_stock)
        check("several, with pictures and a size with no stock, are allowed", s == 201, f"HTTP {s} {body}")
        check("as products of their own", len(ours(admin)) == 2, f"found {len(ours(admin))}")
        remove_ours(admin)

        s, body = add_batch(mod, category_id, [{}, {}], sizes=[{"size": "M", "stockQuantity": 5}])
        check("several, with stock, are refused: no “Change stock”", s == 403, f"HTTP {s} {body}")
        check("and nothing was saved", not ours(admin))

        s, body = add_product(mod, category_id, sizes=[{"size": "M", "stockQuantity": 0, "priceOverride": 1200}])
        check("a size with its own price is allowed: they may change prices", s == 201, f"HTTP {s} {body}")
    finally:
        remove_ours(admin)
        vac.remove_moderators(stack, admin)
        check("everything it made is gone", not ours(admin))

    print()
    if failures:
        print(f"{len(failures)} of {checks} checks FAILED.")
        return 1
    print(f"OK: all {checks} checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
