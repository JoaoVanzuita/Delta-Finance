# Acessibilidade

**Escopo**: T177. Meta: WCAG 2.1 nível AA nas telas do MVP, uso apenas com
teclado e leitor de tela, sem depender de cor ou de mouse.

## 1. Automação

Dois níveis de auditoria executam `@axe-core/playwright` com as tags
`wcag2a`, `wcag2aa`, `wcag21a` e `wcag21aa`:

- `client/e2e/a11y.spec.ts` cobre as rotas principais com stubs enxutos;
- `client/e2e/a11y-app.spec.ts` percorre **todas as rotas**, inclusive detalhes
  dinâmicos (conta, cartão, fatura, investimentos, patrimônio, metas), nos temas
  claro e escuro, usando o banco fictício de screenshots.

Rotas cobertas incluem:

- `/`, `/login`, `/register`, 404 público e autenticado;
- `/dashboard`, `/accounts`, `/accounts/:id`, `/accounts/new`,
  `/accounts/:id/edit`;
- `/cards`, `/cards/:accountId`, `/cards/:accountId/invoices`, `/invoices/:id`;
- `/transactions`, `/transactions/new`, `/categories`, `/reconciliation`;
- `/investments`, `/wealth`, `/goals`;
- `/settings/preferences`, `/settings/integration`, `/settings/privacy`,
  `/account`.

Além do axe, os testes verificam:

- exatamente um `h1` e presença de `main` em cada rota;
- ausência de overflow horizontal em 360, 390, 768, 1280 e 1440 px;
- alvos de toque de `button` e links de navegação com pelo menos 44 px no mobile;
- drawer, dialog e dropdown abertos (dashboard, fatura e menu de transações);
- presença de `main`, `navigation` nomeada e exatamente um `h1` por tela;
- link “Pular para o conteúdo” como primeiro foco de `Tab` e foco em `main`
  após `Enter`;
- `aria-current="page"` no item ativo da navegação;
- navegação por teclado até Privacidade.

Execução:

```bash
cd client
npm run build
npx playwright test e2e/a11y.spec.ts e2e/a11y-app.spec.ts
```

## 2. Teclado

- Ordem de foco segue o DOM: link de pular, marca, navegação, conteúdo, rodapé.
- Todos os controles são `<button>`, `<a>` ou campos com rótulo associado.
- Diálogos e drawers usam a primitiva compartilhada de `components/ui`: foco
  inicial, trap de foco, `Escape` quando permitido, retorno ao gatilho, fundo
  `inert` e scroll lock. O formulário permanece montado quando há erro de API.
- O Dashboard mantém pontos do gráfico de evolução alcançáveis por `Tab`, com
  tooltip textual, além da tabela equivalente e dos botões “Detalhar”.
- Filtros avançados do Dashboard e de Transações ficam em drawer com foco preso;
  chips de filtros ativos têm botão de remoção nomeado.
- Abas de conta, cartão e faturas seguem o padrão ARIA, com setas, Home/End e
  painel associado. O estado da aba é preservado na URL.
- O detalhe de transação abre em drawer; o menu contextual de cada linha tem nome
  acessível e devolve foco ao gatilho.
- Investimentos, Posições e Proventos usam tabs ARIA com estado em `?tab=`. O
  detalhe de posição e o cadastro de provento usam drawers.
- Patrimônio separa ativos e passivos em grupos nomeados; itens fora do cálculo
  têm motivo textual. A criação/edição manual e os detalhes de meta também usam
  drawer.
- Gráficos de proventos, alocação e composição patrimonial têm resumo/tabela
  equivalente e não dependem só de cor.
- A reconciliação mantém a confirmação desabilitada quando falta uma das pernas ou
  as moedas divergem; score e critérios ficam em disclosure secundário.
- A navegação secundária das configurações usa links reais com
  `aria-current="page"`; no mobile ela quebra em duas colunas, sem rolagem
  horizontal da página. Preferências usa `fieldset`/`legend` e radios nativos para
  tema e paleta.
- Credenciais do Meu Pluggy abrem em dialog com foco preso e retorno ao gatilho;
  o Client Secret nunca é reidratado. A remoção de conexão exige confirmação e
  separa “desativar mantendo dados” de “excluir conexão e dados”.
- Falhas de sincronização exibem mensagem de usuário; códigos de diagnóstico
  ficam em `details` secundário. Exclusão de conta mantém resumo de consequências
  antes do formulário.
- Nenhuma ação depende de hover.

## 3. Leitor de tela

Verificação manual recomendada com NVDA (Windows), Orca (Linux) ou VoiceOver
(macOS):

1. O título da página é anunciado e corresponde ao `h1`.
2. Tabelas têm `caption`, cabeçalhos de coluna e valores monetários lidos como
   texto, com sinal e moeda.
3. Gráficos têm resumo textual/tabela equivalente; a informação não depende só
   da cor. No Dashboard, pontos do gráfico têm nome acessível com período,
   receitas, despesas e resultado.
4. Mensagens de erro usam `role="alert"`; sucessos usam `role="status"`.
5. Estados de sincronização e pendências são anunciados sem jargão ou códigos
   internos como mensagem principal.
6. Drawers de categoria, período e movimentações devolvem o foco ao gatilho ao
   fechar.

## 4. Contraste, cor e movimento

- Texto normal e componentes seguem contraste mínimo 4.5:1 / 3:1, nos temas claro
  e escuro e nas quatro paletas curadas.
- Receitas, despesas e estados não usam apenas cor: há rótulo textual e sinal.
  Categorias têm nome, ícone e valor além do slot de cor.
- O badge de aviso usa tinta neutra sobre `warning-soft` para manter 4.5:1.
- Não há animação essencial; `prefers-reduced-motion` é respeitado e nenhum fluxo
  depende de transição.

## 5. Limitações conhecidas

- Os gráficos são SVG/CSS e têm tabela/resumo equivalente; a leitura detalhada de
  séries longas pode exigir navegação por teclado na tabela. Acima de 120 buckets,
  o gráfico agrega visualmente e a tabela preserva a granularidade.
- O protocolo com leitor de tela real é manual e deve ser registrado no ticket
  de release; a automação cobre apenas regras detectáveis.
- A busca de transações é local aos registros carregados e a interface informa
  essa limitação; não há busca global.
- Inputs nativos de data exibem formato conforme o navegador/SO; os textos ao
  redor e as tabelas usam `dd/MM/yyyy`.

## 6. Dispensa registrada

Em **2026-10-24**, o proprietário do produto registrou que a verificação manual
com leitor de tela não é necessária para esta instalação. A decisão não remove
as obrigações automatizadas:

- axe-core, teclado, landmarks, foco e responsividade continuam no CI/E2E;
- qualquer regressão detectável por axe ou teclado continua bloqueando o build;
- a verificação com leitor de tela pode ser reaberta se o produto passar a
  atender usuários que dependam de tecnologia assistiva.
