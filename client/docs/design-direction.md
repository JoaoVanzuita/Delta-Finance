# Direção visual — Delta Finance

> **Status:** aprovada na Fase 0 da refatoração de UI/UX (30/10/2026).
> **Substitui integralmente** a direção anterior baseada em “livro-caixa”.
> **Fonte oficial** para tokens, componentes, telas, gráficos, temas e copy.
>
> Documentos relacionados:
> [`docs/refatoracao-ui-ux/00-baseline-e-decisoes.md`](../../docs/refatoracao-ui-ux/00-baseline-e-decisoes.md)
> e [`docs/refatoracao-ui-ux/relatorio-investigacao-tecnica.md`](../../docs/refatoracao-ui-ux/relatorio-investigacao-tecnica.md).
>
> **Estado de implementação:** fases 0–4 concluídas. Tokens, temas, primitivas,
> AppShell, Dashboard, operações financeiras, investimentos, patrimônio e metas
> já seguem esta direção. A Fase 5 migra configurações; até lá, telas ainda não
> redesenhadas podem manter a composição anterior. Não troque apenas os valores de `tokens.css` sem migrar os
> consumidores de `text-surface`, `primary` e `primary-hover`.

---

## 1. Tese

**Delta Finance é um painel financeiro pessoal e familiar: a tela deve responder,
em segundos, quanto a pessoa tem, quanto entrou, quanto saiu, como isso evolui e
o que precisa de atenção.**

A identidade é de produto financeiro moderno, organizado e confiável — não de
sistema contábil, livro-caixa administrativo ou terminal de trading. A referência
de experiência é o Mobills, usada como inspiração de hierarquia, clareza,
apresentação de saldos, distribuição por categorias, gráficos e organização de
módulos. Layouts, componentes proprietários e identidade de marca do Mobills não
são copiados.

### 1.1 Trabalho de cada tela

Toda tela deve deixar claro, na primeira dobra:

1. **Qual é o número principal** e a que período ele se refere.
2. **Se o dado está completo, atualizado e na moeda correta.**
3. **Qual é a próxima ação útil**, quando existe uma pendência.

Informação secundária continua disponível — em detalhe, drawer, popover ou
tabela acessível — mas não compete com o número principal.

### 1.2 Assinatura: o fio do período

A assinatura visual do Delta Finance é **o fio do período**: uma linha horizontal
fina, na cor de marca, que ancora o indicador principal e conecta visualmente o
seletor de período à nota de atualização/cobertura do dado. Ela aparece apenas
em painéis de resumo (Dashboard, Patrimônio, Investimentos) e no cabeçalho de
tabelas financeiras quando ajuda a separar contexto de valores.

O fio **codifica tempo e cobertura**, não decoração. Não é aplicado em todo card,
formulário ou lista.

### 1.3 Risco deliberado

O redesign abandona a neutralidade cromática da direção anterior: o indicador
principal pode ocupar uma superfície com tinta de marca, e categorias passam a
ter cor e ícone próprios. Isso é intencional para criar hierarquia e leitura
rápida. O risco é controlado por três regras não negociáveis:

- texto sempre com contraste medido no tema e na paleta ativos;
- cor nunca é o único indicador — sempre há rótulo, ícone, sinal ou padrão;
- valores, moedas, período e origem continuam explícitos.

---

## 2. Referência visual: o que aproveitar e o que não copiar

| Aproveitar do Mobills                                          | Não copiar                                                       |
| -------------------------------------------------------------- | ---------------------------------------------------------------- |
| Saldo e resultado em destaque, com o restante em segundo plano | Layout, telas, ilustrações ou componentes literais               |
| Categorias com cor e ícone, legíveis de relance                | Paleta, logotipo, tipografia ou marca do Mobills                 |
| Gráficos simples para evolução, composição e metas             | Gráficos decorativos, 3D ou com dados inventados                 |
| Navegação por áreas do dinheiro, não por tabelas do banco      | Padrões que escondem precisão, moeda ou período                  |
| Pouco texto e divulgação progressiva                           | Excesso de cards iguais ou de textos explicativos óbvios         |
| Sensação de produto leve para uso frequente                    | Tom de aconselhamento financeiro ou recomendação de investimento |

O Delta Finance mantém identidade própria: o símbolo Δ, a paleta curada, a
tipografia de sistema e o fio do período. A experiência é web, responsiva e
self-hosted, não uma cópia de aplicativo móvel.

---

## 3. Sistema de cor

### 3.1 Papéis semânticos

Nenhum componente usa hexadecimal solto. As cores abaixo são papéis; a
implementação usa tokens CSS e utilidades Tailwind 4.

