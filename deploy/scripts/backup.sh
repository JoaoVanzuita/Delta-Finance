#!/usr/bin/env bash
#
# Backup lógico do PostgreSQL do Delta Finance.
#
# - Gera um dump custom (`pg_dump -Fc`) a partir do container `db`.
# - Cifra com `age` (obrigatório por padrão): o arquivo em repouso nunca contém
#   dados legíveis.
# - Mantém retenção configurável (padrão: 30 dias) e grava checksum SHA-256.
#
# Uso:
#   AGE_RECIPIENT=age1... ./deploy/scripts/backup.sh
#
# Variáveis:
#   AGE_RECIPIENT                 chave pública age para cifrar (obrigatória)
#   DELTA_BACKUP_RETENTION_DAYS   dias de retenção (padrão 30)
#   BACKUP_DIR                    diretório de destino (padrão <repo>/backups)
#   ENV_FILE                      arquivo de ambiente (padrão <repo>/.env)
#   ALLOW_UNENCRYPTED_BACKUP=1    permite dump sem cifra (apenas laboratório)
#
# O key ring NÃO é incluído neste backup. Copie `secrets/crypto-keys.json` por
# um canal separado: sem ele, os campos cifrados do dump são irrecuperáveis.
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=lib/backup-retention.sh
. "${SCRIPT_DIR}/lib/backup-retention.sh"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env}"
BACKUP_DIR="${BACKUP_DIR:-${ROOT_DIR}/backups}"
RETENTION_DAYS="${DELTA_BACKUP_RETENTION_DAYS:-30}"
COMPOSE=(docker compose --project-directory "${ROOT_DIR}" -f "${ROOT_DIR}/compose.yaml")

log() { printf '[backup] %s\n' "$*" >&2; }
die() { printf '[backup] ERRO: %s\n' "$*" >&2; exit 1; }

[[ -f "${ENV_FILE}" ]] || die "arquivo de ambiente não encontrado: ${ENV_FILE}"

set -a
# shellcheck disable=SC1090
. "${ENV_FILE}"
set +a

: "${DELTA_DB_NAME:?DELTA_DB_NAME não definido}"
: "${DELTA_DB_USER:?DELTA_DB_USER não definido}"

command -v docker >/dev/null 2>&1 || die "docker não encontrado no PATH"
"${COMPOSE[@]}" ps db >/dev/null 2>&1 || die "serviço db não está disponível no Compose"

mkdir -p "${BACKUP_DIR}"
chmod 700 "${BACKUP_DIR}"

timestamp="$(TZ=America/Sao_Paulo date +%Y%m%dT%H%M%S)"
base_name="delta-${DELTA_DB_NAME}-${timestamp}.dump"

tmp_file="$(mktemp "${BACKUP_DIR}/.${base_name}.XXXXXX")"
cleanup() { rm -f "${tmp_file}"; }
trap cleanup EXIT

log "gerando dump de ${DELTA_DB_NAME} (container db)"
if ! "${COMPOSE[@]}" exec -T db \
  pg_dump --format=custom --no-owner --no-privileges \
  -U "${DELTA_DB_USER}" -d "${DELTA_DB_NAME}" >"${tmp_file}"; then
  die "pg_dump falhou"
fi

[[ -s "${tmp_file}" ]] || die "dump vazio; backup abortado"

if [[ -n "${AGE_RECIPIENT:-}" ]]; then
  command -v age >/dev/null 2>&1 || die "age não encontrado; instale age ou defina ALLOW_UNENCRYPTED_BACKUP=1 (inseguro)"
  final_file="${BACKUP_DIR}/${base_name}.age"
  log "cifrando com age"
  age --encrypt --recipient "${AGE_RECIPIENT}" --output "${final_file}" "${tmp_file}"
elif [[ "${ALLOW_UNENCRYPTED_BACKUP:-0}" == "1" ]]; then
  final_file="${BACKUP_DIR}/${base_name}"
  mv "${tmp_file}" "${final_file}"
  log "AVISO: backup sem cifra (ALLOW_UNENCRYPTED_BACKUP=1); use apenas em laboratório"
else
  die "AGE_RECIPIENT não definido; backup cifrado é obrigatório (ou use ALLOW_UNENCRYPTED_BACKUP=1 em laboratório)"
fi

chmod 600 "${final_file}"
sha256sum "${final_file}" >"${final_file}.sha256"

log "backup concluído: ${final_file}"

log "aplicando retenção de ${RETENTION_DAYS} dias em ${BACKUP_DIR}"
apply_backup_retention "${BACKUP_DIR}" "${RETENTION_DAYS}"

log "lembrete: faça backup separado do key ring (secrets/crypto-keys.json). Sem ele o dump não é legível."
