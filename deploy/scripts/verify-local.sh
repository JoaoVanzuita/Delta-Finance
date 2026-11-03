#!/usr/bin/env bash
#
# Validações locais que espelham os jobs do GitHub Actions.
#
# Uso:
#   ./deploy/scripts/verify-local.sh --quick
#   ./deploy/scripts/verify-local.sh --full
#
# O modo --quick é usado em branches comuns. O modo --full é usado pelo
# pre-push na main e em tags, e também pode ser forçado com DELTA_PREPUSH_FULL=1.
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
MODE="${1:---quick}"

TRIVY_IMAGE="${TRIVY_IMAGE:-aquasec/trivy:0.70.0}"
TRIVY_CACHE_VOLUME="${TRIVY_CACHE_VOLUME:-delta-finance-trivy-cache}"
GITLEAKS_IMAGE="${GITLEAKS_IMAGE:-ghcr.io/gitleaks/gitleaks:v8.24.3}"
ACTIONLINT_IMAGE="${ACTIONLINT_IMAGE:-rhysd/actionlint:1.7.10}"

step() { printf '\n==> %s\n' "$*"; }
warn() { printf '[aviso] %s\n' "$*" >&2; }
die() { printf '[erro] %s\n' "$*" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }
docker_ready() { have docker && docker info >/dev/null 2>&1; }

trap 'printf "\n[verify-local] Falhou na etapa anterior.\n" >&2' ERR

case "${MODE}" in
  --quick | --full) ;;
  -h | --help)
    printf 'Uso: %s [--quick|--full]\n' "$0"
    exit 0
    ;;
  *)
    die "modo inválido: ${MODE}. Use --quick ou --full."
    ;;
esac

require_command() {
  have "$1" || die "comando obrigatório não encontrado: $1"
}

run_version() {
  step "Versões alinhadas (VERSION, Maven e npm)"
  "${ROOT_DIR}/deploy/scripts/check-version.sh"
}

run_whitespace() {
  step "Espaços em branco"
  git -C "${ROOT_DIR}" diff --check
  git -C "${ROOT_DIR}" diff --cached --check
}

run_frontend_format() {
  if [[ ! -d "${ROOT_DIR}/client/node_modules" ]]; then
    warn "client/node_modules ausente; formatação do frontend não foi validada."
    return 0
  fi
  step "Formatação do frontend"
  (
    cd "${ROOT_DIR}/client"
    npm run format:check
  )
}

run_compose() {
  if ! have docker; then
    warn "docker não encontrado; configuração do Compose não foi validada."
    return 0
  fi
  step "Compose de desenvolvimento"
  docker compose --env-file "${ROOT_DIR}/.env.example" -f "${ROOT_DIR}/compose.yaml" config --quiet

  step "Compose de release"
  docker compose --env-file "${ROOT_DIR}/deploy/release/.env.example" \
    -f "${ROOT_DIR}/compose.release.yaml" config --quiet
}

run_gitleaks() {
  step "Gitleaks (histórico completo)"
  if have gitleaks; then
    gitleaks detect --source "${ROOT_DIR}" --config "${ROOT_DIR}/.gitleaks.toml" \
      --redact --no-banner
    return 0
  fi
  if docker_ready; then
    docker run --rm -v "${ROOT_DIR}:/repo" -w /repo "${GITLEAKS_IMAGE}" \
      detect --source /repo --config /repo/.gitleaks.toml --redact --no-banner
    return 0
  fi
  warn "gitleaks e Docker indisponíveis; scan de segredos não foi executado."
}

run_actionlint() {
  local allow_docker="${1:-0}"
  step "Workflows do GitHub Actions"
  if have actionlint; then
    actionlint "${ROOT_DIR}"/.github/workflows/*.yml
    return 0
  fi
  if [[ "${allow_docker}" == "1" ]] && docker_ready; then
    docker run --rm -v "${ROOT_DIR}:/repo" -w /repo "${ACTIONLINT_IMAGE}" \
      -color .github/workflows/*.yml
    return 0
  fi
  warn "actionlint não encontrado; validação dos workflows não foi executada."
}

run_backend() {
  require_command java
  step "Backend (./mvnw verify, TZ=UTC)"
  (
    cd "${ROOT_DIR}/server"
    # O runner do GitHub usa UTC; execute assim para expor dependências do
    # fuso local que não apareceriam no CI.
    TZ=UTC ./mvnw -B --no-transfer-progress verify
  )
}

run_frontend() {
  require_command npm
  if [[ ! -d "${ROOT_DIR}/client/node_modules" ]]; then
    step "Frontend (npm ci)"
    (
      cd "${ROOT_DIR}/client"
      npm ci --no-audit --no-fund
    )
  fi

  step "Frontend (format:check)"
  (
    cd "${ROOT_DIR}/client"
    npm run format:check
  )

  step "Frontend (npm run verify)"
  (
    cd "${ROOT_DIR}/client"
    npm run verify
  )
}

run_e2e() {
  require_command npm
  step "E2E (Playwright)"
  (
    cd "${ROOT_DIR}/client"
    npx playwright install chromium
    npm run test:e2e
  )
}

run_trivy_filesystem() {
  step "Trivy filesystem (vulnerabilidades, segredos e misconfigurações)"
  docker run --rm \
    -v "${ROOT_DIR}:/repo" \
    -w /repo \
    -v "${TRIVY_CACHE_VOLUME}:/root/.cache/trivy" \
    "${TRIVY_IMAGE}" fs \
    --scanners vuln,secret,misconfig \
    --severity HIGH,CRITICAL \
    --ignore-unfixed \
    --exit-code 1 \
    --skip-dirs .git \
    --skip-dirs backups \
    --skip-dirs client/dist \
    --skip-dirs client/node_modules \
    --skip-dirs client/playwright-report \
    --skip-dirs client/screenshots-output \
    --skip-dirs client/test-results \
    --skip-dirs dist \
    --skip-dirs secrets \
    --skip-dirs server/target \
    --skip-files .env \
    --skip-files client/screenshots-output.zip \
    --skip-files server/src/test/resources/crypto-keys-test.json \
    .
}

run_trivy_images() {
  step "Build da imagem do backend"
  docker build --pull -t delta-finance-dev/server:prepush "${ROOT_DIR}/server"

  step "Build da imagem do frontend"
  docker build --pull -t delta-finance-dev/client:prepush \
    -f "${ROOT_DIR}/client/Dockerfile" "${ROOT_DIR}"

  local image
  for image in delta-finance-dev/server:prepush delta-finance-dev/client:prepush; do
    step "Trivy image ${image}"
    docker run --rm \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v "${TRIVY_CACHE_VOLUME}:/root/.cache/trivy" \
      "${TRIVY_IMAGE}" image \
      --severity HIGH,CRITICAL \
      --ignore-unfixed \
      --exit-code 1 \
      "${image}"
  done
}

run_quick() {
  local allow_docker_actionlint="${1:-0}"
  run_version
  run_whitespace
  run_frontend_format
  run_compose
  run_gitleaks
  run_actionlint "${allow_docker_actionlint}"
}

run_full() {
  require_command docker
  require_command git
  require_command python3
  docker_ready || die "Docker não está em execução; ele é necessário para os testes de integração, Trivy e imagens."

  run_quick 1
  run_backend
  run_frontend
  run_e2e
  run_trivy_filesystem
  run_trivy_images
}

printf '[verify-local] modo %s\n' "${MODE#--}"
if [[ "${MODE}" == "--full" ]]; then
  run_full
else
  run_quick
fi
printf '\n[verify-local] Todos os gates locais passaram.\n'
