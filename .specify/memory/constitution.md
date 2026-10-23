<!--
Sync Impact Report
==================
Versão anterior: 1.0.1
Nova versão: 1.0.2
Tipo de alteração: PATCH — remoção de diretriz de apresentação inicial
Princípios modificados: III (remoção de instruções guiadas)
Seções alteradas: Additional Constraints (Uso pretendido)
Seções adicionadas: nenhuma
Seções removidas: nenhuma
TODOs pendentes: nenhum
-->

# Delta Finance Constitution

## Core Principles

### I. Privacidade e Isolamento entre Usuários (NÃO NEGOCIÁVEL)

Cada usuário DEVE acessar somente os próprios dados financeiros, integrações,
credenciais, contas, transações, categorias, relatórios e configurações. Toda
operação DEVE validar no servidor a identidade autenticada e o vínculo de
propriedade do recurso; IDs, URLs, filtros ou payloads enviados pelo cliente
NÃO DEVEM conceder acesso. A autorização DEVE ser aplicada na camada de
domínio/serviços e também nas consultas persistentes, de modo que nenhuma
consulta atravesse o limite do usuário. Credenciais, API Keys, itemIds e dados
de integração NÃO DEVEM ser compartilhados entre usuários. O sistema DEVE
incluir testes automatizados de acesso cruzado com pelo menos dois usuários e
integrações independentes, cobrindo leitura, escrita, exclusão, exportação e
relatórios. A coleta e a retenção de dados pessoais e financeiros DEVEM ser
minimizadas ao necessário para as finalidades documentadas.

**Racional:** em uma instalação multiusuário, a segregação lógica é a fronteira
de privacidade do produto e não pode depender da interface ou da boa-fé do
cliente.

### II. Criptografia e Gestão de Segredos (NÃO NEGOCIÁVEL)

Senhas de login DEVEM ser armazenadas exclusivamente como hash adaptativo
seguro, preferencialmente Argon2id, com parâmetros calibrados; criptografia
reversível de senhas é proibida. Segredos recuperáveis necessários à integração
Pluggy, incluindo o Client Secret, DEVEM ser cifrados com criptografia
autenticada (AEAD) na camada da aplicação. Chaves criptográficas DEVEM residir
fora do PostgreSQL e do código-fonte, ser fornecidas por configuração segura e
ter versionamento, rotação, backup e recuperação documentados; o texto cifrado
DEVE identificar a versão da chave utilizada. API Keys temporárias da Pluggy
NÃO DEVEM ser expostas ao navegador, registradas em logs ou persistidas sem
necessidade justificada. Logs, traces, backups, mensagens de erro e artefatos de
suporte DEVEM ser sanitizados. A promessa de proteção DEVE ser declarada com
precisão: um dump isolado do PostgreSQL, sem as chaves externas, NÃO DEVE
revelar os campos protegidos; isso não equivale a proteção contra um
administrador que controle simultaneamente o backend e as chaves, e o sistema
NÃO DEVE alegar conhecimento zero.

**Racional:** a criptografia precisa sobreviver ao vazamento do banco, mas o
backend necessariamente descriptografa dados para sincronizar e calcular
relatórios. A constituição deve refletir essa fronteira real.

### III. Integração Bancária como Funcionalidade Central (NÃO NEGOCIÁVEL)

