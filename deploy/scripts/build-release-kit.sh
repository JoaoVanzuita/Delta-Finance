#!/usr/bin/env bash
#
# Monta o kit distribuído no GitHub Releases.
#
# Uso local:
#   VERSION=0.1.0 OWNER=joaovanzuita ./deploy/scripts/build-release-kit.sh
#
# No workflow de release, SERVER_DIGEST e CLIENT_DIGEST vêm do buildx.
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION="${VERSION:?defina VERSION}"
OWNER="${OWNER:?defina OWNER}"
SERVER_DIGEST="${SERVER_DIGEST:-}"
CLIENT_DIGEST="${CLIENT_DIGEST:-}"
REQUIRE_DIGESTS="${REQUIRE_DIGESTS:-0}"
OUTPUT_DIR="${OUTPUT_DIR:-${ROOT_DIR}/dist}"

die() {
  printf '[release-kit] ERRO: %s\n' "$*" >&2
  exit 1
}

command -v python3 >/dev/null 2>&1 || die "python3 não encontrado no PATH"
command -v sha256sum >/dev/null 2>&1 || die "sha256sum não encontrado no PATH"

[[ "${VERSION}" =~ ^[0-9A-Za-z._-]+$ ]] || die "VERSION inválida: ${VERSION}"
[[ "${OWNER}" =~ ^[A-Za-z0-9-]+$ ]] || die "OWNER inválido: ${OWNER}"
OWNER="${OWNER,,}"

"${ROOT_DIR}/deploy/scripts/check-version.sh" "v${VERSION}"

if [[ "${REQUIRE_DIGESTS}" == "1" ]]; then
  [[ -n "${SERVER_DIGEST}" ]] || die "SERVER_DIGEST não definido"
  [[ -n "${CLIENT_DIGEST}" ]] || die "CLIENT_DIGEST não definido"
fi

server_image="ghcr.io/${OWNER}/delta-finance-server:${VERSION}"
client_image="ghcr.io/${OWNER}/delta-finance-client:${VERSION}"
if [[ -n "${SERVER_DIGEST}" ]]; then
  server_image="ghcr.io/${OWNER}/delta-finance-server@${SERVER_DIGEST}"
fi
if [[ -n "${CLIENT_DIGEST}" ]]; then
  client_image="ghcr.io/${OWNER}/delta-finance-client@${CLIENT_DIGEST}"
fi

staging_name="delta-finance-${VERSION}"
staging="${OUTPUT_DIR}/${staging_name}"
archive_zip="${staging_name}-amd64.zip"
archive_tar="${staging_name}-amd64.tar.gz"

rm -rf "${staging}"
mkdir -p "${staging}"

cp "${ROOT_DIR}/compose.release.yaml" "${staging}/compose.yaml"
cp "${ROOT_DIR}/deploy/release/.env.example" "${staging}/.env.example"
cp "${ROOT_DIR}/deploy/release/README.md" "${staging}/README.md"
cp "${ROOT_DIR}/deploy/release/export-ca.sh" "${staging}/export-ca.sh"
cp "${ROOT_DIR}/VERSION" "${staging}/VERSION"

sed -i "s/^DELTA_VERSION=.*/DELTA_VERSION=${VERSION}/" "${staging}/.env.example"
sed -i "s/^DELTA_IMAGE_OWNER=.*/DELTA_IMAGE_OWNER=${OWNER}/" "${staging}/.env.example"

cat >"${staging}/release.json" <<EOF
{
  "version": "${VERSION}",
  "commit": "$(git -C "${ROOT_DIR}" rev-parse HEAD)",
  "createdAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "platform": "linux/amd64",
  "images": {
    "server": "${server_image}",
    "client": "${client_image}",
    "postgres": "postgres:18.6-alpine@sha256:10a5c69a83035ab5239f5620d708520e8b28f5aa1f2b20143e6024532edadfb5"
  }
}
EOF

(
  cd "${staging}"
  sha256sum compose.yaml .env.example README.md export-ca.sh VERSION release.json >SHA256SUMS
)

rm -f "${OUTPUT_DIR}/${archive_zip}" "${OUTPUT_DIR}/${archive_tar}" \
  "${OUTPUT_DIR}/${archive_zip}.sha256" "${OUTPUT_DIR}/${archive_tar}.sha256"
zip_path="${OUTPUT_DIR}/${archive_zip}"
python3 - "${OUTPUT_DIR}" "${staging_name}" "${zip_path}" <<'PY'
import os
import sys
import zipfile

output_dir, staging_name, archive_path = sys.argv[1:4]
with zipfile.ZipFile(archive_path, "w", zipfile.ZIP_DEFLATED) as archive:
    for root, directories, files in os.walk(os.path.join(output_dir, staging_name)):
        directories.sort()
        files.sort()
        for name in files:
            full_path = os.path.join(root, name)
            archive.write(full_path, os.path.relpath(full_path, output_dir))
PY

tar -czf "${OUTPUT_DIR}/${archive_tar}" -C "${OUTPUT_DIR}" "${staging_name}"

(
  cd "${OUTPUT_DIR}"
  sha256sum "${archive_zip}" >"${archive_zip}.sha256"
  sha256sum "${archive_tar}" >"${archive_tar}.sha256"
)

printf '[release-kit] %s\n' "${OUTPUT_DIR}/${archive_zip}"
printf '[release-kit] %s\n' "${OUTPUT_DIR}/${archive_tar}"
printf '[release-kit] checksums .sha256 gerados para ambos os formatos\n'
