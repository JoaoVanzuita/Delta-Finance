# Direção visual — Delta Finance

Documento de referência para tokens, tipografia, componentes e comportamento visual.
Consolida a aplicação das skills `frontend-design` e `ui-ux-pro-max` ao produto.

## Tese

**Um livro-caixa privado da família, não um terminal de trading.** A interface
deve transmitir controle e calma: tinta azul-marinho sobre superfícies claras,
hierarquia tipográfica sóbria e números alinhados como em um razão contábil. A
cor aparece para explicar movimento de dinheiro e estado — nunca como decoração.

Público: membros de uma família, em desktop e celular, muitos sem familiaridade
com produtos financeiros. A tela precisa responder “quanto entrou, quanto saiu,
o que precisa da minha atenção” sem sugerir aconselhamento.

## Sistema

### Cores (papéis, não nomes de matiz)

| Papel            | Token                   | Valor     | Uso                                        |
| ---------------- | ----------------------- | --------- | ------------------------------------------ |
| Fundo            | `--color-canvas`        | `#F8FAFC` | plano de fundo da aplicação                |
| Superfície       | `--color-surface`       | `#FFFFFF` | cards, tabelas, diálogos                   |
| Superfície suave | `--color-surface-muted` | `#F1F5F9` | cabeçalhos de tabela, estados vazios       |
| Tinta            | `--color-ink`           | `#0F172A` | texto principal e navegação                |
| Tinta secundária | `--color-ink-muted`     | `#475569` | rótulos, metadados                         |
| Linha            | `--color-line`          | `#DBE3EE` | bordas e divisores                         |
| Primária         | `--color-primary`       | `#0F172A` | ações principais, foco de marca            |
| Ação             | `--color-action`        | `#0369A1` | links e ações secundárias                  |
| Receita          | `--color-income`        | `#047857` | entradas, resultados positivos             |
| Despesa          | `--color-expense`       | `#B91C1C` | saídas, saldo devedor                      |
| Atenção          | `--color-warning`       | `#B45309` | pendências, sincronização, dados estimados |
| Foco             | `--color-focus`         | `#0EA5E9` | anel de foco visível                       |

Contraste: texto normal ≥ 4,5:1; ícones e bordas de controle ≥ 3:1. Receita e
despesa nunca são o único indicador: sempre acompanham sinal, rótulo ou ícone.

### Tipografia

- Uma única família: pilha de sistema (`ui-sans-serif`, `system-ui`, Segoe UI,
  Roboto), com fallback. **Não usar CDN de fontes**: o produto é self-hosted e a
  CSP permite apenas `'self'`; fontes externas também criariam dependência de
  privacidade.
- Papéis: título de página 30–36 px/600; título de seção 18–20 px/600; corpo
  14–16 px/400 com `line-height` 1,5; rótulo utilitário 12–13 px/600 em caixa
  alta e `letter-spacing` discreto.
- Números: `font-variant-numeric: tabular-nums` em toda coluna monetária e em
  datas. Valores à direita; unidades e moeda em texto secundário.
- Medida de leitura: 65–75 caracteres em textos corridos.

### Espaçamento, forma e densidade

- Grade base 4 px; ritmo 8/12/16/24/32/48.
- Raio de 10 px em cards e controles; nada de “pílulas” decorativas.
- Bordas de 1 px em `--color-line` para separar grupos reais. Sombras apenas em
  sobreposições (diálogo, drawer, menu).
- Densidade de ferramenta operacional: tabelas compactas, cabeçalhos fixos e
  linhas com 44 px mínimos de alvo de toque no mobile.
- Largura de conteúdo: 1.200 px; formulários de 480–560 px.

### Assinatura: a linha do razão

Uma régua horizontal de 1 px, alinhada à linha de base de títulos de seção e do
cabeçalho de tabelas monetárias, remete ao papel pautado de um livro-caixa.
Aparece no cabeçalho da aplicação e em tabelas — nunca em todos os cards. É o
elemento memorável, usado com parcimônia.

