# syntax=docker/dockerfile:1.7
# ============================================================================
# Erezer admin — multi-stage build
# Stage 1: Node 22 builds the Angular bundle.
# Stage 2: nginx-alpine serves the static files with SPA fallback.
# Build context is the repo root (this Dockerfile sits at root because the
# admin project is the root Angular workspace).
# ============================================================================

FROM node:22-alpine AS build
WORKDIR /workspace

# Exclude the storefront's own node_modules and the backend tree via the
# .dockerignore at the repo root so this stage only sees what it needs.
COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund

COPY angular.json tsconfig.json tsconfig.app.json tsconfig.spec.json ./
COPY postcss.config.json tailwind.config.js ./
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

    location ~* \.(?:js|css|woff2?|ttf|otf|jpg|jpeg|png|gif|webp|svg|ico)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }

    location / {
        try_files $uri $uri/ /index.html;
        add_header Cache-Control "no-cache";
    }

    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
}
NGINXCONF

# The root Angular workspace name is `delivery-admin` in angular.json,
# so the bundle lands here:
COPY --from=build /workspace/dist/delivery-admin/browser/ /usr/share/nginx/html/

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost/ || exit 1
