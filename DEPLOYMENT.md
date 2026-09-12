# Deploying Erezer with GitHub Actions → Docker Hub → your VM

A push to `main` runs the test suite, builds four Docker images, pushes them to
Docker Hub, copies the compose files to your VM over SSH and rolls the new
images out — rolling back by itself if the stack does not come up healthy.

This guide is the whole setup, start to finish, on a fresh VM. Work through the
steps in order; nothing here assumes you have deployed anything before.

---

## How the pipeline fits together

```
 push to main
      │
      ▼
┌─────────────┐   ci.yml (called by cd.yml)
│  1. test    │   Gradle build + tests, Angular production builds,
└─────────────┘   compose files parse
      │ green
      ▼
┌─────────────┐   4 images, in parallel, tagged <short-sha> + latest
│  2. build   │   ─────────────────────────────────────────────►  Docker Hub
└─────────────┘        delivery-backend  erezer-store
      │                delivery-admin    keycloak
      ▼
┌─────────────┐   scp compose files ──►  VM   ──►  docker compose pull + up -d
│  3. deploy  │   wait for health, roll back on failure,
└─────────────┘   then curl the public URLs from outside
```

**Why four images and not two.** "Frontend and backend" is really three apps
plus a customised auth server: the storefront (`erezer-store`), the admin panel
(`delivery-admin`), the Spring Boot API (`delivery-backend`), and Keycloak with
the Erezer login theme baked in (`keycloak`). Stock Keycloak would silently
serve its own default login page, so the theme has to travel as an image.

**Why the VM never builds.** The VM has no JDK, no Node and no source tree. It
pulls the exact images CI tested. That is also why the Angular apps read their
API and Keycloak URLs from `/env.js` at container start rather than baking them
in at build time — one image works in any environment.

**What runs where.** On the VM, only Caddy is exposed (ports 80/443). Postgres,
Redis, MinIO, Keycloak and the API are reachable only on the internal Docker
network; Caddy terminates TLS and routes by hostname.

### Files this setup adds

| File | Role |
|---|---|
| `.github/workflows/ci.yml` | Tests and builds. Runs on PRs; called by `cd.yml` on `main`. |
| `.github/workflows/cd.yml` | Builds + pushes images, then deploys to the VM. |
| `docker-compose.deploy.yml` | Third overlay: strips every `build:` so the VM pulls from Docker Hub instead of building. |
| `deploy/vm-bootstrap.sh` | One-time VM prep: Docker, firewall, `/opt/erezer`, log caps. |
| `deploy/deploy.sh` | Runs on the VM. Pulls, restarts, waits for health, rolls back on failure. |
| `.env.prod.example` | Template for the VM's `.env`. Never committed with real values. |

---

## Before you start

You need:

- **A VM** with a public IP, Ubuntu 22.04 or 24.04, **at least 4 GB RAM** (the
  JVM, Keycloak, Postgres, Redis, MinIO and Caddy all run on this one host;
  2 GB will thrash), and ~20 GB disk.
- **A domain** you control, with access to its DNS records.
- **A Docker Hub account** — <https://hub.docker.com>.
- **Push access** to the GitHub repo (to add secrets you need admin on it).

---

## Step 1 — Point DNS at the VM

Erezer serves five hostnames. Create an **A record** for each, all pointing at
your VM's public IP:

| Record | Example | Serves |
|---|---|---|
| storefront | `shop.example.com` | customer-facing store |
| admin | `admin.example.com` | staff admin panel |
| api | `api.example.com` | Spring Boot API |
| auth | `auth.example.com` | Keycloak |
| media | `media.example.com` | MinIO (product images) |

> **Do this first and let it propagate.** Caddy asks Let's Encrypt for
> certificates on its first boot, and Let's Encrypt validates by connecting to
> these names over the public internet. If DNS is not live yet, certificate
> issuance fails, and repeated failures hit Let's Encrypt's rate limits — which
> leaves you locked out for hours.

Verify from your laptop before moving on:

```bash
dig +short shop.example.com    # must print your VM's IP
```

---

## Step 2 — Create the Docker Hub access token

1. Log in to <https://hub.docker.com>.
2. **Account Settings → Personal access tokens → Generate new token**.
3. Description: `github-actions-erezer`. Permissions: **Read & Write**.
4. Copy the token now — Docker Hub shows it exactly once.