### Movimento

- Transições de 120–180 ms apenas em cor, opacidade e elevação.
- Nada de animação em valores monetários; nenhum número “contando”.
- `prefers-reduced-motion: reduce` remove transições não essenciais.
- Estados assíncronos usam `aria-live="polite"`; erros usam `role="alert"`.

## Layout responsivo

- **Desktop (≥ 1024 px)**: sidebar fixa de 248 px com marca, navegação e nota de
  ambiente; conteúdo em coluna fluida. Sidebar nunca compete com a tabela.
- **Mobile (< 1024 px)**: top bar com marca e navegação horizontal rolável;
  conteúdo em coluna única. Tabelas viram listas de cartões (a partir das
  histórias de contas/transações), com rótulo por campo.
- Filtros complexos abrem em `sheet` no mobile; nunca escondem o total.
- Nada de sobreposição, corte ou rolagem horizontal da página em 360 px e zoom
  200%.

## Componentes e estados

- Cards agrupam objetos repetidos (conta, fatura, conexão) e têm borda visível.
- Botões: primário escuro para a ação principal da tela; secundário com borda;
  destrutivo apenas com confirmação explícita e nome do objeto.
- Formulários: rótulo sempre visível, dica antes do erro, erro inline associado
  por `aria-describedby`, resumo de erros focável no topo após envio inválido.
- Tabelas: cabeçalho semântico, ordenação anunciada, total sempre visível e
  consistente com o drill-down.
- Estados obrigatórios: carregando (esqueleto com `aria-busy`), vazio (explica o
  que fazer), erro (recuperável, com `requestId`), sem permissão (401/403),
  sincronização pendente/atrasada e dados ausentes ou estimados.
- Gráficos (fase de dashboard) terão resumo textual, tabela alternativa e
  rótulos; cor nunca é o único canal.

## Acessibilidade

- WCAG 2.1 AA; alvo mínimo de 44×44 px; foco visível de 2 px com offset.
- Skip link, landmarks (`header`, `nav`, `main`, `footer`), um `h1` por página.
- Navegação completa por teclado, incluindo diálogos (foco preso e retorno).
- `lang="pt-BR"`, datas em `America/Sao_Paulo`, BRL com duas casas.
- Textos em português brasileiro, sentence case, verbos de ação; sem jargão de
  investimento e sem recomendação de compra/venda/alocação.
- Autenticação compatível com gerenciadores de senha e colagem.

## Direções rejeitadas

| Direção                                            | Por que foi rejeitada                                                               |
| -------------------------------------------------- | ----------------------------------------------------------------------------------- |
| Glassmorphism escuro com gradientes                | Reduz contraste, parece “fintech de trading” e não um registro doméstico confiável. |
| Tipografia manuscrita (Caveat/Quicksand)           | Prejudica leitura de números e passa informalidade excessiva em dados financeiros.  |
| Dashboard dominado por cards e gráficos grandes    | Esconde a granularidade e os totais; o produto é operacional e auditável.           |
| Fonte via Google Fonts                             | Dependência externa e violação da CSP self-hosted; privacidade.                     |
| Cores decorativas por categoria sem semântica fixa | Dificulta comparação entre telas e não sobrevive a daltonismo.                      |

## Regras de implementação

- Tokens ficam em `client/src/styles/tokens.css`; nenhum hexadecimal solto em
  componentes.
- Componentes base usam Tailwind 4 e utilitários semânticos (`bg-surface`,
  `text-ink`, `border-line`, `text-income`).
- Toda tela nova reutiliza `AppShell`, cabeçalho de página e estados de
  `DashboardStates`/equivalentes quando existirem.
- Antes de concluir uma tela: lint, typecheck, testes, axe (sem violações
  críticas/sérias) e inspeção em 360 px e 1440 px.
