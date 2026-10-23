#!/usr/bin/env bash
#
# Restauração de backup do Delta Finance em um banco limpo.
#
# Ação destrutiva: substitui o conteúdo atual do banco. Exige confirmação
# explícita (--yes) e para o serviço `server` durante a restauração para evitar
# escritas concorrentes.
#
# Uso:
#   ./deploy/scripts/restore.sh --file backups/delta-....dump.age --yes
#   AGE_IDENTITY_FILE=~/age-key.txt ./deploy/scripts/restore.sh --latest --yes
#
# Variáveis:
#   AGE_IDENTITY_FILE   chave privada age (obrigatória para arquivos .age)
#   ENV_FILE            arquivo de ambiente (padrão <repo>/.env)
#   DELTA_DB_*          credenciais/banco de destino
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env}"
BACKUP_DIR="${BACKUP_DIR:-${ROOT_DIR}/backups}"
COMPOSE=(docker compose --project-directory "${ROOT_DIR}" -f "${ROOT_DIR}/compose.yaml")

log() { printf '[restore] %s\n' "$*" >&2; }
die() { printf '[restore] ERRO: %s\n' "$*" >&2; exit 1; }

usage() {
  cat >&2 <<'USAGE'
Uso: restore.sh (--file <backup> | --latest) --yes

  --file <caminho>   dump (.dump) ou dump cifrado (.dump.age)
  --latest           usa o backup mais recente em BACKUP_DIR
  --yes              confirma a substituição do banco atual
USAGE
  exit 2
}

backup_file=""
confirmed=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --file) backup_file="${2:-}"; shift 2 ;;
    --latest) backup_file="__latest__"; shift ;;
    --yes) confirmed=1; shift ;;
    -h|--help) usage ;;
    *) die "argumento desconhecido: $1" ;;
  esac
done

[[ "${confirmed}" == "1" ]] || die "confirmação ausente; repita com --yes após validar o destino"
[[ -n "${backup_file}" ]] || usage
[[ -f "${ENV_FILE}" ]] || die "arquivo de ambiente não encontrado: ${ENV_FILE}"

set -a
# shellcheck disable=SC1090
. "${ENV_FILE}"
set +a
: "${DELTA_DB_NAME:?DELTA_DB_NAME não definido}"
: "${DELTA_DB_USER:?DELTA_DB_USER não definido}"

if [[ "${backup_file}" == "__latest__" ]]; then
  backup_file="$(find "${BACKUP_DIR}" -maxdepth 1 -type f \( -name '*.dump' -o -name '*.dump.age' \) -printf '%T@ %p\n' 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2-)"
  [[ -n "${backup_file}" ]] || die "nenhum backup encontrado em ${BACKUP_DIR}"
fi
[[ -f "${backup_file}" ]] || die "backup não encontrado: ${backup_file}"

if [[ -f "${backup_file}.sha256" ]]; then
  log "verificando checksum"
  (cd -- "$(dirname -- "${backup_file}")" && sha256sum --check --status "$(basename -- "${backup_file}").sha256") \
    || die "checksum inválido"
fi

tmp_file=""
cleanup() { [[ -n "${tmp_file}" ]] && rm -f "${tmp_file}"; }
trap cleanup EXIT

if [[ "${backup_file}" == *.age ]]; then
  : "${AGE_IDENTITY_FILE:?AGE_IDENTITY_FILE é obrigatório para backups cifrados}"
  command -v age >/dev/null 2>&1 || die "age não encontrado"
  tmp_file="$(mktemp)"
  log "decifrando backup"
  age --decrypt --identity "${AGE_IDENTITY_FILE}" --output "${tmp_file}" "${backup_file}"
  restore_input="${tmp_file}"
else
  restore_input="${backup_file}"
fi

log "parando o serviço server para evitar escritas concorrentes"
"${COMPOSE[@]}" stop server >/dev/null 2>&1 || true

log "restaurando ${backup_file} em ${DELTA_DB_NAME} (--clean --if-exists)"
if ! "${COMPOSE[@]}" exec -T db \
  pg_restore --clean --if-exists --no-owner --no-privileges \
  -U "${DELTA_DB_USER}" -d "${DELTA_DB_NAME}" <"${restore_input}"; then
  log "pg_restore retornou erro; verifique os avisos acima antes de reiniciar o server"
  exit 1
fi

log "validando schema restaurado"
history_exists="$("${COMPOSE[@]}" exec -T db \
  psql -U "${DELTA_DB_USER}" -d "${DELTA_DB_NAME}" -tAc \
  "select to_regclass('public.flyway_schema_history') is not null" 2>/dev/null | tr -d '[:space:]')"
if [[ "${history_exists}" == "t" ]]; then
  "${COMPOSE[@]}" exec -T db \
    psql -U "${DELTA_DB_USER}" -d "${DELTA_DB_NAME}" -tAc \
    "select max(version) from flyway_schema_history" >&2 || true
else
  log "histórico Flyway ausente no backup (banco sem migrações aplicadas)"
fi

log "reiniciando o serviço server"
"${COMPOSE[@]}" start server >/dev/null

log "restauração concluída. Verifique /actuator/health/readiness e os logs."
log "o key ring deve ser o mesmo usado quando o backup foi criado; caso contrário os campos cifrados não serão legíveis."
