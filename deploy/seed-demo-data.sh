#!/usr/bin/env bash
# ============================================================================
# Seeds the local stack with enough data to actually click through the apps.
#
#   ./deploy/seed-demo-data.sh
#
# A fresh database is completely empty, so the storefront renders a bare page
# and there is nothing to order. This creates a shop, a few categories, and
# products with stock and images.
#
# Safe to re-run: it skips anything that already exists.
#
# Requires the full stack to be up and healthy (docker compose up -d).
# ============================================================================
set -euo pipefail

API="${API:-http://localhost:8080}"
KC="${KC:-http://localhost:9090}"
REALM="${REALM:-delivery-admin}"
CLIENT="${CLIENT:-delivery-admin-ui}"
KC_USER="${KC_USER:-admin}"
KC_PASS="${KC_PASS:-admin}"

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }
ok()  { printf '  \033[32mok\033[0m   %s\n' "$*"; }
skip(){ printf '  \033[33mskip\033[0m %s\n' "$*"; }
die() { printf '\n\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }

# --- preflight -------------------------------------------------------------
curl -sf "${API}/actuator/health" >/dev/null \
  || die "backend not reachable at ${API} - run: docker compose up -d"

say "Authenticating with Keycloak"
TOKEN=$(curl -sf -X POST "${KC}/realms/${REALM}/protocol/openid-connect/token" \
  -d "client_id=${CLIENT}" -d "username=${KC_USER}" -d "password=${KC_PASS}" \
  -d "grant_type=password" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || die "could not get an admin token from ${KC}"
ok "got admin token"

AUTH=(-H "Authorization: Bearer ${TOKEN}")

# --- 1. shop ---------------------------------------------------------------
# Products carry a NOT NULL shop_id, but there is no admin screen or API for
# shops - the storefront is single-tenant in practice. So the row is inserted
# directly; without it every product create fails.
say "Ensuring a shop row exists"
docker compose exec -T postgres psql -U "${POSTGRES_USER:-postgres}" \
  -d "${POSTGRES_DB:-delivery_app_v1}" -q -c \
  "insert into shop (id, name, location, deleted, created_at, updated_at, version)
   values (1, 'Erezer Flagship', 'Dhaka', false, now(), now(), 0)
   on conflict (id) do nothing;" >/dev/null
ok "shop #1 ready"

# --- 2. categories ---------------------------------------------------------
say "Creating categories"
declare -A CAT_ID
for name in "T-Shirts" "Hoodies" "Accessories"; do
  # `|| true` matters: under `set -o pipefail` a grep that matches nothing
  # returns 1, which would abort the script on the first genuinely new category.
  existing=$(curl -sf "${API}/api/categories" \
    | tr '}' '\n' | grep -F "\"name\":\"${name}\"" \
    | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1 || true)
  if [ -n "$existing" ]; then
    CAT_ID["$name"]=$existing
    skip "${name} (id=${existing})"
  else
    id=$(curl -sf -X POST "${API}/api/categories" "${AUTH[@]}" \
      -H "Content-Type: application/json" \
      -d "{\"name\":\"${name}\",\"isActive\":true}" \
      | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)
    [ -n "$id" ] || die "failed to create category ${name}"
    CAT_ID["$name"]=$id
    ok "${name} (id=${id})"
  fi
done

# --- 3. product images -----------------------------------------------------
# Tiny solid-colour PNGs, written to a temp dir. curl is invoked from inside
# that directory: on Git Bash an absolute POSIX path in -F "@..." gets mangled
# into a Windows path and curl fails with exit 26.
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
# 8x8 PNGs (black, grey, white)
printf '%s' 'iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAFUlEQVR42mNkYPhfz0BFwDiqYVgAAOEZBQBIT5EwAAAAAElFTkSuQmCC' | base64 -d > "$TMP/black.png"
printf '%s' 'iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAHUlEQVR42mP8//8/AzGAiYFIMKpwWCkc9fRQVwgAoscHAeBhkOMAAAAASUVORK5CYII=' | base64 -d > "$TMP/grey.png"

# --- 4. products -----------------------------------------------------------
say "Creating products"
existing_names=$(curl -sf "${API}/api/products" | tr '}' '\n' \
  | sed -n 's/.*"name":"\([^"]*\)".*/\1/p' || true)

create_product() {
  local name="$1" desc="$2" price="$3" cat="$4" img="$5" stock="$6" featured="$7"
  if grep -qxF "$name" <<<"$existing_names"; then
    skip "$name"
    return
  fi
  cat > "$TMP/p.json" <<JSON
{"categoryId":${CAT_ID[$cat]},"name":"${name}","description":"${desc}","price":${price},
 "shopId":1,"isAvailable":true,"unit":"items","brand":"Erezer","gender":"UNISEX",
 "isFeatured":${featured},"isNewArrival":true,"lowStockThreshold":5}
JSON
  local resp id
  resp=$(cd "$TMP" && curl -sf -X POST "${API}/api/products" "${AUTH[@]}" \
    -F "productRequestDTO=@p.json;type=application/json" \
    -F "image=@${img}") || die "failed to create ${name}"
  id=$(sed -n 's/.*"id":\([0-9]*\).*/\1/p' <<<"$resp" | head -1)

  # A new product is created OUT_OF_STOCK, so it cannot be added to a cart
  # until stock is set - without this the checkout flow is untestable.
  curl -sf -X PUT "${API}/admin/products/${id}/stock" "${AUTH[@]}" \
    -H "Content-Type: application/json" \
    -d "{\"operation\":\"SET\",\"quantity\":${stock},\"unit\":\"items\",\"lowStockThreshold\":5}" \
    >/dev/null || die "failed to set stock for ${name}"
  ok "${name} (id=${id}, stock=${stock})"
}

create_product "Classic Black Tee"  "Soft pre-shrunk cotton crew neck." 1200 "T-Shirts"    black.png 50 true
create_product "Essential White Tee" "Everyday cotton tee, relaxed fit."  1100 "T-Shirts"    grey.png  40 true
create_product "Erezer Pullover Hoodie" "Brushed fleece with kangaroo pocket." 2800 "Hoodies" black.png 25 true
create_product "Canvas Tote Bag"    "Heavy canvas, reinforced handles."   650 "Accessories" grey.png  60 false

# --- summary ---------------------------------------------------------------
say "Done"
curl -sf "${API}/api/categories" | tr '}' '\n' \
  | sed -n 's/.*"name":"\([^"]*\)".*"productCount":\([0-9]*\).*/  \1: \2 products/p'
printf '\n  Storefront : http://localhost:4200\n'
printf '  Admin      : http://localhost:4300  (admin / admin)\n'
printf '  Mailpit    : http://localhost:8025\n\n'