You do **not** need to create the four repositories by hand: the first push
creates them. They will be **public** by default. If you want them private,
create them as private in Docker Hub first (**Repositories → Create**), named
exactly:

```
delivery-backend    erezer-store    delivery-admin    keycloak
```

> Private repos mean the VM must authenticate to pull. That is one extra
> command in Step 5.

---

## Step 3 — Prepare the VM

SSH into the VM as your normal sudo user (not root):

```bash
ssh youruser@YOUR_VM_IP
```

Run the bootstrap script. It installs Docker Engine and the compose plugin from
Docker's own APT repository (Ubuntu's `docker.io` package is too old and has no
`docker compose`), creates `/opt/erezer`, caps container log growth, and opens
only 22/80/443:

```bash
curl -fsSL https://raw.githubusercontent.com/MehediHasanRapiIIT/erezer-store/main/deploy/vm-bootstrap.sh | bash
```

If the repo is private, copy the script across instead:

```bash
# from your laptop, in the repo
scp deploy/vm-bootstrap.sh youruser@YOUR_VM_IP:~/
ssh youruser@YOUR_VM_IP 'bash ~/vm-bootstrap.sh'
```

**Then log out and back in.** The script adds your user to the `docker` group,
and group membership only applies to new sessions. Confirm:

```bash
docker ps          # must work without sudo
docker compose version
```

---

## Step 4 — Create the SSH deploy key

GitHub needs its own key to reach the VM. Do not reuse your personal key.

**On your laptop:**

```bash
ssh-keygen -t ed25519 -C "github-actions-erezer" -f ~/.ssh/erezer_deploy -N ""
```

That writes two files: `erezer_deploy` (private — goes into GitHub) and
`erezer_deploy.pub` (public — goes onto the VM).

**Authorise the public key on the VM:**

```bash
ssh-copy-id -i ~/.ssh/erezer_deploy.pub youruser@YOUR_VM_IP
```

**Verify it works before trusting CI with it:**

```bash
ssh -i ~/.ssh/erezer_deploy youruser@YOUR_VM_IP 'docker ps && echo DEPLOY_KEY_OK'
```

If that prints `DEPLOY_KEY_OK`, the key is good.

**Capture the host key** so CI can verify it is talking to the right machine:

```bash
ssh-keyscan -H YOUR_VM_IP
```

Keep that output — it becomes the `VM_KNOWN_HOSTS` secret in Step 6.

**Print the private key** — this becomes `VM_SSH_KEY`:

```bash
cat ~/.ssh/erezer_deploy
```

Copy **everything**, including the `-----BEGIN OPENSSH PRIVATE KEY-----` and
`-----END OPENSSH PRIVATE KEY-----` lines and the trailing newline.

---

## Step 5 — Create the VM's `.env`

The VM owns its own secrets. CI overwrites the compose files on every deploy
but **never touches `.env`**, so production credentials can never be clobbered
by repo contents.

On the VM:

```bash
cd /opt/erezer
curl -fsSL https://raw.githubusercontent.com/MehediHasanRapiIIT/erezer-store/main/.env.prod.example -o .env
# (private repo: scp .env.prod.example across from your laptop instead)
nano .env
```

Replace every `CHANGE_ME` value. Generate each secret properly:

```bash
openssl rand -base64 36
```

The values that must be right:

| Variable | What to put |
|---|---|
| `IMAGE_REPO_OWNER` | your Docker Hub **username**, lowercase |
| `STOREFRONT_HOST` `ADMIN_HOST` `API_HOST` `KEYCLOAK_HOST` `MEDIA_HOST` | the five hostnames from Step 1, **no `https://`** |
| `PUBLIC_KEYCLOAK_URL` | the same value as `KEYCLOAK_HOST`, but **with** `https://` |
| `ACME_EMAIL` | a real inbox — Let's Encrypt warns you here before certs expire |
| `POSTGRES_PASSWORD` | generated secret |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | generated; the password is at least 8 chars |
| `KEYCLOAK_ADMIN_PASSWORD` | generated |
| `EREZER_JWT_SECRET` | generated, **minimum 32 characters** |
| `MAIL_HOST` `MAIL_PORT` `MAIL_USERNAME` `MAIL_PASSWORD` `MAIL_FROM` | your real SMTP provider — Mailpit does not run in production |
| `CADDY_GLOBAL_EXTRA` | leave **empty** for real hostnames |

