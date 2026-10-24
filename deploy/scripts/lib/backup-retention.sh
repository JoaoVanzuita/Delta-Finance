#!/usr/bin/env bash
#
# Rotina de retenção de backups compartilhada por backup.sh e pelo teste.
#
# Mantém apenas arquivos de backup do Delta Finance mais novos que N dias.
# Arquivos sem relação (por exemplo, o key ring) nunca são removidos.

# apply_backup_retention <diretório> <dias>
apply_backup_retention() {
  local directory="$1"
  local days="$2"
  [[ -d "${directory}" ]] || return 0
  find "${directory}" -maxdepth 1 -type f \
    \( -name 'delta-*.dump' -o -name 'delta-*.dump.age' \
       -o -name 'delta-*.dump.sha256' -o -name 'delta-*.dump.age.sha256' \) \
    -mtime "+${days}" -print -delete >&2 || true
}
