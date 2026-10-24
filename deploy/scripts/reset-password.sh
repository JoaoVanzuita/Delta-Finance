#!/usr/bin/env bash
#
# Recuperação assistida de senha (T197/FR-139).
#
# Uso:
#   ./deploy/scripts/reset-password.sh --username alice.familia --operator "Suporte Delta"
#
# O operador deve verificar a identidade do titular por um canal independente
# (por exemplo, vídeo ou presencial) antes de executar. O script:
#   - exige DELTA_OPERATOR_RESET_TOKEN no ambiente/.env;
#   - pede confirmação digitando o nome de usuário;
#   - roda um processo efêmero do backend, revoga sessões e imprime a senha
#     temporária uma única vez;
#   - registra PASSWORD_RESET_ASSISTED sanitizado na auditoria.
#
# Nunca cole a senha temporária em ticket, chat ou issue. Entregue por canal
# seguro e oriente a troca imediata.
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env}"
COMPOSE=(docker compose --project-directory "${ROOT_DIR}" -f "${ROOT_DIR}/compose.yaml")

log() { printf '[reset-password] %s\n' "$*" >&2; }
die() { printf '[reset-password] ERRO: %s\n' "$*" >&2; exit 1; }

username=""
operator="${USER:-operator}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --username) username="${2:-}"; shift 2 ;;
    --operator) operator="${2:-}"; shift 2 ;;
    -h|--help)
      printf 'Uso: %s --username <nome> [--operator <nome>]\n' "$0" >&2
      exit 0
      ;;
    *) die "argumento desconhecido: $1" ;;
  esac
done

[[ -n "${username}" ]] || die "informe --username"
[[ -f "${ENV_FILE}" ]] || die "arquivo de ambiente não encontrado: ${ENV_FILE}"

set -a
# shellcheck disable=SC1090
. "${ENV_FILE}"
set +a

: "${DELTA_OPERATOR_RESET_TOKEN:?DELTA_OPERATOR_RESET_TOKEN não definido; recuperação assistida desabilitada}"

log "Verifique a identidade do titular por canal independente antes de continuar."
log "Conta alvo: ${username} | operador: ${operator}"
read -r -p "Digite o nome de usuário novamente para confirmar: " confirmation
[[ "${confirmation}" == "${username}" ]] || die "confirmação não corresponde ao usuário"

"${COMPOSE[@]}" run --rm --no-deps \
  -e DELTA_OPERATOR_RESET_TARGET="${username}" \
  -e DELTA_OPERATOR_RESET_TOKEN="${DELTA_OPERATOR_RESET_TOKEN}" \
  -e DELTA_OPERATOR_RESET_OPERATOR="${operator}" \
  server \
  --spring.main.web-application-type=none \
  --delta.sync.scheduler-enabled=false \
  --delta.privacy.retention.enabled=false

log "Operação concluída. Confirme com o titular o acesso e a troca de senha."
