# Funcionalidades do Delta Finance

**Data de referência:** 2026-10-25  
**Escopo:** comportamento implementado no backend (`server/`) e no frontend (`client/`), confrontado com `specs/001-finance/`.  
**Público:** desenvolvedores, operadores e responsáveis pelo produto.

> Este documento descreve o que o sistema faz **hoje**, não apenas o que a
> especificação planeja. Onde o código não entrega o comportamento completo da
> spec, a limitação é marcada como **Parcial** ou **Não implementado**. O
> contrato REST está em
> [`specs/001-finance/contracts/openapi.yaml`](../specs/001-finance/contracts/openapi.yaml).

## Legenda de estado

| Estado | Significado |
|---|---|
| **Implementado** | O fluxo está disponível na API e/ou na interface e segue a regra principal da spec. |
| **Parcial** | Existe implementação, mas há limitação funcional, de contrato ou de cobertura de UI. |
| **Não implementado** | O requisito documentado não tem código correspondente no fluxo principal. |

## 1. Visão geral

O Delta Finance é uma aplicação web self-hosted de finanças pessoais para um
grupo familiar. Cada pessoa tem uma conta individual e enxerga somente as
próprias contas, transações, faturas, integrações, investimentos, patrimônio e
metas. O sistema não compartilha credenciais bancárias, não movimenta dinheiro,
não cria ordens e não recomenda investimentos.

Premissas operacionais implementadas:

- moeda base BRL;
- fuso `America/Sao_Paulo`;
- semana iniciada na segunda-feira;
- dados financeiros sensíveis cifrados em repouso;
- API same-origin com sessão por cookie e proteção CSRF;
- operação por Docker Compose, PostgreSQL e Caddy;
- integração bancária pela modalidade pessoal **Meu Pluggy**.

## 2. Arquitetura funcional

### 2.1 Backend

O backend é um monólito modular Spring Boot. Os módulos principais são:

| Módulo | Responsabilidade |
|---|---|
| `auth` | Cadastro, login, logout, senha, sessão, política de senha e recuperação assistida. |
| `security` | Filtro de sessão, CSRF, isolamento, `AuthenticatedUser`, tratamento 401/403. |
| `integrations` | Credenciais pessoais do Meu Pluggy e cache de API Key. |
| `connections` | Conexões (`itemId`), sincronização, fila, estados e reconciliação de item. |
| `accounts` | Contas importadas, contas manuais, cartões e perfil de cartão. |
| `transactions` | Importação idempotente, lançamentos manuais, correções e remoção na origem. |
| `categories` | Categorias predefinidas e personalizadas. |
| `reconciliation` | Sugestões de transferência e pagamento de fatura. |
| `invoices` | Ciclos, faturas, itens, pagamentos e créditos. |
| `reporting` | Dashboard, séries temporais, distribuição por categoria e drill-down. |
| `privacy` | Exportação, desconexão, exclusão de conta e retenção. |
| `investments` | Posições manuais de investimento. |
| `wealth` | Patrimônio consolidado e ativos/passivos manuais. |
| `goals` | Metas, alocações explícitas, progresso e histórico. |
| `shared` | Dinheiro, tempo, criptografia, auditoria, paginação, erros e configuração. |

### 2.2 Frontend

A SPA usa React, React Router, TanStack Query, React Hook Form, Zod, Tailwind CSS
e componentes locais. A composição principal está em `client/src/app/`, as telas
em `client/src/features/<domínio>/pages/` e os componentes reutilizáveis em
`client/src/components/`.

## 3. Identidade, sessão e isolamento

**Estado:** Implementado.

### 3.1 Cadastro e login

- Cadastro com nome de usuário único e senha.
- O nome é normalizado com NFKC, remoção de espaços nas pontas e minúsculas para
  unicidade e login; a forma de exibição preserva a capitalização.
- O nome deve ter entre 3 e 40 caracteres e não pode conter caracteres de
  controle ISO.
- Mensagens de login não distinguem usuário inexistente de senha incorreta.
- O custo de verificação de senha é equalizado para usuários inexistentes,
  reduzindo enumeração por tempo de resposta.
- Cadastro limitado a 5 tentativas por hora por IP, em memória, antes de calcular
  o hash da senha; o excesso retorna `429` com `Retry-After`.

### 3.2 Política de senha

- 12 a 128 caracteres (contagem por code points).
- Não exige composição de maiúsculas, números ou símbolos; frases-senha são
  aceitas.
- Não pode ser igual nem conter o nome de usuário normalizado.
- Não pode estar na lista local versionada de senhas comuns
  (`server/src/main/resources/security/common-passwords-v1.txt`, 10.001 linhas).
- A mesma política vale para cadastro e troca de senha.
- Argon2id com `m=19 MiB`, `t=2`, `p=1`, salt de 16 bytes e hash de 32 bytes.

### 3.3 Sessão e CSRF

- Sessão persistida no PostgreSQL via Spring Session JDBC.
- Cookie `__Host-delta_session`, `HttpOnly`, `Secure` (configurável em
  desenvolvimento), `SameSite=Lax`, `Path=/`.
- Expiração por inatividade: 30 minutos.
- Expiração absoluta: 12 horas, verificada por
  `AbsoluteSessionTimeoutFilter`.
- CSRF por cookie `XSRF-TOKEN` + cabeçalho `X-XSRF-TOKEN` nas mutações.
- Troca de ID de sessão no login para prevenir fixação.
- O cliente expira a sessão após 30 minutos sem atividade ou 12 horas do login,
  revalida ao voltar o foco/visibilidade e limpa todo o cache financeiro em 401,
  logout, timeout ou troca de usuário.
- Logout invalida a sessão no servidor e o frontend limpa o cache de consultas.
- Respostas 401/403 são JSON (`ProblemDetail`), sem redirecionamento HTML.

### 3.4 Limitação de tentativas

- 5 falhas em 15 minutos por combinação de usuário normalizado + IP.
- Bloqueio de 15 minutos persistido a partir da falha que atingiu o limite;
  sobrevive à janela deslizante e a reinícios.
- Uma autenticação bem-sucedida zera o contador da combinação.
- Usuário e IP são persistidos apenas como HMAC.
- O bloqueio responde `429` com `Retry-After`.
- Cadastro tem limite separado de 5 tentativas por hora por IP, em memória.

### 3.5 Isolamento entre usuários

- Todo recurso financeiro tem `user_id`, e as consultas usam o usuário da sessão,
  nunca um `user_id` enviado pelo cliente.
- As tabelas usam chaves únicas compostas e FKs compostas `(user_id, id)` para
  impedir associação cruzada no nível do banco.
- Acesso por ID de outro usuário retorna `404`/`403` sem revelar conteúdo.
- Não existe perfil administrativo que consiga ler os dados financeiros de outro
  familiar.