| Papel          | Token                    | Claro                  | Escuro               | Uso                                       |
| -------------- | ------------------------ | ---------------------- | -------------------- | ----------------------------------------- |
| Canvas         | `--color-canvas`         | `#F5F7FB`              | `#0A1120`            | fundo geral da aplicação                  |
| Surface        | `--color-surface`        | `#FFFFFF`              | `#111A2A`            | cards, tabelas, diálogos, sidebar         |
| Surface muted  | `--color-surface-muted`  | `#EDF1F7`              | `#182335`            | cabeçalhos, áreas secundárias, hover      |
| Surface raised | `--color-surface-raised` | `#FFFFFF`              | `#162133`            | popover, drawer, menu, superfície elevada |
| Ink            | `--color-ink`            | `#0F172A`              | `#F1F5F9`            | texto principal                           |
| Ink muted      | `--color-ink-muted`      | `#475569`              | `#A7B4C6`            | rótulos, metadados, texto secundário      |
| Line           | `--color-line`           | `#D9E2EE`              | `#283850`            | divisores e bordas                        |
| Line strong    | `--color-line-strong`    | `#B8C5D6`              | `#3B4E68`            | bordas de controle e separadores fortes   |
| Primary        | `--color-primary`        | paleta                 | paleta               | marca, ação principal, item ativo         |
| Primary hover  | `--color-primary-hover`  | paleta                 | paleta               | hover/pressed da ação principal           |
| On primary     | `--color-on-primary`     | paleta                 | paleta               | texto/ícone sobre a cor principal         |
| Primary soft   | `--color-primary-soft`   | paleta                 | paleta               | fundo de destaque suave                   |
| Action         | `--color-action`         | paleta                 | paleta               | links e ações secundárias                 |
| Action hover   | `--color-action-hover`   | paleta                 | paleta               | hover de links                            |
| Income         | `--color-income`         | `#047857`              | `#34D399`            | receitas, resultado positivo, sucesso     |
| Expense        | `--color-expense`        | `#B91C1C`              | `#F87171`            | despesas, dívida, resultado negativo      |
| Warning        | `--color-warning`        | `#B45309`              | `#FBBF24`            | atenção, pendência, dado estimado         |
| Info           | `--color-info`           | `#0E7490`              | `#67E8F9`            | informação complementar                   |
| Focus          | `--color-focus`          | `#0284C7`              | `#7DD3FC`            | anel de foco acessível                    |
| Scrim          | `--color-scrim`          | `rgb(15 23 42 / 0.52)` | `rgb(2 6 23 / 0.72)` | fundo de overlays                         |

Fundos suaves de estado (`income-soft`, `expense-soft`, `warning-soft`,
`info-soft`, `primary-soft`) são derivados do papel correspondente com
`color-mix(in srgb, <cor> 12–16%, var(--color-surface))`. Não são cores novas.

Quando um estado precisar ser **preenchido**, use o par de contraste
correspondente em vez de assumir branco:

| Token                | Claro     | Escuro    |
| -------------------- | --------- | --------- |
| `--color-on-income`  | `#FFFFFF` | `#04211A` |
| `--color-on-expense` | `#FFFFFF` | `#2A0A0A` |
| `--color-on-warning` | `#FFFFFF` | `#1C1204` |
| `--color-on-info`    | `#FFFFFF` | `#06232A` |

### 3.2 Paletas de marca

A cor principal padrão é **azul**. As paletas são curadas; não há seletor de cor
livre. As cores semânticas de receita, despesa, erro e aviso **não mudam** com a
paleta escolhida.

#### Tema claro

| Paleta        | Primary   | Primary hover | On primary | Primary soft | Action    | Action hover |
| ------------- | --------- | ------------- | ---------- | ------------ | --------- | ------------ |
| Azul (padrão) | `#1D4ED8` | `#1E40AF`     | `#FFFFFF`  | `#E8EFFD`    | `#0369A1` | `#075985`    |
| Verde         | `#047857` | `#065F46`     | `#FFFFFF`  | `#E6F6EF`    | `#0F766E` | `#115E59`    |
| Roxo          | `#6D28D9` | `#5B21B6`     | `#FFFFFF`  | `#F1EBFE`    | `#7E22CE` | `#6B21A8`    |
| Âmbar         | `#B45309` | `#92400E`     | `#FFFFFF`  | `#FDF1DF`    | `#A16207` | `#854D0E`    |

#### Tema escuro

| Paleta        | Primary   | Primary hover | On primary | Primary soft | Action    | Action hover |
| ------------- | --------- | ------------- | ---------- | ------------ | --------- | ------------ |
| Azul (padrão) | `#60A5FA` | `#93C5FD`     | `#071426`  | `#16243B`    | `#7DD3FC` | `#BAE6FD`    |
| Verde         | `#34D399` | `#6EE7B7`     | `#04211A`  | `#102A24`    | `#6EE7B7` | `#A7F3D0`    |
| Roxo          | `#A78BFA` | `#C4B5FD`     | `#140A2E`  | `#241B3D`    | `#C4B5FD` | `#DDD6FE`    |
| Âmbar         | `#FBBF24` | `#FCD34D`     | `#1C1204`  | `#33270F`    | `#FCD34D` | `#FDE68A`    |

`on-primary` é definido por paleta e por tema — nunca presuma branco. A paleta
âmbar clara usa texto branco sobre `#B45309`; no escuro, usa texto escuro sobre
`#FBBF24`. A Fase 1 deve validar contraste de cada par com ferramenta automática
antes de liberar a troca de paleta.

### 3.3 Categorias

Categorias possuem identidade determinística no frontend, sem migration nesta
etapa. A cor é um apoio de leitura; o nome, o ícone e o sinal continuam
obrigatórios.

| Slots | Categoria predefinida                   | Claro     | Escuro    |
| ----- | --------------------------------------- | --------- | --------- |
| 1     | Moradia                                 | `#4F46E5` | `#818CF8` |
| 2     | Alimentação                             | `#C2410C` | `#FB923C` |
| 3     | Transporte                              | `#0369A1` | `#38BDF8` |
| 4     | Saúde                                   | `#BE123C` | `#FB7185` |
| 5     | Educação                                | `#7E22CE` | `#C084FC` |
| 6     | Lazer                                   | `#BE185D` | `#F472B6` |
| 7     | Compras                                 | `#B45309` | `#FBBF24` |
| 8     | Serviços                                | `#0F766E` | `#2DD4BF` |
| 9     | Salário                                 | `#047857` | `#34D399` |
| 10    | Investimentos (inclui Aportes/Resgates) | `#1D4ED8` | `#60A5FA` |
| 11    | Transferências (internas/externas)      | `#475569` | `#94A3B8` |
| 12    | Outros                                  | `#52525B` | `#A1A1AA` |

