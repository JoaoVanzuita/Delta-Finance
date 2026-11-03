#!/usr/bin/env bash
#
# Instala os hooks locais do repositório.
#
# Uso:
#   ./deploy/scripts/install-git-hooks.sh
#   ./deploy/scripts/install-git-hooks.sh --check
#   ./deploy/scripts/install-git-hooks.sh --uninstall
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

HOOKS_DIR=".githooks"
usage() {
  printf 'Uso: %s [--check|--uninstall]\n' "$0"
}

current_hooks_path() {
  git config --local --get core.hooksPath || true
}

install_hooks() {
  local current
  current="$(current_hooks_path)"
  if [[ -n "${current}" && "${current}" != "${HOOKS_DIR}" ]]; then
    printf '[hooks] Substituindo core.hooksPath=%s por %s\n' "${current}" "${HOOKS_DIR}"
  fi

  git config --local core.hooksPath "${HOOKS_DIR}"
  chmod +x "${HOOKS_DIR}/pre-commit" "${HOOKS_DIR}/pre-push"
  chmod +x deploy/scripts/install-git-hooks.sh deploy/scripts/verify-local.sh

  printf '[hooks] Instalados em %s\n' "${HOOKS_DIR}"
  printf '[hooks] pre-commit: segredos, whitespace, versão e formatação do que está staged.\n'
  printf '[hooks] pre-push: validação rápida; gates completos em main e tags v*.\n'
  printf '[hooks] Para pular uma vez: git commit --no-verify / git push --no-verify\n'
}

check_hooks() {
  local current
  current="$(current_hooks_path)"
  if [[ "${current}" == "${HOOKS_DIR}" ]]; then
    printf '[hooks] OK: core.hooksPath=%s\n' "${current}"
    return 0
  fi
  printf '[hooks] Não instalados (core.hooksPath=%s)\n' "${current:-<vazio>}"
  return 1
}

uninstall_hooks() {
  local current
  current="$(current_hooks_path)"
  if [[ "${current}" == "${HOOKS_DIR}" ]]; then
    git config --local --unset core.hooksPath
    printf '[hooks] core.hooksPath removido.\n'
    return 0
  fi
  printf '[hooks] Nada a remover (core.hooksPath=%s)\n' "${current:-<vazio>}"
}

case "${1:-install}" in
  install) install_hooks ;;
  --check | check) check_hooks ;;
  --uninstall | uninstall) uninstall_hooks ;;
  -h | --help) usage ;;
  *)
    usage >&2
    exit 2
    ;;
esac
