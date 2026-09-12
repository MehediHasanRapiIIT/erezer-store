#!/usr/bin/env python3
"""
Bring a running Keycloak in line with delivery-backend/keycloak/delivery-admin-realm.json.

The realm file is only imported when Keycloak starts on an empty database, so
changes made to it later never reach an existing install. This script applies
them through Keycloak's admin API, and is safe to run any number of times:

  * realm role `moderator`
  * lockout after 5 wrong passwords, and SSL required outside local networks
  * client `erezer-backend-admin`, whose service account may manage users,
    and its secret written into .env as KEYCLOAK_ADMIN_CLIENT_SECRET

Usage, from the repo root with the stack running:
    python deploy/keycloak_setup.py

Reads the master admin login from .env (KEYCLOAK_ADMIN / KEYCLOAK_ADMIN_PASSWORD).

In PRODUCTION, point it at Keycloak directly rather than at the public
hostname. deploy/Caddyfile answers /admin/* with 403 on purpose - the admin
console and the admin REST API are not meant to face the internet - so going
through auth.<domain> fails with a bare 403 before Keycloak ever sees the
request. Override the address for one run:

    KEYCLOAK_URL=http://$(docker inspect -f \
      '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' \
      erezer-keycloak):9090 python3 deploy/keycloak_setup.py
"""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENV_FILE = ROOT / ".env"
CLIENT_ID = "erezer-backend-admin"
SERVICE_ROLES = ["manage-users", "view-users", "query-users", "view-realm"]


def read_env() -> dict[str, str]:
    values = {}
    for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            k, v = line.split("=", 1)
            values[k.strip()] = v.strip()
    return values


def write_env(key: str, value: str) -> None:
    lines = ENV_FILE.read_text(encoding="utf-8").splitlines()
    for i, line in enumerate(lines):
        if line.startswith(key + "="):
            lines[i] = f"{key}={value}"
            break
    else:
        lines.append(f"{key}={value}")
    ENV_FILE.write_text("\n".join(lines) + "\n", encoding="utf-8")


ENV = read_env()
# KEYCLOAK_URL in the real environment wins over PUBLIC_KEYCLOAK_URL in .env, so
# a production run can reach Keycloak on the internal network without editing
# (and having to remember to un-edit) the .env file.
BASE = (os.environ.get("KEYCLOAK_URL")
        or ENV.get("PUBLIC_KEYCLOAK_URL", "http://localhost:9090")).rstrip("/")
REALM = ENV.get("KEYCLOAK_REALM", "delivery-admin")


def master_token() -> str:
    body = urllib.parse.urlencode({
        "client_id": "admin-cli", "grant_type": "password",
        "username": ENV.get("KEYCLOAK_ADMIN", "admin"),
        "password": ENV.get("KEYCLOAK_ADMIN_PASSWORD", "admin"),
    }).encode()
    with urllib.request.urlopen(f"{BASE}/realms/master/protocol/openid-connect/token", body) as r:
        return json.load(r)["access_token"]


TOKEN = master_token()


def api(method: str, path: str, payload=None, ok_missing=False):
    req = urllib.request.Request(f"{BASE}/admin/realms/{REALM}{path}", method=method,
                                 data=json.dumps(payload).encode() if payload is not None else None)
    req.add_header("Authorization", f"Bearer {TOKEN}")
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        if ok_missing and e.code == 404:
            return None
        sys.exit(f"Keycloak {method} {path} failed: HTTP {e.code} {e.read()[:300].decode(errors='replace')}")


