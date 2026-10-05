#!/usr/bin/env python3
"""
Live check that a picture is stored exactly as it was uploaded.

The shop never shrinks, re-compresses or converts an uploaded picture: what is
stored, and what the shop then serves, must be the same file, byte for byte.
This uploads real pictures the shop already holds (a JPEG, a PNG and a WebP,
where it has them) through every upload route it can reach without touching
anything customers see, downloads what was stored, and compares the SHA-256.

Routes covered:
    admin    add product (one click), add several products, a product's
             picture gallery, the older add-product request, the general image
             upload (categories, bundles, settings), design-studio items
    shop     a customer's artwork for the design studio

Banners and return photos go through the very same storage function
(FileStorageService.uploadFile) and are covered by its unit test rather than
here: a banner would appear on the home page, and a return needs a real order.

Everything it creates is removed at the end.

    python deploy/verify_images_unchanged.py
"""
from __future__ import annotations

import hashlib
import json
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

API = "http://localhost:8080"
KEYCLOAK = "http://localhost:9090"
REALM, CLIENT, USER, PASSWORD = "delivery-admin", "delivery-admin-ui", "admin", "admin"
NAME = "ZZ Picture Unchanged Check"

failures: list[str] = []
checks = 0
made_urls: list[str] = []


def check(what: str, ok: bool, detail: str = "") -> None:
    global checks
    checks += 1
    print(f"  {'ok' if ok else 'FAIL'}  {what}{'' if ok else '  -> ' + detail}")
    if not ok:
        failures.append(what)


def token() -> str:
    body = urllib.parse.urlencode({
        "client_id": CLIENT, "username": USER, "password": PASSWORD, "grant_type": "password"}).encode()
    with urllib.request.urlopen(f"{KEYCLOAK}/realms/{REALM}/protocol/openid-connect/token", body) as r:
        return json.load(r)["access_token"]


def get(path: str, auth: str | None = None):
    req = urllib.request.Request(API + path)
    if auth:
        req.add_header("Authorization", f"Bearer {auth}")
    with urllib.request.urlopen(req) as r:
        return json.load(r)


def delete(path: str, auth: str) -> None:
    req = urllib.request.Request(API + path, method="DELETE")
    req.add_header("Authorization", f"Bearer {auth}")
    try:
        urllib.request.urlopen(req).close()
    except urllib.error.HTTPError:
        pass


def download(url: str) -> bytes:
    with urllib.request.urlopen(url) as r:
        return r.read()


def post_multipart(path: str, auth: str | None, parts: list[tuple[str, str, bytes, str | None]]):
    """parts: (field name, content type, data, file name or None)."""
    boundary = uuid.uuid4().hex
    body = b""
    for name, content_type, data, filename in parts:
        disposition = f'form-data; name="{name}"' + (f'; filename="{filename}"' if filename else "")
        body += (f"--{boundary}\r\nContent-Disposition: {disposition}\r\n"
                 f"Content-Type: {content_type}\r\n\r\n").encode() + data + b"\r\n"
    body += f"--{boundary}--\r\n".encode()
    req = urllib.request.Request(API + path, data=body, method="POST")
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    if auth:
        req.add_header("Authorization", f"Bearer {auth}")
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, e.read()[:200].decode(errors="replace")


def same(label: str, original: bytes, stored_url: str) -> None:
    """The stored picture is the uploaded one: same bytes, so same size and quality."""
    made_urls.append(stored_url)
    stored = download(stored_url)
    identical = hashlib.sha256(stored).digest() == hashlib.sha256(original).digest()
    check(f"{label}: stored exactly as uploaded ({len(original) / 1024:.0f} KB)", identical,
          f"uploaded {len(original)} bytes, stored {len(stored)} bytes")


def find_samples(auth: str) -> dict[str, tuple[str, bytes]]:
    """A real JPEG, PNG and WebP from what the shop already holds (read only)."""
    urls: list[str] = []
    try:
        banners = get("/api/banners", auth)
        urls += [b.get("imageUrl") for b in (banners if isinstance(banners, list) else banners.get("content", []))]
    except Exception:
        pass
    page = get("/admin/products?size=50", auth)
    for p in page.get("content", []):
        urls.append(p.get("imageUrl"))
        try:
            urls += [i["url"] for i in get(f"/api/products/{p['id']}/images", auth)]
        except Exception:
            pass

    wanted = {"jpeg": (".jpg", ".jpeg"), "png": (".png",), "webp": (".webp",)}
    content_types = {"jpeg": "image/jpeg", "png": "image/png", "webp": "image/webp"}
    found: dict[str, tuple[str, bytes]] = {}
    for url in filter(None, urls):
        for kind, endings in wanted.items():
            if kind in found or not url.lower().endswith(endings):
                continue
            try:
                data = download(url)
            except Exception:
                continue
            if len(data) > 20_000:   # a real picture, not a placeholder
                found[kind] = (content_types[kind], data)
    return found


