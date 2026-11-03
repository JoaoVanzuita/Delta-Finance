#!/usr/bin/env bash
#
# Prepara uma versão de release localmente, sem fazer push.
#
# Uso:
#   ./deploy/scripts/prepare-release.sh 0.1.0
#   ./deploy/scripts/prepare-release.sh 0.1.0 --no-tag
#   ./deploy/scripts/prepare-release.sh 0.1.0 --dry-run
#
# O script:
#   1. exige árvore limpa e branch main;
#   2. alinha VERSION, server/pom.xml, client/package.json e package-lock.json;
#   3. valida as versões;
#   4. cria um commit de release;
#   5. cria a tag anotada vX.Y.Z (salvo --no-tag).
#
# Ele nunca executa git push.
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

VERSION_ARG=""
DRY_RUN=0
CREATE_TAG=1

usage() {
  printf 'Uso: %s <versao> [--dry-run] [--no-tag]\n' "$0"
}

die() {
  printf '[prepare-release] ERRO: %s\n' "$*" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=1 ;;
    --no-tag) CREATE_TAG=0 ;;
    -h | --help)
      usage
      exit 0
      ;;
    -*)
      usage >&2
      exit 2
      ;;
    *)
      [[ -z "${VERSION_ARG}" ]] || die "versão informada mais de uma vez"
      VERSION_ARG="${1#v}"
      ;;
  esac
  shift
done

[[ -n "${VERSION_ARG}" ]] || {
  usage >&2
  exit 2
}
[[ "${VERSION_ARG}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]] \
  || die "versão inválida: ${VERSION_ARG}"

[[ -z "$(git status --porcelain)" ]] \
  || die "a árvore de trabalho precisa estar limpa antes de preparar a release"

current_branch="$(git symbolic-ref --quiet --short HEAD || true)"
[[ "${current_branch}" == "main" ]] \
  || die "a release deve ser preparada na branch main (branch atual: ${current_branch:-detached})"

tag="v${VERSION_ARG}"
git rev-parse --verify --quiet "refs/tags/${tag}" >/dev/null \
  && die "a tag ${tag} já existe"

printf '[prepare-release] versão: %s\n' "${VERSION_ARG}"
printf '[prepare-release] commit: %s\n' "$(git rev-parse --short HEAD)"
printf '[prepare-release] tag:    %s\n' "${tag}"

if [[ "${DRY_RUN}" -eq 1 ]]; then
  printf '[prepare-release] dry-run: nenhuma alteração será feita.\n'
  printf '[prepare-release] arquivos que seriam atualizados: VERSION, server/pom.xml,\n'
  printf '  client/package.json, client/package-lock.json\n'
  exit 0
fi

printf '%s\n' "${VERSION_ARG}" >VERSION

python3 - "${ROOT_DIR}/server/pom.xml" "${VERSION_ARG}" <<'PY'
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
version = sys.argv[2]
text = path.read_text(encoding="utf-8")
pattern = re.compile(
    r"(<artifactId>delta-finance-server</artifactId>\s*<version>)([^<]+)(</version>)"
)
matches = pattern.findall(text)
if len(matches) != 1:
    raise SystemExit("não foi possível localizar a versão do projeto no pom.xml")
updated = pattern.sub(lambda match: f"{match.group(1)}{version}{match.group(3)}", text)
path.write_text(updated, encoding="utf-8")
PY

python3 - "${ROOT_DIR}/client" "${VERSION_ARG}" <<'PY'
import json
import sys
from pathlib import Path

client_dir = Path(sys.argv[1])
version = sys.argv[2]

for name in ("package.json", "package-lock.json"):
    path = client_dir / name
    data = json.loads(path.read_text(encoding="utf-8"))
    data["version"] = version
    if name == "package-lock.json" and "" in data.get("packages", {}):
        data["packages"][""]["version"] = version
    path.write_text(
        json.dumps(data, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
PY

"${ROOT_DIR}/deploy/scripts/check-version.sh" "${tag}"

git add VERSION server/pom.xml client/package.json client/package-lock.json
git diff --cached --check
git diff --cached --stat

git commit -m "chore(release): prepare ${VERSION_ARG}"

if [[ "${CREATE_TAG}" -eq 1 ]]; then
  git tag -a "${tag}" -m "Delta Finance ${VERSION_ARG}"
  printf '[prepare-release] tag criada: %s\n' "${tag}"
else
  printf '[prepare-release] tag não criada (--no-tag).\n'
fi

printf '\n[prepare-release] Nenhum push foi feito.\n'
printf '[prepare-release] Próximos passos:\n'
printf '  ./deploy/scripts/verify-local.sh --full\n'
if [[ "${CREATE_TAG}" -eq 1 ]]; then
  printf '  git push --no-verify origin main %s\n' "${tag}"
else
  printf '  git tag -a %s -m "Delta Finance %s"\n' "${tag}" "${VERSION_ARG}"
  printf '  git push --no-verify origin main %s\n' "${tag}"
fi
