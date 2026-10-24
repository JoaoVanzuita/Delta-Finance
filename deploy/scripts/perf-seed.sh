#!/usr/bin/env bash
#
# Semeia o cenário de referência de performance (T174/T198).
#
# Executa o semeador Java contra um banco de desenvolvimento já migrado. Nunca
# use contra produção: o usuário de performance é recriado do zero.
#
# Uso:
#   DELTA_DB_URL=jdbc:postgresql://localhost:5432/delta_finance \
#   DELTA_DB_USER=delta DELTA_DB_PASSWORD=delta \
#   DELTA_CRYPTO_KEY_FILE=./secrets/crypto-keys.json \
#   ./deploy/scripts/perf-seed.sh
#
# Variáveis opcionais: PERF_USER, PERF_PASSWORD, PERF_TRANSACTIONS (10000).
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
SERVER_DIR="${ROOT_DIR}/server"

: "${DELTA_DB_URL:?DELTA_DB_URL é obrigatório}"
: "${DELTA_DB_USER:?DELTA_DB_USER é obrigatório}"
: "${DELTA_DB_PASSWORD:?DELTA_DB_PASSWORD é obrigatório}"
: "${DELTA_CRYPTO_KEY_FILE:?DELTA_CRYPTO_KEY_FILE é obrigatório}"

command -v java >/dev/null 2>&1 || {
  printf '[perf-seed] ERRO: java não encontrado no PATH\n' >&2
  exit 1
}

cd "${SERVER_DIR}"
./mvnw -q test-compile
./mvnw -q -DincludeScope=test dependency:build-classpath -Dmdep.outputFile=target/perf-classpath.txt

classpath="target/test-classes:target/classes:$(cat target/perf-classpath.txt)"
exec java -cp "${classpath}" com.deltafinance.perf.PerfDataSeeder
