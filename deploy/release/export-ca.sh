#!/usr/bin/env bash
#
# Exporta o certificado raiz da CA interna do Caddy para instalação nos
# dispositivos da LAN.
#
# Uso, a partir do diretório do kit:
#   bash export-ca.sh
#   bash export-ca.sh meu-ca.crt
set -Eeuo pipefail

OUT="${1:-delta-finance-ca.crt}"

die() {
  printf '[export-ca] ERRO: %s\n' "$*" >&2
  exit 1
}

[[ -f compose.yaml || -f docker-compose.yml ]] \
  || die "execute este script no diretório do kit (compose.yaml não encontrado)"

docker compose ps client >/dev/null 2>&1 \
  || die "o serviço client não está disponível; execute 'docker compose up -d' primeiro"

docker compose cp client:/data/caddy/pki/authorities/local/root.crt "${OUT}" \
  || die "não foi possível ler a CA interna; verifique os logs do client"

[[ -s "${OUT}" ]] || die "o arquivo da CA foi gerado vazio"
chmod 644 "${OUT}"

printf '[export-ca] CA exportada para: %s\n' "${OUT}"
if command -v openssl >/dev/null 2>&1; then
  openssl x509 -in "${OUT}" -noout -fingerprint -sha256
else
  printf '[export-ca] SHA-256: '
  sha256sum "${OUT}" 2>/dev/null || shasum -a 256 "${OUT}"
fi
printf '[export-ca] Instale este certificado como Autoridade Certificadora de confiança\n'
printf '[export-ca] nos dispositivos que acessarão a aplicação.\n'
