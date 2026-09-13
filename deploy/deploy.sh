#!/usr/bin/env bash
# ============================================================================
# Erezer - VM-side deploy
#
# Runs ON THE VM, invoked over SSH by .github/workflows/cd.yml after it has
# copied the compose files across. Safe to run by hand too:
#
#   IMAGE_TAG=1a2b3c4 IMAGE_REPO_OWNER=youruser /opt/erezer/deploy/deploy.sh
#
# What it guarantees:
#   * the exact image tag CI built is what starts (no "latest" ambiguity)
#   * the previous tag is recorded, so a failed rollout rolls itself back
#   * the script exits non-zero if the stack is not healthy, so a red deploy
#     shows up red in GitHub instead of quietly leaving prod broken
# ============================================================================
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/erezer}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-300}"
STATE_FILE="$APP_DIR/.deployed-tag"
CADDY_STATE_FILE="$APP_DIR/.deployed-caddyfile-sha"

# Services that must report healthy before a rollout counts as successful.
# postgres/redis/minio are covered transitively: the backend depends on all
# three with `condition: service_healthy` and cannot start without them.
HEALTH_GATED=(backend store admin)

COMPOSE=(docker compose
  -f docker-compose.yml
  -f docker-compose.prod.yml
  -f docker-compose.deploy.yml)

log()  { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33mWARN: %s\033[0m\n' "$*" >&2; }
die()  { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

cd "$APP_DIR" || die "$APP_DIR does not exist - run deploy/vm-bootstrap.sh first"

[[ -f .env ]] || die ".env is missing in $APP_DIR (copy .env.prod.example and fill it in)"

: "${IMAGE_TAG:?IMAGE_TAG must be set (the git SHA CI pushed)}"
: "${IMAGE_REPO_OWNER:?IMAGE_REPO_OWNER must be set (your Docker Hub account)}"
export IMAGE_TAG IMAGE_REPO_OWNER

PREVIOUS_TAG="$(cat "$STATE_FILE" 2>/dev/null || true)"

# ── Wait for one service to report healthy ──────────────────────────────────
# Returns as soon as the container is healthy, and gives up early if it has
# already died - no point burning the full timeout on a container that exited.
wait_for_health() {
  local service="$1" deadline=$((SECONDS + HEALTH_TIMEOUT)) cid state
  while (( SECONDS < deadline )); do
    cid="$("${COMPOSE[@]}" ps -q "$service" 2>/dev/null || true)"
    if [[ -n "$cid" ]]; then
      # Containers with a HEALTHCHECK report Health.Status; ones without only
      # have State.Status, where "running" is the best signal available.
      state="$(docker inspect -f \
        '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
        "$cid" 2>/dev/null || echo unknown)"
      case "$state" in
        healthy|running) printf '    %-10s %s\n' "$service" "$state"; return 0 ;;
        exited|dead)     printf '    %-10s %s\n' "$service" "$state"; return 1 ;;
      esac
    fi
    sleep 5
  done
  printf '    %-10s TIMED OUT after %ss\n' "$service" "$HEALTH_TIMEOUT"
  return 1
}

# ── Roll back to the tag that was running before this attempt ───────────────
rollback() {
  if [[ -z "$PREVIOUS_TAG" || "$PREVIOUS_TAG" == "$IMAGE_TAG" ]]; then
    warn "no previous tag recorded - leaving the stack as it is for inspection"
    return
  fi
  log "Rolling back to $PREVIOUS_TAG"
  if IMAGE_TAG="$PREVIOUS_TAG" "${COMPOSE[@]}" up -d --remove-orphans; then
    warn "rolled back to $PREVIOUS_TAG"
  else
    warn "ROLLBACK FAILED - the stack needs manual attention"
  fi
}

log "Deploying ${IMAGE_REPO_OWNER}/*:${IMAGE_TAG}  (previous: ${PREVIOUS_TAG:-none})"

# ── Caddyfile: check it before anything changes ─────────────────────────────
# CI copies deploy/Caddyfile across, but a running Caddy never notices: it does
# not watch its config, and a single-file bind mount keeps showing the container
# the old file once tar has replaced it. So a changed Caddyfile is validated
# here, where a mistake can still stop the deploy, and applied after the health
# gate below with a restart.
CADDYFILE_SHA="$(sha256sum deploy/Caddyfile | cut -d' ' -f1)"
CADDYFILE_CHANGED=false
if [[ "$CADDYFILE_SHA" != "$(cat "$CADDY_STATE_FILE" 2>/dev/null || true)" ]]; then
  CADDYFILE_CHANGED=true
  log "Caddyfile changed - validating it"
  "${COMPOSE[@]}" run --rm --no-deps -T --entrypoint caddy caddy \
      validate --config /etc/caddy/Caddyfile --adapter caddyfile \
    || die "deploy/Caddyfile is invalid - nothing was changed on the VM"
fi

log "Pulling images from Docker Hub"
"${COMPOSE[@]}" pull --quiet backend store admin keycloak \
  || die "pull failed - check the tag exists and, for private repos, that this VM is logged in (docker login)"

log "Starting the stack"
"${COMPOSE[@]}" up -d --remove-orphans

log "Waiting for health (up to ${HEALTH_TIMEOUT}s per service)"
failed=()
for service in "${HEALTH_GATED[@]}"; do
  wait_for_health "$service" || failed+=("$service")
done

if (( ${#failed[@]} > 0 )); then
  warn "unhealthy: ${failed[*]}"
  for service in "${failed[@]}"; do
    printf '\n--- last 60 log lines: %s ---\n' "$service"
    "${COMPOSE[@]}" logs --tail=60 --no-color "$service" 2>&1 || true
  done
  rollback
  die "deploy of $IMAGE_TAG failed"
fi

echo "$IMAGE_TAG" > "$STATE_FILE"

if [[ "$CADDYFILE_CHANGED" == true ]]; then
  log "Restarting Caddy to apply the new Caddyfile"
  # A restart re-binds the mount, so Caddy reads the file that is on disk now.
  # Certificates live in the caddy-data volume and survive it.
  "${COMPOSE[@]}" restart caddy
  wait_for_health caddy || die "Caddy did not come back after the Caddyfile change"
  echo "$CADDYFILE_SHA" > "$CADDY_STATE_FILE"
fi

log "Reclaiming disk from superseded images"
# Dangling only: never touches a tagged image, so the previous release stays
# on disk and a rollback does not have to re-download it.
docker image prune -f >/dev/null || true

log "Deployed $IMAGE_TAG successfully"
"${COMPOSE[@]}" ps