Regras:

- A chave estável é `predefinedKey` para categorias predefinidas e o `id` para
  categorias personalizadas. Não derive a cor do nome, que pode ser editado.
- Categorias personalizadas usam hash estável do `id` para escolher um dos 12
  slots; colisões são aceitáveis porque nome e ícone desambiguam.
- O ícone vem de um mapa estável de `predefinedKey`/palavras-chave, com fallback
  `Tag` (Lucide). Um mapa de apresentação no frontend é suficiente; não crie
  migration de cor/ícone nesta fase.
- Categorias de receita não usam a cor de “receita” como identidade; a direção do
  valor (entrada/saída) é comunicada por sinal, rótulo e cor semântica.
- Em gráficos, no máximo uma cor por categoria visível; “Outras” usa um tom
  neutro e não uma cor de categoria.

### 3.4 Regras de cor

- Texto normal ≥ 4,5:1; ícones, bordas de controle e elementos gráficos ≥ 3:1.
- Nunca use cor como único canal. Receita/despesa sempre têm sinal (`+`/`−`),
  rótulo textual e/ou ícone.
- Não use a cor semântica de despesa para “erro de formulário” sem distinguir
  mensagem e contexto; erro, dívida e valor negativo são conceitos diferentes.
- Não introduza cores arbitrárias em componentes. Cores de gráfico e categoria
  são tokens; cores calculadas são permitidas apenas em visualizações que
  derivam de tokens.
- O scrim deve isolar o overlay sem apagar o contexto; em tema escuro, verifique
  o contraste composto, não apenas o valor do token.

---

## 4. Temas e personalização

- Temas: `light`, `dark`, `system`. O padrão é `system`.
- Paletas: `blue` (padrão), `green`, `purple`, `amber`.
- Implementação por atributos no elemento raiz, por exemplo
  `<html data-theme="dark" data-accent="blue">`; os nomes podem ser adaptados à
  arquitetura, mas o contrato visual é este.
- Atualize `color-scheme` (`light dark`) e os tokens de overlay/scrim junto com o
  tema.
- Não deve haver flash de tema incorreto na inicialização: resolva o tema antes
  da primeira pintura, com script mínimo no `index.html` ou mecanismo
  equivalente, sem bloquear a renderização.
- A preferência é local ao navegador nesta etapa. Não crie endpoint ou migration
  para preferência visual. Persista somente tema e paleta — nunca dados
  financeiros.
- Quando houver identificador estável de usuário, use uma chave namespaced por
  usuário (por exemplo `delta.prefs.<userId>.theme`) para evitar que duas contas
  no mesmo navegador compartilhem preferências. Se isso não for possível antes
  da autenticação, documente a limitação e mantenha a chave apenas com
  preferências visuais, sem PII.
- A troca de paleta não altera `income`, `expense`, `warning`, `info` nem os
  estados de erro/sucesso. Erro usa `--color-expense`; sucesso usa
  `--color-income`; ambos permanecem fixos em todas as paletas.
- Teste contraste nos dois temas e nas quatro paletas, incluindo foco, disabled,
  hover, links, gráficos e scrim.

---

## 5. Tipografia

Uma única pilha de sistema, sem CDN e sem dependência externa:

```text
ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif
```

| Papel         | Token                  | Tamanho / linha                   | Peso    | Uso                                                   |
| ------------- | ---------------------- | --------------------------------- | ------- | ----------------------------------------------------- |
| Display       | `--text-display`       | `clamp(2rem, 5vw, 2.75rem)` / 1.1 | 700     | número-âncora do Dashboard/Patrimônio                 |
| Page title    | `--text-page-title`    | `1.75rem` / 1.2                   | 700     | `h1` de página                                        |
| Section title | `--text-section-title` | `1.125rem` / 1.4                  | 600     | `h2`/`h3` de seção                                    |
| Body          | `--text-body`          | `1rem` / 1.55                     | 400     | texto corrente                                        |
| Body small    | `--text-body-sm`       | `0.875rem` / 1.5                  | 400/500 | tabelas e listas densas                               |
| Caption       | `--text-caption`       | `0.75rem` / 1.5                   | 400/500 | metadados, notas, timestamps                          |
| Overline      | `--text-overline`      | `0.6875rem` / 1.2                 | 600     | rótulo de grupo, `letter-spacing: 0.08em`, caixa alta |
| Metric XL     | `--text-metric-xl`     | `clamp(2rem, 5vw, 3.5rem)` / 1.05 | 700     | indicador principal                                   |
| Metric LG     | `--text-metric-lg`     | `1.75rem` / 1.15                  | 700     | indicador secundário forte                            |
| Metric MD     | `--text-metric-md`     | `1.375rem` / 1.2                  | 600     | valores de objeto                                     |
| Metric SM     | `--text-metric-sm`     | `1.125rem` / 1.3                  | 600     | valores em linhas e cards compactos                   |

Regras:

- Todo valor monetário, percentual e data usa `font-variant-numeric:
tabular-nums` (classe `.numeric`).
- O número principal é o maior elemento tipográfico da tela; valores
  secundários nunca competem com ele.
