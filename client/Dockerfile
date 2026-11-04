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

# ---------- Caddy ----------
# O binário oficial 2.11.7 ainda é publicado com versões antigas do Go e de
# dependências com CVEs corrigidas. Recompilamos a mesma tag com o toolchain
# atual e as correções mínimas para não entregar um proxy vulnerável.
FROM caddy:2.11.7-builder-alpine@sha256:95a5e7e2692b5eb9545aadc5b90a39f6f509bee59c9508a493f83ee902eaef63 AS caddy-build
RUN apk add --no-cache git
WORKDIR /src
RUN git clone --depth 1 --branch v2.11.7 https://github.com/caddyserver/caddy.git . \
    && go get golang.org/x/net@v0.60.0 github.com/go-jose/go-jose/v4@v4.1.6 \
    && go mod tidy \
    && CGO_ENABLED=0 go build -trimpath -ldflags "-s -w" -o /out/caddy ./cmd/caddy

# ---------- Runtime ----------
FROM caddy:2.11.7-alpine@sha256:e70b164da81ef698b3ad19af799ea6086ce6d9e48ed4079eb9fe26f6fbf14ee5 AS runtime

# Atualiza os pacotes Alpine do momento do build; o binário base ainda carrega
# versões antigas de bibliotecas do sistema.
RUN apk upgrade --no-cache

# O release usa TLS interno; o Compose de desenvolvimento seleciona o
# Caddyfile HTTP atrás do proxy TLS de desenvolvimento.
ARG CADDYFILE=deploy/client-tls.Caddyfile

COPY --from=caddy-build /out/caddy /usr/bin/caddy
COPY ${CADDYFILE} /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv

# Caddy é executado sem privilégios. As portas internas são altas para não
# exigir cap_net_bind_service nem executar o processo como root.
RUN addgroup -S -g 10001 caddy \
    && adduser -S -D -H -u 10001 -G caddy caddy \
    && chown -R caddy:caddy /srv /data /config

USER caddy
EXPOSE 8080 8443