### 3.6 Alteração e recuperação de senha

- Alteração exige a senha atual, aplica a política e rejeita repetir a senha
  anterior.
- As demais sessões são revogadas; a sessão atual permanece.
- A recuperação é **assistida por operador**, sem e-mail e sem autoatendimento.
- O mecanismo exige `DELTA_OPERATOR_RESET_TOKEN`, gera senha temporária aleatória
  de 24 caracteres, revoga todas as sessões por JDBC, limpa bloqueios de login e
  registra auditoria sanitizada. Funciona também no modo sem contexto web usado
  pelo runner operacional.
- Execução pelo script `deploy/scripts/reset-password.sh`; não há endpoint HTTP
  de recuperação de senha.

### 3.7 Auditoria

Eventos relevantes são gravados em `audit_event` sem valores, descrições ou
segredos. Exemplos: login, falha de login, bloqueio, logout, troca de senha,
recuperação assistida, acesso negado, CSRF inválido, alteração de integração,
sincronização, exportação, desconexão, exclusão, correções, conciliações e
alterações de metas/patrimônio.

## 4. Integração Meu Pluggy

**Estado:** Implementado.

### 4.1 Configuração pessoal

- Cada usuário tem no máximo uma integração `MEU_PLUGGY`
  (`UNIQUE (user_id, provider)`).
- `GET /api/v1/integrations/pluggy` devolve a configuração com Client ID mascarado
  (`••••` ou prefixo/sufixo), nunca o Client Secret.
- `PUT /api/v1/integrations/pluggy` valida Client ID/Secret no provedor antes de
  substituir a configuração. Em falha, a configuração anterior é preservada.
- `POST /api/v1/integrations/pluggy/validate` valida sem salvar.
- O Client Secret é write-only: a interface limpa o campo depois do envio e não o
  reidrata.
- A API Key da Pluggy é obtida no servidor por `POST /auth`, fica em cache em
  memória por integração (TTL de 110 minutos) e nunca é devolvida ao navegador.
- Connect Token não é solicitado, armazenado nem usado no MVP.

### 4.2 Conexões (`itemId`)

- Cada conexão é um item autorizado no Meu Pluggy, com ID local estável.
- O usuário cadastra o `itemId` explicitamente; não há descoberta automática.
- O servidor busca o item no provedor antes de salvar.
- `itemId` é cifrado e indexado por HMAC; a interface mostra somente uma versão
  mascarada.
- Vários itens são permitidos por usuário, respeitando o limite configurado
  (padrão 5, com data de verificação versionada). `connectionLimit` e
  `connectionLimitCheckedAt` vêm de `GET /integrations/pluggy`; a UI não usa
  constante de build.
- O limite é por conta pessoal e não se confunde com a quantidade de contas ou
  cartões retornados por um item.
- Conexões `ITEM_UNAVAILABLE` não ocupam vaga no limite.
- Salvar credenciais sem nenhuma conexão válida mantém a integração
  `INCOMPLETE`.
- Adicionar/atualizar itemId reimporta contas e transações de forma idempotente.
- A adição e a reconexão validam o estado do item (`status`, consentimento e erro
  de autorização). Item que exige ação é salvo no estado correspondente e não é
  marcado como ativo.
- A carga inicial e a reconexão registram `sync_run` com contadores e resultado.
- Atualizar o itemId mantém o ID local da conexão, preservando contas e
  histórico. Contas que não aparecem na nova autorização ficam `UNAVAILABLE`,
  saem dos saldos correntes e não duplicam valor quando a nova autorização expõe
  a mesma conta com outro ID.
- Cada conta/cartão expõe `connectionStatus` separado do `status` da conta.
- Remover uma conexão:
  - por padrão, marca a conexão e as contas importadas como indisponíveis e
    preserva o histórico;
  - com `deleteHistory=true`, apaga contas importadas e dados operacionais
    vinculados, mantendo contas manuais e categorias;
  - cancela jobs ativos da conexão.

### 4.3 Sincronização

- A carga inicial de uma conexão importa 12 meses.
- A rotina diária reprocessa 7 dias com sobreposição.
- A reconciliação mensal reprocessa 12 meses, por padrão no dia 1.
- As janelas são configuráveis por `DELTA_SYNC_*`.
- O scheduler tem jitter e roda a cada minuto por padrão.
- A fila `sync_job` usa `FOR UPDATE SKIP LOCKED`; há no máximo um job ativo por
  conexão/tipo.
- Retries usam backoff exponencial a partir de 30 s, com teto de 1 h, ou o
  `Retry-After`/`RateLimit-Reset` do provedor.
- A falha de uma conexão ou usuário não bloqueia as demais.
- `SYNCING` e `lastSyncAttemptAt` são persistidos antes das chamadas externas.
  Jobs `RUNNING` órfãos são recuperados no start: viram `sync_run` com
  `INTERRUPTED`, voltam para `RETRY`/`FAILED` e liberam `SYNCING`.
- Timeout de execução padrão de 10 minutos (`DELTA_SYNC_JOB_TIMEOUT`); ao
  estourar, o job é liberado e a importação para antes da próxima escrita.
- Remover uma conexão ou desconectar a integração cancela a tentativa em
  andamento antes do próximo lote; um worker atrasado não sobrescreve o job.
- Leitura de transações truncada (limite de páginas ou cursor inválido) importa o
  que veio, mas não marca ausências como `SOURCE_REMOVED`.
- A consulta manual (`POST /connections/{id}/sync`) apenas enfileira a leitura
  dos dados disponíveis; não força a instituição de origem a atualizar e não
  promete tempo real.
- `GET /sync/status` devolve integração, conexões e quantidade de jobs ativos.
- `GET /sync/runs` devolve o histórico paginado com tentativa, resultado,
  contadores, duração e código de erro sanitizado.
- Uma resposta vazia produz `EMPTY` e não é interpretada como exclusão de dados.
- Erros de credencial, autorização, consentimento, timeout, rate limit e
  indisponibilidade são mapeados para estados e códigos sanitizados.
- O painel `/settings/integration` mostra estado, última tentativa, último
  sucesso, fila e histórico.

### 4.4 Estados

Integração e conexão usam o mesmo conjunto principal:

`INCOMPLETE`, `ACTIVE`, `SYNCING`, `SYNCED`, `CREDENTIAL_FAILURE`,
`ACTION_REQUIRED`, `AUTHORIZATION_EXPIRED`, `ITEM_UNAVAILABLE`.

A expiração anual da autorização da aplicação é distinta da expiração do
consentimento de cada instituição (`CONSENT_EXPIRED`).

### 4.5 Webhooks (removido)

