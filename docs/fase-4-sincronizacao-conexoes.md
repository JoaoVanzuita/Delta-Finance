# Fase 4 — Sincronização e Conexões

**Status:** Concluída em 2026-10-25  
**Itens:** `C14`, `C15`, `C16`  
**Plano:** [`plano-implementacao-correcoes.md`](plano-implementacao-correcoes.md)  
**Decisão:** [ADR-0004](adr/0004-cancelamento-recuperacao-sync.md) aplicada

> Esta fase torna a fila de sincronização recuperável após crash, cancela
> escritas em andamento, libera o limite de conexões para itens indisponíveis e
> evita marcar remoções a partir de leituras truncadas.

## 1. O que mudou

### C14 — Recuperação e cancelamento de sincronização

- `SyncCancellationRegistry` em memória, por job, com token vinculado à thread da
  tentativa (movido para `shared.sync` para não criar ciclo entre módulos).
- Checkpoints antes de cada conta, de cada transação, antes de marcar ausências e
  antes de cada importação. Cancelar/remover/desconectar interrompe a tentativa
  antes do próximo lote de escrita.
- `SYNCING` e `lastSyncAttemptAt` são persistidos antes das chamadas externas.
- No start, todo job `RUNNING` é considerado órfão (instância única): a tentativa
  vira `sync_run` com `INTERRUPTED`, o job volta para `RETRY`/`FAILED`, e
  `SYNCING` é liberado na conexão e na integração.
- Timeout em memória (`DELTA_SYNC_JOB_TIMEOUT`, padrão 10 min): o job é liberado
  como `JOB_TIMEOUT` e a importação para no próximo checkpoint.
- Um worker atrasado não sobrescreve o job depois de cancelamento ou timeout.
- Exclusão de conexão e desconexão da integração cancelam jobs ativos.

Testes: `SyncCancellationIT`, `SyncRecoveryIT` e `SyncJobIT`.

### C15 — Estado de conexão, limite e probe

- `countByIntegration...` passou a excluir `ITEM_UNAVAILABLE`: a conexão não
  ocupa vaga.
- `GET /api/v1/integrations/pluggy` expõe `connectionLimit` e
  `connectionLimitCheckedAt`.
- A UI usa `connectionLimit`, `connectionCount` e `connectionLimitCheckedAt` do
  backend; as constantes `VITE_PLUGGY_CONNECTION_LIMIT*` foram removidas.
- Salvar credenciais sem conexão válida mantém a integração `INCOMPLETE`.
- Cada conta/cartão expõe `connectionStatus`, separado de `status`.
- O limite continua sendo o da modalidade pessoal, não a quantidade de contas do
  item.

Testes: `ConnectionLimitIT`, `IntegrationApiTest` e a suíte de frontend.

### C16 — Reconciliação de contas e leitura truncada

- `PluggyAccountPage` e `PluggyTransactionPage` carregam um indicador `complete`.
- Transações: cursor inválido ou limite de páginas marca a leitura como
  inconclusiva; o que veio é importado, mas ausências **não** viram
  `SOURCE_REMOVED`.
- Contas: em leitura completa, contas importadas ausentes ficam `UNAVAILABLE`,
  saem dos saldos correntes e preservam transações/histórico.
- Contas ausentes não são mais consultadas para transações até reaparecerem.
- A nova autorização pode expor a mesma conta com outro ID; o saldo não duplica
  porque a conta antiga fica indisponível.
- Leitura conclusiva e vazia continua produzindo `EMPTY` sem remover histórico.

Testes: `AccountReconciliationIT`, `SyncRecoveryIT` e `ItemIdChangeIT`.

## 2. Validação

| Suite | Resultado |
|---|---|
| Backend `./mvnw verify` | **178 testes**, 0 falhas |
| JaCoCo / Spotless / Checkstyle | Verdes |
| `ModuleBoundaryTest` | Verde (portas para evitar ciclos) |
| Frontend `npm test` | **72 testes / 18 arquivos** |
| `typecheck`, `lint`, `format:check`, `build` | Verdes |
| `openapi:lint` | Verde |

## 3. Limitações conhecidas

- Cancelamento, timeout e recuperação de órfãos são em memória e assumem uma
  única instância (ADR-0004). Múltiplas instâncias exigiriam lease persistido.
- O timeout não interrompe uma chamada HTTP bloqueada no provedor; ele libera o
  job e a conexão, e a escrita para no próximo checkpoint quando a chamada
  retorna.
- A leitura de **contas** usa `totalPages`; se o provedor não informar
  `totalPages`, a leitura é considerada completa.
- Contas ausentes não são apagadas; o histórico e as transações continuam
  acessíveis, mas a conta não compõe saldos correntes.
- O probe de credenciais valida a aplicação pessoal no provedor; não substitui a
  autorização de cada item.

## 4. Próxima fase

Fase 5 — metas:

- `C17` correções de metas;
- `C18` observações e rastreabilidade.

## 5. Referências

- [`plano-implementacao-correcoes.md`](plano-implementacao-correcoes.md)
- [`adr/0004-cancelamento-recuperacao-sync.md`](adr/0004-cancelamento-recuperacao-sync.md)
- [`funcionalidades.md`](funcionalidades.md)
- [`operations.md`](operations.md)
- [`telas-e-componentes.md`](telas-e-componentes.md)