- Valores à direita em tabelas e listas; rótulos à esquerda.
- Não use caixa alta em nomes de categoria, descrições ou botões. Overline é
  reservado a grupos e rótulos de contexto.
- Medida de leitura: 60–75 caracteres. Não use parágrafos longos em cards.
- Não use fonte manuscrita, display decorativa ou monoespaçada para dados.

---

## 6. Espaço, forma, elevação e densidade

### 6.1 Espaçamento

Base 4 px, ritmo 4/8/12/16/24/32/48/64/80.

| Token       | Valor | Uso típico                          |
| ----------- | ----- | ----------------------------------- |
| `--space-1` | 4 px  | ajuste óptico, gap de ícone         |
| `--space-2` | 8 px  | gap interno compacto                |
| `--space-3` | 12 px | padding de célula/linha             |
| `--space-4` | 16 px | padding padrão de card e formulário |
| `--space-5` | 24 px | separação entre blocos              |
| `--space-6` | 32 px | seção                               |
| `--space-7` | 48 px | seção ampla / topo de página        |
| `--space-8` | 64 px | intervalo editorial raro            |
| `--space-9` | 80 px | apenas hero público                 |

Conteúdo máximo: 1280 px em telas largas; 1440 px apenas para tabelas densas.
Formulários: 520–640 px. Texto corrente: 60–75 ch.

### 6.2 Raios

| Token              | Valor  | Uso                                |
| ------------------ | ------ | ---------------------------------- |
| `--radius-control` | 10 px  | botões, inputs, selects            |
| `--radius-card`    | 14 px  | cards de objeto e indicadores      |
| `--radius-panel`   | 18 px  | hero, drawer, dialog, sidebar      |
| `--radius-pill`    | 999 px | somente chips de status e avatares |

Botões não são pílulas. Não arredonde tudo com o mesmo raio.

### 6.3 Elevação e bordas

- Agrupamento usa **borda de 1 px** e contraste de superfície; sombra não
  substitui hierarquia.
- `--shadow-card`: sombra sutil para objetos elevados sobre o canvas.
- `--shadow-overlay`: sombra forte apenas para dialog, drawer, popover e menu.
- Em tema escuro, reduza sombras e reforce bordas/superfícies; sombra preta sobre
  fundo escuro quase não comunica elevação.
- Não use sombra em todos os cards. Não use “card dentro de card”.

### 6.4 Densidade

- **Confortável** (padrão): 16–24 px de padding; uso em Dashboard, planejamento e
  formulários.
- **Compacta**: 8–12 px de padding vertical; uso em tabelas, listas de
  movimentações, faturas e configurações.
- Linhas de tabela: 44 px mínimos no desktop; 48 px no mobile.
- A densidade é uma decisão por tela, não um estilo global aplicado a tudo.
- Tokens de densidade: `--density-pad-comfortable` (16 px),
  `--density-pad-compact` (10 px), `--density-row-comfortable` (48 px),
  `--density-row-compact` (40 px). No mobile, o alvo de toque nunca fica abaixo
  de 44 px, mesmo na densidade compacta.

---

## 7. Superfícies e cards

| Tipo                     | Aparência                                                                     | Uso                                    | Não usar para                          |
| ------------------------ | ----------------------------------------------------------------------------- | -------------------------------------- | -------------------------------------- |
| **Hero de resultado**    | Superfície ampla, `primary-soft` ou tinta de marca, número XL, fio do período | indicador principal da tela            | listas, formulários, avisos            |
| **Indicador secundário** | Card compacto, borda 1 px, número LG, rótulo e nota de período                | receitas, despesas, saldo, dívida      | repetir o mesmo valor do hero          |
| **Card de objeto**       | Superfície, cabeçalho com ícone/nome, valor, metadados, ações                 | conta, cartão, posição, meta, conexão  | agrupar seções inteiras                |
| **Painel de formulário** | Superfície plana, seções com título, sem sombra                               | criação/edição                         | decorar a página                       |
| **Aviso/alerta**         | Faixa inline com ícone, título curto, texto e ação                            | pendência, erro, cobertura, estimativa | esconder informação crítica em tooltip |
| **Grupo**                | Título de seção + divisor + conteúdo                                          | agrupar objetos relacionados           | transformar cada linha em card         |
| **Conteúdo secundário**  | Texto/caption, tabela recolhível, disclosure                                  | origem, cobertura, detalhe técnico     | competir com o número principal        |

Regras:

- Cada card tem uma finalidade nomeável. Se dois cards mostram o mesmo valor, um
  deles deve sair.
- Não aninhe cards. Um card de objeto pode conter seções e divisores, não outro
  card com borda e sombra.
- O hero não repete todos os indicadores; ele apresenta o principal e deixa os
  demais em uma faixa de indicadores secundários.
- Estados vazios, erros e avisos não usam o mesmo tratamento visual de um objeto
  financeiro.

---

## 8. Gráficos e visualização

Gráficos existem para interpretar dados reais, não para preencher espaço.

| Objetivo                         | Gráfico                     | Quando usar                                      | Evitar                                          |
| -------------------------------- | --------------------------- | ------------------------------------------------ | ----------------------------------------------- |
| Evolução temporal                | linha ou área               | receitas, despesas e resultado ao longo do tempo | pizza, barras por dia em séries longas          |
| Composição de despesas           | donut                       | sempre; top 8 + “Outras” quando necessário       | muitas fatias sem agrupamento, 3D, explosão     |
| Composição de receitas           | donut                       | abas Despesas/Receitas na mesma seção            | dois gráficos full-width empilhados             |
| Ranking/comparação de categorias | barras horizontais          | rankings fora da composição do dashboard         | donut ilegível, tabela sem destaque             |
| Comparação temporal              | barras verticais            | meses, semanas, períodos discretos               | linha para poucos pontos                        |
| Meta                             | barra de progresso          | progresso planejado vs. alvo                     | gauge decorativo                                |
| Alocação de investimentos        | donut ou barras horizontais | uma única representação principal                | repetir o mesmo dado em barras + tabela + texto |

