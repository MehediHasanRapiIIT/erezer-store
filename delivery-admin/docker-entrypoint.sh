#!/bin/sh
# ============================================================================
# Writes /env.js from container environment variables before nginx starts.
#
# Installed into /docker-entrypoint.d/, which the official nginx image runs on
# boot - so the image keeps its stock ENTRYPOINT and CMD.
#
# This is what makes one built image usable in every environment: the Angular
# bundle reads window.__ENV__ (see src/app/core/runtime-config.ts) instead of
# a URL compiled into it.
# ============================================================================
set -eu

# Defaults match a plain local stack, so `docker run` with no -e still works.
: "${API_BASE_URL:=http://localhost:8080}"
: "${KEYCLOAK_URL:=http://localhost:9090}"
: "${KEYCLOAK_REALM:=delivery-admin}"
: "${KEYCLOAK_CLIENT_ID:=delivery-admin-ui}"
: "${SENTRY_DSN:=}"
: "${SENTRY_ENV:=production}"
: "${SENTRY_RELEASE:=delivery-admin@latest}"

cat > /usr/share/nginx/html/env.js <<EOF
// Generated at container start by docker-entrypoint.sh - do not edit.
window.__ENV__ = {
  API_BASE_URL: "${API_BASE_URL}",
  KEYCLOAK_URL: "${KEYCLOAK_URL}",
  KEYCLOAK_REALM: "${KEYCLOAK_REALM}",
  KEYCLOAK_CLIENT_ID: "${KEYCLOAK_CLIENT_ID}",
  SENTRY_DSN: "${SENTRY_DSN}",
  SENTRY_ENV: "${SENTRY_ENV}",
  SENTRY_RELEASE: "${SENTRY_RELEASE}"
};
EOF

echo "[erezer] env.js written -> API_BASE_URL=${API_BASE_URL} KEYCLOAK_URL=${KEYCLOAK_URL}"
