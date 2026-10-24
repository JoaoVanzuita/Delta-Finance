#!/usr/bin/env bash
#
# OWASP ZAP baseline (T173).
#
# Executa o scan passivo/ativo leve do ZAP contra a aplicação em execução e grava
# relatórios HTML, JSON e Markdown em docs/security/zap/ (fora do Git apenas se
# o operador optar por REPORT_DIR externo).
#
# Uso:
#   ./deploy/scripts/zap-baseline.sh                        # frontend em :4173
#   ./deploy/scripts/zap-baseline.sh https://delta.exemplo  # stack publicado
#   ./deploy/scripts/zap-baseline.sh http://127.0.0.1:8080 http://127.0.0.1:4173
#
# Requisitos: Docker. O container usa a rede do host para alcançar a aplicação
# local; em Linux isso é seguro porque o scan não publica portas.
#
# O baseline não autentica. Rotas autenticadas são cobertas pelos testes de
# segurança automatizados (SecurityHeadersIT, IsolationIT, FinalIsolationIT) e
# devem ser revisadas manualmente no relatório quando houver sessão de teste.
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
REPORT_DIR="${REPORT_DIR:-${ROOT_DIR}/docs/security/zap}"
ZAP_IMAGE="${ZAP_IMAGE:-zaproxy/zap-stable:2.16.1}"
TIMEOUT_MINUTES="${ZAP_TIMEOUT_MINUTES:-5}"

if [[ $# -eq 0 ]]; then
  set -- "http://127.0.0.1:4173"
fi

command -v docker >/dev/null 2>&1 || {
  printf '[zap] ERRO: docker não encontrado no PATH\n' >&2
  exit 1
}

mkdir -p "${REPORT_DIR}"
timestamp="$(TZ=America/Sao_Paulo date +%Y%m%dT%H%M%S)"
status=0

for target in "$@"; do
  slug="$(printf '%s' "${target}" | sed -E 's#^https?://##; s#[^A-Za-z0-9._-]+#_#g')"
  printf '[zap] baseline em %s\n' "${target}"
  if docker run --rm --network host \
    -v "${REPORT_DIR}:/zap/wrk/:rw" \
    "${ZAP_IMAGE}" \
    zap-baseline.py \
    -t "${target}" \
    -m "${TIMEOUT_MINUTES}" \
    -I \
    -r "zap-${slug}-${timestamp}.html" \
    -J "zap-${slug}-${timestamp}.json" \
    -w "zap-${slug}-${timestamp}.md"; then
    printf '[zap] sem falhas de baseline em %s\n' "${target}"
  else
    printf '[zap] alertas/falhas registrados em %s; revise o relatório\n' "${target}" >&2
    status=1
  fi
done

printf '[zap] relatórios em %s\n' "${REPORT_DIR}"
exit "${status}"