Regras obrigatórias:

- Use SVG/CSS próprio inicialmente. Não instale biblioteca de gráficos sem
  necessidade medida (interação, zoom, séries longas) e justificativa técnica.
- Todo gráfico tem resumo textual e tabela equivalente acessível. A tabela pode
  ficar em disclosure, mas precisa existir e refletir os mesmos dados.
- Tooltip mostra período, série e valor; deve ser alcançável por teclado e não
  depender de hover.
- Não invente pontos, categorias, séries ou valores. Lacunas são lacunas.
- Não plote séries históricas de patrimônio ou investimentos enquanto não
  existirem snapshots (Fase 7). Não use a data atual para simular passado.
- Para períodos longos, agregue visualmente e preserve o total; ofereça a
  granularidade detalhada na tabela. Acima de ~120 pontos, agregue por mês ou
  intervalo, salvo necessidade explícita.
- Séries múltiplas usam cor + estilo de linha (sólida/tracejada) + rótulo direto;
  não dependa só de cor.
- Não use eixo duplo para misturar unidades diferentes.
- Percentuais de utilização devem declarar a base (usado/limite ou
  devido/limite) e não misturar bases no mesmo indicador.

---

## 9. Navegação e arquitetura da informação

### 9.1 Grupos

| Grupo             | Itens                                         | Rotas existentes                                                                   |
| ----------------- | --------------------------------------------- | ---------------------------------------------------------------------------------- |
| **VISÃO GERAL**   | Dashboard                                     | `/dashboard`                                                                       |
| **MEU DINHEIRO**  | Contas, Cartões, Transações                   | `/accounts`, `/cards`, `/transactions`                                             |
| **PLANEJAR**      | Investimentos, Patrimônio, Metas              | `/investments`, `/wealth`, `/goals`                                                |
| **ORGANIZAR**     | Categorias, Reconciliação                     | `/categories`, `/reconciliation`                                                   |
| **CONFIGURAÇÕES** | Preferências*, Integração, Privacidade, Conta | `/settings/preferences`*, `/settings/integration`, `/settings/privacy`, `/account` |

\* `Preferências` foi criada na Fase 5, com tema, paleta e prévia. Integração,
Privacidade e Conta compartilham a navegação secundária e mantêm URLs próprias.
Nenhum recurso existente é removido.

### 9.2 Ícones (Lucide)

| Item          | Ícone sugerido     |
| ------------- | ------------------ |
| Dashboard     | `LayoutDashboard`  |
| Contas        | `Wallet`           |
| Cartões       | `CreditCard`       |
| Transações    | `ArrowLeftRight`   |
| Investimentos | `TrendingUp`       |
| Patrimônio    | `Landmark`         |
| Metas         | `Target`           |
| Categorias    | `Tags`             |
| Reconciliação | `GitCompareArrows` |
| Preferências  | `Palette`          |
| Integração    | `Plug`             |
| Privacidade   | `ShieldCheck`      |
| Conta         | `UserRound`        |

Use um único estilo (outline), espessura consistente, `aria-hidden` em ícones
decorativos e nome acessível em ações somente com ícone.

### 9.3 Layout por viewport

- **Desktop (≥ 1024 px):** sidebar fixa de 248–264 px, agrupada, com marca no
  topo, itens com ícone + rótulo, estado ativo inequívoco e indicador de ação
  necessária quando houver. Conteúdo em coluna fluida com largura máxima.
- **Tablet (768–1023 px):** navegação compacta em rail de ícones (72–80 px) ou
  drawer; rótulos acessíveis por tooltip/nome, sem perder destinos. Conteúdo em
  uma ou duas colunas.
- **Mobile (< 768 px):** top bar com marca, título curto da página e botão de
  menu (44 × 44 px); navegação em drawer modal, com grupos, foco preso, `Escape`,
  retorno de foco e fechamento ao navegar. Nunca use uma faixa horizontal com 13
  itens.
- A navegação é renderizada uma única vez; evite landmarks duplicados.

### 9.4 Público versus autenticado

- `/`, `/login` e `/register` usam layout público, sem sidebar autenticada.
- Usuário autenticado em `/` é levado a `/dashboard`.
- Após login/cadastro, o destino padrão é `/dashboard`.
- `state.from` e deep links continuam preservados; um link protegido deve voltar
  ao destino original após autenticar.
- O 404 usa o layout correspondente ao estado da sessão e oferece caminho de
  volta; não mostra sidebar autenticada para visitante.
- Rotas existentes são preservadas. Novas rotas de configurações têm URLs
  próprias e não quebram links antigos.

### 9.5 Cabeçalho de página

Toda página autenticada tem:

- `h1` único e descritivo;
- contexto curto (período, moeda, origem) quando necessário;
- ação principal no máximo uma;
- ações secundárias em menu ou botão secundário;
- nota de atualização/cobertura quando o dado pode estar incompleto.

---

## 10. Padrões de interação