**Estado:** removido em 2026-10-25 (S-04).

- Não há inscrição, endpoint público, processamento de eventos nem painel de
  webhook.
- A atualização continua pela sincronização programada: janela diária de 7 dias
  e reconciliação mensal de 12 meses.
- As tabelas `webhook_event` e `webhook_subscription` foram removidas pela
  baseline `V001__baseline.sql`; só existem em backups anteriores a 2026-10-26.
- A flag `DELTA_PLUGGY_WEBHOOKS_ENABLED` e o campo `webhooksEnabled` foram
  removidos.

## 5. Contas e cartões

**Estado:** Implementado.

### 5.1 Tipos e origens

| Dimensão | Valores |
|---|---|
| Origem | `IMPORTED`, `MANUAL` |
| Tipo | `BANK`, `CREDIT`, `CASH`, `WALLET` |
| Estado | `ACTIVE`, `INACTIVE`, `UNAVAILABLE`, `CLOSED` |

Contas importadas vêm do Meu Pluggy e pertencem a uma conexão. Contas manuais
não têm conexão externa e podem ser `BANK`, `CASH` ou `WALLET`; não é permitido
criar cartão de crédito manual.

### 5.2 Contas manuais

- Criação por `POST /api/v1/accounts`.
- Edição por `PATCH /api/v1/accounts/{id}`.
- Campos: tipo, apelido, saldo de referência, data de referência, moeda e
  observações.
- O tipo é imutável depois da criação.
- A conta pode ser desativada (`INACTIVE`): não recebe novos lançamentos ou
  transferências, mas preserva histórico e relatórios.
- O saldo armazenado é um **saldo de referência** informado pelo usuário
  (`referenceBalance`).
- O saldo exibido (`currentBalance`) é o **saldo efetivo**: saldo de referência
  ajustado por lançamentos `POSTED`/`RECONCILED` da conta com data igual ou
  posterior à data de referência e por pagamentos de fatura confirmados que
  saíram da conta sem transação de origem.
- Receitas entram positivas; despesas, as duas pernas de transferências,
  reembolsos/chargebacks e ajustes entram com o sinal efetivo já normalizado.
- Lançamentos anteriores à referência e pendentes não alteram o saldo.
- O saldo de referência permanece intacto para edição; a UI usa
  `referenceBalance` no formulário e `currentBalance` nas listas, no detalhe e no
  dashboard.
- O cálculo inclui lançamentos do mesmo dia da referência porque a transação
  guarda apenas a data, não o instante.

### 5.3 Contas importadas e cartões

- A importação faz upsert por `(connection_id, external_account_id_hash)`; o ID
  local é estável.
- Tipo de conta desconhecido é preservado como `UNKNOWN`, não convertido em
  `BANK`; a UI mostra “Tipo não informado” e a conta não entra no saldo
  consolidado.
- O saldo importado é o saldo disponível/referência devolvido pelo provedor.
- Campos ausentes ficam nulos; a UI mostra “Não informado” ou “Sem apelido”.
- Cartões de crédito recebem um `card_profile` com fechamento, vencimento e
  limite apenas quando o provedor fornece os dois dias utilizáveis.
- Sem `creditData` (ou com dados incompletos), o cartão fica **sem perfil** e sem
  ciclo. A UI mostra “Configure o ciclo do cartão para gerar faturas” e as
  compras permanecem não alocadas até a configuração.
- Uma reimportação sem dados do provedor não recria nem sobrescreve um perfil
  configurado pelo usuário; com dados válidos do provedor, o ciclo de um perfil
  `PROVIDER` é atualizado.
- O painel de contas mostra tipo, origem, estado, última sincronização, cartão e
  aviso para moeda diferente de BRL.
- O dashboard exclui cartões do saldo atual e mostra o saldo devedor separado.

## 6. Transações

**Estado:** Implementado.

### 6.1 Transações importadas

- Importadas por conta, com `GET /v2/transactions` e cursor.
- Chave de deduplicação HMAC: provedor + conexão + conta + identificador externo.
- Fingerprint usado **apenas** na reconciliação explícita de troca de `itemId`;
  no fluxo normal, duas compras idênticas com IDs externos diferentes continuam
  sendo duas transações.
- Transação sem valor ou sem data é descartada, não vira `0,00` nem “hoje”; o
  `sync_run` registra `failed_count` e `IMPORT_MISSING_FIELDS` com status
  `PARTIAL`.
- Histórico de identidade externa em `external_identity_history`.
- Status possíveis: `PENDING`, `POSTED`, `SOURCE_REMOVED`, `RECONCILED`.
- `SOURCE_REMOVED` indica ausência detectada na origem; a transação sai
  imediatamente dos totais correntes e permanece no histórico.
- Se o identificador voltar a aparecer em uma leitura posterior, a marca é
  limpa automaticamente e o status atual da origem é restaurado.
- Entrada positiva em cartão é `REFUND`/`CHARGEBACK`, nunca receita; entrada
  positiva em conta bancária é `CREDIT`.
- `PENDING` não entra em receitas, despesas, resultado, categorias, drill-down
  nem totais de fatura; aparece em bloco separado no dashboard.
- `PENDING → POSTED` atualiza o mesmo registro e ele passa a compor os totais uma
  única vez.
- A sincronização atualiza campos não corrigidos e preserva os campos corrigidos
  pelo usuário (`edited_fields`).
- Valores são assinados: negativo para saída, positivo para entrada.
- Transações sem descrição recebem descrição vazia e a UI mostra “Descrição não
  informada”.
- Categorias fornecidas pelo provedor são criadas automaticamente se ainda não
  existirem para o usuário.

### 6.2 Lançamentos manuais

- Tipos: `DEBIT` (despesa), `CREDIT` (receita), `TRANSFER`, `REFUND`,
  `CHARGEBACK`, `ADJUSTMENT`.
- Toda transação manual nasce `POSTED` e com origem `MANUAL`.
- O sinal é normalizado pelo tipo.
- Transferência cria duas pernas vinculadas, uma negativa na origem e uma
  positiva no destino, e um `internal_match` já confirmado; não entra em receitas
  ou despesas.
- Reembolso/chargeback pode ser vinculado à despesa original; o valor é positivo,
  a categoria é herdada quando não informada e reembolsos parciais são aceitos no
  fluxo manual.
- Reembolsos/chargebacks importados usam matching parcial determinístico:
  mesma conta, janela de 90 dias, menor despesa candidata que cobre o valor e
  descrição compatível; empates e descrições incompatíveis não são vinculados
  automaticamente. O vínculo herda a categoria da original e pode ser refeito
  pelo usuário em `PATCH /transactions/{id}` com `relatedTransactionId`.
