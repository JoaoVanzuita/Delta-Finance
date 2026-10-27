# Fase 0 — Baseline e Inventário

**Status:** Concluída em 2026-10-25  
**Atualização:** os itens `C01`, `C02`, `C03`, `C04`, `C25` e `C26` foram
concluídos na Fase 1; a matriz abaixo é a fotografia histórica da Fase 0.

**Base:** `main` em `775e1baea91b99c3d5ab0741c07ffe58a600e773` + correções D-01 a D-20 não commitadas  
**Plano:** [`plano-implementacao-correcoes.md`](plano-implementacao-correcoes.md)  
**Branch de referência congelada:** `refactor-speckit-converge` em
`cbf5295dc14e02517ee8256b67bac733a88ce321`

> Esta fase não altera comportamento da aplicação. Ela registra a fotografia do
> código antes das correções, preserva a working tree, reserva as migrações e
> formaliza as decisões que bloqueiam os próximos itens.

## 1. Preservação da working tree

Nenhum commit foi criado na `main`. A working tree foi preservada em três
camadas independentes:

| Camada | Conteúdo | Identificador |
|---|---|---|
| Ref Git de backup | Alterações em arquivos rastreados | `refs/backup/phase0-baseline` = `d04b3c8da4a0daa704bf22a3c34d859163c2ecd8` |
| Patch | Diff completo dos arquivos rastreados | `/tmp/opencode/phase0-baseline/tracked.patch` |
| Arquivo | Working tree completa, exceto `.git`, `node_modules`, `target` e `dist` | `/tmp/opencode/phase0-baseline/worktree.tar.gz` |

Checksums dos artefatos externos:

```text
6028b3e4531c49e3304142a2e19e918b1bc5e0f7e119b44a9e51b016e9cfe587  tracked.patch
c03a1783f050652164a41f255fc96531e8d396860fef53fb3f5e641dcee6a587  worktree.tar.gz
9421b83d8c11073ba5d227225c613c1929868879c408e65441d48ae06c98781a  status.txt
```

Recuperação dos arquivos rastreados:

```bash
# Inspecionar
git show refs/backup/phase0-baseline

# Recuperar sobre uma cópia limpa da main
git diff main refs/backup/phase0-baseline | git apply
```

Recuperação dos arquivos não rastreados:

```bash
tar -xzf /tmp/opencode/phase0-baseline/worktree.tar.gz -C <destino>
```

**Ressalva:** `/tmp` é temporário. O ref Git e o patch são a referência primária;
o tar é uma cópia adicional. Antes de iniciar a Fase 1, recomenda-se criar um
commit ou branch de trabalho, conforme a decisão do responsável.

Estado no momento da preservação: **92 entradas** entre arquivos modificados e
não rastreados, incluindo `docs/plano-implementacao-correcoes.md`, as migrações
`V014`/`V015` e as correções D-01 a D-20.

## 2. Baseline executado

### 2.1 Backend

```bash
cd server
./mvnw verify
```

| Item | Resultado |
|---|---|
| Testes | **143** executados, 0 falhas, 0 erros, 0 ignorados |
| Build | `BUILD SUCCESS` |
| Duração | 2 min 09 s |
| JaCoCo | Todos os limites de cobertura atendidos |
| Spotless | 386 arquivos Java limpos |
| Checkstyle | 0 violações |
| `MigrationIT` | 4 testes verdes (banco vazio, reaplicação, snapshot e migração quebrada) |

Arquivo de evidência: `/tmp/opencode/phase0-baseline/backend-verify.log`.

### 2.2 Cliente

| Comando | Resultado |
|---|---|
| `npm run openapi:lint` | **Passou** — contrato válido |
| `npm run typecheck` | **Passou** |
| `npm run lint` | **Passou** — 0 erros |
| `npm run format:check` | **Passou** — todos os arquivos no padrão Prettier |
| `npm test` | **Passou** — 17 arquivos, **66 testes** |
| `npm run build` | **Passou** |
| `npm run openapi:check` | **Falhou apenas pelo diff não commitado** (ver nota) |

Nota sobre `openapi:check`: o comando regenera `schema.d.ts` e roda
`git diff --exit-code`. Como o `schema.d.ts` já está modificado na working tree,
o `git diff` contra o `HEAD` sempre retorna diferença. A regeneração foi
comparada com a cópia preservada antes do comando e **não produziu nenhuma
alteração adicional**. Portanto, os tipos estão sincronizados com o contrato
atual; o gate voltará a passar depois do commit da working tree.

Arquivos de evidência: `/tmp/opencode/phase0-baseline/client-*.log`.

### 2.3 Não executado nesta fase