| Padrão                     | Usar para                                                                          | Evitar                                                     |
| -------------------------- | ---------------------------------------------------------------------------------- | ---------------------------------------------------------- |
| **Tabs**                   | seções relacionadas do mesmo objeto (cartão, investimentos, configurações)         | esconder seções não relacionadas ou criar tabs decorativas |
| **Drawer**                 | detalhe contextual, edição rápida, filtros avançados, detalhe de transação/posição | formulários longos com muitas etapas                       |
| **Dialog**                 | operação atômica, confirmação, pagamento, item de fatura                           | navegação entre páginas                                    |
| **Página própria**         | fluxos complexos, deep link, formulário longo, detalhe de fatura                   | conteúdo que cabe em um drawer                             |
| **Popover**                | filtros compactos, seleção, menu de ações                                          | conteúdo essencial que precisa estar sempre visível        |
| **Toast**                  | confirmação não crítica, cópia, ação concluída                                     | erro que exige decisão                                     |
| **Alerta persistente**     | ação necessária, reconexão, dados desatualizados                                   | sucesso efêmero                                            |
| **Empty state**            | ausência de dados com causa e próximo passo                                        | “Nenhum item” sem orientação                               |
| **Divulgação progressiva** | campos avançados, detalhes técnicos, cobertura                                     | esconder o campo principal                                 |

Regras:

- Filtros: período e resumo sempre visíveis; filtros avançados em popover/drawer.
  Preserve todos os filtros existentes, a validação de intervalo e a semântica dos
  endpoints. Filtros do Dashboard e de Transações vivem na URL (search params),
  com suporte a voltar/recarregar.
- Formulários: primeiro o tipo de movimentação, depois conta, valor, data,
  descrição e categoria; campos condicionais aparecem só quando aplicáveis;
  observações e detalhes ficam em seção secundária. Mostre um resumo do efeito
  (entrada/saída/transferência) antes de salvar.
- Preserve dados preenchidos quando houver erro de API. Não feche o diálogo
  perdendo o formulário.
- Ações destrutivas exigem confirmação explícita com o nome do objeto e resumo
  das consequências. “Remover conexão” e “excluir histórico” não podem parecer a
  mesma operação.
- Toasts usam `role="status"`; erros usam `role="alert"`. Não empilhe toasts
  infinitos.
- Toda ação assíncrona tem estado pendente, disabled e feedback. Não use apenas
  “Carregando…” quando um skeleton preserva o layout.

---

## 11. Responsividade

Breakpoints de referência (Tailwind 4): `sm` 640, `md` 768, `lg` 1024, `xl` 1280,
`2xl` 1440.

| Contexto              | Comportamento                                                                                      |
| --------------------- | -------------------------------------------------------------------------------------------------- |
| **Desktop ≥ 1280**    | sidebar completa, grids de 2–4 colunas, gráficos lado a lado, tabelas completas                    |
| **Desktop 1024–1279** | sidebar completa ou compacta; grids de 2–3 colunas; tabelas com colunas essenciais                 |
| **Tablet 768–1023**   | rail/drawer, conteúdo em 1–2 colunas, tabelas convertidas em listas quando necessário              |
| **Mobile 360–767**    | top bar + drawer, conteúdo empilhado, cards de largura total, ações fixas/sticky quando necessário |

Regras:

- Nenhuma rolagem horizontal da página em 360 px ou zoom 200%.
- Tabelas: cabeçalho semântico no desktop; no mobile, lista de cards com
  rótulo/valor por campo (`dl`), mantendo a mesma informação.
- Drawers e dialogs ocupam a largura disponível no mobile, com header/footer
  sticky e alvos de toque de 44 px.
- Formulários: uma coluna no mobile; duas no desktop quando o campo for curto.
- Gráficos mantêm legibilidade: reduza rótulos, não remova a tabela acessível.
- Teste em 360 × 800, 390 × 844, 768 × 1024, 1280 × 900 e 1440 × 900, nos temas
  claro e escuro.

---

## 12. Princípios de UX

1. **Insight antes de filtro.** O usuário vê o resultado e o contexto antes de
   escolher filtros avançados.
2. **Um número principal por tela.** O restante é hierarquia, não uma grade de
   números iguais.
3. **Menos texto, mais sinal.** Remova explicações óbvias; mantenha a origem, a
   cobertura, a data e a precisão acessíveis.
4. **Não esconda o que dá confiança.** Moeda, período, fonte, status de
   atualização e estimativa ficam visíveis ou a um clique.
5. **Cor não é o único canal.** Sempre há rótulo, ícone, sinal ou padrão.
6. **Nada de rótulo interno.** “Fase secundária”, “Fase posterior”, “MVP”,
   “itemId”, “Bill oficial”, “critérios v1”, “espelho de transação” e
   `EXPENSE_INCREASE` não são linguagem de usuário. Traduza para o efeito real.
7. **Contexto preservado.** Abrir detalhe, editar e voltar não deve perder
   filtros, período, aba ou posição de rolagem.
8. **Fluxos previsíveis.** O mesmo tipo de ação usa o mesmo padrão em todas as
   telas.
9. **Estimado não é realizado.** Resultado estimado, patrimônio estimado e
   cobertura desconhecida têm rótulo e explicação próprios.
10. **Planejado não é movimentação.** Alocações de meta são planejamento; não
    parecem dinheiro reservado ou transferido.
11. **Transparência sobre a cobertura.** Ausência de dado não é saldo zero.
12. **Sem promessa de histórico.** Não mostre gráfico histórico de patrimônio ou
    investimentos antes de existir fonte real.
13. **Sem aconselhamento.** Não recomende compra, venda ou alocação; descreva
    dados e consequências.
