# Checklist de release

**Escopo**: T188. Congela versões e digests, registra gates e evidências antes de
publicar uma versão do MVP bancário.

## 1. Versões fixadas

| Componente                      | Versão                    | Digest (multi-arch)                                                       |
| ------------------------------- | ------------------------- | ------------------------------------------------------------------------- |
| PostgreSQL                      | 18.6-alpine               | `sha256:10a5c69a83035ab5239f5620d708520e8b28f5aa1f2b20143e6024532edadfb5` |
| Caddy (dentro da imagem client) | 2.11.7-alpine + rebuild   | runtime `sha256:e70b164da81ef698b3ad19af799ea6086ce6d9e48ed4079eb9fe26f6fbf14ee5`; builder `sha256:95a5e7e2692b5eb9545aadc5b90a39f6f509bee59c9508a493f83ee902eaef63` |
| Maven build                     | 3.9.16-eclipse-temurin-25 | `sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976` |
| JRE runtime                     | 25.0.4.1_1-jre            | `sha256:78f1f183913482ef390c2722f46c3f0ce76ada341d69590e7c482ec943ee72d5` |
| Node build                      | 24.22.0-alpine            | `sha256:44fce696f38ba8c987c4db89954051000bbd05ffd8d8659ba15ed4a8602e937f` |
| Java                            | 25.0.4.1+1                | toolchain do CI                                                           |
| k6                              | 2.3.0                     | imagem oficial `grafana/k6:2.3.0`                                         |
| ZAP                             | 2.16.1                    | `zaproxy/zap-stable:2.16.1`                                               |
| Trivy                           | ação v0.36.0               | workflow de segurança                                                     |
| Gitleaks                        | ação v2                   | workflow de segurança                                                     |

Digests conferidos com `docker buildx imagetools inspect <imagem>` em 2026-11-02.
Ao atualizar uma imagem, atualize esta tabela e o Compose/Dockerfile juntos.

O `client` recompila Caddy v2.11.7 com o toolchain Go 1.27.3 do builder e
atualiza `golang.org/x/net` e `go-jose` para as versões corrigidas; o runtime
também aplica `apk upgrade` para receber os pacotes Alpine de segurança. O
processo roda como usuário `caddy` (UID 10001) na porta interna 8080.

## 2. Gates automatizados

- [ ] `server/`: `./mvnw verify` verde (testes unitários + IT + Spotless +
      Checkstyle + SpotBugs + JaCoCo).
- [ ] Cobertura de linhas no domínio ≥ 80%; `Money` e `AuthenticatedUser` em
      100%; `QueryBudgetIT` verde.
- [ ] `client/`: `npm run verify` verde (typecheck, lint, testes, build e
      `openapi:check`) e `npm run format:check`.
- [ ] `npx playwright test` verde, incluindo `a11y.spec.ts` e
      `responsive.spec.ts`.
- [ ] `npm run openapi:lint` e tipos gerados sem diff.
- [ ] Workflow `security.yml`: gitleaks, Trivy fs e Trivy image sem
      HIGH/CRITICAL corrigíveis.
- [ ] `./deploy/scripts/install-git-hooks.sh` executado e `pre-push` completo
      verde antes de criar a tag.
- [ ] `deploy/scripts/tests/backup-retention-test.sh` OK.
- [ ] `BackupRestoreIT`, `KeyRotationIT`, `PasswordResetIT` verdes.
- [ ] `MigrationIT` verde em banco vazio e existente.

## 3. Gates manuais

- [ ] Gate real Meu Pluggy com duas aplicações independentes
      (`docs/runbooks/pluggy-acceptance.md`).
- [ ] Restauração real em banco limpo com key ring (`docs/runbooks/backup.md`).
- [ ] Drill de rotação de chave (`docs/runbooks/key-rotation.md`).
- [x] Leitor de tela real — **dispensado em 2026-10-24**; axe/teclado mantidos.
- [x] Protocolo SC-001 com 5 participantes — **dispensado em 2026-10-24**; roteiro técnico e tempos automatizados registrados.
- [ ] Lançamento manual em menos de 90 s por um usuário novo.

## 4. Performance (k6)

Executar com dados sintéticos de 5 usuários × 10.000 transações:

```bash
deploy/scripts/perf-seed.sh
docker run --rm --network host -i grafana/k6:2.3.0 run - < server/perf/dashboard.js
CONNECTION_ID=<uuid> docker run --rm --network host -i grafana/k6:2.3.0 run - \
  < server/perf/auth-crud-sync.js
```

Metas: dashboard/filtros p95 ≤ 2 s; CRUD/listas p95 ≤ 500 ms; login/logout
p95 ≤ 1 s; sincronização ≤ 60 s. Anexar resumo (sem dados financeiros).

## 5. Empacotamento e publicação

- [ ] Workflow `release.yml` verde para a tag `vX.Y.Z`.
- [ ] `./deploy/scripts/check-version.sh vX.Y.Z` confirma `VERSION`,
      `server/pom.xml`, `client/package.json` e tag.
- [ ] `docker compose config --quiet` (desenvolvimento) sem erro.
- [ ] `docker compose --env-file deploy/release/.env.example -f compose.release.yaml config --quiet`
      sem erro.
- [ ] Imagens `linux/amd64` publicadas no GHCR com a tag `X.Y.Z`:
      `delta-finance-server` e `delta-finance-client`.
- [ ] Pacotes do GHCR públicos, ou login com PAT documentado para os usuários.
- [ ] Compose de release usa `db`, `server` e `client`; não tem `build`, apenas
      `client` publica porta e o banco usa `delta-finance-prod_pgdata`.
- [ ] Desenvolvimento usa o projeto `delta-finance-dev` e o volume
      `delta-finance-dev_pgdata`, sem compartilhar dados com produção.
- [ ] Kit do GitHub Release publicado em `.zip` e `.tar.gz`, com checksums, e
      contém `compose.yaml`, `.env.example`, `README.md`, `VERSION`,
      `release.json` e `SHA256SUMS`.
- [ ] `.env.example` atualizado; nenhum segredo real ou key ring no kit.
- [ ] Migrações Flyway novas a partir de `V002`; nenhuma migração aplicada foi
      editada (a baseline é `V001__baseline.sql`).
- [ ] `application-prod.yml` sem stubs, debug ou dados de teste.
- [ ] `client/.env.production` apenas com limites versionados.
- [ ] Tag `vX.Y.Z` e release notes com riscos, limitações e compatibilidade de
      rollback do schema.

## 6. Promoção da RC para final

Depois dos gates manuais e da instalação real, prepare a versão final localmente:

```bash
./deploy/scripts/prepare-release.sh 0.1.0
./deploy/scripts/verify-local.sh --full
git push --no-verify origin main v0.1.0
```

O script não faz push. Use `--no-tag` se quiser revisar o commit antes de criar a
tag. O `--no-verify` evita manter a conexão SSH ociosa durante os gates; execute
o `verify-local.sh --full` imediatamente antes.

## 6. Pós-release

- [ ] Health checks verdes por 30 minutos.
- [ ] Fila de sincronização drenando; sem falhas repetidas.
- [ ] Backup pós-release concluído e checksum validado.
- [ ] Alertas de falha de backup e sincronização ativos.
- [ ] `release.json` registra commit, plataforma e digests das imagens.
- [ ] Pacotes do GHCR continuam públicos e as tags antigas foram preservadas
      para rollback.