A integração bancária automática pelo fluxo pessoal Meu Pluggy é requisito
obrigatório do MVP, não um recurso opcional ou futuro. Cada usuário DEVE
configurar a própria aplicação pessoal no Dashboard Pluggy, informando seu
Client ID, Client Secret e os itemIds das próprias conexões; o sistema NÃO DEVE
contar com descoberta automática de itens, pois `GET /v2/items` não está
disponível nesse fluxo. A API Key temporária DEVE ser obtida pelo backend via
`POST /auth`, mantida em cache com expiração vinculada à integração correta e
renovada no servidor antes do vencimento, sem ser solicitada ao usuário. A
integração DEVE funcionar de ponta a ponta com dados reais; mocks, Sandbox ou
dados fictícios NÃO DEVEM ser apresentados como dados reais do usuário. A
interface e a documentação DEVEM refletir os limites vigentes do Meu Pluggy:
atualização automática diária, ausência de atualização manual de itens proxy,
limite documentado de conexões por conta pessoal, expiração periódica da
autorização da aplicação, consentimentos bancários independentes e possibilidade
de mudança de itemId na reconexão.
Operações manuais são complemento e contingência e NÃO DEVEM substituir nem
rebaixar a integração bancária do escopo principal.

**Racional:** a proposta de valor do produto depende de dados bancários
automáticos e corretos. Declarar sucesso com dados simulados ou prometer
atualização inexistente violaria a confiança do usuário e a finalidade do
sistema.

### IV. Correção e Rastreabilidade Financeira (NÃO NEGOCIÁVEL)

Valores monetários DEVEM usar tipos decimais apropriados em toda a cadeia, como
`BigDecimal` no backend; ponto flutuante binário NÃO DEVE ser usado em cálculos
financeiros. Importações e sincronizações DEVEM ser idempotentes, deduplicadas
por chave de origem estável e recuperáveis após falha parcial. Identificadores
da origem, histórico relevante e correções manuais DEVEM ser preservados segundo
uma política de precedência explícita. Compras no cartão DEVEM contar como
despesa, e o pagamento da fatura NÃO DEVE contabilizar novamente as mesmas
compras. Transferências entre contas próprias NÃO DEVEM ser tratadas como
receita ou despesa. Estornos, reembolsos, transações pendentes e ajustes DEVEM
ter semântica explícita e regra de reconciliação definida. Lista vazia, erro
externo ou mudança de itemId NÃO DEVE ser interpretado como exclusão. Todo total
exibido DEVE ser rastreável às transações ou posições que o compõem.

**Racional:** um único lançamento duplicado, omitido ou classificado
incorretamente corrompe todos os agregados do produto. A rastreabilidade é o que
permite auditar e corrigir os números.

### V. Simplicidade e Manutenibilidade

O produto DEVE ser um monólito modular com duas aplicações no mesmo repositório,
`server/` e `client/`; microserviços NÃO DEVEM ser adotados sem necessidade
comprovada, documentada e aprovada. Regras financeiras, autenticação,
persistência, relatórios e integração Pluggy DEVEM ter responsabilidades e
fronteiras explícitas. Abstrações prematuras, dependências sem propósito e
complexidade operacional desnecessária DEVEM ser evitadas. O modelo financeiro
DEVE ser compatível com a evolução para patrimônio, metas e investimentos, sem
antecipar essas fases no MVP.

**Racional:** o sistema será operado por uma família em infraestrutura própria.
A simplicidade reduz custo de manutenção, superfície de falha e esforço de
operação sem sacrificar a correção financeira.

### VI. Confiabilidade, Testes e Observabilidade

Autenticação, autorização, isolamento, importação, deduplicação, reconciliação
financeira e renovação de credenciais DEVEM ter testes automatizados. O sistema
DEVE tratar explicitamente erros externos, limites de requisição, timeouts,
indisponibilidade, consentimento expirado, mudança de itemId e reconexão, com
retries limitados, backoff e idempotência. A falha de um usuário ou integração
NÃO DEVE bloquear a sincronização dos demais. Falhas e listas vazias NÃO DEVEM
apagar históricos; exclusões DEVEM exigir ação explícita e política documentada.
A interface DEVE exibir a última tentativa, a última sincronização bem-sucedida e
o estado de cada conexão, sem sugerir atualização em tempo real ou forçar
atualização bancária inexistente. Logs DEVEM ser sanitizados, estruturados e
suficientes para diagnosticar tentativas, resultados, duração e códigos de erro,
sem expor segredos ou conteúdo financeiro desnecessário. Dados já importados e
lançamentos manuais DEVEM permanecer disponíveis quando o provedor externo
estiver indisponível.

