# syntax=docker/dockerfile:1.7
# ============================================================================
# Keycloak + the Erezer login theme
#
# The stock Keycloak image has no idea about our theme, so the custom login
# page silently falls back to Keycloak's default. Baking the theme into an
# image makes it travel with the deployment - no host directory to remember to
# copy, and it works identically on any machine or registry.
#
# Build context is this directory (delivery-admin/keycloak-theme/).
#
# Locally, docker-compose.yml ALSO bind-mounts erezer/ over this copy so you can
# edit the theme and just refresh the browser. In production there is no mount,
# and this baked-in copy is what gets served.
#
# Applying it is two separate things, and BOTH are required:
#   1. the theme files exist here, under /opt/keycloak/themes/<name>
#   2. the realm's `loginTheme` is set to that name - see
#      delivery-backend/keycloak/delivery-admin-realm.json
# ============================================================================

FROM quay.io/keycloak/keycloak:26.0

# Theme name must match `loginTheme` in the realm JSON.
COPY erezer /opt/keycloak/themes/erezer