- Reembolso/chargeback **sem vínculo** não reduz despesa nem vira receita; fica
  visível na lista de lançamentos e pode ser vinculado manualmente (ADR-0001).
- O vínculo limita a redução ao valor efetivo da original; o excedente de cartão
  permanece como crédito para faturas futuras.
- Valor e data de uma perna de transferência não podem ser corrigidos
  isoladamente, seja transferência manual ou par importado já confirmado; a
  mensagem orienta excluir e recriar o par. Descrição, categoria e observações
  continuam editáveis sem quebrar o vínculo.
- Ajuste exige motivo explícito.
- A conta precisa estar `ACTIVE`.
- Exclusão de lançamento manual:
  - permitida pela API `DELETE /transactions/{id}`;
  - exclui as duas pernas de uma transferência;
  - recusa excluir uma despesa que ainda tenha reembolsos/chargebacks vinculados;
  - recusa excluir transação importada.
- A UI oferece “Excluir lançamento” somente para `source=MANUAL`, com confirmação
  explícita e aviso sobre transferências (duas pernas) e vínculos de reembolso.

### 6.3 Correções

- Campos corrigíveis: descrição, data, valor, categoria e observações.
- A correção é registrada em `transaction_correction` com valores anteriores e
  novos cifrados.
- `edited_fields` marca os campos corrigidos.
- A correção do usuário prevalece sobre sincronizações posteriores; o valor
  original da origem permanece para auditoria.
- A alteração de valor preserva o sinal coerente com o tipo.
- A UI usa o `CorrectionDialog`, disponível na lista de transações.

### 6.4 Remoção na origem

- Quando uma transação deixa de aparecer em uma leitura conclusiva e não vazia,
  o importador marca `status=SOURCE_REMOVED` e
  `reconciliationState=SOURCE_REMOVED`. A transação sai imediatamente dos totais
  correntes, do saldo efetivo e do espelho de fatura, mas a linha e a auditoria
  permanecem.
- Se a transação reaparecer em uma leitura posterior, a marca é limpa
  automaticamente e o status atual da origem (`PENDING` ou `POSTED`) é
  restaurado, sem duplicar a linha.
- Não há revisão manual, `RESTORE` nem `CONFIRM_REMOVAL`; o endpoint
  `POST /transactions/{id}/source-removal` e o painel de revisão foram removidos.
- Leitura vazia ou truncada continua inconclusiva e não marca remoções.

### 6.5 Filtros e paginação

Implementado na API de transações:

- `accountId`
- `categoryId`
- `status`
- `type`
- `origin`
- `from`/`to`
- `minAmount`/`maxAmount` por magnitude, aplicados após a decifragem
- `cursor`/`limit` com paginação keyset; a varredura por faixa de valor continua
  até reunir `limit + 1` correspondências, preservando `hasMore`/`nextCursor`
- intervalo inválido (`minAmount > maxAmount`) retorna `422`.

Implementado na tela de transações:

- filtro por categoria;
- campos “Valor mínimo” e “Valor máximo”, com validação local antes da consulta.

## 7. Categorias

**Estado:** Implementado.

- Categorias predefinidas são criadas sob demanda na primeira listagem:
  Moradia, Alimentação, Transporte, Saúde, Educação, Lazer, Compras, Serviços,
  Salário, Investimentos, Transferências e Outros.
- Categorias personalizadas têm nome cifrado e unicidade por HMAC.
- Categorias têm tipo `INCOME`, `EXPENSE`, `TRANSFER` ou `OTHER`.
- Criação, edição de nome/tipo e desativação por API e tela.
- Desativar não apaga transações: os lançamentos vinculados são reatribuídos para
  outra categoria ativa ou ficam sem categoria.
- A desativação marca a categoria como inativa; reutilizar o mesmo nome reativa a
  categoria existente.
- A exclusão lógica é o comportamento padrão; não há “apagar tudo”.

## 8. Reconciliação de movimentos internos

**Estado:** Implementado.

- O sistema sugere correspondências entre débitos e créditos do mesmo usuário em
  contas diferentes.
- Critérios determinísticos:
  - sinais opostos;
  - tolerância de `max(R$ 0,01, 0,5% do maior valor absoluto)`;
  - janela de ±3 dias para `TRANSFER` e ±7 dias para `CARD_PAYMENT`;
  - score com pesos 50 (valor), 30 (data) e 20 (contas);
  - limiar 80;
  - desempate por menor diferença de data, depois menor diferença de valor e,
    por fim, menor identificador local.
- Nenhuma correspondência é confirmada automaticamente.
- Confirmar marca os dois lados como `reconciliationState=MATCHED`, excluindo-os
  de receitas/despesas.
- Rejeitar mantém os lançamentos normais e impede a repetição daquele par.
- Transferência manual já nasce com correspondência confirmada.
- O dashboard mostra a quantidade de movimentos internos pendentes.
- As sugestões são geradas ao concluir uma sincronização bem-sucedida, a carga
  inicial e a reconexão de item, além da consulta da tela de reconciliação. O
  dashboard e a reconciliação concordam sem exigir visita prévia à tela.

## 9. Faturas de cartão

**Estado:** Implementado. Bills do provedor ficam fora do MVP por decisão explícita.

### 9.1 Ciclo e configuração

- Cada cartão pode ter dia de fechamento, vencimento e limite.
- O ciclo local é calculado no fuso `America/Sao_Paulo`.
- As faturas têm `OPEN`, `CLOSED`, `PARTIALLY_PAID`, `PAID` e `OVERDUE`.
- O vencimento é calculado no mês seguinte ao fechamento.
- Alterar o ciclo não recalcula faturas já fechadas ou pagas; a mudança vale para
  ciclos futuros.
- Compras que chegam depois do fechamento de uma fatura existente são alocadas ao
  ciclo seguinte.
- Sem ciclo configurado, o cartão não tem `card_profile`, não gera faturas e as
  compras permanecem não alocadas até o usuário informar fechamento e vencimento.

### 9.2 Geração de faturas

- A fatura do ciclo é gerada automaticamente após sincronização, compra manual e
  correção; não é necessário abrir a tela de faturas. `GET
  /cards/{accountId}/invoices` continua garantindo a geração de forma idempotente.
- Apenas transações efetivas (`POSTED`/`RECONCILED` e sem remoção na origem
  pendente) do cartão geram itens.
- Transação `PENDING` ou com remoção na origem pendente tem o espelho suspenso e
  sai do total; ao voltar a ser efetiva, o item é reativado na mesma fatura (ou
  recriado na primeira fatura aberta). A suspensão é distinguida de remoção
  manual por `invoice_item.mirror_suspended` (`V019`).
- Fatura paga nunca é reescrita: nem geração, nem suspensão, nem correção.
- Cada transação origina um item ativo, ou um item por parcela quando há plano de
  parcelamento.
