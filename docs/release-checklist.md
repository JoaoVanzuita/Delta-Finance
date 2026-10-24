# Checklist de release

**Escopo**: T188. Congela versões e digests, registra gates e evidências antes de
publicar uma versão do MVP bancário.

## 1. Versões fixadas

| Componente | Versão | Digest (multi-arch) |
|---|---|---|
| PostgreSQL | 18.6-alpine | `sha256:898c60cb390d1085bc97bdd6a1ae968757421df394b071848b52b1c09c89d6a5` |
| Caddy (proxy e cliente) | 2.11.7-alpine | `sha256:eb4d35b7452d1ab6171fbdbe8d9275b2301dad6dad92115d91e19fb9b24d510f` |
| Maven build | 3.9.16-eclipse-temurin-25 | `sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976` |
| JRE runtime | 25.0.4.1_1-jre | `sha256:dbb43d81099eec91e0800226ed2296d03146ee7857165b95112372ad0d67e87f` |
| Node build | 24.22.0-alpine | `sha256:44fce696f38ba8c987c4db89954051000bbd05ffd8d8659ba15ed4a8602e937f` |
| Java | 25.0.4.1+1 | toolchain do CI |
| k6 | 2.3.0 | imagem oficial `grafana/k6:2.3.0` |
| ZAP | 2.16.1 | `zaproxy/zap-stable:2.16.1` |
| Trivy | ação 0.36.0 | workflow de segurança |
| Gitleaks | ação v2 | workflow de segurança |

Digests conferidos com `docker buildx imagetools inspect <imagem>` em 2026-10-24.
Ao atualizar uma imagem, atualize esta tabela e o Compose/Dockerfile juntos.

## 2. Gates automatizados

- [ ] `server/`: `./mvnw verify` verde (testes unitários + IT + Spotless +
      Checkstyle + JaCoCo).
- [ ] Cobertura de linhas no domínio ≥ 80%; `Money`, `OwnershipGuard` e
      `AuthenticatedUser` em 100%.
- [ ] `client/`: `npm run format:check`, `npm run lint`, `npm run typecheck`,
      `npm test`, `npm run build`.
- [ ] `npx playwright test` verde, incluindo `a11y.spec.ts` e
      `responsive.spec.ts`.
- [ ] `npm run openapi:lint` e tipos gerados sem diff.
- [ ] Workflow `security.yml`: gitleaks, Trivy fs e Trivy image sem
      HIGH/CRITICAL corrigíveis.
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

## 5. Empacotamento

- [ ] `docker compose config --quiet` sem erro.
- [ ] `docker compose build` reproduzível.
- [ ] `.env.example` atualizado; nenhum segredo real versionado.
- [ ] Migrações Flyway novas com versão seguinte; nenhuma migração editada.
- [ ] `application-prod.yml` sem stubs, debug ou dados de teste.
- [ ] `client/.env.production` apenas com limites versionados.
- [ ] Tag/commit assinado e changelog com riscos e limitações.

## 6. Pós-release

- [ ] Health checks verdes por 30 minutos.
- [ ] Fila de sincronização drenando; sem falhas repetidas.
- [ ] Backup pós-release concluído e checksum validado.
- [ ] Alertas de falha de backup e sincronização ativos.
- [ ] Registro de versão e digests neste arquivo atualizado.