14. **Português brasileiro, sentence case, verbo de ação.** Use “Salvar
    alterações”, não “Submeter”. Evite jargão técnico e caixa alta.

---

## 13. Integridade financeira

O design não pode mudar a interpretação dos valores. A fonte de verdade é o
backend; a UI formata e organiza, mas não recalcula totais financeiros.

### 13.1 Nomenclatura

| Conceito                            | Nome na interface                     | Regra                                                                  |
| ----------------------------------- | ------------------------------------- | ---------------------------------------------------------------------- |
| `periodResult`                      | **Resultado do período**              | receitas − despesas, conforme a regra vigente do backend               |
| `income`                            | **Receitas**                          | não inclui transferências nem itens de fatura                          |
| `expenses`                          | **Despesas**                          | segue a regra atual; aportes ainda não são classificados separadamente |
| `currentBalance`                    | **Saldo em contas**                   | contas ativas BANK/CASH/WALLET em BRL; informar a data de referência   |
| `cardOutstandingBalance`            | **Saldo devedor de cartões**          | dívida; nunca somar ao saldo em contas como se fosse ativo             |
| `pendingIncome` / `pendingExpenses` | **A receber / A pagar (pendentes)**   | valores separados; não criar um “resultado pendente” líquido           |
| `grossAssets`                       | **Ativos**                            | bens e saldos que compõem o patrimônio                                 |
| `liabilities`                       | **Passivos**                          | dívidas, incluindo cartões; agrupar separadamente                      |
| `netWorth`                          | **Patrimônio líquido**                | ativos − passivos                                                      |
| `incomeLast12Months`                | **Proventos recebidos (12 meses)**    | proventos importados e manuais; não são receita do Dashboard hoje      |
| Resultado do provedor               | **Resultado informado pelo provedor** | não chamar de rentabilidade realizada                                  |
| `estimatedProfit`                   | **Resultado estimado**                | estimativa; nunca apresentar como realizado                            |
| Alocações de meta                   | **Alocações planejadas**              | não são movimentações financeiras reais                                |
| Progresso por alocação              | **Progresso por alocações**           | distinto de progresso por patrimônio líquido                           |

### 13.2 Regras

- Não calcule novamente no frontend valores que já têm regra consolidada no
  backend (resultado, saldo, dívida, patrimônio, progresso, resultado estimado).
- Não altere sinais, tipos, fórmulas, arredondamentos ou regras de
  transferência/reembolso/reconciliação.
- Não some moedas distintas. Consolide apenas BRL; exiba outras moedas na moeda
  original, com código (`USD`, `EUR`) e sem símbolo de R$.
- Se um agregado do backend misturar moedas, não o apresente como total
  confiável; mostre os itens por moeda e registre a dependência de correção.
  No estado atual, dashboard e patrimônio já excluem cartões não-BRL dos
  agregados; a UI continua exibindo o cartão individual na moeda original.
- Valores monetários chegam como string decimal; formate sem converter para
  `number` de ponto flutuante quando isso puder perder precisão.
- Formate negativos com sinal de menos (−) e mantenha o valor absoluto legível.
  A cor de despesa é apoio, não o único sinal.
- Percentuais: uma casa decimal (`12,3%`), exceto inteiros exatos (`50%`).
  Nunca misture `50%` e `50,00%` na mesma tela.
- Datas: `dd/MM/yyyy` em textos e campos; `America/Sao_Paulo`; datas relativas
  (“hoje”, “ontem”, “há 3 dias”) apenas quando não houver ambiguidade.
- “Fatura atual” significa fatura **OPEN** do ciclo corrente. Fatura paga,
  fechada ou vencida recebe “Última fatura”/“Fatura fechada” e não oferece ações
  incompatíveis com o estado.
- “Compras não alocadas” lista somente débitos. Créditos e estornos aparecem
  separados como “Créditos não alocados”; nunca como compra.
- Proventos não entram nas receitas do Dashboard nesta fase. A UI deve dizer
  isso de forma direta, sem prometer integração futura como se já existisse.
- Não prometa histórico patrimonial ou de investimentos. O histórico é a Fase 7
  e exige snapshots, endpoint e migration próprios.
- Toda estimativa, lacuna ou dado desatualizado tem rótulo visível e data de
  referência. “Não compartilhado pelo provedor” ≠ “saldo zero”.
- A origem do dado (importado/manual/provedor) fica acessível no detalhe, sem
  dominar a visão principal.

---

## 14. Acessibilidade

Meta: **WCAG 2.1 AA** em todas as rotas, estados e temas.

- Texto normal ≥ 4,5:1; texto grande ≥ 3:1; componentes, ícones e bordas de
  controle ≥ 3:1.
- Foco visível de 2 px com offset; nunca remova o outline sem substituto
  equivalente. Em superfícies coloridas, garanta contraste do anel.
- Skip link, landmarks (`header`, `nav`, `main`, `footer`), um `h1` por página e
  hierarquia de headings sem saltos.
- Navegação completa por teclado; ordem de foco segue a ordem visual.
- Dialogs/drawers: foco inicial, trap, `Escape` quando permitido, retorno ao
  elemento que abriu, fundo `inert`/não interativo e scroll lock.