- Tipos `DEBIT` viram `PURCHASE` ou `INSTALLMENT`; tipos de entrada (`REFUND`,
  `CHARGEBACK`, `CREDIT`) viram `REFUND` com efeito de redução.
- Importações com `creditCardMetadata` persistem `installmentNumber` e
  `totalInstallments` na transação e os copiam para o item da fatura.
- Entrada em cartão é sempre classificada como redução de despesa, nunca como
  receita, inclusive quando a descrição não contém palavras como “estorno”.
- O item espelho compõe o total da fatura uma única vez e não é somado novamente
  a despesas, receitas, resultado, categorias ou drill-down.
- Um cartão sem perfil configurado não gera fatura.

### 9.3 Itens manuais

- Tipos: `PURCHASE`, `INSTALLMENT`, `INTEREST`, `FEE`, `TAX`, `CREDIT`,
  `REFUND`, `MANUAL_ADJUSTMENT`.
- `reportingEffect`: `EXPENSE_INCREASE`, `EXPENSE_DECREASE` ou `NONE`.
- O valor do item é sempre magnitude positiva; o efeito define a direção.
- `PURCHASE` e `INSTALLMENT` exigem transação vinculada.
- `INTEREST`, `FEE` e `TAX` exigem `EXPENSE_INCREASE`.
- `CREDIT` e `REFUND` exigem `EXPENSE_DECREASE` apenas em itens manuais.
- Itens vinculados a transação são espelhos: o servidor grava sempre
  `reportingEffect=NONE`, ignorando o valor enviado no request. A direção do
  item (compra/estorno) e o total da fatura continuam derivados do tipo.
- Itens espelho não entram na projeção de relatórios e não podem ser
  editados/removidos pela UI.
- A edição de item manual gera histórico em `invoice_item_correction`.
- Em faturas fechadas, ajustes são sinalizados por `hasAdjustments` e
  `adjustmentCount`, sem alterar o status de pagamento.
- Fatura `PAID` é imutável: adicionar, editar ou remover item retorna `409`
  (`INVOICE_PAID_IMMUTABLE`). Correção de valor, data ou descrição de compra
  vinculada a fatura paga retorna `409` (`TRANSACTION_PAID_INVOICE`).
- `totalInstallments=1` em item manual é tratado como item comum (sem metadados
  de parcelamento) e aparece normalmente nos relatórios.

### 9.4 Pagamentos

- `POST /invoices/{id}/payments` registra pagamento total ou parcial em conta
  ativa do próprio usuário.
- O pagamento reduz `outstanding` e não cria despesa.
- Pagamentos não são receita nem despesa.
- O status é recalculado: `PAID`, `OVERDUE`, `PARTIALLY_PAID`, `CLOSED` ou
  `OPEN`. Atraso prevalece sobre pagamento parcial: uma fatura vencida com
  pagamento parcial fica `OVERDUE`.
- Pagamentos acima do saldo devedor não são creditados automaticamente; o saldo
  é limitado a zero.

### 9.5 Crédito acumulado

- Itens `EXPENSE_DECREASE` sem transação reduzem o total da fatura até o limite do
  total corrente; o excedente vira crédito do cartão.
- O crédito acumulado fica em `card_profile.credit_balance`.
- O crédito é aplicado à primeira fatura não paga em ordem de vencimento; se
  exceder, o residual segue para a próxima.
- Faturas pagas não são reabertas.
- O crédito não compõe `income`, `expenses` ou `currentBalance`.
- A fatura expõe `appliedCredit`, e a UI mostra o `creditBalance` do cartão na
  tela de detalhe.
- **Ponto de atenção:** a ordem de aplicação no código é por `cycleStart`, não
  explicitamente por `dueDate`. Em geral coincide, mas pode divergir após mudança
  de ciclo.

### 9.6 Parcelamento

**Estado:** Implementado para compras manuais e metadados importados.

- O modelo e a API armazenam `installmentNumber`, `totalInstallments` e
  `installmentPlanKey`; a transação também guarda os metadados informados pela
  origem.
- Ao adicionar um item `PURCHASE`/`INSTALLMENT` com `totalInstallments > 1`, o
  backend cria uma parcela por ciclo, da parcela 1 até o total, a partir da fatura
  atual, com valor por parcela e ocorrência no fechamento de cada ciclo seguinte.
- O espelho automático existente é substituído pelo plano, sem duplicar a
  transação; o índice único passa a considerar
  `(user_id, transaction_id, installment_number)`.
- A importação de `creditCardMetadata` persiste número/total de parcelas e marca o
  item como `INSTALLMENT`; se o provedor enviar cada parcela como transação
  separada, cada uma é contabilizada no seu ciclo.
- O dashboard conta cada parcela no período da sua ocorrência, não o valor total
  da compra antecipadamente.
- A UI mostra “Valor de cada parcela” e avisa quantas parcelas serão criadas.
- Correção de valor, descrição ou data da compra propaga para todas as parcelas
  ativas do plano. Apenas a âncora (primeira parcela) muda de ciclo quando a data
  efetiva cai em outro ciclo.
- Cada parcela entra uma única vez no período correspondente ao seu `occurredOn`;
  o dashboard e o drill-down usam a mesma projeção.

### 9.7 Bills do provedor (removido)

**Estado:** removido em 2026-10-25 (S-05).

- A fatura local por ciclo é a fonte de verdade; Bills do provedor não são
  presumidas nem importadas pelo fluxo de sincronização.
- O serviço experimental `ProviderBillImportService`, seu teste de integração e
  os valores `PROVIDER_BILL`/`PROVIDER` foram removidos.
- A UI e o contrato oferecem apenas origens `LOCAL`, `IMPORTED` e `MANUAL`.
- As colunas `provider_bill_id_*` e `provider_item_id_*` foram removidas pela
  baseline `V001__baseline.sql`.

## 10. Dashboard e relatórios

**Estado:** Implementado.

### 10.1 Indicadores

`/dashboard/summary` devolve:

- `periodStart`, `periodEnd`;
- `income`;
- `expenses` (total único de despesa efetiva);
- `periodResult` (`income - expenses`);
- `currentBalance` (contas `BANK`, `CASH`, `WALLET` ativas, em BRL, sem cartões;
  contas manuais entram com o saldo efetivo ajustado pelos lançamentos);
- `currentBalanceReferenceAt` (data de referência mais antiga entre as contas que
  compõem o saldo);
- `pendingCount`, `pendingIncome`, `pendingExpenses`;
- `pendingInternalMatches`;
- `lastSuccessfulSyncAt` relevante ao filtro;
- `cardOutstandingBalance` (passivo de cartões separado).

