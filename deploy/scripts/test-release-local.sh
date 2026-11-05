#!/usr/bin/env bash
#
# Ensaio local de release de produção a partir da árvore da branch atual.
#
# Builda as imagens do server e do client localmente (mesmas contexts do
# workflow de release), sobe o stack de release isolado, roda o smoke test e
# derruba tudo. Não usa a main nem imagens publicadas no GHCR.
#
# Uso:
#   ./deploy/scripts/test-release-local.sh [opções]
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"

PROJECT="${DELTA_TEST_PROJECT:-delta-finance-local-test}"
PLATFORM="${DELTA_TEST_PLATFORM:-linux/amd64}"
HTTP_PORT="${DELTA_TEST_HTTP_PORT:-18080}"
HTTPS_PORT="${DELTA_TEST_HTTPS_PORT:-18443}"
RUN_ROOT="${DELTA_TEST_RUN_ROOT:-${TMPDIR:-/tmp}}"
HOST=""
STOP_DEV=0
KEEP=0
NO_BUILD=0
RUN_DIR=""
SUCCESS=0

step() { printf '\n==> %s\n' "$*"; }
info() { printf '    %s\n' "$*"; }
warn() { printf '[aviso] %s\n' "$*" >&2; }
die()  { printf '[erro] %s\n' "$*" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }

usage() {
  cat <<'USAGE'
Uso: test-release-local.sh [opções]

  --host <ipv4>      DELTA_LAN_HOST (padrão: IP da LAN detectado)
  --stop-dev         para o stack de desenvolvimento antes do ensaio
  --keep             mantém o stack no ar e os artefatos ao terminar
  --no-build         reutiliza as imagens já buildadas
  --platform <plat>  plataforma do build (padrão: linux/amd64)
  --project <nome>   nome do projeto Compose (padrão: delta-finance-local-test)
  --http-port <p>    porta HTTP no host (padrão: 18080)
  --https-port <p>   porta HTTPS no host (padrão: 18443)
  -h, --help         mostra esta ajuda
USAGE
}

