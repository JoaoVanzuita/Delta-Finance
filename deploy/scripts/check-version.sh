#!/usr/bin/env bash
#
# Verifica se VERSION, pom.xml, package.json e, opcionalmente, a tag Git usam a
# mesma versão. Uso:
#   ./deploy/scripts/check-version.sh
#   ./deploy/scripts/check-version.sh v0.1.0
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION_FILE="${ROOT_DIR}/VERSION"
POM_FILE="${ROOT_DIR}/server/pom.xml"
PACKAGE_FILE="${ROOT_DIR}/client/package.json"

die() {
  printf '[version] ERRO: %s\n' "$*" >&2
  exit 1
}

command -v python3 >/dev/null 2>&1 || die "python3 não encontrado no PATH"
[[ -f "${VERSION_FILE}" ]] || die "arquivo VERSION não encontrado"
[[ -f "${POM_FILE}" ]] || die "server/pom.xml não encontrado"
[[ -f "${PACKAGE_FILE}" ]] || die "client/package.json não encontrado"

version="$(tr -d '[:space:]' <"${VERSION_FILE}")"
[[ -n "${version}" ]] || die "VERSION está vazio"

pom_version="$(
  python3 - "${POM_FILE}" <<'PY'
import sys
import xml.etree.ElementTree as ET

namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse(sys.argv[1]).getroot()
value = root.findtext("m:version", namespaces=namespace)
if value is None:
    raise SystemExit("versão do projeto não encontrada no pom.xml")
print(value.strip())
PY
)"

package_version="$(
  python3 - "${PACKAGE_FILE}" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    print(json.load(handle)["version"])
PY
)"

[[ "${pom_version}" == "${version}" ]] \
  || die "server/pom.xml=${pom_version}, VERSION=${version}"
[[ "${package_version}" == "${version}" ]] \
  || die "client/package.json=${package_version}, VERSION=${version}"

if [[ $# -gt 0 ]]; then
  tag="${1#v}"
  [[ "${tag}" == "${version}" ]] \
    || die "tag=${tag}, VERSION=${version}"
fi

printf '[version] alinhada: %s\n' "${version}"