- Tabs seguem o padrão ARIA (`tablist`, `tab`, `tabpanel`, setas, Home/End).
- Tabelas têm `caption`, cabeçalhos com `scope` e associação correta.
- Gráficos têm `figure`/`figcaption`, resumo textual e tabela equivalente.
- Toasts usam `aria-live="polite"`; erros usam `role="alert"`.
- Alvos de toque ≥ 44 × 44 px; no mobile, ≥ 48 px quando possível.
- `prefers-reduced-motion` remove animações não essenciais.
- Não dependa de hover, cor, gesto ou arrastar para concluir uma tarefa.
- Teste com zoom 200% e largura de 360 px; não deve haver corte ou overflow
  horizontal.
- Dark mode é testado independentemente; contraste não é inferido do light.

---

## 15. Movimento

- Transições de 120–200 ms em cor, opacidade, elevação e transformação.
- Tokens: `--motion-fast` (120 ms), `--motion-base` (180 ms),
  `--ease-standard` (`cubic-bezier(0.2, 0, 0, 1)`).
- Nada de contagem animada de valores, parallax ou animação decorativa em
  gráficos.
- Skeletons preservam o layout; spinners são reservados a ações inline.
- `prefers-reduced-motion: reduce` remove transições não essenciais e mantém
  feedback de estado por texto/ícone.
- Abertura de drawer/dialog pode usar fade/translate curto; nunca bloqueia a
  leitura nem atrasa a ação.

---

## 16. Direções rejeitadas

| Direção                                                             | Por que foi rejeitada                                                            |
| ------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| Cópia literal do Mobills                                            | Perde identidade, viola marca e não considera o contexto web/self-hosted.        |
| Livro-caixa administrativo, com tudo em tabela e mesma hierarquia   | Não responde às perguntas do usuário e foi a direção substituída.                |
| Glassmorphism, neon ou gradientes chamativos                        | Reduz contraste, parece “fintech de trading” e não transmite controle doméstico. |
| Fontes externas (Google Fonts) ou manuscritas                       | Violam CSP/self-hosting, criam dependência de privacidade e prejudicam números.  |
| Todos os blocos como cards iguais                                   | Apaga a diferença entre indicador, objeto, formulário e aviso.                   |
| Dashboard dominado por filtros ou por gráficos decorativos          | Esconde o insight e a granularidade; gráfico sem tabela não é auditável.         |
| Cores livres por categoria ou cor como único canal                  | Quebra contraste e não sobrevive a daltonismo.                                   |
| Histórico patrimonial “estimado” com data atual                     | Inventa passado e destrói a confiança nos dados.                                 |
| Esconder moeda, período, cobertura ou precisão para “limpar” a tela | Minimalismo não pode custar transparência financeira.                            |
| Pílulas em todos os botões e raios exagerados                       | Reduz hierarquia e legibilidade de ferramenta operacional.                       |

---

## 17. Regras de implementação e definição de pronto

### 17.1 Tokens e componentes

- Tokens ficam em `client/src/styles/tokens.css`, com pares claro/escuro e
  `data-accent`.
- Componentes usam utilidades semânticas (`bg-surface`, `text-ink`,
  `border-line`, `text-income`, `bg-primary`, `text-on-primary`). Não use
  hexadecimal solto.
- Primitivas em `client/src/components/ui/`; composição de classes com `cn()` e
  variantes com `class-variance-authority`, que já estão instalados.
- Cada primitiva tem props tipadas, variantes explícitas, estados
  disabled/loading/error quando aplicável, foco visível, semântica correta,
  suporte a dark e teste.
- Não crie componente sem consumidor real. Não crie componente gigante com
  múltiplas responsabilidades.
- Formatação compartilhada em utilitários (`Intl.NumberFormat`/
  `Intl.DateTimeFormat`, pt-BR), com moeda explícita e sem float para dinheiro.

### 17.2 Preservação

- Não altere cálculos, regras, contratos OpenAPI, endpoints, migrations ou
  integração Pluggy para atender ao visual.
- Não remova funcionalidades, rotas ou deep links.
- Não armazene dados financeiros em `localStorage`; apenas preferências visuais
  não sensíveis.
- Não use dados mockados em produção. Mocks são permitidos em testes,
  screenshots e stubs de desenvolvimento.

### 17.3 Checklist de tela

Antes de considerar uma tela concluída:

- hierarquia: número principal evidente na primeira dobra;
- estados: loading, vazio, erro, parcial, sem permissão e dados desatualizados;
- responsividade: 360, 390, 768, 1280 e 1440 px, sem overflow;
- temas: claro e escuro; paleta padrão e uma alternativa;
- acessibilidade: teclado, foco, headings, labels, contraste e axe sem
  violações críticas/sérias;
- dados: moeda, período, origem, cobertura e estimativa corretos;
- testes: unitário/componente, E2E relevante, lint, typecheck e build;
- screenshots atualizados quando a fase exigir;
- nenhuma regra financeira alterada sem decisão e teste específicos.

### 17.4 Governança

Mudanças nesta direção exigem atualização deste documento e registro no relatório
da fase. Conflitos com código existente são resolvidos a favor da integridade
financeira, acessibilidade e contratos — nunca por conveniência visual.

### 17.5 Carregamento e desempenho

- Páginas de negócio são carregadas sob demanda por rota. O fallback de
  carregamento é um esqueleto com `role="status"` e fica **dentro** do layout
  autenticado, preservando sidebar e navegação secundária.
- Login, cadastro e Home pública permanecem no chunk principal por serem a porta
  de entrada.
- Não use skeleton decorativo sem consulta correspondente; não use spinner de
  página inteira quando o conteúdo principal já pode ser renderizado.
- Otimizações só entram com evidência de bundle, requisições repetidas ou
  renderização excessiva. O aviso de chunk do Vite é um sinal, não uma meta
  isolada.
