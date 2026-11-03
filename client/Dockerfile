# syntax=docker/dockerfile:1

# Contexto de build é a raiz do repositório (ver compose.yaml), para que este
# Dockerfile possa reutilizar deploy/client.Caddyfile.

# ---------- Build ----------
FROM node:24.22.0-alpine@sha256:44fce696f38ba8c987c4db89954051000bbd05ffd8d8659ba15ed4a8602e937f AS build
WORKDIR /app

COPY client/package.json client/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY client/ ./

# O contrato versionado é a fonte dos tipos do cliente; o lint e a geração
# fazem parte do build (prebuild). O caminho relativo esperado é ../specs.
COPY specs/001-finance/contracts/openapi.yaml /specs/001-finance/contracts/openapi.yaml
RUN npm run build

# ---------- Runtime ----------
FROM caddy:2.11.7-alpine@sha256:e70b164da81ef698b3ad19af799ea6086ce6d9e48ed4079eb9fe26f6fbf14ee5 AS runtime

COPY deploy/client.Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv

EXPOSE 80
