# syntax=docker/dockerfile:1.7
# ============================================================================
# Erezer storefront — multi-stage build
# Stage 1: Node 22 builds the Angular bundle.
# Stage 2: nginx-alpine serves the static files with SPA fallback.
# ============================================================================

FROM node:22-alpine AS build
WORKDIR /workspace

# Cache deps before copying source — long lockfile, short app diffs.
COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build -- --configuration production


FROM nginx:1.27-alpine AS runtime

# SPA-friendly nginx config: fallback to index.html for client routes,
# long-cache JS/CSS, short-cache index.html.
COPY <<'NGINXCONF' /etc/nginx/conf.d/default.conf
server {
    listen 80 default_server;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    # Gzip for text assets.
    gzip on;
    gzip_types text/plain text/css application/json application/javascript
               application/xml+rss application/atom+xml image/svg+xml;
    gzip_min_length 256;

    # Hashed asset files are immutable.
    location ~* \.(?:js|css|woff2?|ttf|otf|jpg|jpeg|png|gif|webp|svg|ico)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }

    # SPA fallback for Angular routes.
    location / {
        try_files $uri $uri/ /index.html;
        add_header Cache-Control "no-cache";
    }

    # Basic security headers (lean — tighten CSP per app needs).
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
}
NGINXCONF

COPY --from=build /workspace/dist/erezer-store/browser/ /usr/share/nginx/html/

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost/ || exit 1