def main() -> int:
    # 1. The moderator role.
    if api("GET", "/roles/moderator", ok_missing=True) is None:
        api("POST", "/roles", {"name": "moderator",
                               "description": "Erezer staff with only the permissions an admin gives them"})
        print("created realm role 'moderator'")
    else:
        print("realm role 'moderator' already present")

    # 2. Lockout and SSL. Only these fields are sent, so nothing else changes.
    api("PUT", "", {"bruteForceProtected": True, "permanentLockout": False, "failureFactor": 5,
                    "waitIncrementSeconds": 60, "maxFailureWaitSeconds": 900,
                    "minimumQuickLoginWaitSeconds": 60, "sslRequired": "external"})
    print("lockout after 5 wrong passwords: on; SSL required outside local networks")

    # 3. The admin UI client has to accept the production admin URL.
    #
    # The realm JSON whitelists only http://localhost:4300 and :4200, because
    # that is what a developer runs. It is imported on Keycloak's FIRST boot and
    # never again, so a production install inherits those localhost-only values
    # and every real login dies with:
    #
    #   LOGIN_ERROR ... error="invalid_redirect_uri"
    #       redirect_uri="https://admin.<domain>/login"
    #
    # Existing entries are kept rather than replaced, so one Keycloak can serve
    # a production host and a developer's localhost at the same time.
    ui_client = ENV.get("KEYCLOAK_CLIENT_ID", "delivery-admin-ui")
    hosts = [h for h in (ENV.get("ADMIN_HOST", ""), ENV.get("STOREFRONT_HOST", "")) if h]
    if hosts:
        found_ui = api("GET", f"/clients?clientId={ui_client}")
        if not found_ui:
            print(f"client '{ui_client}' not found - skipping redirect URI update")
        else:
            ui = found_ui[0]
            redirects = sorted(set(ui.get("redirectUris") or []) | {f"https://{h}/*" for h in hosts})
            origins = sorted(set(ui.get("webOrigins") or []) | {f"https://{h}" for h in hosts})
            if redirects != sorted(ui.get("redirectUris") or []) or origins != sorted(ui.get("webOrigins") or []):
                api("PUT", f"/clients/{ui['id']}", {**ui, "redirectUris": redirects, "webOrigins": origins})
                print(f"client '{ui_client}' now accepts:", ", ".join(f"https://{h}" for h in hosts))
            else:
                print(f"client '{ui_client}' already accepts:", ", ".join(f"https://{h}" for h in hosts))

    # 4. The staff-accounts client and its service account.
    found = api("GET", f"/clients?clientId={CLIENT_ID}")
    if not found:
        api("POST", "/clients", {
            "clientId": CLIENT_ID, "name": "Erezer backend (staff accounts)",
            "enabled": True, "publicClient": False, "clientAuthenticatorType": "client-secret",
            "serviceAccountsEnabled": True, "standardFlowEnabled": False,
            "directAccessGrantsEnabled": False, "implicitFlowEnabled": False,
        })
        found = api("GET", f"/clients?clientId={CLIENT_ID}")
        print(f"created client '{CLIENT_ID}'")
    else:
        print(f"client '{CLIENT_ID}' already present")
    client_uuid = found[0]["id"]

    sa_user = api("GET", f"/clients/{client_uuid}/service-account-user")
    rm = api("GET", "/clients?clientId=realm-management")[0]["id"]
    have = {r["name"] for r in api("GET", f"/users/{sa_user['id']}/role-mappings/clients/{rm}") or []}
    wanted = [api("GET", f"/clients/{rm}/roles/{name}") for name in SERVICE_ROLES if name not in have]
    if wanted:
        api("POST", f"/users/{sa_user['id']}/role-mappings/clients/{rm}", wanted)
        print("granted service account:", ", ".join(r["name"] for r in wanted))
    else:
        print("service account already has:", ", ".join(SERVICE_ROLES))

    # 5. The secret, for the backend.
    secret = api("GET", f"/clients/{client_uuid}/client-secret")["value"]
    write_env("KEYCLOAK_ADMIN_CLIENT_ID", CLIENT_ID)
    write_env("KEYCLOAK_ADMIN_CLIENT_SECRET", secret)
    print("wrote KEYCLOAK_ADMIN_CLIENT_ID and KEYCLOAK_ADMIN_CLIENT_SECRET to .env")
    return 0


if __name__ == "__main__":
    sys.exit(main())