**Racional:** a dependência de uma API externa introduz falhas que o produto não
controla. A operação precisa continuar previsível e os incidentes precisam ser
diagnosticáveis sem vazar dados.

### VII. Experiência Responsiva e Acessível

A interface DEVE ser simples, previsível, acessível e utilizável em desktop e
mobile, atendendo aos critérios aplicáveis da WCAG 2.1 nível AA. Estados de
carregamento, erro, ausência de dados, autorização expirada e sincronização
pendente DEVEM ser explícitos e acionáveis. Gráficos, totais, filtros e listas
DEVEM permanecer legíveis em telas pequenas, oferecer alternativa textual e não
depender exclusivamente de cor. Os fluxos críticos DEVEM ser operáveis por
teclado e leitores de tela. As skills `frontend-design` e `ui-ux-pro-max` do
OpenCode DEVEM orientar decisões visuais, subordinadas aos requisitos
funcionais, de segurança e de acessibilidade.

**Racional:** os dados financeiros só geram valor quando o usuário consegue
interpretá-los e agir sobre eles em qualquer dispositivo, inclusive com
tecnologia assistiva.

### VIII. Self-Hosting e Autonomia Operacional

O sistema DEVE ser iniciável por Docker Compose, com configuração externa ao
código, migrações versionadas e procedimentos documentados de instalação,
backup, restauração, atualização e recuperação. Segredos reais NÃO DEVEM existir
em imagens, repositório, exemplos, fixtures, logs ou arquivos Compose
versionados. Sempre que exposto fora do host local, o acesso DEVE usar HTTPS por
proxy reverso. A implantação NÃO DEVE depender da integração de investimentos
para entregar o valor principal do gerenciamento bancário. A indisponibilidade
de fontes externas NÃO DEVE impedir a operação local com os dados já
armazenados.

**Racional:** o produto precisa ser recuperável e operável pelo próprio grupo
familiar, sem serviços proprietários de terceiros nem conhecimento implícito na
máquina de desenvolvimento.

### IX. Integração de Investimentos com Escopo Controlado

XP Investimentos e Santander Corretora são instituições de interesse de uma fase
secundária; a disponibilidade de ativos ou conectores no fluxo pessoal Meu
Pluggy NÃO DEVE ser presumida sem verificação na documentação oficial vigente e
no Dashboard. O sistema PODE prever um modelo de investimentos e registro
manual, mas importação automática NÃO DEVE ser declarada funcional sem validação
real de ponta a ponta. Cobertura de conector direto ou teste em Sandbox NÃO
constitui prova de disponibilidade no Meu Pluggy. O módulo NÃO DEVE atrasar a
integração bancária nem o dashboard do MVP e NÃO DEVE oferecer recomendações de
compra ou venda, consultoria ou aconselhamento de investimentos.

**Racional:** a cobertura de conectores pode variar por modalidade e instituição.
Prometer importação não validada gera expectativa falsa e compromete a
credibilidade do produto.

### X. Patrimônio e Planejamento Financeiro

A evolução do produto DEVE prever patrimônio líquido, ativos, passivos e metas
financeiras pessoais sem atrasar o MVP bancário. Saldo de conta, fluxo de caixa,
patrimônio bruto, passivos e patrimônio líquido DEVEM ser conceitos distintos;
compras, faturas, transferências e ativos já representados por contas ou
investimentos NÃO DEVEM ser contados duas vezes. O patrimônio histórico DEVE
usar posições ou snapshots datados e comunicar lacunas, estimativas e dados
desatualizados, sem inventar valores. Metas DEVEM definir valor-alvo, prazo
opcional, origem do valor atual e regra de progresso; o mesmo dinheiro NÃO DEVE
ser contado integralmente em várias metas sem alocação explícita. Patrimônio e
metas pertencem a uma fase posterior ao MVP, mas o modelo financeiro DEVE evoluir
de forma coerente para acomodá-los. Metas servem para acompanhamento e
planejamento; NÃO DEVEM movimentar dinheiro ou executar ordens.