Lock it down:

```bash
chmod 600 /opt/erezer/.env
```

**If your Docker Hub repos are private**, log the VM in once (the credentials
persist in `~/.docker/config.json`, so the deploy never has to handle them):

```bash
docker login -u YOUR_DOCKERHUB_USERNAME
```

> **`POSTGRES_DB` matters.** The Postgres container creates the database named
> there, and the backend's JDBC URL is built from the same variable. Leave it
> at `delivery_app_v1` unless you change it in both senses knowingly.

---

## Step 6 — Add the GitHub secrets and variables

In the repo: **Settings → Secrets and variables → Actions**.

### Secrets (tab: *Secrets* → *New repository secret*)

| Secret | Value | Where it came from |
|---|---|---|
| `DOCKERHUB_USERNAME` | your Docker Hub username, lowercase | Step 2 |
| `DOCKERHUB_TOKEN` | the access token | Step 2 |
| `VM_HOST` | the VM's public IP or a hostname that resolves to it | your provider |
| `VM_USER` | the Linux user you SSH in as (e.g. `ubuntu`) | Step 3 |
| `VM_SSH_KEY` | the **entire** private key file | Step 4 |
| `VM_KNOWN_HOSTS` | the `ssh-keyscan` output | Step 4 |
| `VM_SSH_PORT` | *optional* — only if SSH is not on 22 | — |

### Variables (tab: *Variables* → *New repository variable*)

Variables are not secret; they exist so the deploy can smoke-test the real URLs.

| Variable | Value |
|---|---|
| `STOREFRONT_HOST` | `shop.example.com` |
| `API_HOST` | `api.example.com` |
| `APP_DIR` | *optional* — only if you did not use `/opt/erezer` |

> `VM_KNOWN_HOSTS` is optional in the sense that the workflow falls back to
> `ssh-keyscan` at deploy time — but that fallback trusts whatever answers on
> the first connection. Set it.

### Optional: require an approval before production

**Settings → Environments → New environment → `production`.** Add yourself
under *Required reviewers*. The `deploy` job already targets this environment,
so every rollout then waits for a human click. Images still build and push
without approval — only the VM rollout pauses.

---

## Step 7 — First deploy

Commit and push everything this setup added:

```bash
git add .github docker-compose.deploy.yml deploy/deploy.sh deploy/vm-bootstrap.sh \
        .env.prod.example .gitignore .env.example docker-compose.yml docker-compose.prod.yml \
        DEPLOYMENT.md
git commit -m "Add CI/CD: build to Docker Hub, deploy to VM"
git push origin main
```

Watch it in the repo's **Actions** tab. The first run takes roughly 10–15
minutes — Gradle and npm caches are cold, and four images build from scratch.
Later runs are much faster because layer caches live in the registry.

When the `deploy` job is green, check the site:

```
https://shop.example.com     storefront
https://admin.example.com    admin panel
https://api.example.com/actuator/health   → {"status":"UP"}
```

Certificates are issued on Caddy's first boot and can take 30–60 seconds. If
the browser shows a TLS warning, wait and reload before assuming failure.

---

## Step 8 — Post-install, once only

### 8a. Finish the Keycloak setup

The realm import only runs on Keycloak's very first boot, so a few settings —
the `moderator` role, lockout policy, and the confidential client the backend
uses to manage staff logins — are applied by script:

```bash
ssh youruser@YOUR_VM_IP
cd /opt/erezer
python3 deploy/keycloak_setup.py
```

It writes `KEYCLOAK_ADMIN_CLIENT_SECRET` into `/opt/erezer/.env`. Restart the
backend so it picks the secret up:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.deploy.yml \
  up -d backend
```

### 8b. Change the seeded admin password

The realm ships with `admin` / `admin`. Log in at
`https://admin.example.com`, or via the Keycloak console, and change it
immediately.

### 8c. Confirm the storage buckets are public-read

Product images are served to browsers straight from MinIO, so the buckets need
anonymous read. The `minio-init` container does this on every boot — confirm it
succeeded:

```bash
docker logs erezer-minio-init     # expect: "MinIO buckets ready"
```

---

## Day-to-day operation

### Deploy

Push to `main`. That is the whole workflow.

### Roll back

Two ways, both safe:

- **Automatic.** If the new images do not report healthy within 5 minutes,
  `deploy.sh` restores the previously deployed tag and fails the job. You get a
  red build and a working site.
- **Manual.** **Actions → CD → Run workflow**, and type the 7-character commit
  SHA of a known-good build into *Existing tag to deploy*. That skips tests and
  builds and redeploys those exact images.

To see what is currently running:

```bash
cat /opt/erezer/.deployed-tag
```

### Read logs

```bash
cd /opt/erezer
C="-f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.deploy.yml"
docker compose $C ps                      # health of everything
docker compose $C logs -f backend         # tail the API
docker compose $C logs --tail=100 caddy   # TLS and routing problems
```

### Restart or stop

```bash
docker compose $C restart backend
docker compose $C down        # stop everything; volumes survive
```

Never run `docker compose down -v` on the VM — `-v` deletes the volumes, which
means the database, the uploaded images and Caddy's certificates.

### Back up

A nightly `pg_dump` runs automatically into the `backup-data` volume, with
14-day retention. **That volume is on the same host as the database**, so it
does not protect you from losing the VM. For real safety, set `BACKUP_S3_*` in
`/opt/erezer/.env` and restart the `pg-backup` service.

Take an immediate dump before anything risky:

```bash
docker compose $C exec -T postgres pg_dump -U postgres delivery_app_v1 | gzip > ~/erezer-$(date +%F).sql.gz
```

---

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Deploy fails at *Configure SSH* | `VM_SSH_KEY` is truncated. It must include the BEGIN/END lines and a trailing newline. |
| `Permission denied (publickey)` | The public key is not in the VM user's `~/.ssh/authorized_keys`, or `VM_USER` is wrong. Re-run `ssh-copy-id`. |
| `permission denied while trying to connect to the Docker daemon` | The deploy user is not in the `docker` group, or was added but never logged out and back in. |
| Pull fails with `pull access denied` | Repos are private and the VM is not logged in. Run `docker login` on the VM. |
| `deploy of <tag> failed`, backend unhealthy | Read the log lines the script prints. Usually a missing or malformed value in `/opt/erezer/.env`. |
| Browser shows a TLS warning | DNS is not pointing at the VM yet, or 80/443 are blocked upstream (a cloud provider security group is separate from `ufw`). Caddy needs port 80 reachable to validate. |
| Admin login fails with an issuer/token mismatch | `KEYCLOAK_HOST` in `.env` does not match the host the browser uses. Both the backend's issuer and the admin panel's Keycloak URL are derived from it. |
| Images do not render in the storefront | `MEDIA_HOST` is wrong, or the buckets are not public-read — check `docker logs erezer-minio-init`. |
| Backend healthy locally, 502 through Caddy | Caddy started before the backend. `docker compose $C restart caddy`. |
| First deploy works, second says "no space left on device" | Old images accumulated. `docker image prune -a -f` (the running stack is untouched). |

### When a deploy fails and you need the truth

```bash
ssh youruser@YOUR_VM_IP
cd /opt/erezer
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.deploy.yml ps
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.deploy.yml logs --tail=200 backend
```

---

## Security notes

- **The `.env` never leaves the VM.** It is not in the repo, not in CI, and not
  in the tarball the deploy copies across.
- **Rotate the Docker Hub token** from Docker Hub's UI if it is ever exposed;
  update the `DOCKERHUB_TOKEN` secret afterwards.
- **The deploy key is a shell on your VM.** It is not restricted to the deploy
  script. If you want it to be, add a `command="..."` restriction in the VM's
  `authorized_keys`.
- **Only 22, 80 and 443 are open.** Postgres, Redis, MinIO and Keycloak are not
  published to the host at all under the production overlay — they exist only
  on the internal Docker network. That also means you cannot reach Postgres by
  tunnelling to the VM's `localhost:5432`; nothing listens there. Go in through
  the container instead:
  ```bash
  ssh youruser@YOUR_VM_IP
  cd /opt/erezer
  docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.deploy.yml \
    exec postgres psql -U postgres -d delivery_app_v1
  ```
  Resist the urge to republish the port "just for now" — it is the single
  easiest way to end up with an internet-facing database.
- **`docker login` on the VM writes credentials in plain text** to
  `~/.docker/config.json`. Use a read-only Docker Hub token there, not the
  read-write one CI uses.