def remove_file(url: str) -> None:
    bucket, name = url.rstrip("/").split("/")[-2:]
    subprocess.run(["docker", "exec", "erezer-minio", "sh", "-c",
                    "mc alias set here http://localhost:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD >/dev/null"
                    f" && mc rm here/{bucket}/{name} >/dev/null"], check=False)


def our_products(auth: str) -> list[dict]:
    page = get(f"/admin/products?q={urllib.parse.quote(NAME)}&size=50", auth)
    return [p for p in page.get("content", []) if p["name"] == NAME]


def main() -> int:
    auth = token()
    samples = find_samples(auth)
    if not samples:
        print("The shop holds no pictures to test with yet. Upload one product picture and run this again.")
        return 1
    print("testing with real pictures from the shop: "
          + ", ".join(f"{k.upper()} {len(d) / 1024:.0f} KB" for k, (_, d) in samples.items()))
    category_id = get("/api/categories", auth)[0]["id"]
    product = {"name": NAME, "productCode": "ZZ-SAME", "description": "Picture check.", "price": 500,
               "categoryId": category_id, "shopId": 1, "isAvailable": False}
    for p in our_products(auth):
        delete(f"/api/products/{p['id']}", auth)

    try:
        for kind, (content_type, data) in samples.items():
            ext = "jpg" if kind == "jpeg" else kind
            file = f"sample.{ext}"
            print(f"\n{kind.upper()}")

            s, body = post_multipart("/admin/uploads/image", auth, [("file", content_type, data, file)])
            if s == 200:
                same("general image upload (categories, bundles, settings)", data, body["url"])
            else:
                check("general image upload accepted", False, f"HTTP {s} {body}")

            s, body = post_multipart("/admin/custom-design/uploads/image", auth, [("file", content_type, data, file)])
            if s == 200:
                same("design-studio item", data, body["url"])
            else:
                check("design-studio item accepted", False, f"HTTP {s} {body}")

            s, body = post_multipart("/api/custom-design/upload", None, [("file", content_type, data, file)])
            if s == 200:
                same("a customer's artwork (shop)", data, body["url"])
            else:
                check("a customer's artwork accepted", False, f"HTTP {s} {body}")

            s, body = post_multipart("/admin/products/full", auth, [
                ("product", "application/json", json.dumps(product).encode(), None),
                ("pictures", content_type, data, file)])
            if s == 201:
                same("add product (one click)", data, body["imageUrl"])
                s2, img = post_multipart(f"/admin/products/{body['id']}/images", auth, [("file", content_type, data, file)])
                if s2 == 201:
                    same("a product's picture gallery (edit page)", data, img["url"])
                else:
                    check("gallery upload accepted", False, f"HTTP {s2} {img}")
            else:
                check("add product accepted", False, f"HTTP {s} {body}")

            batch = {"shared": {**product, "name": "", "productCode": ""}, "sizes": [],
                     "items": [{"name": NAME, "productCode": "ZZ-SAME-B"}]}
            s, body = post_multipart("/admin/products/batch", auth, [
                ("batch", "application/json", json.dumps(batch).encode(), None),
                ("pictures-0", content_type, data, file)])
            if s == 201:
                same("add several products", data, body[0]["imageUrl"])
            else:
                check("add several products accepted", False, f"HTTP {s} {body}")

            s, body = post_multipart("/api/products", auth, [
                ("productRequestDTO", "application/json", json.dumps(product).encode(), None),
                ("image", content_type, data, file)])
            if s == 200:
                same("the older add-product request", data, body["imageUrl"])
            else:
                check("older add-product accepted", False, f"HTTP {s} {body}")
    finally:
        for p in our_products(auth):
            delete(f"/api/products/{p['id']}", auth)
        for url in made_urls:
            remove_file(url)
        global checks
        check("everything it made is gone", not our_products(auth))

    print()
    if failures:
        print(f"{len(failures)} of {checks} checks FAILED.")
        return 1
    print(f"OK: all {checks} checks passed. Every picture was stored exactly as uploaded.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