**Racional:** patrimônio e metas só são confiáveis quando cada valor tem origem,
data e regra de composição claras. O MVP bancário não pode ser atrasado por essa
evolução.

### XI. Decisões, Documentação e Limites Externos

Decisões relevantes, riscos, premissas e limitações externas DEVEM ser
registrados nos artefatos do projeto. Quando a API, a instituição ou a
modalidade não garantir uma capacidade, a limitação DEVE ser documentada e o
sucesso NÃO DEVE ser simulado. Requisitos centrais DEVEM permanecer estáveis; a
evolução DEVE ocorrer em incrementos pequenos, verificáveis e testáveis.
Verificações de dependências externas DEVEM registrar data, escopo e evidência,
e qualquer capacidade declarada como funcional DEVE ter validação
correspondente.

**Racional:** decisões e limitações não documentadas são redescobertas como
defeitos. O registro explícito mantém a especificação, o plano e a operação
alinhados à realidade do provedor.

## Additional Constraints

**Stack e estrutura.** O backend DEVE usar Java com Spring Boot e Spring
Security; a persistência DEVE usar PostgreSQL com migrações versionadas e
reproduzíveis; o frontend DEVE usar React, TypeScript e shadcn/ui; a operação
DEVE usar Docker e Docker Compose. O repositório DEVE ser único, com aplicações
separadas em `server/` e `client/`. Essas escolhas são restrições do projeto e só
podem ser alteradas por emenda constitucional.

**Uso pretendido.** O sistema é uma instalação self-hosted privada, destinada a
um grupo familiar, sem finalidade comercial. Ele NÃO DEVE ser oferecido como
serviço a terceiros nem comercializado sem emenda constitucional e validação
legal e contratual específica. Cada usuário DEVE usar credenciais e conexões
próprias; o sistema NÃO DEVE contornar a configuração da integração nem os
limites da modalidade Meu Pluggy.

**Padrões iniciais de apresentação.** Os relatórios e agregações DEVEM adotar
BRL, fuso horário `America/Sao_Paulo` e semana iniciada na segunda-feira como
padrões iniciais, de forma consistente e testável, até que configuração
explícita em contrário seja especificada.

**Segurança de sessão e implantação.** Sessões DEVEM usar cookies seguros com
`HttpOnly` e `SameSite` adequados; CSRF DEVE ser tratado conforme o modelo de
autenticação; tokens de sessão NÃO DEVEM ser armazenados em `localStorage`.
Autenticação DEVE ter limitação de tentativas, entradas DEVEM ser validadas e
mensagens de erro NÃO DEVEM revelar informações sensíveis. Acesso pela rede DEVE
usar HTTPS por proxy reverso.

## Development Workflow & Quality Gates

**Fluxo Spec Kit.** Funcionalidades DEVEM percorrer as etapas de especificação,
esclarecimento quando necessário, planejamento, tarefas e implementação. Cada
artefato DEVE passar por uma verificação de conformidade com esta constituição.
Especificações DEVEM declarar o escopo de autorização, a classificação dos dados,
as invariantes financeiras, as dependências externas, os modos de falha e os
critérios de aceitação. Planos DEVEM incluir a checagem constitucional e
justificar qualquer desvio. Tarefas de teste DEVEM fazer parte da entrega, não
ser postergadas para depois da implementação.

**Portões de qualidade.** Uma funcionalidade só pode ser considerada concluída
quando:

- os testes automatizados passarem, incluindo regras financeiras, migrações
  PostgreSQL, autenticação, autorização e isolamento entre pelo menos dois
  usuários com integrações independentes;
