#!/usr/bin/env python3
"""
Live check of staff permissions, endpoint by endpoint, plus customer isolation.

1. Reads every staff endpoint and its rule from the running backend
   (GET /admin/access/endpoints), so new endpoints are covered by themselves.
2. Adds two throwaway moderators through the Staff API:
     verify-reader  holds every "see" permission (keys ending in .view, plus
                    "See money totals" and "See subscribers");
     verify-editor  holds every other permission.
3. Calls every endpoint as each of them. Where the rule refuses them, the
   answer must be 403. Where it lets them in, it must not be 401 or 403.
   Also: nothing answers without a login, and the admin reaches every read.
4. Repeats the customer-isolation checks: one customer can't read or change
   another's data, and a customer login is refused by the admin side.
5. Deletes both moderators through the Staff API.

It never changes real data. Refusals are tested on every endpoint, and a
refused request never reaches the code that would act. On the "allowed" side
it sends only reads, or actions aimed at a record id that doesn't exist.
Creating things (a POST or PUT without an id in the path) is only ever tested
from the refused side.

Usage (stack running, from the repo root; deploy/keycloak_setup.py done once):
    python deploy/verify_access_control.py
    python deploy/verify_access_control.py --admin-user admin --admin-password admin

Reads .env for the Keycloak master login (KEYCLOAK_ADMIN / KEYCLOAK_ADMIN_PASSWORD),
used only to stand in for each test moderator choosing their own password at
first login, and EREZER_JWT_SECRET for the customer checks.

Exit status 0 means every check matched.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
import re
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REALM = "delivery-admin"
CLIENT = "delivery-admin-ui"
ZERO = "00000000-0000-0000-0000-000000000000"
READ_EXTRA = {"finance.revenue", "newsletter.subscribers"}
MODERATORS = ("verify-reader", "verify-editor")


def read_env() -> dict[str, str]:
    values: dict[str, str] = {}
    path = ROOT / ".env"
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.split("=", 1)
                values[k.strip()] = v.strip()
    return values


ENV = read_env()


# ── HTTP ─────────────────────────────────────────────────────────────────────

def http(method: str, url: str, token: str | None = None, *, json_body=None, form=None,
         raw: bytes | None = None, content_type: str | None = None):
    req = urllib.request.Request(url, method=method)
    data = None
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        req.add_header("Content-Type", "application/x-www-form-urlencoded")
    elif raw is not None:
        data = raw
        req.add_header("Content-Type", content_type or "application/octet-stream")
    elif json_body is not None:
        data = json.dumps(json_body).encode()
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, data, timeout=30) as r:
            text = r.read().decode("utf-8", "replace")
            return r.status, (json.loads(text) if text.strip().startswith(("{", "[")) else text)
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(text)
        except ValueError:
            return e.code, text


class Stack:
    def __init__(self, api: str, keycloak: str):
        self.api = api.rstrip("/")
        self.kc = keycloak.rstrip("/")

    def login(self, username: str, password: str) -> str:
        s, body = http("POST", f"{self.kc}/realms/{REALM}/protocol/openid-connect/token",
                       form={"client_id": CLIENT, "grant_type": "password",
                             "username": username, "password": password})
        if s != 200:
            raise SystemExit(f"Login as {username} failed: HTTP {s} {str(body)[:200]}")
        return body["access_token"]

    def kc_admin(self, method: str, path: str, payload=None):
        s, body = http("POST", f"{self.kc}/realms/master/protocol/openid-connect/token",
                       form={"client_id": "admin-cli", "grant_type": "password",
                             "username": ENV.get("KEYCLOAK_ADMIN") or "admin",
                             "password": ENV.get("KEYCLOAK_ADMIN_PASSWORD") or "admin"})
        if s != 200:
            raise SystemExit("The Keycloak master login in .env was refused.")
        return http(method, f"{self.kc}/admin/realms/{REALM}{path}", body["access_token"], json_body=payload)


# ── results ──────────────────────────────────────────────────────────────────

class Results:
    def __init__(self):
        self.checks = 0
        self.fails: list[str] = []
        self.warnings: list[str] = []

    def expect(self, label: str, ok: bool, detail: str = "", quiet: bool = False):
        self.checks += 1
        if not ok:
            self.fails.append(f"{label} {detail}".strip())
        if not quiet or not ok:
            print(f"  {'ok ' if ok else 'BAD'} {label} {detail}".rstrip())


def allowed(rule: dict, held: set[str]) -> bool:
    kind, perms = rule["rule"], set(rule["permissions"])
    if kind == "ADMIN_ONLY":
        return False
    if kind == "ANY_STAFF":
        return True
    if kind == "ANY_OF":
        return bool(perms & held)
    return perms <= held


def call(stack: Stack, rule: dict, token: str | None):
    path = re.sub(r"\{[^}]+\}", ZERO, rule["path"])
    method = rule["method"]
    if method == "GET":
        return http(method, stack.api + path, token)
    if any("multipart" in c for c in rule.get("consumes", [])):
        boundary = "verify" + secrets.token_hex(8)
        body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"probe\"\r\n\r\n1\r\n"
                f"--{boundary}--\r\n").encode()
        return http(method, stack.api + path, token, raw=body,
                    content_type=f"multipart/form-data; boundary={boundary}")
    return http(method, stack.api + path, token, json_body={})


def safe_when_allowed(rule: dict) -> bool:
    """Reads, and actions on an id in the path (which won't exist). Never creates."""
    return rule["method"] == "GET" or "{" in rule["path"]


# ── staff sweep ──────────────────────────────────────────────────────────────

def remove_moderators(stack: Stack, admin_token: str):
    s, staff = http("GET", stack.api + "/admin/staff", admin_token)
    if s == 200:
        for m in staff:
            if m["username"] in MODERATORS:
                http("DELETE", f"{stack.api}/admin/staff/{m['id']}?confirm={m['username']}", admin_token)
    for username in MODERATORS:  # a login left behind by an interrupted run
        s, found = stack.kc_admin("GET", f"/users?username={username}&exact=true")
        for u in (found or []) if s == 200 else []:
            stack.kc_admin("DELETE", f"/users/{u['id']}")


def add_moderator(stack: Stack, admin_token: str, username: str, permissions: list[str]) -> str:
    password = secrets.token_urlsafe(15)
    s, body = http("POST", stack.api + "/admin/staff", admin_token, json_body={
        "fullName": "Verify " + username.split("-")[1].title(), "username": username,
        "email": f"{username}@erezer.local", "temporaryPassword": password, "permissions": permissions})
    if s != 201:
        raise SystemExit(f"Could not add {username}: HTTP {s} {str(body)[:300]}")
    # Stand in for the person choosing their own password at first login.
    _, found = stack.kc_admin("GET", f"/users?username={username}&exact=true")
    stack.kc_admin("PUT", f"/users/{found[0]['id']}", {"requiredActions": []})
    return stack.login(username, password)


def staff_sweep(stack: Stack, results: Results, admin_token: str):
    s, rules = http("GET", stack.api + "/admin/access/endpoints", admin_token)
    if s != 200:
        raise SystemExit(f"Could not read the endpoint rules: HTTP {s} {str(rules)[:200]}")
    s, catalogue = http("GET", stack.api + "/admin/permissions", admin_token)
    keys = [p["key"] for p in catalogue]
    reader_keys = sorted(k for k in keys if k.endswith(".view") or k in READ_EXTRA)
    editor_keys = sorted(k for k in keys if k not in reader_keys)
    print(f"{len(rules)} staff endpoints, {len(keys)} permissions "
          f"({len(reader_keys)} to verify-reader, {len(editor_keys)} to verify-editor)")

    remove_moderators(stack, admin_token)
    try:
        people = {
            "verify-reader": (add_moderator(stack, admin_token, "verify-reader", reader_keys), set(reader_keys)),
            "verify-editor": (add_moderator(stack, admin_token, "verify-editor", editor_keys), set(editor_keys)),
        }
        skipped = 0
        for name, (token, held) in people.items():
            print(f"== as {name} ==")
            refused = let_in = 0
            for rule in rules:
                label = f"{rule['method']:6} {rule['path']}"
                if allowed(rule, held):
                    if not safe_when_allowed(rule):
                        skipped += 1
                        continue
                    status, body = call(stack, rule, token)
                    results.expect(f"{name} allowed  {label}", status not in (401, 403),
                                   f"-> {status} {str(body)[:120] if status in (401, 403) else ''}", quiet=True)
                    if status >= 500:
                        results.warnings.append(f"{name} {label} -> {status} (dummy id)")
                    let_in += 1
                else:
                    status, body = call(stack, rule, token)
                    results.expect(f"{name} refused  {label}", status == 403, f"-> {status}", quiet=True)
                    refused += 1
            print(f"  {refused} endpoints checked for refusal, {let_in} checked for access")
        print(f"  ({skipped} create/replace calls only tested from the refused side)")

        print("== without a login ==")
        for rule in rules:
            status, _ = call(stack, rule, None)
            results.expect(f"no login {rule['method']} {rule['path']}", status == 401, f"-> {status}", quiet=True)
        print(f"  {len(rules)} endpoints checked")

        print("== as the admin ==")
        reads = [r for r in rules if r["method"] == "GET"]
        for rule in reads:
            status, _ = call(stack, rule, admin_token)
            results.expect(f"admin GET {rule['path']}", status not in (401, 403), f"-> {status}", quiet=True)
        print(f"  {len(reads)} reads checked")
    finally:
        remove_moderators(stack, admin_token)
        s, staff = http("GET", stack.api + "/admin/staff", admin_token)
        left = [m["username"] for m in staff if m["username"] in MODERATORS] if s == 200 else ["(unknown)"]
        results.expect("test moderators removed", not left, str(left) if left else "")


# ── customer isolation ───────────────────────────────────────────────────────

def psql(sql: str) -> str:
    r = subprocess.run(["docker", "compose", "exec", "-T", "postgres", "psql", "-U", "postgres",
                        "-d", "delivery_app_v1", "-At", "-F", "|", "-c", sql],
                       capture_output=True, text=True, cwd=ROOT)
    if r.returncode != 0:
        raise RuntimeError(r.stderr.strip())
    return r.stdout.strip()


def customer_token(user_id: str, email: str) -> str:
    secret = ENV.get("EREZER_JWT_SECRET", "")

    def b64(d: bytes) -> str:
        return base64.urlsafe_b64encode(d).rstrip(b"=").decode()

    now = int(time.time())
    head = b64(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
    claims = b64(json.dumps({"iss": "erezer-store", "sub": user_id, "email": email, "tt": "access",
                             "iat": now, "exp": now + 600}).encode())
    sig = b64(hmac.new(secret.encode(), f"{head}.{claims}".encode(), hashlib.sha256).digest())
    return f"{head}.{claims}.{sig}"


def customer_sweep(stack: Stack, results: Results):
    print("== customers can't reach each other's data ==")
    if not ENV.get("EREZER_JWT_SECRET"):
        results.warnings.append("customer checks skipped: no EREZER_JWT_SECRET in .env")
        return
    rows = psql("SELECT u.id, u.email FROM users u WHERE EXISTS "
                "(SELECT 1 FROM orders o WHERE o.client_id = u.id) ORDER BY u.email LIMIT 2").splitlines()
    if len(rows) < 2:
        results.warnings.append("customer checks skipped: fewer than two customers with orders")
        return
    (a_id, a_email), (b_id, b_email) = (r.split("|") for r in rows)
    a = customer_token(a_id, a_email)
    base = f"{stack.api}/app/consumer"
    results.expect("customer reads their own orders", http("GET", f"{base}/{a_id}/orders", a)[0] == 200)
    results.expect("customer reads their own order history pages",
                   http("GET", f"{base}/{a_id}/orders/paged", a)[0] == 200)
    results.expect("no login, someone's orders", http("GET", f"{base}/{b_id}/orders")[0] == 401)
    for label, path in (("orders", "/orders"), ("order history pages", "/orders/paged"), ("profile", "/profile"), ("addresses", "/addresses"),
                        ("cart", "/cart"), ("returns", "/returns"), ("design drafts", "/custom-design/drafts")):
        status = http("GET", f"{base}/{b_id}{path}", a)[0]
        results.expect(f"customer reads another's {label}", status == 403, f"-> {status}")
    status = http("POST", f"{base}/{b_id}/cart", a, json_body={})[0]
    results.expect("customer changes another's cart", status == 403, f"-> {status}")
    status = http("GET", f"{stack.api}/admin/me", a)[0]
    results.expect("customer login on the admin side", status == 401, f"-> {status}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--api", default="http://localhost:8080")
    parser.add_argument("--keycloak", default=ENV.get("PUBLIC_KEYCLOAK_URL") or "http://localhost:9090")
    parser.add_argument("--admin-user", default="admin")
    parser.add_argument("--admin-password", default="admin")
    args = parser.parse_args()

    stack = Stack(args.api, args.keycloak)
    results = Results()
    admin_token = stack.login(args.admin_user, args.admin_password)
    staff_sweep(stack, results, admin_token)
    customer_sweep(stack, results)

    print()
    for w in results.warnings:
        print("  note:", w)
    if results.fails:
        print(f"{len(results.fails)} FAILED of {results.checks}:")
        for f in results.fails:
            print("  -", f)
        return 1
    print(f"OK: all {results.checks} checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
