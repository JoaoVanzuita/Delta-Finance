# syntax=docker/dockerfile:1

# Contexto de build é a raiz do repositório (ver compose.yaml), para que este
# Dockerfile possa reutilizar deploy/client.Caddyfile.

# ---------- Build ----------
FROM node:24.22.0-alpine AS build
WORKDIR /app

COPY client/package.json client/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY client/ ./
RUN npm run build

# ---------- Runtime ----------
FROM caddy:2.11.7-alpine AS runtime

COPY deploy/client.Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv

EXPOSE 80
