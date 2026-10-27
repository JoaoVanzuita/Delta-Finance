# Fase 5 — Metas Financeiras

**Status:** Concluída em 2026-10-25  
**Itens:** `C17`, `C18`  
**Plano:** [`plano-implementacao-correcoes.md`](plano-implementacao-correcoes.md)

> **Nota de 2026-10-26:** o corte S-03 removeu a regra de redução de dívida, o
> campo `initialValue` e a fonte `MANUAL_LIABILITY` das alocações de meta. As
> referências a dívida abaixo são históricas; a decisão vigente está em
> [`analise-simplificacao.md`](analise-simplificacao.md), seção 11.3.6.

> Esta fase corrige o cálculo de redução de dívida, impede que o mesmo saldo seja
> contado integralmente em várias metas, sinaliza alocação fixa acima da
> capacidade, recusa fontes fora de BRL e adiciona observações e rastreabilidade
> das fontes.

## 1. Decisões

- **Dívida inicial é o denominador**: `DEBT_REDUCTION` mede
  `(dívida inicial − dívida atual) ÷ dívida inicial`, com resultado limitado
  entre 0% e 100%. A dívida atual vem do passivo manual vinculado, não do valor
  fixo alocado.
- **Fonte única de dívida**: metas `DEBT_REDUCTION` usam exatamente uma fonte,
  `MANUAL` ou `MANUAL_LIABILITY`. A capacidade é validada contra a dívida
  inicial, para que a edição continue possível depois de amortizar.
- **Saldo compartilhado é reservado**: ao calcular o progresso, cada saldo é
  consumido na ordem das alocações e reservado primeiro para metas mais antigas
  (`created_at`, com `id` como desempate).
- **Valor fixo é exato**: quando o saldo cai abaixo do valor fixo, a
  contribuição não é truncada. A API devolve `capacityExceeded=true` e a UI pede
  ajuste explícito. Percentuais, ao contrário, se limitam ao saldo disponível.
- **Somente BRL**: contas em outra moeda não são aceitas como fonte (422 em
  `allocations`) e não aparecem no seletor da UI.
- **Observações são cifradas**: `notes` é opcional, limitado a 2000 caracteres e
  gravado em `financial_goal.notes_enc` (migração `V016`). Alterar apenas as
  observações não marca `CHANGED`.
- **Alteração material**: além de valor-alvo, prazo, tipo e regra, mudanças de
  dívida inicial, fonte, modo, valor ou percentual de alocação marcam `CHANGED`
  até o reconhecimento explícito.

## 2. O que mudou

### C17 — Correções de metas

- `GoalProgressService` calcula a redução de dívida a partir da dívida inicial e
  do saldo atual do passivo manual, limitada entre 0 e a dívida inicial.
- Percentual de progresso passou a ser limitado também no piso (0%).
- `contributionsForProgress` reserva o saldo consumido por metas mais antigas e
  limita alocações percentuais ao disponível.
- `AllocationContribution` carrega `capacityExceeded`; `GoalAllocationView` e
  `GoalAllocationResponse` expõem o indicador.
- `validateSourceCapacity` recebe a regra e a dívida inicial; em
  `DEBT_REDUCTION`, a capacidade é a dívida inicial.
- Contas com `currency != BRL` são recusadas em `allocations`.
- Criação/edição de `DEBT_REDUCTION` valida exatamente uma fonte manual ou
  passivo manual.
- A UI mostra o aviso de capacidade excedida em cada alocação.

Testes: `GoalsIT` (dívida com passivo, piso/teto, valor fixo, múltiplas metas,
edição após amortização e fonte USD).

### C18 — Observações e rastreabilidade

- Migração `V016__goal_notes.sql` adiciona `financial_goal.notes_enc`.
- `notes` entra em `GoalRequest`, `GoalResponse`, `GoalView`, persistência
  cifrada e exportação (`financial_goals.jsonl`/`.csv`).
- `GoalAllocationResponse` expõe `sourceName`, resolvido para conta (alias ou
  instituição), investimento (ativo e ticker), item manual (descrição) ou
  “Valor manual”.
- A UI exibe as observações no cartão da meta, o nome da fonte em cada alocação
  e usa seletores de conta (somente BRL), investimento e item manual em vez de
  exigir o UUID bruto.
- Alterações de alocação e de dívida inicial passam a marcar `CHANGED`; o botão
  “Entendi a alteração” retorna a meta para `ACTIVE`.

Testes: `GoalsIT` (observações, nome da fonte, `CHANGED`, exportação) e
`GoalProgress.test.tsx` (formulário, seletores e aviso).

## 3. Validação

| Suite | Resultado |
|---|---|
| Backend `./mvnw clean verify` | **186 testes**, 0 falhas |
| JaCoCo / Spotless / Checkstyle | Verdes |
| `ModuleBoundaryTest` | Verde |
| Frontend `npm test` | **74 testes / 18 arquivos** |
| `typecheck`, `lint`, `format:check`, `build` | Verdes |
| `openapi:lint` | Verde |

## 4. Limitações conhecidas

- A prioridade entre metas que compartilham a mesma fonte usa `created_at` e o
  `id` como desempate; não há rateio proporcional nem reserva editável.
- Valores fixos acima da capacidade só são sinalizados na leitura
  (`capacityExceeded`). A criação/edição continua rejeitando a soma acima do
  saldo disponível, exigindo ajuste explícito.
- O nome da fonte é informativo: se a conta, investimento ou item for removido,
  `sourceName` fica nulo e a alocação continua sinalizada pela API.
- Contas em moeda diferente de BRL são recusadas, não convertidas.
- Observações não participam da detecção de alteração material nem do cálculo de
  progresso.
- Não há backfill de observações para metas existentes: o campo nasce nulo.

## 5. Próxima fase

Fase 6 — dashboard, performance e dados complementares:

- `C19` scheduler de movimentos internos e performance;
- `C20` completude de saldo e moedas.

## 6. Referências

- [`plano-implementacao-correcoes.md`](plano-implementacao-correcoes.md)
- [`funcionalidades.md`](funcionalidades.md)
- [`telas-e-componentes.md`](telas-e-componentes.md)
- [`../specs/001-finance/data-model.md`](../specs/001-finance/data-model.md)
- [`../specs/001-finance/contracts/openapi.yaml`](../specs/001-finance/contracts/openapi.yaml)
