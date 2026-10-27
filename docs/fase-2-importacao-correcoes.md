# Fase 2 — Integridade da Importação e Correções

**Status:** Concluída em 2026-10-25  
**Itens:** `C05`, `C06`, `C07`, `C08`  
**Plano:** [`plano-implementacao-correcoes.md`](plano-implementacao-correcoes.md)  
**Decisões:** [ADR-0001](adr/0001-reembolso-sem-vinculo.md) aplicada

> Esta fase corrige a identidade das transações importadas, o tratamento de
> dados ausentes, o vínculo de reembolsos e a correção de transferências.

## 1. O que mudou

### C05 — Fingerprint apenas na reconciliação de `itemId`

- O fluxo normal usa apenas `dedup_key` (provedor + conexão + conta + ID externo).
- O fingerprint só é consultado com `reconcileIdentity=true`, usado pela troca de
  `itemId`.
- Duas compras legítimas idênticas com IDs externos diferentes continuam sendo
  duas transações.
- `external_identity_history` continua sendo atualizado.
- Reimportação e troca de `itemId` seguem idempotentes.

Testes: `ImportIntegrityIT` e `ItemIdChangeIT` com IDs externos diferentes.

### C06 — Dados ausentes e tipo de conta `UNKNOWN`

- Transação sem valor ou sem data não é persistida com `0,00`/“hoje”; entra em
  `failed_count`.
- `sync_run` fica `PARTIAL` com `error_code=IMPORT_MISSING_FIELDS`.
- Tipo de conta desconhecido é preservado como `UNKNOWN`, não `BANK`.
- `V018__unknown_account_type.sql` amplia a constraint de tipo.
- `UNKNOWN` não compõe o saldo consolidado e aparece como “Tipo não informado” na
  lista e no detalhe da conta.
- Contas manuais continuam aceitando apenas `BANK`, `CASH` e `WALLET`.

Testes: `ImportIntegrityIT` (campos ausentes e tipo desconhecido) e
`MigrationIT`.

### C07 — Reembolsos e chargebacks determinísticos

- Novo `RefundMatcher` centraliza o vínculo usado pela importação e pelo
  lançamento manual.
- Critérios: mesma conta, janela de 90 dias, menor despesa que cobre o valor,
  descrição compatível e ausência de empate.
- Reembolso com descrição apenas genérica usa o valor; descrição com comerciante
  exige ao menos um token em comum.
- Reembolso sem vínculo não entra em receita nem despesa (ADR-0001), mas continua
  visível na lista de lançamentos e pode ser vinculado manualmente.
- O vínculo herda categoria, limita a redução ao valor efetivo da original e
  preserva o crédito de fatura para o excedente.
- Não há religação automática retroativa; o vínculo manual é o caminho.

Testes: `ImportIntegrityIT`, `PluggyIntegrationSetupIT` e
`FinalFinancialRulesIT`.

### C08 — Correção de transferência em duas pernas

- Alterar valor ou data de uma perna de transferência retorna `409 CONFLICT`.
- Vale para transferência manual (pernas ligadas) e para par importado já
  confirmado em `internal_match`.
- Descrição, categoria e observações continuam editáveis sem quebrar o vínculo.
- A mensagem orienta excluir e recriar o par.

Testes: `ManualEntriesIT` e `InternalMatchIT`.

## 2. Validação

| Suite | Resultado |
|---|---|
| Backend `./mvnw verify` | **158 testes**, 0 falhas |
| JaCoCo / Spotless / Checkstyle | Verdes |
| Frontend `npm test` | **71 testes / 18 arquivos** |
| `typecheck`, `lint`, `format:check`, `build` | Verdes |
| `MigrationIT` | Verde, incluindo `V018` |

## 3. Limitações conhecidas

- Reembolso sem vínculo aparece na lista de lançamentos, mas não há uma fila
  dedicada de conciliação; o vínculo é feito pelo diálogo de correção.
- A compatibilidade de descrição é conservadora. Se não houver token de
  comerciante em comum, o reembolso não é vinculado automaticamente.
- Reembolso importado antes da original não é religado retroativamente; o
  usuário vincula manualmente.
- O excedente de cartão usa o mecanismo de crédito de fatura já existente; não
  há uma tela específica para excedentes de reembolso.
- A correção de descrição/categoria/observações em uma perna de transferência
  vale para a perna editada; valor e data permanecem bloqueados em ambas.

## 4. Próxima fase

Fase 3 — faturas, espelhos e relatórios:

- `C09` ciclo de vida do espelho de fatura (ADR-0005: sem flag);
- `C10` proteção de fatura paga e `OVERDUE`;
- `C11` parcelas e itens manuais;
- `C12` reembolsos nos relatórios;
- `C13` geração após sync, compra e correção.

## 5. Referências

- [`plano-implementacao-correcoes.md`](plano-implementacao-correcoes.md)
- [`adr/0001-reembolso-sem-vinculo.md`](adr/0001-reembolso-sem-vinculo.md)
- [`adr/0005-feature-flag-espelho-fatura.md`](adr/0005-feature-flag-espelho-fatura.md)
- [`funcionalidades.md`](funcionalidades.md)
- [`operations.md`](operations.md)