Não existe `cashFlow` nem total líquido de pendentes.

### 10.2 Períodos e filtros

- Períodos `WEEK`, `MONTH`, `YEAR` no calendário de `America/Sao_Paulo`.
- Semana começa na segunda-feira.
- Intervalos de agregação são `[início, fim)`.
- `from`/`to` incluem o dia final.
- Intervalos maiores que 366 dias retornam `422` com orientação para estreitar.
- Filtros implementados: `period`, `from`, `to`, `accountId` repetível,
  `categoryId`, `type`, `origin`, `minAmount`, `maxAmount`.
- `accountId` pode ser repetido (`accountId=a&accountId=b`) ou separado por
  vírgula; sem o parâmetro, entram todas as contas e cartões.
- A UI usa checkboxes para selecionar várias contas/cartões; nenhum selecionado
  significa todos.
- Cartões são tratados como contas no filtro (`accountId`), não como um filtro
  separado.
- Saldo, cartões, última sincronização, categorias, série e drill-down respeitam
  o conjunto selecionado.

### 10.3 Distribuição e série temporal

- `/dashboard/categories` devolve a distribuição de despesas por categoria,
  incluindo a categoria sintética “Sem categoria”
  (`00000000-0000-0000-0000-000000000000`).
- “Gastos por categoria” é o detalhamento do mesmo total de despesas, não um
  total paralelo.
- `/dashboard/timeseries` devolve buckets com `label`, `start`, `end`, `income`,
  `expenses` e `result`; para `YEAR`, os buckets são mensais; para semana/mês, são
  diários.
- Nenhum bucket exibe despesa negativa: reduções sem despesa no mesmo dia são
  aplicadas ao próximo dia com despesa e o residual volta para os últimos dias,
  preservando a soma do período.
- `/reports/entries` é a mesma projeção de cálculo usada pelos indicadores,
  garantindo que o drill-down some o total. A soma das categorias também é igual
  ao indicador de despesas, pois cada categoria é limitada a zero.
- O filtro `type=REFUND` inclui somente reduções (reembolsos/chargebacks e itens
  de crédito), nunca juros, encargos ou tarifas.

### 10.4 Itens e drill-down

- Entradas de relatório podem ser `TRANSACTION` ou `INVOICE_ITEM`.
- Itens de fatura vinculados a transação não geram entrada adicional.
- Itens de fatura sem transação aparecem como `INVOICE_ITEM`, com efeito de
  aumento ou redução de despesa. A redução é limitada ao total da fatura, ao
  total de despesas do cartão no período e nunca deixa o indicador negativo; o
  excedente permanece como crédito do cartão.
- Cada entrada identifica `origin` `IMPORTED` ou `MANUAL`.
- Transferências, transações `MATCHED`, moedas diferentes de BRL e pendentes ficam
  fora dos totais oficiais.
- O drill-down é paginado por cursor e ordenado por data/id decrescentes.
- A UI oferece tabela em desktop, cards em mobile, resumo textual e navegação por
  teclado.

### 10.5 Última sincronização e estados vazios

- `lastSuccessfulSyncAt` considera a sincronização mais recente com status
  `SUCCESS` ou `EMPTY` entre as conexões que possuem contas/cartões no filtro.
- Sem conexão aplicável, é nulo e a UI mostra “sem sincronização no período”.
- O dashboard sinaliza saldo ausente, desatualizado ou com data de referência
  antiga, além de estados de carregamento, erro e período vazio.

## 11. Privacidade, exportação e ciclo de vida

**Estado:** Implementado, incluindo os módulos de investimentos, patrimônio e
metas.

### 11.1 Exportação

- `GET /api/v1/me/export` gera um ZIP em streaming com JSON Lines e CSV por
  conjunto de dados.
- Datasets atualmente exportados:
  perfil, contas, perfis de cartão, posições de investimento, ativos/passivos
  manuais, metas e suas observações, alocações e histórico de progresso,
  categorias, transações, faturas, itens de fatura, pagamentos,
  correções de itens, correções de transações, movimentos internos, integrações,
  conexões, execuções de sincronização, jobs e auditoria. Datasets de webhooks e
  snapshots patrimoniais foram removidos e não são gerados.
- Valores financeiros, descrições, apelidos e nomes de categoria são decifrados
  para o titular.
- Não são exportados: Client ID/Secret, API Key, `password_hash`, tokens, chaves,
  HMACs, itemIds brutos e hashes em geral.
- O ZIP inclui `manifest.json` com versão, data, fuso, moeda, total de registros
  e contagem por arquivo.
- A exportação é escopada pelo `user_id` da sessão.
- A exportação cobre 100% dos dados ativos dos módulos financeiros; os testes
  criam posição, item patrimonial, meta, alocação e histórico e verificam o
  conjunto exato de arquivos, contagens do manifesto, ausência de datasets
  removidos e ausência de dados de outro titular.

### 11.2 Desconexão

- `DELETE /api/v1/integrations/pluggy` remove credenciais e interrompe novas
  sincronizações.
- Por padrão, preserva os dados históricos importados.
- Com `deleteImportedData=true`, apaga somente contas importadas e seus dados em
  cascata; contas manuais, lançamentos nelas e categorias são preservados.
- Jobs ativos da integração são cancelados.
- A UI oferece confirmação e a opção de excluir os dados importados.

### 11.3 Exclusão de conta

- `POST /api/v1/me/delete` exige senha atual, a palavra `EXCLUIR` e ciência da
  política de retenção na UI.
- Revoga todas as sessões, cancela jobs, desconecta a integração, remove dados
  ativos e mantém um tombstone em `app_user` com status `DELETED`.
- Backups expiram em até 30 dias; auditoria sanitizada permanece por 1 ano.
- A operação não afeta outros usuários.
- A exclusão remove explicitamente, por `user_id`, histórico de metas, alocações,
  metas, posições de investimento e ativos/passivos manuais, além de contas,
  transações, faturas, categorias, integrações e dados operacionais.
- O tombstone de `app_user` e a auditoria sanitizada permanecem como antes.

### 11.4 Retenção

O `RetentionJob` executa diariamente às 03:30 em `America/Sao_Paulo` e remove:

- `login_attempt`: 30 dias;
- `sync_run`: 90 dias;
- `audit_event`: 1 ano;
- `idempotency_key`: após 24 h de expiração.

## 12. Investimentos

**Estado:** Implementado como módulo manual; **não** há importação automática.

- CRUD de posições em `/investments` e
  `/api/v1/investments/positions`.
- Campos: corretora (`XP`, `SANTANDER`, `OTHER`), ativo, ticker opcional,
  quantidade, preço unitário de referência **ou** valor total, data de referência
  e observações.