- Testes E2E Playwright, axe e responsividade.
- `docker compose config/build`.
- Varreduras de segurança (Gitleaks, Trivy, ZAP).
- Teste manual de restauração de backup e rotação de chave.

Esses gates continuam obrigatórios nas fases seguintes e no release.

## 3. Congelamento da branch de referência

| Campo | Valor |
|---|---|
| Branch | `refactor-speckit-converge` |
| Commit | `cbf5295dc14e02517ee8256b67bac733a88ce321` |
| Data | 2026-10-25 09:19:43 -0300 |
| Assunto | `refactor: converge speckit specs with implementation (post Phase 14)` |
| Relação com a `main` | 1 commit à frente, 0 atrás |
| Base da `main` | `775e1baea91b99c3d5ab0741c07ffe58a600e773` |

Regras mantidas:

- A branch é somente referência de hipóteses, testes e ideias.
- Não haverá merge, fast-forward ou cherry-pick integral.
- Qualquer trecho aproveitado passa pelo
  [gate de análise](plano-implementacao-correcoes.md#2-gate-obrigatório-de-análise-da-branch).
- O hash acima é o congelado para auditoria; se a branch mudar, o novo hash
  precisa ser registrado antes de qualquer nova análise.

## 4. Reserva de migrações

As versões abaixo foram reservadas. **Nenhum arquivo de migração foi criado
nesta fase**; a criação acontece no item que a utiliza, com teste de migração.

| Versão | Arquivo planejado | Conteúdo | Fase | Status |
|---|---|---|---|---|
| `V016` | `V016__goal_notes.sql` | `financial_goal.notes_enc` | 5 (C18) | Reservada |
| `V017` | `V017__login_block.sql` | `login_attempt.blocked_until` | 1 (C02) | Reservada |
| `V018` | `V018__unknown_account_type.sql` | `financial_account.type = UNKNOWN` | 2 (C06) | Aplicada |
| `V019` | `V019__invoice_item_mirror_suspension.sql` | `invoice_item.mirror_suspended` | 3 (C09) | Aplicada |
| `V020+` | conforme os itens seguintes | índices e constraints adicionais | conforme necessidade | Livre |

Restrições operacionais:

- `V014` e `V015` são imutáveis.
- `V010`–`V013` existem apenas na branch de referência e **não podem** ser
  criadas na `main`, pois chegariam fora de ordem depois de `V014`/`V015`.
- A reserva está registrada em
  [`server/src/main/resources/db/migration/README.md`](../server/src/main/resources/db/migration/README.md).
- Toda migração precisa passar em banco vazio e em snapshot anterior
  (`MigrationIT`) e atualizar `specs/001-finance/data-model.md`.

## 5. ADRs registradas

| ADR | Decisão | Status | Bloqueia |
|---|---|---|---|
| [ADR-0001](adr/0001-reembolso-sem-vinculo.md) | Semântica de reembolso importado sem vínculo determinístico | Aceito | C07 |
| [ADR-0002](adr/0002-webhook-token-segredo-servidor.md) | Manter webhook com token/segredo gerados pelo servidor | **Substituído (S-04)** | C04, C24 |
| [ADR-0003](adr/0003-rate-limit-cadastro.md) | Armazenamento do rate limit de cadastro por IP | Aceito | C03 |
| [ADR-0004](adr/0004-cancelamento-recuperacao-sync.md) | Cancelamento e recuperação de sincronização em memória | Aceito | C14 |
| [ADR-0005](adr/0005-feature-flag-espelho-fatura.md) | Ciclo de vida do espelho de fatura sem feature flag | Aceito | C09 |

O índice e o template estão em [`adr/README.md`](adr/README.md).

## 6. Matriz de cobertura

Legenda:

- **COBERTO** — todos os critérios de aceite implementados e testados.
- **PARCIAL** — parte dos critérios implementada, com lacunas objetivas.
- **AUSENTE** — sem implementação relevante.

Resumo: **0 cobertos**, **20 parciais** e **6 ausentes** (`C03`, `C06`, `C08`,
`C13`, `C15`, `C26`).

### 6.1 Segurança, sessão e autenticação

| ID | Status | Evidências atuais | Lacunas principais |
|---|---|---|---|
| C01 | PARCIAL | `client/src/features/auth/hooks/useSession.ts` limpa cache no logout; `client/src/lib/api/client.ts` emite evento em 401 | Sem timers de 30 min/12 h; sem revalidação em foco/`visibilitychange`/`pageshow`; 401 não limpa o cache completo; login não limpa cache antes de hidratar |
| C02 | PARCIAL | `LoginAttemptService` com janela deslizante; `AuthService` usa `getRemoteAddr`; `GlobalExceptionHandler` envia `Retry-After` | Sem `blocked_until` persistido nem `V017`; bloqueio liberado antes do fim quando as falhas se espalham na janela; sem trusted proxies; testes de bloqueio persistido ausentes |
| C03 | AUSENTE | `AuthController.register` chama `AuthService.register` direto | Nenhum limiter por IP antes do Argon2id; sem 429/`Retry-After`; sem testes |
| C04 | PARCIAL | `IdempotencyFilter` após `CsrfFilter`, TTL de 24 h, replay 2xx, reserva e `409` em andamento; `IdempotencyIT` | Sem replay de 4xx; sem testes de concorrência real, 5xx, chave inválida e ordem de filtros; headers originais não são reproduzidos |
| C25 | PARCIAL | `AssistedPasswordResetService`, runner, script e `PasswordResetIT` | Runner em `web-application-type=none` ainda depende de `FindByIndexNameSessionRepository`; token do script não chega à propriedade validada; teste não executa o caminho real |
| C26 | AUSENTE | `JsonAccessDeniedHandler` e `OwnershipGuard` apenas respondem 403/404 | Nenhum evento de auditoria para acesso negado ou CSRF; `AuditEventType` sem tipo; sem testes |

### 6.2 Importação e correções

| ID | Status | Evidências atuais | Lacunas principais |
|---|---|---|---|
| C05 | PARCIAL | `dedup_key` com identificador externo, fingerprint calculado e `external_identity_history` | Fallback por fingerprint é incondicional e funde duas compras idênticas; não existe `reconcileIdentity`; teste de duas compras idênticas ausente |
| C06 | AUSENTE | `TransactionImportService` e `AccountImportService` importam dados | Valor/data ausentes viram `0,00`/hoje; tipo desconhecido vira `BANK`; `UNKNOWN` não existe no enum, banco ou UI; sem `V018` e sem diagnóstico em `sync_run` |
| C07 | PARCIAL | Matching por valor na importação, vínculo manual e limite acumulado no relatório | Sem comparação de descrição; ambiguidade só por empate de valor; reembolso sem vínculo reduz despesas; sem retro-match nem evento de decisão; sem teste de excedente de `REFUND` |
| C08 | AUSENTE | Par de transferência é criado e excluído em `ManualTransactionService` | `TransactionCorrectionService` não verifica `relatedTransactionId` nem `InternalMatch`; valor/data de uma perna podem ser alterados; sem erro de conflito ou teste |

### 6.3 Faturas, espelhos e relatórios

| ID | Status | Evidências atuais | Lacunas principais |
|---|---|---|---|
| C09 | PARCIAL | `InvoiceGenerationService` gera espelho para status efetivos; `InvoiceBalanceService` recalcula totais | Sem eventos/listeners; sem suspensão/reativação de espelho; `SOURCE_REMOVED` continua compondo; correção não move/atualiza item; exclusão manual não remove espelho; sem `V019` |
| C10 | PARCIAL | `OVERDUE` existe; fatura `PAID` não é reaberta; ajustes em fatura fechada são sinalizados; Bill tem guarda | CRUD de item e correção de transação permitem fatura paga; `statusFor` retorna `PARTIALLY_PAID` antes de `OVERDUE`; UI não bloqueia; testes ausentes |
| C11 | PARCIAL | Relatórios excluem a transação-mãe e contabilizam parcelas/manuais; `InvoiceItemService` cria parcelas manuais | Compra importada parcelada gera um único espelho; parcelas futuras somem; sem testes de categoria, `UNCATEGORIZED`, item manual com 1 parcela e soma do drill-down |
| C12 | PARCIAL | Limite de reembolso, piso de `expenses` e crédito de fatura | Categorias e série podem ficar negativas; drill-down pode divergir do indicador; filtro `REFUND` inclui juros/tarifas; reembolso sem vínculo reduz despesas; testes incompletos |
| C13 | AUSENTE | Geração só ocorre ao abrir faturas, no patrimônio e na importação de Bill | Sync, compra manual no cartão e correção não geram/recalculam fatura nem `cardOutstandingBalance`; sem testes sem abrir faturas |

### 6.4 Sincronização e conexões

| ID | Status | Evidências atuais | Lacunas principais |
|---|---|---|---|
| C14 | PARCIAL | `SyncJobService` com fila, retry/backoff e cancelamento que marca `FAILED` | Nunca reclamar jobs `RUNNING`; tentativa interrompida não vai para `sync_run`; `SYNCING`/`lastSyncAttemptAt` não são persistidos antes das chamadas; sem checkpoints de cancelamento |
| C15 | AUSENTE | Limite configurável e contagem de conexões existem | `ITEM_UNAVAILABLE` ocupa vaga; DTO não expõe limite/data/flag; UI usa variável de build; sem probe ao salvar credenciais; sem `connectionStatus` por conta/cartão |
| C16 | PARCIAL | Lista vazia não remove histórico; troca de `itemId` preserva dados locais | Conta ausente não vira `UNAVAILABLE`; leitura truncada ou cursor inválido marca `SOURCE_REMOVED`; sem testes de `MAX_PAGES`, cursor inválido e conta removida |

### 6.5 Metas e dashboard

| ID | Status | Evidências atuais | Lacunas principais |
|---|---|---|---|
| C17 | PARCIAL | Denominador é a dívida inicial; percentual limitado a 100; alocação fixa exata; capacidade por fonte | Dívida atual vem das contribuições, não do passivo; capacidade não é validada contra a dívida inicial; edição após amortização pode falhar; moeda da fonte não é validada; múltiplas metas `NET_WORTH` contam o patrimônio inteiro |
| C18 | PARCIAL | Estado `CHANGED` e reconhecimento existem | Sem `notes`, `V016` ou exportação; sem nome legível da fonte; alteração de alocação/percentual/dívida inicial não marca `CHANGED` |
| C19 | PARCIAL | Sugestões ao fim de sync/carga/reconexão; dashboard só conta pendências; rejeição não reaparece | Sem scheduler próprio nem flag/intervalo; `list` ainda chama `suggest` com duplo loop no caminho de leitura; sem teste de performance com 10.000 transações |
| C20 | PARCIAL | Saldo consolidado exclui moeda diferente de BRL; patrimônio tem completude | Saldo ausente vira `0,00`; sem `currentBalanceComplete` nem contagem de contas sem saldo; cartão USD entra no saldo devedor; UI não explica exclusões; sem testes |

### 6.6 UI, acessibilidade e contrato

| ID | Status | Evidências atuais | Lacunas principais |
|---|---|---|---|
| C21 | PARCIAL | Erro com retry no dashboard e sync; formulários convertem `25,90` | Sem normalização de `1.234,56`; filtros somem no erro do dashboard; integração renderiza formulário vazio durante loading; retry limitado; sem testes de 500/rede |
| C22 | PARCIAL | Diálogos têm `role`, `aria-modal` e `aria-labelledby`; um `autoFocus` | Sem trap de foco, Escape ou restauração ao acionador; foco inicial só em um diálogo; sem testes de teclado ou axe com diálogo aberto |
| C23 | PARCIAL | Servidor pagina investimentos por cursor | UI só mostra os primeiros 50 e calcula totais sobre eles; patrimônio sem filtros de período; cartão USD entra no patrimônio; saldo negativo não vira passivo; `stale` de cartão usa hoje |
| C24 | PARCIAL | `ContractSurfaceIT`, lint/tipos no CI e `schema.d.ts` sincronizado | `cursor`/`limit` de investimentos não documentados; docs de acessibilidade afirmam foco em diálogo inexistente; contrato não valida parâmetros/DTOs |

## 7. Conclusão da Fase 0

Checklist de saída:

- [x] Working tree preservada (ref Git, patch e tar).
- [x] Suíte completa executada e registrada.
- [x] Matriz “coberto / parcial / ausente” criada.
- [x] Hash da branch de referência congelado.
- [x] Migrações `V016`–`V019` reservadas e regras registradas.
- [x] ADRs das decisões abertas criadas.

Critérios de entrada da Fase 1:

1. **C01, C02, C04, C25 e C26** podem começar imediatamente; `C02` cria `V017`.
2. **C03** segue a [ADR-0003](adr/0003-rate-limit-cadastro.md), aceita em memória.
3. **C07** segue a [ADR-0001](adr/0001-reembolso-sem-vinculo.md), aceita: reembolso sem vínculo não reduz despesas.
4. **C09** segue a [ADR-0005](adr/0005-feature-flag-espelho-fatura.md), aceita sem flag; `V019` aplicada na Fase 3.
5. **C14** segue a [ADR-0004](adr/0004-cancelamento-recuperacao-sync.md), aceita em memória para instância única; não reserva `V020`.
6. Cada item segue o [gate de análise](plano-implementacao-correcoes.md#2-gate-obrigatório-de-análise-da-branch)
   e nasce de um teste de regressão que falha antes da correção.
7. O baseline deve ser repetido ao final de cada fase e antes do release.

## 8. Referências

- [`plano-implementacao-correcoes.md`](plano-implementacao-correcoes.md)
- [`adr/README.md`](adr/README.md)
- [`relatorio-divergencias-specs-codigo.md`](relatorio-divergencias-specs-codigo.md)
- [`security.md`](security.md)
- [`operations.md`](operations.md)
- [`release-checklist.md`](release-checklist.md)
- [`../server/src/main/resources/db/migration/README.md`](../server/src/main/resources/db/migration/README.md)
