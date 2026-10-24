#!/usr/bin/env bash
#
# Drill/execução da rotação online da chave de dados (T176).
#
# Pré-requisitos:
#   1. nova chave adicionada ao key ring em secrets/crypto-keys.json;
#   2. .env com DELTA_CRYPTO_ACTIVE_VERSION apontando para a nova versão;
#   3. chave antiga mantida no key ring até o fim da rotação;
#   4. backup verificado.
#
# O script sobe um processo efêmero do backend, executa KeyRotationJob e encerra.
# A rotação da chave de índice HMAC não é automática: exige reindexação, conforme
# docs/runbooks/key-rotation.md.
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE=(docker compose --project-directory "${ROOT_DIR}" -f "${ROOT_DIR}/compose.yaml")

confirm="${1:-}"
if [[ "${confirm}" != "--yes" ]]; then
  printf 'Uso: %s --yes\n' "$0" >&2
  printf 'Confirme que o backup foi verificado e que a nova versão está ativa no key ring.\n' >&2
  exit 2
fi

printf '[key-rotation] executando rotação com DELTA_CRYPTO_ACTIVE_VERSION=%s\n' \
  "${DELTA_CRYPTO_ACTIVE_VERSION:-1}"

"${COMPOSE[@]}" run --rm --no-deps \
  -e DELTA_KEY_ROTATION_RUN=true \
  server \
  --spring.main.web-application-type=none \
  --delta.sync.scheduler-enabled=false \
  --delta.privacy.retention.enabled=false

printf '[key-rotation] rotação concluída; remova a chave antiga do key ring somente após validar leituras.\n'
