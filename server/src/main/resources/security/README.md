# Lista de senhas comuns (v1)

Arquivo: `common-passwords-v1.txt`

- **Origem**: SecLists — `Passwords/Common-Credentials/10k-most-common.txt`
  (<https://github.com/danielmiessler/SecLists>), licença MIT.
- **Data de incorporação**: 2026-10-22.
- **Entradas**: 10.001 linhas (1 senha por linha; sem comentários).
- **SHA-256**: `68782d6a4a19a4768d5f15dd66bd534e7a33055cc755411e33f16d18c50fdcce`
- **Normalização na carga**: `strip` + minúsculas; linhas vazias e iniciadas por `#` são
  ignoradas.

A política de senha exige no mínimo 10.000 entradas e falha no start se a lista estiver menor.
Atualizações criam um novo arquivo versionado (`common-passwords-v2.txt`) e um novo
`DEFAULT_VERSION`, mantendo o histórico para auditoria. Nenhuma chamada externa é feita em runtime.
