# syntax=docker/dockerfile:1.7
# ============================================================================
# Erezer storefront - multi-stage build
# Stage 1: Node 22 builds the Angular bundle.
# Stage 2: nginx-alpine serves it with SPA fallback.
#
# Build context is this directory (erezer-store/).
# ============================================================================

FROM node:22-alpine AS build
WORKDIR /workspace

# Dependencies first: the lockfile changes far less often than app source, so
# this layer stays cached across ordinary code edits.
COPY package.json package-lock.json* ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

COPY angular.json tsconfig.json tsconfig.app.json tsconfig.spec.json ./
COPY .postcssrc.json ./
COPY src ./src
COPY public ./public

RUN npm run build -- --configuration production


FROM nginx:1.27-alpine AS runtime

COPY <<'NGINXCONF' /etc/nginx/conf.d/default.conf
server {
    listen 80 default_server;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    gzip on;
    gzip_types text/plain text/css application/json application/javascript
               application/xml+rss application/atom+xml image/svg+xml;
    gzip_min_length 256;

    # Runtime config. This MUST come before the hashed-asset rule below:
    # env.js ends in .js but is rewritten on every container start, so caching
    # it would pin the browser to a previous environment's URLs. An exact-match
    # (`=`) location outranks the regex location, so this wins.
    location = /env.js {
        add_header Cache-Control "no-store, no-cache, must-revalidate" always;
        expires -1;
    }

    # The sitemap search engines read is built by the backend from the live
    # catalog, so a new product is listed by itself. The upstream is a variable
    # and Docker's own DNS the resolver, so nginx looks the backend up per
    # request instead of at startup - it still starts when the backend is down.
    location = /sitemap.xml {
        resolver 127.0.0.11 valid=30s ipv6=off;
        set $sitemap_upstream http://backend:8080;
        proxy_pass $sitemap_upstream/api/sitemap.xml;
        proxy_set_header Host $host;
        add_header Cache-Control "public, max-age=3600";
    }

    # Hashed build output is content-addressed and safe to cache forever.
    location ~* \.(?:js|css|woff2?|ttf|otf|jpg|jpeg|png|gif|webp|svg|ico)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }

    # SPA fallback for Angular client-side routes.
    location / {
        try_files $uri $uri/ /index.html;
        add_header Cache-Control "no-cache";
    }

    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
}
NGINXCONF

COPY --from=build /workspace/dist/erezer-store/browser/ /usr/share/nginx/html/

# The stock nginx entrypoint executes everything in this directory at boot,
# so env.js is regenerated on every start without overriding ENTRYPOINT/CMD.
COPY --chmod=0755 docker-entrypoint.sh /docker-entrypoint.d/40-erezer-env.sh

EXPOSE 80
# Probe 127.0.0.1, not localhost: inside the container localhost also
# resolves to ::1, nginx listens on IPv4 only, and wget tries the IPv6
# address first - which fails while the server is perfectly healthy.
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