cleanup() {
  if (( KEEP )); then
    if [[ -n "$RUN_DIR" ]]; then
      printf '\n[test-release] --keep: stack mantido no ar; artefatos em %s\n' "$RUN_DIR"
    fi
  elif [[ -n "$RUN_DIR" && -d "$RUN_DIR" ]]; then
    ( cd "$RUN_DIR" && COMPOSE_PROJECT_NAME="$PROJECT" docker compose down -v >/dev/null 2>&1 ) || true
    if (( SUCCESS )); then
      printf '\n[test-release] stack derrubado. Artefatos: %s\n' "$RUN_DIR"
    else
      printf '\n[test-release] falha: stack derrubado. Artefatos: %s\n' "$RUN_DIR" >&2
    fi
  fi
  if (( STOP_DEV )); then
    printf '[test-release] stack dev parado (--stop-dev). Religar: (cd "%s" && docker compose -f compose.yaml up -d)\n' "$ROOT_DIR" >&2
  fi
}
trap cleanup EXIT

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host) [[ $# -ge 2 ]] || die "--host exige um valor"; HOST="$2"; shift 2 ;;
    --stop-dev) STOP_DEV=1; shift ;;
    --keep) KEEP=1; shift ;;
    --no-build) NO_BUILD=1; shift ;;
    --platform) [[ $# -ge 2 ]] || die "--platform exige um valor"; PLATFORM="$2"; shift 2 ;;
    --project) [[ $# -ge 2 ]] || die "--project exige um valor"; PROJECT="$2"; shift 2 ;;
    --http-port) [[ $# -ge 2 ]] || die "--http-port exige um valor"; HTTP_PORT="$2"; shift 2 ;;
    --https-port) [[ $# -ge 2 ]] || die "--https-port exige um valor"; HTTPS_PORT="$2"; shift 2 ;;
    -h | --help) usage; exit 0 ;;
    -*) die "opção desconhecida: $1" ;;
    *) die "argumento inesperado: $1" ;;
  esac
done

have docker || die "docker não encontrado"
have openssl || die "openssl não encontrado"
docker compose version >/dev/null 2>&1 || die "docker compose (v2) não encontrado"
docker info >/dev/null 2>&1 || die "não foi possível falar com o Docker; ele está em execução?"

detect_lan_ip() {
  local ip=""
  if have ip; then
    ip="$(ip route get 1.1.1.1 2>/dev/null | awk '{for (i=1;i<=NF;i++) if ($i=="src") {print $(i+1); exit}}' || true)"
  fi
  if [[ -z "$ip" ]] && have hostname; then
    ip="$(hostname -I 2>/dev/null | tr ' ' '\n' | grep -vE '^(127\.|172\.(1[7-9]|2[0-9]|3[01])\.)' | head -n1 || true)"
  fi
  printf '%s' "$ip"
}

if [[ -z "$HOST" ]]; then
  HOST="$(detect_lan_ip)"
fi
[[ -n "$HOST" ]] || die "não foi possível detectar o IP da LAN; informe com --host <ipv4>"
[[ "$HOST" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || die "--host inválido (IPv4 esperado): ${HOST}"

port_busy() {
  local port="$1"
  if have ss; then
    ss -H -ltn 2>/dev/null | awk '{print $4}' | grep -qE "[:.]${port}$"
  else
    return 1
  fi
}
if port_busy "$HTTP_PORT"; then die "porta ${HTTP_PORT} já está em uso; use --http-port para escolher outra"; fi
if port_busy "$HTTPS_PORT"; then die "porta ${HTTPS_PORT} já está em uso; use --https-port para escolher outra"; fi

BRANCH="$(git -C "$ROOT_DIR" branch --show-current 2>/dev/null || true)"
COMMIT="$(git -C "$ROOT_DIR" rev-parse --short HEAD 2>/dev/null || true)"
if [[ -n "$(git -C "$ROOT_DIR" status --porcelain 2>/dev/null || true)" ]]; then
  DIRTY="sim"
else
  DIRTY="não"
fi
VERSION="$(tr -d '[:space:]' < "${ROOT_DIR}/VERSION" 2>/dev/null || true)"
[[ -n "$VERSION" ]] || die "não foi possível ler VERSION"

step "Ensaio de release local"
info "branch: ${BRANCH:-detached} (${COMMIT:-?})  árvore suja: ${DIRTY}"
info "host: ${HOST}  http: ${HTTP_PORT}  https: ${HTTPS_PORT}"
info "projeto: ${PROJECT}  plataforma: ${PLATFORM}"
info "versão: ${VERSION}"

if (( STOP_DEV )); then
  step "Parando o stack de desenvolvimento (delta-finance-dev)"
  ( cd "$ROOT_DIR" && docker compose -f compose.yaml down )
fi

SERVER_IMAGE="${PROJECT}/server:local"
CLIENT_IMAGE="${PROJECT}/client:local"

if (( NO_BUILD )); then
  step "Reutilizando imagens existentes (--no-build)"
else
  step "Build do server (${PLATFORM})"
  docker build --platform "$PLATFORM" -t "$SERVER_IMAGE" "${ROOT_DIR}/server"
  step "Build do client (${PLATFORM})"
  docker build --platform "$PLATFORM" -t "$CLIENT_IMAGE" -f "${ROOT_DIR}/client/Dockerfile" "${ROOT_DIR}"
fi

mkdir -p "$RUN_ROOT"
RUN_DIR="$(mktemp -d "${RUN_ROOT}/delta-finance-release-test.XXXXXX")"

step "Preparando o ensaio em ${RUN_DIR}"
cp "${ROOT_DIR}/compose.release.yaml" "${RUN_DIR}/compose.yaml"
cp "${ROOT_DIR}/deploy/release/smoke-test.sh" "${RUN_DIR}/smoke-test.sh"
chmod 755 "${RUN_DIR}/smoke-test.sh"

mkdir -p "${RUN_DIR}/secrets"
umask 077
cat > "${RUN_DIR}/secrets/crypto-keys.json" <<EOF
{
  "data":  [{ "version": 1, "key": "$(openssl rand -base64 32)" }],
  "index": [{ "version": 1, "key": "$(openssl rand -base64 32)" }]
}
EOF
chmod 640 "${RUN_DIR}/secrets/crypto-keys.json"
KEYRING_GID="$(stat -c %g "${RUN_DIR}/secrets/crypto-keys.json" 2>/dev/null || stat -f %g "${RUN_DIR}/secrets/crypto-keys.json")"
DB_NAME="delta_finance"
DB_USER="delta"
DB_PASSWORD="$(openssl rand -hex 24)"

cat > "${RUN_DIR}/compose.override.yaml" <<EOF
name: ${PROJECT}

services:
  server:
    image: ${SERVER_IMAGE}
  client:
    image: ${CLIENT_IMAGE}

volumes:
  pgdata:
    name: ${PROJECT}_pgdata
  caddy_data:
    name: ${PROJECT}_caddy_data
EOF

cat > "${RUN_DIR}/.env" <<EOF
DELTA_VERSION=${VERSION}
DELTA_IMAGE_OWNER=local
DELTA_DB_NAME=${DB_NAME}
DELTA_DB_USER=${DB_USER}
DELTA_DB_PASSWORD=${DB_PASSWORD}
DELTA_DB_POOL_SIZE=10
DELTA_LAN_HOST=${HOST}
LAN_HTTP_PORT=${HTTP_PORT}
LAN_HTTPS_PORT=${HTTPS_PORT}
DELTA_KEYRING_GID=${KEYRING_GID}
DELTA_CRYPTO_ACTIVE_VERSION=1
DELTA_INDEX_KEY_VERSION=1
DELTA_SERVER_MEM_LIMIT=1g
DELTA_OPERATOR_RESET_TOKEN=
DELTA_KEY_ROTATION_RUN=false
EOF

step "Validando a configuração do Compose de ensaio"
resolved_config="$( cd "$RUN_DIR" && COMPOSE_PROJECT_NAME="$PROJECT" docker compose config )"
if ! printf '%s' "$resolved_config" | grep -q -- "${PROJECT}_pgdata"; then
  die "volume pgdata não ficou isolado em ${PROJECT}_pgdata; abortando para não tocar produção"
fi
if ! printf '%s' "$resolved_config" | grep -q -- "${PROJECT}_caddy_data"; then
  die "volume caddy_data não ficou isolado em ${PROJECT}_caddy_data; abortando para não tocar produção"
fi
if printf '%s' "$resolved_config" | grep -q -- 'delta-finance-prod_'; then
  die "a configuração ainda referencia volumes de produção; abortando"
fi
info "volumes isolados e configuração válida"

step "Subindo o stack de release"
if ! ( cd "$RUN_DIR" && COMPOSE_PROJECT_NAME="$PROJECT" docker compose up -d --wait --wait-timeout 300 ); then
  ( cd "$RUN_DIR" && COMPOSE_PROJECT_NAME="$PROJECT" docker compose logs --no-color > "${RUN_DIR}/logs.txt" 2>&1 ) || true
  printf '\n[test-release] últimos logs:\n' >&2
  tail -n 120 "${RUN_DIR}/logs.txt" >&2 || true
  die "o stack não ficou saudável em 300s"
fi

( cd "$RUN_DIR" && COMPOSE_PROJECT_NAME="$PROJECT" docker compose logs --no-color > "${RUN_DIR}/logs.txt" 2>&1 ) || true

step "Estado dos serviços"
( cd "$RUN_DIR" && COMPOSE_PROJECT_NAME="$PROJECT" docker compose ps )

step "Migrações (Flyway)"
migrations="$( cd "$RUN_DIR" && COMPOSE_PROJECT_NAME="$PROJECT" docker compose exec -T db \
  psql -U "$DB_USER" -d "$DB_NAME" -tAc 'select count(*) from flyway_schema_history' 2>/dev/null || true )"
migrations="$(printf '%s' "$migrations" | tr -d '[:space:]')"
if [[ "$migrations" =~ ^[0-9]+$ ]] && (( migrations >= 1 )); then
  info "Flyway: ${migrations} migração(ões) registrada(s) em flyway_schema_history"
else
  die "não foi possível confirmar as migrações no banco (flyway_schema_history)"
fi

step "Smoke test"
( cd "$RUN_DIR" && COMPOSE_PROJECT_NAME="$PROJECT" bash smoke-test.sh ) 2>&1 | tee "${RUN_DIR}/smoke-test.log"

SUCCESS=1
step "Resultado"
info "branch: ${BRANCH:-detached} (${COMMIT:-?})  árvore suja: ${DIRTY}"
info "host: ${HOST}  http: ${HTTP_PORT}  https: ${HTTPS_PORT}"
info "projeto: ${PROJECT}"
info "artefatos: ${RUN_DIR}"
printf '\n[test-release] ENSAIO DE RELEASE OK\n'
