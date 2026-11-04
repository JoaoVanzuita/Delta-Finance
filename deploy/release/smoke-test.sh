#!/usr/bin/env bash
#
# Smoke test da instalação de release: valida o acesso HTTPS pelo IP fixo do
# host, o certificado emitido pela CA interna do Caddy e os headers de
# segurança.
#
# Rode com a stack já em execução, a partir do diretório do kit:
#   bash smoke-test.sh
#
# Requer docker compose (v2), curl e openssl. No Windows, execute no Git Bash
# ou no WSL; o equivalente em PowerShell é executar os comandos curl abaixo.
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

checks=0
failures=0

step() { printf '\n==> %s\n' "$*"; }
ok()   { checks=$((checks + 1)); printf '   [ok]     %s\n' "$*"; }
fail() { checks=$((checks + 1)); failures=$((failures + 1)); printf '   [FALHA]  %s\n' "$*" >&2; }
die()  { printf '[smoke-test] ERRO: %s\n' "$*" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }

# Lê uma variável do ambiente ou, se ausente, do .env local (sem fazer source).
read_env() {
  local key="$1" fallback="${2:-}" v
  if [[ -v "${key}" && -n "${!key}" ]]; then
    printf '%s' "${!key}"
    return
  fi
  if [[ -f .env ]]; then
    v="$(sed -n "s/^[[:space:]]*${key}=//p" .env | tail -n 1)"
    v="${v%$'\r'}"
    if [[ "${v:0:1}" == '"' || "${v:0:1}" == "'" ]]; then
      v="${v:1:${#v}-2}"
    fi
    if [[ -n "${v}" ]]; then
      printf '%s' "${v}"
      return
    fi
  fi
  printf '%s' "${fallback}"
}

have curl    || die "curl não encontrado."
have docker  || die "docker não encontrado."
have openssl || die "openssl não encontrado."
docker compose version >/dev/null 2>&1 || die "docker compose (v2) não encontrado."

[[ -f compose.yaml || -f docker-compose.yml ]] \
  || die "execute este script no diretório do kit (compose.yaml não encontrado)."

host="$(read_env DELTA_LAN_HOST '')"
http_port="$(read_env LAN_HTTP_PORT 80)"
https_port="$(read_env LAN_HTTPS_PORT 443)"

[[ -n "${host}" ]] || die "DELTA_LAN_HOST não definido no .env."

# O acesso de release usa IP fixo; um hostname aqui indica configuração errada.
if [[ ! "${host}" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
  die "DELTA_LAN_HOST='${host}' não é um IPv4; a release usa IP fixo no host."
fi

docker compose ps client >/dev/null 2>&1 \
  || die "serviço client não está rodando; execute 'docker compose up -d' antes."

ca_file="$(mktemp)"
trap 'rm -f "${ca_file}"' EXIT

step "CA interna"
docker compose cp client:/data/caddy/pki/authorities/local/root.crt "${ca_file}" \
  || die "não foi possível exportar a CA; verifique 'docker compose logs client'."
[[ -s "${ca_file}" ]] || die "CA exportada vazia."
ok "CA exportada e usada para validar a cadeia TLS (via --cacert)"

base="https://${host}:${https_port}"

step "Acesso em ${base}"

# 1) HTTP redireciona para HTTPS.
redirect="$(curl -sS -D - -o /dev/null "http://${host}:${http_port}/" || true)"
rstatus="$(printf '%s' "${redirect}" | head -n1 | awk '{print $2}')"
rloc="$(printf '%s' "${redirect}" | tr -d '\r' | awk 'tolower($1)=="location:" {print $2}')"
if [[ "${rstatus}" == "308" && "${rloc}" == "https://${host}"* ]]; then
  ok "HTTP -> HTTPS (${rstatus}) para ${rloc}"
  if [[ "${https_port}" == "443" && "${rloc}" != "https://${host}/" ]]; then
    fail "redirect não termina na porta 443: ${rloc}"
  fi
else
  fail "HTTP não redirecionou para HTTPS (status=${rstatus}, location=${rloc:-<vazio>})"
fi

# 2) Container client saudável (o /healthz é interno, na porta 8080 do container).
client_id="$(docker compose ps -q client)"
client_health="$(docker inspect --format '{{.State.Health.Status}}' "${client_id}" 2>/dev/null || true)"
if [[ "${client_health}" == "healthy" ]]; then
  ok "client saudável (healthcheck do Compose)"
else
  fail "client não está saudável (status=${client_health:-<desconhecido>})"
fi

# 3) SPA servida na raiz.
html="$(curl -fsS --cacert "${ca_file}" "${base}/" 2>/dev/null || true)"
if [[ "${html}" == *"<!doctype html"* ]]; then
  ok "SPA servida em /"
else
  fail "SPA não servida em /"
fi

# 4) Headers de segurança na resposta HTML.
headers="$(curl -sS --cacert "${ca_file}" -D - -o /dev/null "${base}/" 2>/dev/null || true)"
norm="$(printf '%s' "${headers}" | tr -d '\r')"

hsts="$(printf '%s' "${norm}" | grep -i '^Strict-Transport-Security:' | head -n1 || true)"
if [[ "${hsts}" == *"max-age=31536000"* && "${hsts}" == *"includeSubDomains"* ]]; then
  ok "HSTS max-age=31536000; includeSubDomains"
else
  fail "HSTS ausente ou incompleto"
fi

assert_header() {
  local name="$1" pattern="$2"
  if printf '%s' "${norm}" | grep -qiE "^${pattern}:"; then
    ok "header ${name}"
  else
    fail "header ${name} ausente"
  fi
}

assert_header "X-Frame-Options" "X-Frame-Options"
assert_header "X-Content-Type-Options" "X-Content-Type-Options"
assert_header "Content-Security-Policy" "Content-Security-Policy"

# 5) Certificado cobre o IP configurado.
san="$({ openssl s_client -connect "${host}:${https_port}" </dev/null 2>/dev/null \
  | openssl x509 -noout -ext subjectAltName 2>/dev/null; } || true)"
if printf '%s' "${san}" | grep -q "IP Address:${host}"; then
  ok "certificado com SAN IP Address:${host}"
else
  fail "certificado não cobre ${host} (SAN: ${san:-<vazio>})"
fi

# 6) API na mesma origem (requer server saudável); o endpoint de CSRF responde 204.
api_code="$(curl -sS --cacert "${ca_file}" -o /dev/null -w '%{http_code}' \
  "${base}/api/v1/auth/csrf" 2>/dev/null || true)"
if [[ "${api_code}" == "204" || "${api_code}" == "200" ]]; then
  ok "/api/v1/auth/csrf respondeu ${api_code}"
else
  fail "/api/v1/auth/csrf respondeu ${api_code:-<vazio>} (esperado 204)"
fi

step "Resultado"
printf '   %d verificações, %d falhas\n' "${checks}" "${failures}"
if [[ "${failures}" -eq 0 ]]; then
  printf '   [ok] SMOKE TEST OK\n'
else
  printf '   [FALHA] SMOKE TEST FALHOU\n' >&2
  exit 1
fi
