#!/usr/bin/env bash
# Smoke test for delivery-backend Spring Boot service.
#
# Usage (from delivery-backend/):
#   .claude/skills/run-delivery-backend/smoke.sh            # probe an already-running server
#   .claude/skills/run-delivery-backend/smoke.sh --launch   # build (if needed), launch, probe, stop
#
# Env overrides:
#   BASE_URL  default http://localhost:8080
#   JAR       default build/libs/delivery-backend-0.0.1-SNAPSHOT.jar
#   STARTUP_TIMEOUT_SEC  default 90

set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
JAR="${JAR:-build/libs/delivery-backend-0.0.1-SNAPSHOT.jar}"
STARTUP_TIMEOUT_SEC="${STARTUP_TIMEOUT_SEC:-90}"
LAUNCH=0
LOG="/tmp/delivery-backend.log"
PID_FILE="/tmp/delivery-backend.pid"

for arg in "$@"; do
  case "$arg" in
    --launch) LAUNCH=1 ;;
    -h|--help)
      sed -n '2,12p' "$0"; exit 0 ;;
  esac
done

red()   { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
info()  { printf '\033[36m[smoke]\033[0m %s\n' "$*"; }

cleanup() {
  if [ "$LAUNCH" = "1" ] && [ -f "$PID_FILE" ]; then
    pid="$(cat "$PID_FILE")"
    info "stopping pid $pid"
    if command -v taskkill >/dev/null 2>&1; then
      # On Windows, nohup spawns a bash helper whose child is java.exe.
      # //T kills the tree; otherwise java.exe survives. //F = force.
      taskkill //F //T //PID "$pid" >/dev/null 2>&1 || true
      # Belt-and-braces: if java.exe is still bound to 8080, kill it directly.
      java_pid="$(netstat -ano 2>/dev/null | awk '/:8080[[:space:]]+.*LISTENING/ {print $5; exit}')"
      if [ -n "$java_pid" ]; then
        taskkill //F //PID "$java_pid" >/dev/null 2>&1 || true
      fi
    else
      kill "$pid" 2>/dev/null || true
    fi
    rm -f "$PID_FILE"
  fi
}
trap cleanup EXIT

if [ "$LAUNCH" = "1" ]; then
  if [ ! -f "$JAR" ]; then
    info "jar not found; running ./gradlew assemble"
    ./gradlew assemble --no-daemon -q || { red "gradle build failed"; exit 1; }
  fi
  rm -f "$LOG"
  info "launching $JAR (log: $LOG)"
  nohup java -jar "$JAR" > "$LOG" 2>&1 &
  echo $! > "$PID_FILE"
  info "waiting up to ${STARTUP_TIMEOUT_SEC}s for 'Started DeliveryBackendApplication'"
  deadline=$(( $(date +%s) + STARTUP_TIMEOUT_SEC ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    if grep -q "Started DeliveryBackendApplication" "$LOG" 2>/dev/null; then
      break
    fi
    if grep -qE "APPLICATION FAILED|Error starting|ApplicationContextException" "$LOG" 2>/dev/null; then
      red "startup failed; last 40 lines of $LOG:"
      tail -40 "$LOG"
      exit 1
    fi
    sleep 1
  done
  if ! grep -q "Started DeliveryBackendApplication" "$LOG" 2>/dev/null; then
    red "timed out waiting for startup"
    tail -40 "$LOG"
    exit 1
  fi
  green "server up"
fi

fail=0
probe() {
  # probe METHOD URL EXPECTED_CODE [LABEL]
  method="$1"; url="$2"; expected="$3"; label="${4:-$url}"
  code="$(curl -s -o /tmp/smoke.body -w "%{http_code}" -X "$method" "$BASE_URL$url")"
  if [ "$code" = "$expected" ]; then
    green "  OK  $method $label -> $code"
  else
    red   "  FAIL $method $label -> $code (expected $expected)"
    head -c 300 /tmp/smoke.body; echo
    fail=1
  fi
}

info "probing $BASE_URL"
# Public endpoints (permitAll in SecurityConfig)
probe GET /v3/api-docs              200 "OpenAPI doc"
probe GET /swagger-ui/index.html    200 "Swagger UI"
probe GET /api/categories           200 "categories list"
probe GET /api/products             200 "products list"
probe GET /api/banners              200 "banners list"
probe GET /app/home                 200 "home page data"

# Protected endpoints: should refuse without a Bearer token
probe GET /admin/dashboard          401 "admin (no JWT)"
probe GET /api/cart                 401 "user cart (no JWT)"

if [ "$fail" = "0" ]; then
  green "ALL PROBES PASSED"
  exit 0
else
  red "SOME PROBES FAILED"
  exit 1
fi
