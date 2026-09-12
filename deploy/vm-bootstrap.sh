#!/usr/bin/env bash
# ============================================================================
# Erezer - one-time VM preparation (Ubuntu/Debian)
#
# Run ONCE on a fresh VM, as a user with sudo:
#
#   curl -fsSL https://raw.githubusercontent.com/<owner>/<repo>/main/deploy/vm-bootstrap.sh | bash
#   # or, if you already copied the repo across:
#   bash deploy/vm-bootstrap.sh
#
# Installs Docker Engine + the compose plugin from Docker's own APT repo (the
# distro's `docker.io` package ships an old engine and no `docker compose`),
# creates /opt/erezer owned by the deploy user, and opens 80/443.
#
# It does NOT create .env and does NOT deploy anything - those are steps 5 and
# 7 of DEPLOYMENT.md.
# ============================================================================
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/erezer}"
DEPLOY_USER="${DEPLOY_USER:-$(id -un)}"

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
die() { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

[[ "$(id -u)" -ne 0 ]] || die "run this as a normal sudo user, not as root - the deploy user must not be root"
command -v sudo >/dev/null || die "sudo is required"

log "Installing base packages"
sudo apt-get update -qq
sudo apt-get install -y -qq ca-certificates curl gnupg git ufw

if ! command -v docker >/dev/null; then
  log "Installing Docker Engine from Docker's APT repository"
  sudo install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
    | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  sudo chmod a+r /etc/apt/keyrings/docker.gpg
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" \
    | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
  sudo apt-get update -qq
  sudo apt-get install -y -qq \
    docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
else
  log "Docker already installed: $(docker --version)"
fi

sudo systemctl enable --now docker

log "Adding $DEPLOY_USER to the docker group"
# Without this the CI deploy step would need sudo over SSH, which means either
# a passwordless sudo rule or an interactive prompt that never gets answered.
sudo usermod -aG docker "$DEPLOY_USER"

log "Creating $APP_DIR"
sudo mkdir -p "$APP_DIR"
sudo chown -R "$DEPLOY_USER":"$DEPLOY_USER" "$APP_DIR"

log "Capping container log growth"
# Docker's default json-file driver has NO size limit: one chatty container
# will happily fill the disk and take the whole stack down with it.
sudo mkdir -p /etc/docker
if [[ ! -f /etc/docker/daemon.json ]]; then
  sudo tee /etc/docker/daemon.json >/dev/null <<'JSON'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "20m", "max-file": "5" }
}
JSON
  sudo systemctl restart docker
else
  echo "    /etc/docker/daemon.json already exists - leaving it alone."
  echo "    Make sure it caps log size, or the disk will fill up eventually."
fi

log "Configuring the firewall"
# Caddy is the only thing that should be reachable from the internet. Every
# other port in the compose file is unpublished by the prod overlay.
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable
sudo ufw status verbose

cat <<EOS

------------------------------------------------------------------
VM is ready.

  app directory : $APP_DIR
  deploy user   : $DEPLOY_USER
  docker        : $(docker --version)
  compose       : $(docker compose version --short 2>/dev/null || echo 'plugin installed')

The docker group change only applies to NEW sessions - log out and back
in before running any docker command as $DEPLOY_USER.

Next: step 5 of DEPLOYMENT.md (create $APP_DIR/.env).
------------------------------------------------------------------
EOS