- Total da posição = `quantidade × preço unitário`.
- Quando apenas o valor total é informado, o preço unitário é
  `total ÷ quantidade`, arredondado para 8 casas decimais.
- O total é sempre recalculado no servidor; não é cotação de mercado.
- Não há importação automática, recomendação, ranking, ordem ou aconselhamento.
- A disponibilidade de conectores XP/Santander no Meu Pluggy não é presumida.
- A tela mostra um aviso explícito de que o registro é manual.
- Posições entram na visão patrimonial e podem ser fontes de alocação de metas.

## 13. Patrimônio

**Estado:** Implementado como módulo pós-MVP.

- `/wealth/overview` consolida:
  - contas `BANK`, `CASH` e `WALLET` ativas em BRL;
  - saldo devedor de cartões como passivo;
  - posições de investimento;
  - ativos e passivos manuais.
- Cada fonte entra uma única vez.
- Contas inativas, encerradas ou indisponíveis não compõem o patrimônio atual.
- Contas em moeda diferente de BRL não entram no consolidado.
- Saldo ausente vira lacuna explícita, não zero inventado.
- Cartões sem fatura/saldo conhecido viram lacuna, não saldo zero.
- Referências com mais de 30 dias geram `completeness=ESTIMATED` e marcação de
  valor antigo; dados ausentes geram `PARTIAL`.
- Ativos/passivos manuais têm tipo, descrição, valor de referência, data e
  observações, com CRUD completo.
- Snapshots patrimoniais foram removidos em 2026-10-26 (S-02); a visão atual e os
  itens manuais permanecem. As tabelas `wealth_snapshot` e
  `wealth_snapshot_item` foram removidas pela baseline `V001__baseline.sql`.
- Não há reconstrução retroativa nem inferência de saldos passados.
- Valores manuais não são apresentados como cotação de mercado.

## 14. Metas

**Estado:** Implementado como módulo pós-MVP.

- Tipos: `EMERGENCY_FUND`, `TRAVEL`, `PLANNED_PURCHASE`, `NET_WORTH`.
- Regras de progresso: `ALLOCATIONS`, `NET_WORTH`.
- Campos: nome, descrição, observações, tipo, valor-alvo, data-alvo opcional,
  estado, regra e alocações.
- Observações são opcionais (até 2000 caracteres), gravadas cifradas e
  exportadas junto da meta; alterar apenas as observações não marca `CHANGED`.
- Alocações:
  - `FIXED_AMOUNT` é exata;
  - `PERCENTAGE` usa `floor(valor-alvo × percentual ÷ 100, 2)`;
  - o resíduo de arredondamento fica na última alocação percentual;
  - percentuais somam no máximo 100%;
  - percentuais abaixo de 100% deixam saldo não alocado visível;
  - o excesso é permitido, mas o progresso fica limitado a 100%.
- Fontes de alocação: `MANUAL`, `ACCOUNT`, `INVESTMENT`, `MANUAL_ASSET`.
  Cada alocação devolve `sourceName` com o nome legível da fonte (alias da conta,
  ativo/ticker ou descrição do ativo manual).
- A UI oferece seletores com contas em BRL, investimentos e itens manuais em vez
  de exigir o UUID bruto da fonte.
- Contas em moeda diferente de BRL são recusadas em `allocations` e não aparecem
  no seletor.
- A soma das alocações de uma mesma fonte entre metas não pode exceder o saldo da
  fonte; alocações `MANUAL` não têm saldo associado.
- Ao calcular o progresso, o saldo de uma fonte é consumido na ordem das
  alocações e reservado primeiro para metas mais antigas; percentuais se limitam
  ao disponível e não contam o mesmo saldo integralmente em duas metas.
- Valor fixo é exato. Se o saldo cair abaixo do valor fixo, a contribuição não é
  truncada: a alocação devolve `capacityExceeded=true` e a UI pede ajuste
  explícito. A criação/edição continua recusando soma acima do saldo disponível.
- Progresso de `NET_WORTH`: patrimônio líquido atual da visão patrimonial.
- A regra de redução de dívida, o campo `initialValue` e a coluna
  `initial_value_enc` foram removidos em 2026-10-26 (S-03 e baseline).
- Estados:
  - `ACHIEVED` quando percentual >= 100%;
  - `OVERDUE` quando a data-alvo passou e o progresso é < 100% e a meta não está
    pausada/arquivada;
  - `PAUSED`/`ARCHIVED` por ação explícita, congelando o progresso;
  - `CHANGED` após alteração material de valor-alvo, prazo, tipo, regra ou
    alocação (fonte, modo, valor ou percentual), até o usuário reconhecer a
    alteração.
- Histórico de progresso: snapshots datados, um por meta/dia, gravados **sob
  demanda** quando a meta é criada, consultada ou tem o estado alterado.
- Não há job diário dedicado: dias sem acesso à meta não geram snapshot. A UI e o
  README descrevem o comportamento como histórico datado sob demanda.
- Metas não movimentam dinheiro, não criam ordens e não executam ações bancárias.

## 15. Segurança, criptografia e auditoria

### 15.1 Criptografia

- AES-256-GCM com versão de chave e AAD composto por `user_id`, tabela, coluna e
  ID do registro.
- Campos `[enc]` incluem saldos, valores, descrições, apelidos, instituição,
  nomes de categoria, totais de fatura, pagamentos, observações, Client ID/Secret,
  posições, patrimônio e metas.
- Campos `[hmac]` incluem IDs externos, dedup, fingerprint, username, IP e nomes
  normalizados de categoria.
- Chaves ficam em key ring externo ao banco e à imagem, com versão ativa,
  rotação e backup separados.
- Senhas usam Argon2id e hash não reversível.
- O sistema não promete conhecimento zero; a proteção é contra leitura do banco
  sem o key ring.

### 15.2 Erros e logs

- Erros da API usam `ProblemDetail` (RFC 9457), com `code`, `detail`, `requestId`
  e erros por campo.
- Validações retornam `422`; conflitos `409`; não autenticado `401`; proibido
  `403`; excesso de tentativas `429` com `Retry-After`.
- Logs são sanitizados e não incluem valores financeiros, descrições, Client
  Secret ou API Key.
- `audit_event` registra eventos sem conteúdo financeiro desnecessário.

### 15.3 Idempotência

**Estado:** Implementado no fluxo HTTP, restrito às escritas financeiras críticas.

- Aceitam o cabeçalho `Idempotency-Key` com 8 a 128 caracteres:
  - transações: `POST /transactions`, `PATCH`/`DELETE /transactions/{id}`;
  - itens de fatura: `POST /invoices/{id}/items`,
    `PATCH`/`DELETE /invoice-items/{id}`;
  - pagamentos: `POST /invoices/{id}/payments`.
