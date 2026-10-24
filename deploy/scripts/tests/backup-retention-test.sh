#!/usr/bin/env bash
#
# Teste da retenção de backups (T175): garante que arquivos com mais de 30 dias
# são removidos e que backups válidos e arquivos alheios (key ring) permanecem.
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/backup-retention.sh
. "${SCRIPT_DIR}/../lib/backup-retention.sh"

workdir="$(mktemp -d)"
trap 'rm -rf "${workdir}"' EXIT

new_dump="${workdir}/delta-finance-$(date +%Y%m%d).dump.age"
old_dump="${workdir}/delta-finance-old.dump.age"
old_checksum="${old_dump}.sha256"
new_checksum="${new_dump}.sha256"
keyring="${workdir}/crypto-keys.json"

touch "${new_dump}" "${new_checksum}" "${keyring}"
touch "${old_dump}" "${old_checksum}"
# 31 dias no passado; o limite é estritamente maior que 30.
touch -d '31 days ago' "${old_dump}" "${old_checksum}"

apply_backup_retention "${workdir}" 30 2>/dev/null

[[ -f "${new_dump}" ]] || { echo 'FALHA: backup novo removido' >&2; exit 1; }
[[ -f "${new_checksum}" ]] || { echo 'FALHA: checksum novo removido' >&2; exit 1; }
[[ ! -f "${old_dump}" ]] || { echo 'FALHA: backup antigo preservado' >&2; exit 1; }
[[ ! -f "${old_checksum}" ]] || { echo 'FALHA: checksum antigo preservado' >&2; exit 1; }
[[ -f "${keyring}" ]] || { echo 'FALHA: arquivo alheio removido' >&2; exit 1; }

echo 'OK: retenção de 30 dias removeu expirados e preservou vigentes e key ring.'
