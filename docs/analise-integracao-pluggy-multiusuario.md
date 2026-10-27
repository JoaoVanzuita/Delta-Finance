# Análise técnica: integração multiusuário com Meu Pluggy

**Projeto:** Sistema financeiro self-hosted para uso familiar não comercial  
**Data da análise:** 22/10/2026  
**Versão:** 1.0

> **Nota de 2026-10-26:** análise histórica. Webhooks foram removidos no S-04; a
> sincronização programada é o único caminho de atualização. O escopo vigente
> está em [`funcionalidades.md`](funcionalidades.md) e no contrato OpenAPI.

## 1. Objetivo e premissas

O sistema permitirá que vários familiares utilizem uma mesma instalação para acompanhar suas próprias contas bancárias, cartões de crédito e transações. O projeto será self-hosted, sem finalidade comercial, e usará Java/Spring Boot no backend, PostgreSQL, React/TypeScript no frontend e Docker Compose.

A integração bancária automática via Meu Pluggy é um requisito central do MVP. Cada usuário utilizará sua própria conta Meu Pluggy, a própria aplicação pessoal no Dashboard Pluggy e as próprias conexões autorizadas. O responsável pelo projeto confirmou que esse cenário familiar é viável para seu uso pretendido; a análise técnica assume essa confirmação e mantém como requisito que cada pessoa utilize credenciais e conexões que lhe pertencem.

Fontes oficiais principais:

- [Guia pessoal — Meu Pluggy](https://docs.pluggy.ai/pt/docs/guides/meu-pluggy-personal-use)
- [Referência de autenticação](https://v2.docs.pluggy.ai/pt/reference/authentication)
- [API — listar contas](https://v2.docs.pluggy.ai/pt/reference/account/accounts-list)
- [Documentação de Items/conexões](https://v2.docs.pluggy.ai/pt/docs/connections/item)
- [Referência de webhooks](https://v2.docs.pluggy.ai/pt/reference/webhooks)
- [Tipos de investimento](https://docs.pluggy.ai/pt/docs/products/investments)
- [Cobertura de investimentos por conector](https://docs.pluggy.ai/en/docs/connections/investments-coverage)

> **Nota sobre o escopo da documentação:** o guia público chama o Meu Pluggy de solução para uso pessoal e diz que empresas que constroem um produto para seus usuários devem seguir a integração regular. Como o cenário familiar multiusuário foi confirmado pelo responsável do projeto como autorizado para sua utilização, este documento registra essa confirmação como premissa do projeto. Não se deve inferir daí autorização para publicar o sistema como serviço para terceiros, comercializá-lo ou ampliar seu uso para além do cenário confirmado.

## 2. Conceitos e credenciais

O modelo tem credenciais com finalidades diferentes. Elas não devem ser confundidas.

| Elemento | Significado | Quem fornece/obtém | Armazenamento recomendado |
|---|---|---|---|
| Conta Meu Pluggy | Conta pessoal na qual o titular conecta os próprios bancos | Cada usuário, no ambiente Meu Pluggy | O sistema não precisa armazenar a senha desta conta |
| Client ID | Identificador da aplicação pessoal do usuário no Dashboard Pluggy | Cada usuário copia do Dashboard | Pode ser protegido como dado de configuração; armazenar criptografado é defensivo e consistente com a política do projeto |
| Client Secret | Segredo da aplicação pessoal | Cada usuário copia/gera no Dashboard | Criptografado na aplicação; nunca retornado à UI após salvar |
| `itemId` | Identificador de uma conexão bancária adicionada à aplicação pessoal | Cada usuário copia do item correspondente no Dashboard | Persistir em associação com a integração e o usuário proprietário |
| API Key | Credencial temporária obtida por `POST /auth` | Gerada pelo backend a partir de Client ID/Secret | Preferencialmente cache de servidor com expiração; não solicitada ao usuário |
| Connect Token | Token temporário e restrito usado pelo Pluggy Connect Widget | Gerado pelo backend quando o fluxo de widget é usado | Não é necessário como credencial permanente; não faz parte do cadastro inicial descrito pelo Meu Pluggy |

A documentação atual informa que a API Key dura **duas horas** e dá acesso aos endpoints permitidos à aplicação. O Connect Token dura **30 minutos** e tem escopo reduzido, associado a um item; é destinado a fluxos de cliente/widget. As credenciais Client ID e Client Secret devem ser utilizadas no servidor, não no navegador. Fonte: [Autenticação da API Pluggy](https://v2.docs.pluggy.ai/pt/reference/authentication).

## 3. Configuração por usuário

Cada usuário deve concluir a seguinte configuração inicial:

1. Criar ou acessar sua conta no [Meu Pluggy](https://meu.pluggy.ai/) e conectar individualmente as instituições financeiras que pretende acompanhar.
2. Acessar o [Dashboard Pluggy](https://dashboard.pluggy.ai/) usando a mesma conta.
3. Criar/selecionar a aplicação pessoal, habilitar o conector `MeuPluggy` e usar o fluxo `Demo` → `Conectar Conta` → `MeuPluggy`. É importante escolher o conector MeuPluggy, e não o conector direto do banco nem o Sandbox.
4. Copiar o `Client ID`, o `Client Secret` e o `itemId` de cada conexão exibida em `Itens Conectados`.
5. Entrar no sistema financeiro e cadastrar os dados na área autenticada de integração.
6. O backend valida as credenciais e cada item; após sucesso, começa a importação dos dados disponíveis.

O guia oficial informa que o `Client Secret` é exibido apenas quando gerado. Se o usuário não o guardou, pode precisar gerar outro no Dashboard. Também informa que `GET /v2/items` não está disponível para contas Meu Pluggy, por isso a configuração precisa permitir a entrada explícita de cada `itemId`.

Cada banco conectado no Meu Pluggy precisa ser autorizado separadamente. Se o usuário adicionar outro banco futuramente ao Meu Pluggy, deve adicioná-lo também à própria aplicação e cadastrar o novo `itemId` no sistema financeiro.

## 4. Como funciona a autenticação no backend

O backend mantém uma configuração independente por usuário. Em alto nível:

```text
Usuário autenticado no sistema
  → backend localiza a integração daquele usuário
  → descriptografa Client ID/Client Secret dentro do servidor
  → POST https://api.pluggy.ai/auth
  → recebe API Key temporária
  → consulta contas/transações do itemId daquele usuário
  → valida e deduplica os registros
  → armazena dados associados ao usuário proprietário
```

### API Key temporária

- Não pedir que o usuário gere ou cadastre API Key.
- Gerar a API Key por meio de `POST /auth`, no servidor.
- Reutilizá-la até perto do vencimento e renová-la quando necessário; não chamar `/auth` para cada requisição.
- Cachear a chave na memória do backend, indexada pela integração correta, com expiração. Depois de reiniciar, é aceitável gerar uma nova chave quando necessário.
- Nunca incluir a API Key em respostas HTTP para o navegador, logs, traces ou mensagens de erro.
- Não compartilhar chaves entre aplicações pessoais diferentes.

### Por que um par de credenciais por pessoa?

Cada usuário utiliza a sua própria aplicação pessoal e seus próprios itens. Assim, as credenciais da aplicação A nunca podem ser utilizadas para recuperar os itens que foram adicionados à aplicação B. Essa separação deve ser representada explicitamente no banco e no código; não deve depender de confiar em um ID enviado pelo frontend.

## 5. Modelo de integração sugerido

O modelo lógico pode conter as seguintes entidades. Os nomes são ilustrativos; devem ser adaptados ao padrão final do projeto.

### `USER_INTEGRATION`

- `id`
- `user_id`
- `provider` (por exemplo, `PLUGGY_MEUPLUGGY`)
- `client_id_ciphertext`
- `client_secret_ciphertext`
- `encryption_key_version`
- `status`
- `created_at`, `updated_at`
- `last_sync_attempt_at`, `last_successful_sync_at`
- `last_error_code` e uma mensagem de erro sanitizada

### `EXTERNAL_ITEM`

- `id` interno
- `user_integration_id`
- `external_item_id` (`itemId` do Pluggy)
- `institution_name` quando disponível
- `status` e `consent_expires_at` quando retornado
- `last_sync_at`
- `created_at`, `updated_at`

### `FINANCIAL_ACCOUNT`

- `id` interno
- `user_id`
- `external_item_id` interno
- `external_account_id`
- tipo de conta (`BANK`, `CREDIT` ou os valores normalizados relevantes)
- instituição, nome/apelido, moeda e saldos disponíveis
- metadados de origem estritamente necessários

### `TRANSACTION`

- `id` interno
- `user_id`
- `financial_account_id`
- origem (`MANUAL` ou `PLUGGY`)
- identificador externo da transação
- descrição, data, valor decimal, tipo, categoria
- estado/status se fornecido
- campos de origem e campos editados pelo usuário com uma política de precedência definida
- timestamps de importação e última atualização externa

A deduplicação deve utilizar uma chave única estável que considere a origem e seus identificadores. Uma estratégia possível é a combinação de integração/item, conta externa e transação externa, mas ela deve ser confirmada contra o formato de IDs retornado pela API. O requisito essencial é que uma nova sincronização não crie outra linha para a mesma transação.

Todos os recursos devem ter uma relação explícita com o proprietário local. Mesmo quando `itemId`, `accountId` ou IDs de transação parecem únicos, o sistema deve validar o vínculo até `user_id`.

## 6. Contas, cartões e transações

O guia pessoal exemplifica a consulta de contas com `GET /accounts?itemId=<itemId>` e a consulta de transações de uma conta por `accountId`. A referência atual da API deve ser consultada no momento da implementação, porque algumas operações antigas de listagem de transações podem estar marcadas como obsoletas.

A aplicação deve importar todos os tipos de conta disponíveis e reconhecidos no retorno, incluindo contas bancárias e contas de crédito/cartões quando fornecidos. O suporte real depende do conector e do que a instituição expõe.

O sistema deve armazenar transações originais com identificação da fonte para poder reconciliar mudanças. Antes de somar um valor em relatórios, aplicar regras financeiras para evitar dupla contagem:

- compra no cartão conta como despesa;
- pagamento da fatura não conta de novo as compras já registradas;
- transferência entre contas próprias não é receita nem despesa;
- estorno/reembolso ajusta a despesa conforme regra definida;
- transações pendentes e efetivadas devem ser reconciliadas sem duplicidade;
- dados alterados pelo usuário, especialmente categoria e anotações, não devem ser silenciosamente perdidos em sincronizações.

## 7. Frequência e atualização de dados

O Meu Pluggy atualiza a conexão de origem a cada **24 horas**. Itens ligados ao MeuPluggy refletem as atualizações da conexão de origem automaticamente e, nesse fluxo, não podem ser atualizados manualmente via API. A consulta feita pelo sistema financeiro recupera os dados disponíveis; ela não força a instituição a atualizar imediatamente.

Recomendação para o MVP:

- rotina de sincronização diária, com proteção contra duas execuções concorrentes para a mesma integração;
- opção de “consultar dados disponíveis agora”, descrita sem promessa de forçar uma nova leitura bancária;
- mostrar data/hora da última tentativa e da última sincronização bem-sucedida;
- registrar totais de registros lidos/criados/atualizados sem expor conteúdo financeiro em logs;
- implementar retries limitados para falhas temporárias e backoff para rate limits;
- manter sincronização programada como reconciliação mesmo se webhooks forem implementados.

A frequência correta pode ser revisitada conforme a modalidade e limites de requisição. Para o fluxo pessoal com origem atualizada diariamente, polling excessivamente frequente normalmente não cria dados bancários mais recentes.

## 8. Webhooks

O guia do Meu Pluggy diz que os itens enviam webhooks como outros itens. A API lista eventos como `item/updated`, `item/error` e eventos relacionados a transações. Webhooks podem reduzir o atraso entre a atualização do item e a importação pelo sistema, mas não substituem a reconciliação.

Se adotados:

- O backend deve configurar o webhook para cada aplicação pessoal que suportar esse mecanismo, sem exigir que o familiar mantenha infraestrutura própria de webhook.
- Vários usuários podem apontar para um endpoint comum, mas cada inscrição precisa ter um método próprio de autenticação/identificação de origem, por exemplo, um cabeçalho secreto diferente armazenado criptografado.
- O endpoint deve validar a inscrição, fazer parsing mínimo e responder 2xx rapidamente; a importação real ocorre em worker/job assíncrono.
- Deduplicar eventos pelo `eventId` quando aplicável e tornar a execução idempotente.
- Para eventos `item/*`, a API pode incluir `itemId`; para eventos de transações, mapear o item e o usuário pelo vínculo de conta/item guardado localmente. Não confiar cegamente em `clientUserId` ou no payload como fonte de autorização.
- Um webhook indica que houve uma atualização/mudança; o backend deve consultar os endpoints adequados e validar os dados completos.
- Tratar entregas repetidas, fora de ordem, perdidas e falhas de entrega. A rotina diária de sincronização corrige divergências.

Se webhooks complicarem demais o primeiro incremento, é aceitável adiar essa otimização, desde que a sincronização programada funcione de ponta a ponta no MVP.

## 9. Expiração e reconexão

Há duas camadas separadas de consentimento:

1. A autorização concedida pelo usuário à aplicação pessoal para ler as conexões do Meu Pluggy, que dura **um ano**.
2. O consentimento individual de cada instituição bancária dentro do Meu Pluggy, que pode ter seu próprio ciclo de validade.

A documentação relata que, após expirar a autorização da aplicação, as leituras de contas/transações podem retornar listas vazias e o item pode chegar ao estado `LOGIN_ERROR` com código `USER_AUTHORIZATION_EXPIRED`, acionando webhook de erro. A documentação também alerta para não presumir que o `itemId` será o mesmo após renovar a autorização.

Comportamento obrigatório do sistema:

1. detectar a expiração por estado, código de erro e `consentExpiresAt` quando disponível;
2. marcar a integração/itens como necessitando ação do usuário;
3. mostrar instruções para repetir a conexão do conector MeuPluggy no Dashboard;
4. permitir atualizar os `itemId` na instalação local;
5. validar os novos itens e reconciliar as transações pelo histórico local e IDs de origem;
6. retomar importação idempotente;
7. nunca apagar dados antigos apenas porque uma resposta veio vazia ou uma conexão mudou.

A ausência de dados não pode ser usada isoladamente como sinal para excluir contas ou transações. Se o usuário desconectar a integração, a política de retenção dos dados já importados deve ser explícita.

## 10. Segurança e privacidade

O requisito do projeto é proteger os dados contra alguém que tenha acesso ao conteúdo do PostgreSQL, mas não às chaves externas da aplicação. Isso não equivale a criptografia com conhecimento zero: o backend precisa descriptografar dados para importar, categorizar e calcular dashboards.

Medidas recomendadas:

- hash de senha com Argon2id ou opção igualmente apropriada;
- criptografia autenticada na camada de aplicação para Client Secret e campos financeiros sensíveis;
- chaves externas ao banco e às imagens Docker, com versão/rotação documentadas;
- nenhum Client Secret ou API Key enviado para o frontend após a configuração;
- sessão segura e autorização em toda API, sem confiar em um `user_id` do cliente;
- logs sanitizados, sem tokens, segredos ou payloads financeiros completos;
- backup criptografado e estratégia documentada para preservar/recuperar as chaves;
- testes automatizados com pelo menos dois usuários e duas integrações independentes;
- filtros, paginação e exportações sempre limitados ao usuário autenticado.

Há um trade-off: criptografar campos como descrição e valor no nível de aplicação pode tornar filtros/agrupamentos SQL mais difíceis. O plano técnico deve explicar quais campos ficam criptografados, quais metadados permanecem visíveis, e como relatórios são calculados após descriptografia autorizada ou por um modelo alternativo cuidadosamente avaliado. Não criptografar indiscriminadamente sem considerar a experiência e a correção do dashboard.

## 11. Limite de conexões

A documentação do Meu Pluggy informa até **cinco conexões** na modalidade pessoal. O limite aplica-se às conexões, não necessariamente ao número de contas individuais, cartões ou produtos financeiros que podem aparecer em dados retornados por uma conexão. A compatibilidade exata depende da instituição e do conector.

A interface deve mostrar quantos `itemId` estão configurados por usuário e alertar quando o limite documentado for atingido. O sistema não deve presumir que cada conta/cartão consome uma conexão, nem prometer cobertura de todos os produtos oferecidos por uma instituição.

## 12. XP Investimentos e Santander Corretora

### XP Investimentos

A tabela pública da Pluggy para conectores diretos de investimentos mostra XP Investimentos com cobertura para `Equity` e `Real Estate`, que correspondem às classes amplas em que se encontram ações e fundos imobiliários. A API também descreve posições de investimento com campos como nome, código, tipo, quantidade, valor e saldo.

Entretanto, a cobertura de um conector direto não prova que ele esteja disponível pela cadeia de acesso do Meu Pluggy pessoal. A possibilidade de conectar a XP para investimentos precisa ser testada no Dashboard e confirmada para a modalidade pessoal antes de integrar automaticamente esses dados.

### Santander Corretora

A tabela pública de conectores diretos de investimento consultada lista XP Investimentos, BTG Pactual, Avenue e EQI, mas não lista Santander Corretora nesse conjunto. A tabela também apresenta Santander Empresas como conector empresarial com cobertura limitada a renda fixa e fundos mútuos, não a ações/FIIs. Isso não é evidência suficiente para prometer acesso a ações e FIIs da Santander Corretora no contexto pessoal.

### Decisão recomendada

- Manter XP e Santander como corretoras-alvo para a evolução de investimentos.
- Implementar primeiro o registro manual de posições, se o módulo for necessário antes de confirmar a cobertura.
- Fazer prova de conceito separada para cada conector/modalidade antes de declarar importação automática.
- Não atrasar sincronização bancária, cartões e dashboard do MVP por causa das corretoras.
- Não fornecer recomendações de investimento; limitar o módulo a acompanhamento de dados/posições.

Fontes: [Produto Investimento](https://docs.pluggy.ai/pt/docs/products/investments) e [Cobertura de investimentos](https://docs.pluggy.ai/en/docs/connections/investments-coverage).

## 13. Riscos e decisões técnicas recomendadas

| Risco | Mitigação recomendada |
|---|---|
| Credenciais de um usuário usadas para outro | Serviço de integração com contexto explícito de `user_id`; segredos e cache separados por integração; testes de isolamento |
| `Client Secret` exposto em frontend/log | Envio somente no formulário seguro; API não o retorna; logs sanitizados; criptografia em repouso |
| API Key expirada | Cache com TTL e renovação server-side por `POST /auth` |
| Item ID mudou na renovação | Permitir substituição de itemId e reconciliar por chaves locais/IDs de transação |
| A API retorna lista vazia após expiração | Não excluir histórico por lista vazia; verificar status, consentimento e erro |
| Sincronização duplica transações | Restrições únicas de origem e upsert idempotente |
| Atualização bancária não é imediata | UI comunica a última sincronização e a janela de atualização diária do Meu Pluggy |
| Webhook duplicado ou perdido | Idempotência + reconciliação programada |
| Campo criptografado atrapalha dashboard | Definir modelo de consulta; documentar metadados visíveis e testar consistência dos relatórios |
| Conector de investimento não acessível no Meu Pluggy | Validação independente por conector; fallback manual e escopo secundário |
| Limite de conexões atingido | Mostrar itens configurados, respeitar limite por usuário e explicar o procedimento |

## 14. Critérios de conclusão da integração bancária do MVP

A integração não deve ser considerada pronta até que todos os pontos seguintes sejam demonstrados:

- [ ] Dois usuários diferentes conseguem cadastrar pares Client ID/Client Secret diferentes.
- [ ] Cada usuário consegue registrar múltiplos `itemId` seus.
- [ ] O sistema valida credenciais e itens antes de indicar conexão ativa.
- [ ] API Keys são obtidas no backend, têm expiração controlada e nunca aparecem no cliente.
- [ ] Contas bancárias/cartões e transações disponíveis são importados corretamente.
- [ ] Uma segunda sincronização não duplica transações.
- [ ] Uma falha de um usuário não bloqueia a sincronização de outros usuários.
- [ ] Nenhum usuário consegue acessar dados, conexões, segredos ou relatórios de outro usuário.
- [ ] Expiração anual, mudança de itemId e reconexão podem ser resolvidas sem perda histórica.
- [ ] O estado da sincronização e os erros são compreensíveis na interface.
- [ ] O sistema continua operável com dados locais quando a Pluggy está indisponível.
- [ ] O backup/restauração inclui o planejamento seguro das chaves de criptografia.

## 15. Conclusão

O desenho recomendado é uma integração Pluggy por usuário: cada pessoa informa o próprio Client ID, Client Secret e um ou mais itemId; o backend autentica essa aplicação, mantém API Key temporária com expiração, consulta as contas/transações e salva os dados vinculados ao proprietário local. A configuração é manual uma única vez, mas o registro cotidiano das movimentações pode ser automatizado de acordo com os dados atualizados pelo Meu Pluggy.

As questões mais críticas para a implementação são o isolamento dos segredos e dos itens entre usuários, a deduplicação das transações, o tratamento de autorização anual e a representação honesta do atraso de atualização de até 24 horas. O módulo de investimentos deve ser validado separadamente e permanecer secundário.