- As demais mutações autenticadas ignoram o cabeçalho.
- A chave é única por usuário; a primeira resposta 2xx ou 4xx é cifrada e
  reproduzida em repetições com o mesmo método, caminho, query string e corpo.
- O mesmo par usuário/chave com corpo diferente retorna `409`
  `IDEMPOTENCY_KEY_REUSED`; uma requisição concorrente retorna `409`
  `IDEMPOTENCY_IN_PROGRESS`.
- O TTL é de 24 h, alinhado ao `RetentionJob`. Respostas 5xx liberam a chave para
  um novo retry; para corrigir um erro 4xx é necessário usar outra chave.
- Login, registro e CSRF ficam fora do filtro.
- O cliente gera uma chave por operação crítica e reutiliza a mesma no retry de
  CSRF, sem persistir em `localStorage`.

## 16. Operação self-hosted

- PostgreSQL com migrações Flyway; migrações aplicadas no start.
- Health checks de liveness e readiness; readiness inclui banco e key ring.
- Logs JSON em stdout/stderr.
- Backup `pg_dump -Fc` criptografado com `age`, retenção de 30 dias; key ring
  copiado separadamente.
- Restauração documentada em `docs/runbooks/restore.md`.
- CI de segurança com gitleaks, Trivy fs/image e ZAP baseline.
- Sem Redis, sem broker externo; fila de sincronização no PostgreSQL.
  Idempotência HTTP implementada na tabela `idempotency_key`.
- Scheduler de sincronização com jitter, retry e shutdown gracioso.

## 17. Superfície REST

Base: `/api/v1`. Sessão por cookie e CSRF nas mutações. Apenas as escritas
financeiras críticas aceitam `Idempotency-Key` (8–128 caracteres) com replay de
24 h: transações, itens de fatura e pagamentos de fatura. As demais mutações
ignoram o cabeçalho. O contrato versionado está em
[`../specs/001-finance/contracts/openapi.yaml`](../specs/001-finance/contracts/openapi.yaml).

| Área | Métodos e caminhos principais |
|---|---|
| Autenticação | `GET /auth/csrf`, `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`, `PUT /auth/password` |
| Integração | `GET/PUT/DELETE /integrations/pluggy`, `POST /integrations/pluggy/validate` |
| Conexões | `GET/POST /connections`, `PATCH/DELETE /connections/{id}`, `POST /connections/{id}/sync` |
| Sincronização | `GET /sync/status`, `GET /sync/runs` |
| Contas | `GET/POST /accounts`, `GET/PATCH /accounts/{id}`, `PUT /cards/{accountId}/profile` |
| Transações | `GET/POST /transactions`, `PATCH/DELETE /transactions/{id}` |
| Categorias | `GET/POST /categories`, `PATCH/DELETE /categories/{id}` |
| Faturas | `GET /cards/{accountId}/invoices`, `GET /invoices/{id}`, `POST /invoices/{id}/items`, `PATCH/DELETE /invoice-items/{id}`, `GET /invoice-items/{id}/corrections`, `POST /invoices/{id}/payments` |
| Reconciliação | `GET /internal-matches`, `POST /internal-matches/{id}/confirm`, `POST /internal-matches/{id}/reject` |
| Relatórios | `GET /dashboard/summary`, `GET /dashboard/categories`, `GET /dashboard/timeseries`, `GET /reports/entries` |
| Privacidade | `GET /me/export`, `POST /me/delete` |
| Investimentos | `GET/POST /investments/positions`, `GET/PATCH/DELETE /investments/positions/{id}` |
| Patrimônio | `GET /wealth/overview`, `GET/POST /wealth/manual-items`, `GET/PATCH/DELETE /wealth/manual-items/{id}` |
| Metas | `GET/POST /goals`, `GET/PATCH/DELETE /goals/{id}`, `POST /goals/{id}/status` |

O contrato atual tem 43 caminhos e 63 operações, contagem refletida no `plan.md`.
A superfície de operações é verificada por teste de contrato contra os controllers.

## 18. Rastreabilidade das histórias de usuário

| História | Funcionalidade principal | Onde está no código | Estado |
|---|---|---|---|
| US1 | Conta pessoal e isolamento | `auth`, `security`, `/login`, `/register`, `/account` | Implementado |
| US2 | Configuração Meu Pluggy e importação | `integrations`, `connections`, `accounts`, `transactions`, `/settings/integration` | Implementado |
| US3 | Sincronização e recuperação | `connections`, `sync`, painel de integração | Implementado; remoção/restauração automática de `SOURCE_REMOVED` |
| US4 | Dashboard e relatórios | `reporting`, `/dashboard`, `/reports/entries` | Implementado |
| US5 | Contas, lançamentos, categorias e correções | `accounts`, `transactions`, `categories`, `reconciliation` | Implementado |
| US6 | Faturas de cartão | `invoices`, `/cards/.../invoices`, `/invoices/:id` | Implementado; Bills removidos |
| US7 | Privacidade e ciclo de vida | `privacy`, `/settings/privacy` | Implementado |
| US8 | Investimentos manuais | `investments`, `/investments` | Implementado |
| US9 | Patrimônio | `wealth`, `/wealth` | Implementado; snapshots removidos |
| US10 | Metas | `goals`, `/goals` | Implementado; histórico sob demanda |

## 19. Limitações atuais e divergências conhecidas

> **Correções de 2026-10-25:** as 20 divergências identificadas (D-01 a D-20)
> foram corrigidas no código, nos testes ou na documentação, conforme a decisão
> registrada em cada item. Não há divergência ativa conhecida entre `/specs` e o
> código.

Decisões intencionais que podem parecer limitações:

- **Bills do provedor:** removidos em 2026-10-25; a fatura local por ciclo é a
  fonte de verdade e não há serviço de importação de Bills.
- **Histórico de metas:** snapshot por meta/dia gravado sob demanda; não há job
  diário e dias sem acesso não geram ponto.
- **Filtro de cartão:** cartões são filtrados como `accountId`, junto das contas,
  sem um filtro separado na UI.

As limitações acima refletem o comportamento atual do código e do contrato
OpenAPI.

## 20. Referências

- [`../specs/001-finance/spec.md`](../specs/001-finance/spec.md)
- [`../specs/001-finance/plan.md`](../specs/001-finance/plan.md)
- [`../specs/001-finance/data-model.md`](../specs/001-finance/data-model.md)
- [`../specs/001-finance/contracts/openapi.yaml`](../specs/001-finance/contracts/openapi.yaml)
- [`../specs/001-finance/quickstart.md`](../specs/001-finance/quickstart.md)
- [`security.md`](security.md)
- [`operations.md`](operations.md)
- [`accessibility.md`](accessibility.md)
