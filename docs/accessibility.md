# Acessibilidade

**Escopo**: T177. Meta: WCAG 2.1 nível AA nas telas do MVP, uso apenas com
teclado e leitor de tela, sem depender de cor ou de mouse.

## 1. Automação

`client/e2e/a11y.spec.ts` executa `@axe-core/playwright` com as tags
`wcag2a`, `wcag2aa`, `wcag21a` e `wcag21aa` em todas as rotas principais:

- `/`, `/login`, `/register`
- `/dashboard`, `/accounts`, `/accounts/new`
- `/transactions`, `/transactions/new`, `/categories`, `/reconciliation`
- `/investments`
- `/wealth`
- `/settings/integration`, `/settings/privacy`, `/account`

Além do axe, o teste verifica:

- presença de `main`, `navigation` nomeada e exatamente um `h1` por tela;
- link “Pular para o conteúdo” como primeiro foco de `Tab` e foco em `main`
  após `Enter`;
- `aria-current="page"` no item ativo da navegação;
- navegação por teclado até Privacidade.

Execução:

```bash
cd client
npm run build
npx playwright test e2e/a11y.spec.ts
```

## 2. Teclado

- Ordem de foco segue o DOM: link de pular, marca, navegação, conteúdo, rodapé.
- Todos os controles são `<button>`, `<a>` ou campos com rótulo associado.
- Diálogos de confirmação (privacidade, exclusão) são alcançáveis por `Tab` e
  acionáveis por `Enter`/`Espaço`; o foco permanece no diálogo enquanto aberto.
- Nenhuma ação depende de hover.

## 3. Leitor de tela

Verificação manual recomendada com NVDA (Windows), Orca (Linux) ou VoiceOver
(macOS):

1. O título da página é anunciado e corresponde ao `h1`.
2. Tabelas têm cabeçalhos de coluna; valores monetários são lidos como texto,
   com sinal e moeda.
3. Gráficos têm resumo textual/tabela equivalente; a informação não depende só
   da cor.
4. Mensagens de erro usam `role="alert"`; sucessos usam `role="status"`.
5. Estados de sincronização e pendências são anunciados sem jargão.

## 4. Contraste, cor e movimento

- Texto normal e componentes seguem contraste mínimo 4.5:1 / 3:1.
- Receitas, despesas e estados não usam apenas cor: há rótulo textual e sinal.
- Não há animação essencial; `prefers-reduced-motion` é respeitado pelo
  navegador e nenhum fluxo depende de transição.

## 5. Limitações conhecidas

- Gráficos Recharts são complementados por tabela/resumo; a leitura detalhada
  de séries longas pode exigir navegação por teclado na tabela.
- O protocolo com leitor de tela real é manual e deve ser registrado no ticket
  de release; a automação cobre apenas regras detectáveis.


## 6. Dispensa registrada

Em **2026-10-24**, o proprietário do produto registrou que a verificação manual
com leitor de tela não é necessária para esta instalação. A decisão não remove
as obrigações automatizadas:

- axe-core, teclado, landmarks, foco e responsividade continuam no CI/E2E;
- qualquer regressão detectável por axe ou teclado continua bloqueando o build;
- a verificação com leitor de tela pode ser reaberta se o produto passar a
  atender usuários que dependam de tecnologia assistiva.