- importação, deduplicação, retry, reconciliação e preservação de histórico
  estiverem cobertos por testes;
- os fluxos de sincronização, credenciais inválidas, API Key expirada,
  autorização anual expirada e mudança de itemId estiverem testados;
- os estados de carregamento, erro, vazio, autorização expirada e sincronização
  pendente estiverem implementados e verificados;
- responsividade e acessibilidade tiverem sido verificadas nos tamanhos de tela
  e tecnologias assistivas alvo;
- nenhum segredo real estiver presente em código, fixtures, logs, exemplos ou
  Compose versionado, e o Client Secret e a API Key nunca chegarem ao navegador;
- a documentação de instalação, operação, backup, restauração, atualização e
  limites externos estiver atualizada;
- dados simulados, Sandbox ou mocks não forem apresentados como dados reais.

**Portão específico da integração bancária.** A integração bancária do MVP só
pode ser declarada concluída quando forem demonstrados: dois usuários com pares
Client ID/Client Secret distintos; múltiplos itemIds por usuário; validação de
credenciais e itens antes de indicar conexão ativa; API Keys obtidas e renovadas
exclusivamente no backend; importação correta de contas, cartões e transações;
segunda sincronização sem duplicação; falha de um usuário sem bloqueio dos
demais; ausência de acesso cruzado; expiração anual, mudança de itemId e
reconexão sem perda histórica; estado e última sincronização compreensíveis na
interface; operação com dados locais quando a Pluggy estiver indisponível; e
backup/restauração com estratégia segura para as chaves de criptografia.

**Documentação e mudanças.** Toda correção de regra financeira DEVE incluir
teste de regressão. Migrações DEVEM ser testadas em banco vazio e em banco com
dados existentes. Mudanças de requisito central DEVEM seguir o procedimento de
emenda e não podem ser introduzidas silenciosamente durante a implementação.
Procedimentos de backup e restauração DEVEM ser documentados e verificados.

## Governance

Esta constituição é a autoridade máxima do projeto e prevalece sobre práticas,
planos, tarefas e decisões conflitantes. Especificações, planos, tarefas,
revisões e entregas DEVEM verificar conformidade com os princípios e restrições
aqui definidos. Não conformidade bloqueia a entrega. Uma exceção temporária DEVE
ser justificada por escrito, aprovada pelo responsável pelo projeto, ter prazo e
plano de correção, e ser registrada no artefato de planejamento correspondente;
uma exceção permanente exige emenda constitucional.

**Procedimento de emenda.** Propostas de emenda DEVEM descrever a mudança, a
motivação, o impacto em privacidade, segurança, correção financeira, operação e
compatibilidade, além do plano de migração dos artefatos afetados. A aprovação é
do responsável pelo projeto. Após a aprovação, a constituição, a data de última
alteração e os artefatos dependentes DEVEM ser atualizados na mesma entrega.

**Versionamento.** A constituição usa versionamento semântico no formato
`MAJOR.MINOR.PATCH`. `MAJOR` indica remoção ou redefinição incompatível de
princípio ou seção. `MINOR` indica novo princípio, nova seção ou expansão
material de orientação existente. `PATCH` indica esclarecimento, correção de
redação ou refinamento não semântico. A data de ratificação original NÃO DEVE
ser alterada; a data de última alteração DEVE ser atualizada em toda emenda.

**Revisão de conformidade.** A conformidade DEVE ser revisada em cada portão de
fase e em cada liberação. A constituição DEVE ser reavaliada quando a
documentação da Pluggy, a disponibilidade de conectores ou uma limitação externa
mudar, e no mínimo anualmente. Decisões, riscos, premissas e limitações DEVEM ser
registrados nos artefatos do projeto, e não apenas em conversas ou conhecimento
tácito.

**Version**: 1.0.2 | **Ratified**: 2026-10-22 | **Last Amended**: 2026-10-23
