# Feature Specification: MVP de Finanças Pessoais Familiares

**Feature Branch**: `001-finance`

**Created**: 2026-10-22

**Status**: Approved

**Nota editorial**: Requisitos funcionais renumerados para FR-001–FR-139; sem alteração semântica. (2026-10-23)

**Input**: User description: "Sistema web self-hosted de finanças pessoais para um grupo familiar, com usuários isolados, integração bancária automática via Meu Pluggy, lançamentos manuais complementares e investimentos de prioridade secundária; patrimônio e metas pertencem a uma fase posterior ao MVP."

## Clarifications

### Session 2026-10-22

- Q: O MVP deve permitir que o usuário crie contas manuais — por exemplo, dinheiro em espécie ou carteira — para associar lançamentos não bancários, ou os lançamentos manuais devem usar apenas contas importadas do Meu Pluggy? → A: Permitir contas manuais; lançamentos manuais e transferências podem ser associados a essas contas.
- Q: Quais campos de uma transação importada o usuário pode corrigir manualmente, e como essas correções devem se comportar quando o provedor enviar uma atualização? → A: Permitir editar categoria, descrição, data, valor e observações; a correção do usuário prevalece sobre atualizações da origem, mantendo os valores originais para auditoria.
- Q: Como o sistema deve identificar e classificar movimentos internos entre contas próprias — como transferências e pagamentos de fatura de cartão — para impedir que sejam contados como receita ou despesa? → A: Detecção assistida; o sistema sugere correspondências por valor, data e contas, e o usuário confirma antes de classificar como transferência ou pagamento de fatura.
- Q: No dashboard, o “saldo” exibido por semana, mês e ano deve representar o resultado do período (receitas menos despesas), o saldo atual consolidado das contas, ou ambos com rótulos distintos? → A: Mostrar ambos com rótulos e datas distintos: resultado do período e saldo atual consolidado das contas.
- Q: O MVP deve incluir gestão de faturas de cartão — com fechamento, vencimento, valor e status de pagamento — ou basta tratar compras e pagamentos de fatura para evitar dupla contagem nos relatórios? → A: Gestão completa: ciclos, parcelamento, pagamentos parciais, juros e encargos, com edição manual.
- Q: Como distinguir “despesas” e “gastos” no dashboard? → A: “Despesas” é o total; “gastos por categoria” é a distribuição do mesmo total.
- Q: O saldo atual consolidado inclui dívida de cartão? → A: Não; cartões aparecem separados como passivo/fatura.
- Q: Qual é a convenção de sinal dos valores? → A: Negativo para saída, positivo para entrada; o tipo deve ser consistente.
- Q: Quais são os limites de semana, mês e ano? → A: Períodos calendário em America/Sao_Paulo, semana iniciando na segunda-feira, intervalos [início, fim); filtros from/to incluem o dia final.
- Q: Como reembolsos e chargebacks afetam os totais? → A: São entradas vinculadas à transação original, herdam a categoria e reduzem despesas no período em que ocorrem; o original não é alterado.
- Q: Como uma transferência manual aparece no sistema? → A: Duas pernas vinculadas, saída na origem e entrada no destino, excluídas de receitas/despesas.
- Q: O status “ajustada” substitui o status de pagamento da fatura? → A: Não; ajustes são um indicador separado.
- Q: Quais critérios a sugestão de movimento interno usa? → A: Critérios determinísticos de valor, data e contas, com limiar de confiança e confirmação do usuário.
- Q: Como alocações de metas são calculadas? → A: Modo valor fixo ou percentual, com exatamente um valor informado, arredondamento explícito e sem dupla contagem.
- Q: Transações pendentes compõem os totais oficiais do dashboard e dos relatórios? → A: Não. Apenas transações `POSTED` e `RECONCILED` compõem receitas, despesas, resultado do período, distribuição por categoria, drill-down e totais de fatura. Pendentes aparecem em seção própria, com quantidade e soma; ao serem efetivadas, o mesmo registro passa a compor os totais uma única vez.
- Q: Qual é a política mínima de senha? → A: 12 a 128 caracteres, sem composição obrigatória (frases-senha são permitidas), que não contenham o nome de usuário normalizado nem sejam iguais a ele, e ausentes de uma lista local versionada de senhas comuns com, no mínimo, 10.000 entradas. A mesma política vale para cadastro e troca de senha.
- Q: Quais são os limites de tentativa de login? → A: 5 falhas por 15 minutos por combinação de usuário e IP, com bloqueio de 15 minutos; a mensagem é genérica e uma autenticação bem-sucedida zera o contador.
- Q: Como itens de fatura sem transação afetam as despesas? → A: Itens vinculados a uma transação são espelhos e não somam novamente. Itens sem transação de juros, encargos, tarifas ou ajuste de despesa aumentam `expenses` uma única vez; créditos, estornos ou ajustes de crédito reduzem `expenses` observado o limite do FR-077 — o menor entre o valor do item, o total da fatura antes do item e as despesas do mesmo cartão no período do relatório —, e o excedente vira crédito para faturas futuras, nunca despesa negativa.
- Q: Como o total de uma posição de investimento é calculado? → A: `quantidade × preço unitário de referência`; quando apenas o total for conhecido, o preço unitário é `total ÷ quantidade`, arredondado para 8 casas decimais.
- Q: Como o progresso de uma meta de redução de dívida é calculado? → A: `(dívida inicial − dívida atual) ÷ dívida inicial`, limitado entre 0% e 100%; a dívida inicial é registrada na criação da meta.
- Q: Como valores em moeda diferente de BRL são tratados? → A: São exibidos com a moeda e sinalizados; não entram em totais consolidados nem são convertidos automaticamente até existir regra explícita de conversão.
- Três requisitos de orientação inicial foram removidos por decisão de escopo; os demais identificadores FR permanecem estáveis.

## User Scenarios & Testing *(mandatory)*

O MVP entrega um espaço financeiro privado por familiar, alimentado principalmente
pela importação automática das conexões bancárias pessoais de cada usuário no
Meu Pluggy. Lançamentos manuais complementam a importação, mas não a substituem.
Investimentos, patrimônio e metas são evoluções posteriores e não podem atrasar
o núcleo bancário.

### User Story 1 - Conta pessoal e isolamento entre familiares (Priority: P1)

Como membro da família, quero criar minha conta, entrar no sistema e acessar
somente o meu espaço financeiro, para que meus dados não sejam vistos nem
alterados por outro familiar.

**Why this priority**: sem autenticação e isolamento, nenhum dado financeiro pode
ser armazenado com segurança. Essa história é a base de privacidade de todo o
produto.

**Independent Test**: criar duas contas, registrar dados distintos em cada uma e
tentar acessar, alterar, excluir, exportar e relatar dados da outra pessoa por
meio de identificadores, URLs, filtros e conteúdo manipulado. Todas as
tentativas cruzadas devem ser negadas sem revelar conteúdo ou existência.

**Acceptance Scenarios**:

1. **Given** um visitante com nome de usuário único e senha válida, **When**
   ele se registra, **Then** a conta é criada e ele consegue entrar.
2. **Given** um nome de usuário já utilizado, **When** alguém tenta se
   registrar com ele, **Then** o cadastro é recusado com mensagem segura e
   nenhum dado da conta existente é revelado.
3. **Given** dois usuários autenticados com dados próprios, **When** um deles
   tenta acessar conta, transação, categoria, integração ou relatório do outro
   por identificador, URL, filtro ou conteúdo, **Then** a operação é negada e
   nenhum dado é retornado ou alterado.
4. **Given** 5 tentativas de login inválido em 15 minutos para o mesmo
   usuário e IP, **When** a quinta falha ocorre, **Then** novas tentativas são
   bloqueadas por 15 minutos e a mensagem não distingue usuário inexistente de
   senha incorreta.
5. **Given** um usuário autenticado, **When** ele altera a própria senha
   informando a senha atual, **Then** a nova senha passa a valer, a anterior
   deixa de funcionar e as demais sessões são encerradas.
6. **Given** um usuário que saiu ou cuja sessão expirou, **When** ele tenta
   voltar a uma área protegida, **Then** precisa autenticar-se novamente e
   nenhum dado sensível permanece visível.

---

### User Story 2 - Configurar o Meu Pluggy pessoal e importar dados bancários (Priority: P1)

Como membro da família, quero configurar minha própria aplicação pessoal do Meu
Pluggy e informar minhas conexões, para que minhas contas, cartões e transações
sejam importados automaticamente sem compartilhar credenciais com ninguém.

**Why this priority**: a integração bancária automática é o núcleo do MVP. Sem
ela, o produto não cumpre a proposta de valor principal.

**Independent Test**: configurar duas integrações independentes, cada uma com
credenciais e conexões próprias, importar dados e verificar que cada registro
pertence ao usuário e à conexão corretos. Credenciais inválidas não devem ativar
a integração nem revelar segredos.

**Acceptance Scenarios**:

1. **Given** Client ID, Client Secret e ao menos um itemId válido pertencentes
   ao usuário, **When** ele salva a configuração, **Then** o sistema valida as
   credenciais e a conexão antes de marcar a integração como ativa.
2. **Given** credenciais inválidas, expiradas ou de outra pessoa, **When** o
   usuário tenta salvar, **Then** a integração não é ativada, a mensagem é
   acionável e nenhum segredo, token ou resposta sensível do provedor é exibido.
3. **Given** um usuário com vários itemIds, **When** a importação ocorre,
   **Then** contas, cartões e transações são associados à conexão e ao usuário
   corretos, sem mistura entre conexões.
4. **Given** uma transação importada, **When** ela é exibida, **Then** sua
   origem bancária é identificada e ela é distinguida de um lançamento manual.
5. **Given** um Client Secret já salvo, **When** o usuário reabre a configuração
   ou consulta a integração, **Then** o segredo não é exibido nem devolvido à
   interface, e a credencial temporária de acesso nunca é mostrada.
6. **Given** um campo ausente no retorno do provedor, **When** a conta ou
   transação é exibida, **Then** o sistema informa que o dado não foi fornecido,
   sem inventar valor.
7. **Given** uma nova conexão adicionada posteriormente pelo usuário, **When**
   ele cadastra o novo itemId, **Then** pode sincronizá-la sem refazer as
   conexões já existentes.

---

### User Story 3 - Sincronizar com segurança e recuperar conexões (Priority: P1)

Como membro da família, quero acompanhar o estado da sincronização e resolver
credenciais inválidas ou autorizações expiradas, para continuar confiando nos
dados sem perder o histórico já importado.

**Why this priority**: a integração depende de um provedor externo com atualização
diária e autorizações que expiram. Sem recuperação explícita, os dados deixam de
ser confiáveis e o usuário não sabe o que fazer.

**Independent Test**: simular sincronização bem-sucedida, resposta vazia,
credencial inválida, autorização expirada, indisponibilidade e mudança de itemId.
Verificar estados, mensagens, preservação do histórico e ausência de duplicação
após a reconexão.

**Acceptance Scenarios**:

1. **Given** uma integração ativa, **When** a sincronização programada ocorre,
   **Then** o sistema registra a tentativa, atualiza o estado e exibe a última
   sincronização bem-sucedida.
2. **Given** uma resposta vazia, um erro ou uma indisponibilidade do provedor,
   **When** a sincronização termina, **Then** o histórico existente permanece
   intacto e a interface indica falha, pendência ou necessidade de ação.
3. **Given** uma autorização expirada ou credencial inválida, **When** o
   sistema detecta o problema, **Then** a conexão passa a exigir ação do
   usuário, com instruções de renovação, e os dados já importados continuam
   acessíveis.
4. **Given** uma reconexão em que o itemId mudou, **When** o usuário atualiza o
   identificador, **Then** o sistema valida a nova conexão, reconcilia o
   histórico e não perde nem duplica registros.
5. **Given** a mesma sincronização executada várias vezes, **When** os mesmos
   dados são processados novamente, **Then** nenhuma transação é duplicada.
6. **Given** a falha de um usuário ou de uma conexão, **When** a sincronização
   geral ocorre, **Then** as demais integrações continuam funcionando.
7. **Given** o provedor indisponível, **When** o usuário abre o sistema,
   **Then** dados já importados, lançamentos manuais e relatórios continuam
   disponíveis.
8. **Given** o usuário solicita consultar os dados disponíveis agora, **When**
   a consulta é executada, **Then** o sistema busca o que já está disponível e
   informa que isso não força uma atualização na instituição de origem nem
   promete sincronização em tempo real.

---

### User Story 4 - Visualizar dashboard e relatórios consolidados (Priority: P2)

Como membro da família, quero visualizar receitas, despesas, resultado do
período, saldo atual das contas e distribuição de despesas por período, conta,
cartão e categoria, para entender minha situação financeira e conferir os
lançamentos que compõem cada total.

**Why this priority**: a importação só gera valor quando o usuário consegue
interpretar os dados e identificar de onde vêm os números.

**Independent Test**: montar um conjunto conhecido de transações em várias contas
e cartões, aplicar filtros e conferir totais, gráficos e listas de detalhamento.
Os totais devem respeitar as regras financeiras e corresponder exatamente às
transações filtradas.

**Acceptance Scenarios**:

1. **Given** transações em mais de uma conta ou cartão, **When** o dashboard é
   aberto, **Then** o resultado do período (receitas menos despesas) e o saldo
   atual consolidado das contas são exibidos com rótulos e datas de referência
   distintos, por semana, mês e ano.
2. **Given** filtros por período, conta, cartão, categoria, tipo de movimento,
   origem ou faixa de valor, **When** o usuário os aplica, **Then** totais e
   gráficos refletem somente as transações correspondentes.
3. **Given** um gráfico por categoria ou evolução temporal, **When** o usuário
   seleciona uma parte, **Then** vê a lista de transações que compõem aquele
   total e a soma da lista corresponde ao valor exibido.
4. **Given** uma compra no cartão e o pagamento da fatura, **When** os relatórios
   são calculados, **Then** a despesa é contabilizada uma única vez.
5. **Given** uma transferência entre contas próprias, **When** os relatórios
   são calculados, **Then** ela não aparece como receita nem como despesa.
6. **Given** saldo ausente, desatualizado ou estimado, **When** ele é exibido,
   **Then** a interface sinaliza a condição e a data de referência.
7. **Given** uma tela estreita de celular, **When** o dashboard é usado,
   **Then** totais, filtros, gráficos e listas permanecem legíveis e operáveis.

---

### User Story 5 - Gerenciar contas, lançamentos manuais e correções de transações importadas (Priority: P2)

Como membro da família, quero criar contas manuais para dinheiro e carteira,
registrar nelas receitas, despesas, transferências, estornos e reembolsos, além
de criar categorias, classificar e corrigir transações importadas, para cobrir
operações ausentes e manter meus dados organizados.

**Why this priority**: operações manuais são o complemento e o plano de
contingência quando a importação não cobre uma operação, sem substituir a
integração bancária.

**Independent Test**: criar uma conta manual, registrar cada tipo de lançamento
manual nela, categorizar e corrigir uma transação importada, executar novas
sincronizações e verificar persistência, filtros e atualização dos relatórios.

**Acceptance Scenarios**:

1. **Given** um usuário autenticado, **When** ele cria uma receita, despesa,
   transferência, estorno, reembolso ou ajuste manual, **Then** o lançamento é
   salvo com origem manual e aparece nos relatórios aplicáveis.
2. **Given** uma transação importada, **When** o usuário a categoriza, **Then**
   a escolha persiste após sincronizações posteriores e não é sobrescrita
   silenciosamente por dados da origem.
3. **Given** uma categoria personalizada, **When** o usuário a cria, edita ou
   exclui, **Then** apenas os próprios lançamentos são afetados e nenhuma
   transação é apagada; lançamentos vinculados ficam sem categoria ou são
   reatribuídos conforme a escolha do usuário.
4. **Given** um lançamento manual, **When** o usuário o edita ou exclui,
   **Then** os totais e relatórios são atualizados de forma consistente.
5. **Given** o provedor externo indisponível, **When** o usuário registra um
   lançamento manual, **Then** ele é salvo e permanece utilizável.
6. **Given** lançamentos de tipos e origens diferentes, **When** o usuário
   aplica filtros, **Then** encontra os registros por período, conta, cartão,
   categoria, tipo, origem e faixa de valor.
7. **Given** uma conta manual com saldo de referência e data, **When** o usuário
   registra receitas, despesas ou transferências nela, **Then** o saldo exibido
   reflete o saldo de referência ajustado pelos lançamentos posteriores e os
   lançamentos aparecem nos relatórios aplicáveis.
8. **Given** uma conta manual com lançamentos, **When** o usuário a desativa,
   **Then** novos lançamentos não podem ser associados a ela, mas o histórico e
   os relatórios permanecem disponíveis.
9. **Given** uma transação importada, **When** o usuário corrige descrição, data,
   valor, categoria ou observações, **Then** a correção é identificada como do
   usuário, os valores originais da origem permanecem auditáveis e a
   sincronização seguinte não apaga a correção.
10. **Given** um débito e um crédito com valor e data compatíveis em contas
    próprias, **When** o sistema sugere uma transferência ou pagamento de fatura,
    **Then** o usuário confirma ou rejeita a sugestão; somente após a confirmação
    o movimento é excluído de receitas e despesas, e a decisão fica registrada.

---

### User Story 6 - Gerenciar faturas de cartão (Priority: P2)

Como membro da família, quero configurar o ciclo dos meus cartões e acompanhar
faturas, parcelas, pagamentos parciais, juros e encargos, para saber exatamente
quanto devo em cada fatura sem duplicar despesas.

**Why this priority**: cartões concentram parte relevante dos gastos e o
pagamento da fatura não pode ser confundido com uma nova despesa. A gestão
explícita da fatura torna os relatórios confiáveis.

**Independent Test**: configurar um cartão, gerar faturas com compras à vista e
parceladas, registrar pagamento parcial e encargos, editar um item manual e
conferir totais, status, saldo devedor e ausência de duplicação.

**Acceptance Scenarios**:

1. **Given** um cartão de crédito, **When** o usuário configura dia de
   fechamento e vencimento, **Then** o sistema organiza as compras em faturas
   por ciclo e exibe a fatura aberta e as futuras.
2. **Given** uma compra parcelada, **When** as faturas são formadas, **Then**
   cada parcela é contabilizada no ciclo correspondente, sem contar o valor
   total antecipadamente.
3. **Given** uma fatura fechada, **When** o usuário registra pagamento total ou
   parcial, **Then** o saldo devedor e o status são atualizados e o pagamento
   não é contado como despesa.
4. **Given** uma fatura com juros, encargos, tarifas, créditos ou estornos,
   **When** o usuário os registra, **Then** cada item aparece separadamente e
   afeta o total conforme sua natureza, sem duplicar compras.
5. **Given** uma fatura, **When** o usuário adiciona, edita ou remove um item
   manual, **Then** o total e o saldo devedor são recalculados, a origem do
   ajuste é registrada e as compras importadas não são apagadas.
6. **Given** um pagamento de fatura, **When** os relatórios são calculados,
   **Then** ele é tratado como movimento interno e as compras já contabilizadas
   não são contadas novamente.
7. **Given** dois usuários com cartões próprios, **When** cada um consulta suas
   faturas, **Then** nenhum vê cartão, fatura, item ou pagamento do outro.

---

### User Story 7 - Controlar conta, exportar dados e encerrar vínculos (Priority: P2)

Como membro da família, quero exportar meus dados, desconectar minha integração
e excluir minha conta, para manter controle sobre minhas informações e sobre o
encerramento do uso do sistema.

**Why this priority**: privacidade e autonomia exigem que o usuário consiga sair,
levar seus dados e remover seus vínculos sem depender de outra pessoa.

**Independent Test**: executar cada ação com dois usuários e verificar que a
exportação contém apenas os dados do solicitante, que a desconexão interrompe a
sincronização e que a exclusão remove o acesso sem afetar o outro usuário.

**Acceptance Scenarios**:

1. **Given** um usuário autenticado, **When** ele solicita a exportação dos
   próprios dados, **Then** recebe uma cópia portátil e documentada de seus dados
   ativos, sem segredos e sem dados de outros usuários.
2. **Given** uma integração configurada, **When** o usuário a desconecta,
   **Then** novas sincronizações são interrompidas, as credenciais são removidas
   e os dados já importados são preservados, salvo exclusão explícita.
3. **Given** um usuário que confirma a exclusão da conta, **When** a operação é
   concluída, **Then** o acesso é bloqueado, credenciais e dados ativos são
   removidos conforme a política documentada, e os demais usuários não são
   afetados.
4. **Given** uma sincronização em andamento, **When** o usuário exclui a conta
   ou desconecta a integração, **Then** o processamento é encerrado com
   segurança e não deixa dados parciais ou corrompidos.
5. **Given** a exclusão de um usuário, **When** os relatórios e exportações dos
   demais são gerados, **Then** nenhum dado do usuário excluído é incluído.

---

### User Story 8 - Acompanhar investimentos manualmente (Priority: P3 - fase secundária)

Como membro da família, quero registrar manualmente minhas posições de ações e
fundos imobiliários, inicialmente com foco em XP Investimentos e Santander
Corretora, para acompanhar o que possuo sem depender de uma integração ainda não
validada.

**Why this priority**: investimentos são um módulo secundário. O registro manual
entrega valor sem presumir que os conectores estejam disponíveis no fluxo Meu
Pluggy.

**Independent Test**: cadastrar, editar e excluir posições manuais, conferir os
totais e a data de referência e verificar que nenhuma importação automática é
oferecida ou anunciada sem validação real.

**Acceptance Scenarios**:

1. **Given** um usuário autenticado, **When** ele cadastra uma posição com ativo,
   ticker ou código, quantidade, preço ou valor de referência, data de
   referência, corretora e observações, **Then** a posição é exibida como
   registro manual.
2. **Given** uma posição manual, **When** o total de investimentos é exibido,
   **Then** o valor usa a data de referência e é identificado como informado pelo
   usuário, não como cotação de mercado.
3. **Given** que a disponibilidade do conector no Meu Pluggy não foi validada,
   **When** o módulo de investimentos é apresentado, **Then** o sistema não
   oferece nem declara importação automática como funcional.
4. **Given** qualquer posição de investimento, **When** o usuário consulta o
   módulo, **Then** não recebe recomendação de compra, venda ou alocação.
5. **Given** que o módulo secundário não está concluído, **When** o MVP bancário
   é avaliado, **Then** sua conclusão não depende de investimentos.

---

### User Story 9 - Acompanhar patrimônio consolidado (Priority: P4 - pós-MVP)

Como membro da família, quero visualizar patrimônio bruto, passivos e patrimônio
líquido ao longo do tempo, para acompanhar minha evolução patrimonial com dados
datados e sem dupla contagem.

**Why this priority**: patrimônio é uma evolução planejada, posterior ao MVP
bancário. Deve ser especificado para orientar a evolução do modelo, sem bloquear
a primeira entrega.

**Independent Test**: combinar saldos de contas, cartões, posições de
investimento e ativos/passivos manuais, gerar snapshots e conferir cálculos,
datas, lacunas e ausência de dupla contagem.

**Acceptance Scenarios**:

1. **Given** ativos, passivos e suas origens, **When** a visão patrimonial é
   aberta, **Then** o sistema mostra patrimônio bruto, total de passivos e
   patrimônio líquido calculado como ativos menos passivos.
2. **Given** contas, cartões, investimentos e registros manuais, **When** o
   patrimônio é consolidado, **Then** o mesmo valor não é contado duas vezes e
   saldos de cartão são tratados como passivos.
3. **Given** snapshots patrimoniais datados, **When** o usuário consulta a
   evolução, **Then** vê a data de referência e a composição de cada posição,
   com sinalização de lacunas, estimativas e dados desatualizados.
4. **Given** histórico insuficiente, **When** o usuário consulta períodos
   anteriores, **Then** o sistema não inventa valores nem infere saldos passados
   a partir de dados atuais incompletos.
5. **Given** um valor de referência manual, **When** ele é exibido, **Then** não
   é apresentado como cotação de mercado.
6. **Given** dois usuários, **When** cada um consulta seu patrimônio, **Then**
   nenhum vê ativos, passivos ou snapshots do outro.

---

### User Story 10 - Planejar metas financeiras (Priority: P4 - pós-MVP)

Como membro da família, quero criar metas financeiras com valor-alvo, prazo
opcional e regra de progresso transparente, para acompanhar objetivos como
reserva de emergência, viagem ou redução de dívida sem movimentar dinheiro.

**Why this priority**: metas complementam o planejamento patrimonial em uma fase
posterior. Não são pré-requisito para o MVP bancário.

**Independent Test**: criar metas com diferentes origens de progresso, atualizar
os dados de origem e verificar cálculo, histórico, alocação explícita, estados e
ausência de movimentação financeira.

**Acceptance Scenarios**:

1. **Given** um usuário autenticado, **When** ele cria uma meta com nome, tipo,
   valor-alvo, prazo opcional e regra de progresso, **Then** a meta exibe valor
   atual, percentual, valor restante, prazo e a regra que determina o progresso.
2. **Given** uma meta cuja origem é valor alocado manualmente, contas ou ativos
   selecionados ou patrimônio líquido, **When** os dados de origem mudam,
   **Then** o progresso é recalculado e o histórico datado é preservado.
3. **Given** o mesmo saldo disponível para várias metas, **When** não há
   alocação explícita, **Then** o valor não é contado integralmente em todas as
   metas e a regra de rateio ou de exclusão fica visível.
4. **Given** metas atingidas, pausadas, arquivadas, atrasadas ou alteradas,
   **When** o usuário consulta a lista, **Then** cada estado é apresentado de
   forma distinta e consistente com os critérios definidos.
5. **Given** uma meta financeira, **When** o usuário interage com ela, **Then**
   o sistema não move dinheiro, não cria ordens e não executa ações bancárias.
6. **Given** dois usuários, **When** cada um consulta suas metas, **Then**
   nenhum vê metas ou progresso do outro.

---

### Edge Cases

- Cadastro com nome de usuário já existente, senha fraca ou tentativa repetida de
  criação deve falhar sem revelar dados de outra conta.
- Sessão expirada durante uma importação, edição ou exportação deve interromper a
  operação com segurança e preservar a consistência dos dados.
- Duas contas de usuários diferentes podem registrar conexões com o mesmo
  identificador externo; elas devem permanecer completamente separadas e nunca
  ser fundidas.
- Um usuário pode ter vários itemIds, inclusive de instituições diferentes; a
  falha ou remoção de um item não pode afetar os demais.
- O mesmo item pode retornar várias contas e cartões; o limite de conexões não
  deve ser confundido com a quantidade de contas ou produtos.
- Credenciais válidas podem se tornar inválidas após rotação do segredo; a
  atualização deve ser validada sem expor o valor anterior ou o novo.
- A credencial temporária do provedor pode expirar no meio de uma operação; a
  renovação deve ser transparente e não pode misturar credenciais de usuários.
- A autorização da aplicação pode expirar anualmente e o consentimento de cada
  banco pode expirar separadamente; os dois eventos devem ser distinguidos.
- Após renovação, o itemId pode mudar; a atualização deve reconciliar o histórico
  sem duplicar transações nem apagar correções.
- Uma lista vazia pode significar ausência real de transações, falha de
  autorização ou indisponibilidade; o sistema não deve tratar automaticamente
  como exclusão.
- Uma sincronização pode ser interrompida no meio; a próxima execução deve
  retomar sem duplicar nem perder registros já confirmados.
- Um evento de atualização pode chegar duplicado, atrasado, fora de ordem ou sem
  dados completos; a consulta de reconciliação deve prevalecer.
- Transações com o mesmo identificador externo em contas diferentes não devem
  ser deduplicadas entre si.
- Uma transação pendente pode virar efetivada com mudança de data, descrição ou
  valor; a reconciliação deve atualizar o registro sem criar outro.
- Se a origem alterar um campo que o usuário corrigiu, a correção do usuário
  prevalece e os valores originais da origem permanecem registrados para
  auditoria.
- Uma transação pode desaparecer da origem após ter sido importada; ela deve ser
  marcada e preservada para auditoria, não apagada silenciosamente.
- Estornos e reembolsos podem ser parciais ou posteriores à compra; a regra de
  impacto nos totais deve ser explícita.
- O pagamento da fatura pode aparecer como transação separada das compras; os
  relatórios não podem contar a mesma despesa duas vezes.
- Uma compra no cartão pode ocorrer após o fechamento; deve entrar na fatura
  seguinte, salvo ajuste explícito.
- Uma compra parcelada deve gerar parcelas em ciclos distintos; o valor total
  não pode ser contado de uma vez nem as parcelas duplicadas.
- Um pagamento parcial de fatura deve reduzir o saldo devedor sem criar nova
  despesa; o status deve refletir o valor restante.
- Juros, encargos e tarifas devem ser despesas explícitas da fatura, não
  pagamentos nem duplicação das compras.
- Um estorno ou crédito na fatura deve reduzir o total conforme a regra, sem
  apagar a compra original.
- Uma alteração manual de item de fatura deve preservar o valor anterior e a
  origem do ajuste.
- Mudança de fechamento ou vencimento não pode reescrever faturas passadas;
  transações atrasadas devem ir para o ciclo seguinte ou para ajuste explícito.
- Um cartão sem ciclo configurado deve exibir transações não alocadas, sem
  inventar fatura.
- Uma transferência entre contas próprias pode aparecer nas duas pontas; deve
  ser tratada como movimento único para fins de receita e despesa.
- Uma transferência pode ocorrer entre conta manual e conta importada do próprio
  usuário, sem se tornar receita ou despesa.
- Uma sugestão de transferência ou pagamento de fatura não confirmada deve
  permanecer visível como pendência e não pode ser tratada como classificada
  definitivamente.
- Uma conta manual com lançamentos pode ser desativada; o histórico permanece e
  novos lançamentos não podem usá-la.
- Uma categoria com transações vinculadas pode ser excluída; as transações devem
  permanecer e ficar sem categoria ou ser reatribuídas explicitamente.
- Um valor monetário pode chegar com moeda diferente de BRL; o MVP deve
  sinalizar a moeda e não convertê-la automaticamente sem regra definida.
- Saldos bancários podem estar ausentes, negativos, desatualizados ou defasados
  em relação à data da transação; a interface deve informar a condição.
- O saldo atual consolidado pode incluir contas manuais e importadas; ele não
  deve ser somado ao resultado do período como se fossem a mesma métrica.
- Uma exportação pode ser solicitada durante sincronização; o resultado deve ser
  consistente e conter apenas dados do solicitante.
- A exclusão da conta pode ocorrer enquanto uma sincronização está em andamento;
  o processamento deve ser cancelado com segurança.
- Um usuário pode perder a senha; a recuperação não pode conceder acesso aos
  dados de outra pessoa nem depender de segredo exposto na interface.
- Gráficos e tabelas podem ser usados apenas com teclado ou leitor de tela; deve
  haver alternativa textual e associação entre rótulos e valores.
- O sistema pode estar indisponível para o provedor externo; a operação local
  deve continuar e nenhuma resposta vazia deve ser tratada como exclusão.

## Requirements *(mandatory)*

### Functional Requirements

#### Conta, sessão e isolamento

- **FR-001**: O sistema DEVE permitir cadastro com nome de usuário único e senha.
- **FR-002**: O sistema DEVE recusar nomes de usuário duplicados e senhas que não
  atendam à política mínima: 12 a 128 caracteres, sem exigência de composição,
  que não contenham o nome de usuário normalizado (comparação sem distinção de
  maiúsculas/minúsculas) nem sejam iguais a ele, e ausentes de uma lista local
  versionada de senhas comuns com, no mínimo, 10.000 entradas mantidas no
  repositório e atualizáveis sem chamada externa, usando mensagens que não
  revelem dados de outra conta.
- **FR-003**: O sistema DEVE permitir login, logout e encerramento de sessão,
  impedindo o acesso a áreas protegidas após o encerramento. A sessão DEVE
  expirar após 30 minutos de inatividade e 12 horas absolutas.
- **FR-004**: Todo recurso financeiro DEVE pertencer a exatamente um usuário, e a
  autorização DEVE ser verificada no servidor em toda leitura, escrita, exclusão,
  exportação e operação de relatório.
- **FR-005**: Identificadores, URLs, filtros ou conteúdo enviado pelo cliente
  NÃO DEVEM conceder acesso a recursos de outro usuário.
- **FR-006**: Tentativas de acesso cruzado DEVEM ser negadas sem revelar a
  existência, o conteúdo ou os metadados do recurso alheio.
- **FR-007**: Nenhum perfil, tela ou função do produto DEVE conceder a um
  familiar acesso aos dados financeiros de outro; a separação não pode ser
  contornada por um administrador funcional.
- **FR-008**: O sistema DEVE limitar tentativas de autenticação a 5 falhas por
  15 minutos por combinação de usuário e IP e bloquear novas tentativas por 15
  minutos, com mensagem genérica que não distinga usuário inexistente de senha
  incorreta; autenticação bem-sucedida DEVE zerar o contador.
- **FR-009**: A alteração de senha DEVE exigir a senha atual, aplicar a nova
  política de senha e encerrar as demais sessões ativas do usuário.
- **FR-010**: Credenciais de sessão DEVEM ser protegidas contra acesso por
  scripts, não DEVEM ser armazenadas em área acessível ao navegador e as
  requisições DEVEM ser protegidas contra falsificação de origem.
- **FR-011**: O logout e a expiração DEVEM impedir o retorno a telas protegidas
  por histórico do navegador ou conteúdo temporário.
- **FR-012**: O sistema DEVE minimizar a coleta e a retenção de dados pessoais e
  financeiros ao necessário para as finalidades documentadas.

#### Configuração pessoal do Meu Pluggy

- **FR-013**: O sistema DEVE oferecer uma área de configuração de integração por
  usuário, acessível apenas ao próprio usuário.
- **FR-014**: O sistema DEVE permitir cadastrar, validar, atualizar e remover o
  Client ID, o Client Secret e múltiplos itemIds pertencentes ao usuário.
- **FR-015**: A integração só DEVE ser marcada como ativa após validar as
  credenciais e ao menos uma conexão pertencente ao usuário.
- **FR-016**: Credenciais inválidas, revogadas ou de outra pessoa NÃO DEVEM ativar
  a integração e DEVEM produzir mensagem acionável sem expor segredo, token ou
  resposta sensível do provedor.
- **FR-017**: O Client Secret DEVE ser tratado como segredo de escrita única, não
  DEVE ser devolvido à interface após o salvamento, exibido novamente,
  armazenado em área acessível ao navegador ou registrado em logs.
- **FR-018**: O sistema DEVE obter a credencial temporária de acesso ao provedor
  no servidor, a partir das credenciais pessoais armazenadas; o usuário NÃO DEVE
  precisar fornecer essa credencial.
- **FR-019**: A credencial temporária DEVE ser vinculada à integração e ao
  usuário corretos, reutilizada somente durante sua validade documentada
  (atualmente duas horas), renovada de forma transparente e nunca compartilhada
  entre usuários.
- **FR-020**: A credencial temporária NÃO DEVE ser persistida sem necessidade
  operacional justificada e documentada, nem exposta ao navegador, a logs ou a
  mensagens de erro.
- **FR-021**: O sistema DEVE permitir múltiplos itemIds por usuário, mantendo
  cada um vinculado à integração e ao proprietário corretos, mesmo que
  identificadores externos coincidam entre usuários. Um itemId só DEVE ser
  aceito se existir na aplicação Meu Pluggy do próprio usuário; item inexistente
  ou malformado DEVE ser recusado como erro de validação do campo `itemId`, sem
  registrar conexão.
- **FR-022**: O sistema DEVE exibir a quantidade de conexões configuradas,
  informar o limite da modalidade em configuração versionada e datada (atualmente
  5 conexões por conta pessoal), registrar a data da última verificação e alertar
  quando o limite for atingido, sem confundir conexões com a quantidade de contas
  ou cartões retornados.
- **FR-023**: A remoção de um item DEVE interromper novas sincronizações daquele
  item, remover suas credenciais de acesso e preservar o histórico por padrão,
  salvo exclusão explícita do usuário.
- **FR-024**: Após nova autorização, o usuário DEVE poder atualizar o itemId; o
  sistema DEVE validar a nova conexão, reconciliar o histórico e impedir
  duplicação ou perda de dados. Se o novo itemId não existir na aplicação do
  usuário, a atualização DEVE ser recusada sem alterar o vínculo anterior.
- **FR-025**: A atualização de Client ID ou Client Secret só DEVE substituir a
  configuração anterior após validação bem-sucedida; em caso de falha, a
  configuração anterior DEVE permanecer e o usuário DEVE ser informado.
- **FR-026**: O sistema NÃO DEVE solicitar Connect Token no perfil pessoal no MVP;
  esse token só PODE ser considerado em uma etapa futura que use o widget de
  conexão do provedor.

#### Importação, sincronização e recuperação

- **FR-027**: O sistema DEVE importar contas bancárias e contas ou cartões de
  crédito disponíveis nos itens autorizados do usuário.
- **FR-028**: Para cada conta ou cartão importado, o sistema DEVE apresentar
  tipo, instituição, apelido, saldo disponível quando fornecido, última
  sincronização e estado da conexão.
- **FR-029**: O sistema DEVE importar transações com descrição, data, valor,
  identificador de origem, conta, tipo e demais metadados que a origem
  efetivamente disponibilizar.
- **FR-030**: O sistema DEVE distinguir transações importadas de lançamentos
  manuais em todas as telas, filtros, relatórios e entradas de drill-down.
- **FR-031**: Dados ausentes NÃO DEVEM ser inventados, estimados sem indicação
  ou substituídos por exemplos; o sistema DEVE indicar quando a origem não
  forneceu o campo.
- **FR-032**: O sistema DEVE impedir associação cruzada entre contas, itens,
  integrações e usuários diferentes.
- **FR-033**: O sistema DEVE executar sincronização programada compatível com a
  atualização diária das conexões de origem, com carga inicial de 12 meses,
  reprocessamento diário de 7 dias com sobreposição e reconciliação mensal de 12
  meses; as janelas DEVEM ser configuráveis e documentadas e o sistema NÃO DEVE
  prometer sincronização em tempo real.
- **FR-034**: O sistema DEVE permitir consultar os dados já disponíveis, deixando
  claro que a consulta não força a instituição de origem a atualizar.
- **FR-035**: O sistema DEVE representar, no mínimo, os estados: configuração
  incompleta, ativa, sincronizando, sincronizada, falha de credenciais, requer
  ação do usuário, autorização expirada e item indisponível.
- **FR-036**: O sistema DEVE exibir a última tentativa de sincronização e a
  última sincronização bem-sucedida, com data e hora de referência.
- **FR-037**: O sistema DEVE distinguir a expiração da autorização da aplicação
  pessoal (atualmente anual) da expiração do consentimento individual de cada
  instituição.
- **FR-038**: O sistema DEVE tratar autorização expirada, erro de login, resposta
  vazia inesperada, timeout, limite de requisições e indisponibilidade sem
  excluir dados locais.
- **FR-039**: Após nova autorização, o sistema DEVE validar os novos itemIds e
  reconciliar os dados existentes antes de retomar a sincronização.
- **FR-040**: O sistema NÃO DEVE apagar histórico porque um item retornou lista
  vazia, falhou, mudou de estado, teve seu itemId alterado ou ficou
  indisponível. A remoção definitiva do histórico DEVE exigir ação explícita do
  usuário e seguir política documentada; a transição para `SOURCE_REMOVED`
  segue o FR-046.
- **FR-041**: A recuperação de falhas DEVE retomar a sincronização sem duplicar
  registros nem perder dados já confirmados.
- **FR-042**: Importações e sincronizações DEVEM ser idempotentes para
  atualizações repetidas e falhas parciais.
- **FR-043**: Cada item externo DEVE ser relacionado a uma integração e a um
  usuário proprietário em registro local que permita provar essa associação.
- **FR-044**: A deduplicação DEVE usar chave composta estável que considere
  provedor, integração ou item, conta e identificador externo da transação
  quando necessário.
- **FR-045**: Quando a origem informar mudanças, o sistema DEVE atualizar o
  registro importado preservando categorização e correções manuais conforme
  regra de precedência explícita.
- **FR-046**: Quando uma transação deixar de aparecer em uma sincronização
  conclusiva, o sistema DEVE marcá-la como não encontrada na origem, preservar
  seu histórico e auditoria e excluí-la dos totais correntes após reconciliação
  explícita, sem inferir exclusão de resposta vazia ou falha. O usuário PODE
  restaurar uma transação `SOURCE_REMOVED` quando ela voltar a existir na origem
  ou quando a remoção tiver sido incorreta. A restauração DEVE validar a origem,
  preservar a auditoria e impedir duplicata; a transação só volta aos totais se
  estiver `POSTED` ou `RECONCILED`.
- **FR-047**: Se o provedor enviar notificações de atualização, o sistema DEVE
  tratá-las apenas como sinal para consultar os dados completos, sem confiar no
  conteúdo da notificação como fonte integral.
- **FR-048**: Notificações de atualização, se usadas, DEVEM ser configuradas por
  integração pessoal, autenticadas, protegidas contra repetição, associadas ao
  item e ao usuário por vínculo persistido, processadas de forma assíncrona e
  respondidas rapidamente; a sincronização programada DEVE permanecer como
  reconciliação.
- **FR-049**: O sistema DEVE respeitar limites de requisição, aplicar recuo
  progressivo em falhas temporárias e limitar a concorrência.
- **FR-050**: A falha de um usuário ou de uma conexão NÃO DEVE bloquear a
  sincronização dos demais.
- **FR-051**: Quando o provedor estiver indisponível, dados já importados,
  lançamentos manuais e relatórios DEVEM permanecer disponíveis.
- **FR-052**: Registros de sincronização DEVEM conter tentativa, resultado,
  duração e código de erro sanitizado, sem segredos, credenciais temporárias,
  conteúdos financeiros completos ou dados pessoais desnecessários.

#### Regras financeiras, lançamentos manuais e categorias

- **FR-053**: Valores monetários DEVEM ser armazenados e calculados com precisão
  decimal exata e consistente, sem perda por arredondamento binário. O valor
  efetivo DEVE ser assinado: negativo para saída e positivo para entrada; o tipo
  DEVE ser consistente com o sinal, e transferências entre contas próprias NÃO
  DEVEM entrar em receitas ou despesas.
- **FR-054**: Compras no cartão DEVEM contar como despesa e o pagamento da fatura
  NÃO DEVE contabilizar novamente as mesmas compras; a correspondência entre o
  pagamento e a fatura segue o FR-055.
- **FR-055**: Transferências entre contas próprias e pagamentos de fatura NÃO
  DEVEM ser receitas nem despesas. O sistema DEVE sugerir correspondências entre
  débitos e créditos do mesmo usuário por valor, data e contas, usando critérios
  determinísticos: contas diferentes, sinais opostos, tolerância de
  `max(R$ 0,01, 0,5% do maior valor absoluto entre os dois movimentos)`, janela
  de ±3 dias para `TRANSFER` e ±7 dias para `CARD_PAYMENT`, score com pesos 50
  para valor, 30 para data e 20 para contas, e limiar 80. Empates DEVEM ser
  resolvidos pela menor diferença de data, depois pela menor diferença de valor
  e, por fim, pelo menor identificador local. Nenhuma correspondência DEVE ser
  confirmada automaticamente; o usuário DEVE confirmar ou rejeitar.
- **FR-056**: O sistema DEVE distinguir, com rótulos e cálculos próprios:
  - **gasto efetivo** (`expenses`): despesas por competência, incluindo compras
    de cartão e itens de fatura com `EXPENSE_INCREASE`, excluindo pagamentos de
    fatura e transferências internas;
  - **resultado do período** (`periodResult`): `income − expenses`;
  - **saldo atual consolidado** (`currentBalance`): soma dos saldos de contas
    `BANK`, `CASH` e `WALLET` com status `ACTIVE`, sem cartões;
  - **transferência interna**: movimento confirmado entre contas próprias, sem
    impacto em `income`/`expenses`;
  - **fluxo de caixa**: variação líquida de caixa nas contas `BANK`, `CASH` e
    `WALLET`, incluindo receitas e despesas realizadas e pagamentos de fatura,
    excluindo compras de cartão até o pagamento; transferências internas se
    compensam. O MVP NÃO DEVE exibir um total separado de fluxo de caixa nem
    rotular `currentBalance` ou `periodResult` como fluxo de caixa.
- **FR-057**: Estornos, reembolsos e chargebacks DEVEM ser transações de entrada
  vinculadas à transação original por `related_transaction_id`, herdar sua
  categoria e reduzir as despesas no período em que ocorrem; a transação original
  NÃO DEVE ser alterada. Reembolsos parciais DEVEM ser suportados. Reembolsos e
  chargebacks vinculados NÃO DEVEM reduzir despesas além do valor efetivo da
  transação original; o excedente DEVE ser tratado como crédito para faturas
  futuras, observado o FR-077. Ajustes DEVEM ter sinal e motivo explícitos e
  NÃO DEVEM ser vinculados automaticamente sem confirmação.
- **FR-058**: Transações `PENDING` NÃO DEVEM compor receitas, despesas,
  resultado do período, distribuição por categoria, drill-down nem totais de
  fatura. Elas DEVEM ser exibidas separadamente com `pendingCount`,
  `pendingIncome` e `pendingExpenses`, identificadas como não efetivadas; o MVP
  NÃO DEVE exibir um total líquido de pendentes. Quando uma transação pendente se
  tornar efetivada, o MESMO registro DEVE ser atualizado para `POSTED` e passar
  a compor os totais uma única vez, sem criar duplicata. Se voltar a pendente ou
  for marcada como removida na origem após reconciliação explícita, o registro
  DEVE sair dos totais correntes sem apagar o histórico. A transição NÃO DEVE
  reescrever faturas já fechadas ou pagas, observado o FR-081.
- **FR-059**: O sistema DEVE permitir que o usuário corrija categoria, descrição,
  data, valor e observações de uma transação importada; a correção DEVE ser
  identificada como sobrescrita do usuário, preservar os valores originais da
  origem para auditoria e prevalecer sobre atualizações posteriores da origem.
- **FR-060**: Quando a origem enviar valor diferente de um campo já corrigido,
  o sistema DEVE manter a correção do usuário, registrar o novo valor da origem
  em auditoria e NÃO DEVE reabrir nem sobrescrever a correção. Campos não
  corrigidos são atualizados normalmente, observado o FR-059.
- **FR-061**: A sincronização NÃO DEVE duplicar compras nem eliminar correções
  manuais.
- **FR-062**: O sistema DEVE permitir criar, editar e desativar contas manuais
  do próprio usuário, com tipo, apelido, saldo de referência, data de referência
  e observações; lançamentos manuais e transferências DEVEM poder ser associados
  a essas contas. Conta manual desativada NÃO DEVE receber novos lançamentos nem
  transferências e DEVE preservar o histórico e os relatórios existentes. O tipo
  da conta manual NÃO DEVE ser alterado após a criação.
- **FR-063**: O sistema DEVE permitir criar, editar, visualizar e excluir
  receitas, despesas, transferências, estornos, reembolsos e ajustes manuais,
  identificando a origem manual.
- **FR-064**: Uma transferência manual DEVE criar duas transações vinculadas,
  uma de saída na conta de origem e uma de entrada na conta de destino, ambas do
  próprio usuário, com o mesmo valor e data; o par DEVE ser registrado como
  movimento interno confirmado e NÃO DEVE alterar receitas ou despesas.
- **FR-065**: O sistema DEVE oferecer categorias predefinidas e permitir
  categorias personalizadas por usuário.
- **FR-066**: O sistema DEVE permitir categorizar transações importadas e
  preservar a escolha após sincronizações posteriores.
- **FR-067**: A exclusão de uma categoria NÃO DEVE excluir transações; os
  lançamentos vinculados DEVEM ficar sem categoria ou ser reatribuídos conforme
  escolha explícita.
- **FR-068**: O sistema DEVE permitir filtrar lançamentos por período, conta,
  cartão, categoria, tipo, origem e faixa de valor.
- **FR-069**: O sistema NÃO DEVE exigir cadastro manual para uma transação
  corretamente importada.
- **FR-070**: Lançamentos manuais DEVEM permanecer disponíveis como complemento
  e contingência quando a integração falhar ou estiver indisponível.
- **FR-071**: Todos os totais de período e relatórios DEVEM considerar
  transações `POSTED`/`RECONCILED` e itens de fatura sem transação, aplicar as
  regras financeiras definidas nesta especificação e corresponder exatamente às
  entradas de detalhamento retornadas pelo mesmo filtro, observado o FR-077. O
  total da fatura é calculado pelo FR-079 e inclui itens vinculados uma única
  vez; os totais de período e o drill-down usam a projeção `ReportEntry`.

#### Cartões de crédito e faturas

- **FR-072**: O sistema DEVE permitir configurar, por cartão de crédito, dia de
  fechamento, dia de vencimento e limite quando disponível.
- **FR-073**: O sistema DEVE organizar compras e demais itens de cartão em
  faturas por ciclo, com data de fechamento, vencimento, status e valor total.
- **FR-074**: Cada fatura DEVE ter estados explícitos, no mínimo: aberta,
  fechada, parcialmente paga, paga e vencida. Ajustes manuais DEVEM ser
  sinalizados por indicador próprio e NÃO DEVEM substituir o status de pagamento.
- **FR-075**: Compras parceladas DEVEM ser representadas por parcelas; o usuário
  DEVE poder informar o número de parcelas e o valor de cada uma quando a origem
  não fornecer o detalhamento; cada parcela DEVE ser contabilizada como despesa
  no ciclo correspondente, sem contar o valor total da compra antecipadamente.
- **FR-076**: O sistema DEVE permitir registrar pagamentos totais ou parciais de
  fatura, vinculados a uma conta do próprio usuário, observado o FR-054; o
  pagamento DEVE reduzir o saldo devedor da fatura.
- **FR-077**: Juros, encargos, tarifas, créditos, estornos e ajustes DEVEM ser
  registrados como itens explícitos da fatura, com `reporting_effect` explícito:
  `EXPENSE_INCREASE`, `EXPENSE_DECREASE` ou `NONE`. O valor do item é sempre uma
  magnitude positiva; o efeito determina a direção. Itens de fatura vinculados a
  transação são espelhos: compõem o total da fatura uma única vez e NÃO DEVEM ser
  somados novamente em `expenses`, `income`, `periodResult`, categorias ou
  drill-down. Item sem transação com efeito
  `EXPENSE_INCREASE` aumenta `expenses` uma única vez. Item sem transação com
  efeito `EXPENSE_DECREASE` tem valor efetivo de redução igual ao menor entre o
  valor do item, o total da fatura antes deste item e as despesas do mesmo cartão
  no período do relatório
  em que o item é contabilizado; esse valor reduz `expenses` e o total da fatura
  uma única vez, e o restante DEVE permanecer como crédito do cartão para faturas
  futuras, sem gerar despesa negativa. O crédito acumulado de
  faturas anteriores DEVE ser aplicado automaticamente à primeira fatura não paga
  (`OPEN`, `CLOSED`, `PARTIALLY_PAID` ou `OVERDUE`) em ordem de vencimento; se
  exceder o total dessa fatura, o remanescente DEVE permanecer para a fatura
  seguinte.
  Faturas já pagas NÃO DEVEM ser reabertas. O crédito NÃO DEVE compor `income`,
  `currentBalance` ou `expenses` e DEVE ser exibido separadamente na fatura e no
  extrato do cartão. `PURCHASE` e `INSTALLMENT` DEVEM estar vinculados a uma
  transação; sem vínculo, DEVEM ser rejeitados como item de fatura. A conversão
  em transação manual é uma ação explícita e separada e NUNCA DEVE criar item
  órfão. A compra original NÃO DEVE ser alterada.
- **FR-078**: O sistema DEVE permitir adicionar, editar e remover itens manuais
  de fatura, preservando a origem do ajuste, o motivo, o histórico e os valores
  anteriores em registro auditável.
- **FR-079**: O valor da fatura e o saldo devedor DEVEM ser calculados a partir
  de compras, parcelas, juros, encargos, tarifas, créditos, estornos e
  pagamentos, com status consistente, observado o FR-077.
- **FR-080**: A baixa de pagamento DEVE reduzir o saldo devedor sem criar nova
  despesa nem duplicar compras já contabilizadas, observados o FR-054 e o
  FR-076.
- **FR-081**: Mudanças no ciclo de fechamento ou vencimento NÃO DEVEM reescrever
  faturas já fechadas ou pagas; DEVEM aplicar-se aos ciclos futuros, salvo
  ajuste explícito com auditoria.
- **FR-082**: Transações que cheguem após o fechamento DEVEM ser alocadas ao
  ciclo seguinte ou a um ajuste explícito, sem alterar silenciosamente uma
  fatura paga.
- **FR-083**: O sistema DEVE permitir visualizar faturas por cartão e por
  período, com detalhamento dos itens que compõem cada total.
- **FR-084**: Faturas, itens e pagamentos DEVEM ser isolados por usuário e
  cartão, sem acesso cruzado.
- **FR-085**: O sistema NÃO DEVE calcular juros ou encargos automaticamente;
  valores não fornecidos pela origem DEVEM ser informados pelo usuário ou
  exibidos como ausentes.

#### Dashboard e relatórios

- **FR-086**: O dashboard DEVE mostrar receitas, despesas, resultado do período
  e a distribuição de despesas por categoria, por semana, mês e ano, além do
  saldo atual consolidado apenas de contas bancárias e manuais (`BANK`, `CASH`,
  `WALLET`) com status `ACTIVE`; contas `INACTIVE`, `CLOSED` ou `UNAVAILABLE`
  NÃO DEVEM compor o saldo atual, mas permanecem no histórico e nos relatórios
  do período em que tiveram movimento. O saldo DEVE ter rótulo e data de
  referência distintos daqueles usados para o resultado do período. “Gastos” é a
  visão de despesas por categoria e NÃO DEVE ser um total paralelo. Saldos
  devedores de cartões DEVEM ser exibidos separadamente e NÃO DEVEM ser somados a
  esse saldo.
- **FR-087**: O dashboard DEVE consolidar todas as contas e cartões do usuário e
  permitir filtrar por uma ou várias contas ou cartões, observado o FR-086.
- **FR-088**: O sistema DEVE mostrar evolução temporal e permitir drill-down dos
  totais, incluindo transações e itens de fatura sem transação, observados o
  FR-077 e o FR-086.
- **FR-089**: A soma da lista de detalhamento DEVE corresponder ao total do
  gráfico ou indicador que a originou.
- **FR-090**: O sistema DEVE permitir filtros por intervalo de datas, categoria,
  conta, cartão, tipo de movimento, origem e faixa de valor.
- **FR-091**: O sistema DEVE usar, como configuração inicial, BRL, fuso horário
  America/Sao_Paulo e semana iniciada na segunda-feira. Os períodos DEVEM ser
  calendário nesse fuso; agregações usam intervalos `[início, fim)` e filtros
  `from`/`to` incluem o dia final. A varredura por requisição DEVE ser limitada;
  intervalo amplo demais DEVE retornar 422 com orientação para estreitar o
  filtro, sem truncar dados silenciosamente.
- **FR-092**: Os relatórios DEVEM observar o FR-054, o FR-055 e o FR-064 para
  não contabilizar duas vezes compras no cartão, pagamento de fatura ou
  transferências entre contas próprias.
- **FR-093**: Saldos ausentes, desatualizados ou estimados DEVEM ser sinalizados
  com clareza, incluindo a data de referência disponível.
- **FR-094**: Os totais dos gráficos DEVEM observar o FR-071 e o FR-089, sem
  valores inventados ou ajustes ocultos.
- **FR-095**: O dashboard DEVE exibir a origem dos dados, o estado da última
  sincronização relevante para o período consultado e os movimentos internos
  pendentes de confirmação. A última sincronização relevante DEVE ser a mais
  recente bem-sucedida entre as conexões que possuem contas ou cartões incluídos
  no filtro do relatório; se nenhuma conexão se aplicar, o valor DEVE ser nulo e
  a interface DEVE indicar “sem sincronização no período”.

#### Segurança, privacidade e ciclo de vida

- **FR-096**: Senhas DEVEM ser armazenadas apenas em forma não reversível,
  adequada para verificação segura, nunca em formato recuperável.
- **FR-097**: Client Secret, saldos, valores, descrições e demais campos
  financeiros sensíveis DEVEM ser protegidos com criptografia autenticada, de
  modo que seu conteúdo não seja legível sem as chaves externas.
- **FR-098**: Chaves criptográficas DEVEM ficar fora do banco de dados e dos
  artefatos de implantação, com versionamento, rotação, backup, recuperação e
  implicações de perda documentados.
- **FR-099**: Uma cópia isolada do banco de dados, sem as chaves externas, NÃO
  DEVE revelar os campos protegidos; alterações indevidas nos dados cifrados
  DEVEM ser detectáveis.
- **FR-100**: O sistema DEVE documentar quais campos permanecem pesquisáveis e
  quais metadados ficam visíveis no banco de dados, assim como os limites da
  promessa de proteção; NÃO DEVE alegar conhecimento zero.
- **FR-101**: Segredos, credenciais temporárias, tokens e dados financeiros
  desnecessários NÃO DEVEM aparecer em logs, mensagens de erro, exportações,
  armazenamento do navegador ou artefatos de suporte.
- **FR-102**: O sistema DEVE validar entradas e proteger sessões e operações
  contra falsificação de origem, acesso não autorizado e tentativas abusivas.
- **FR-103**: O sistema DEVE permitir que o usuário exporte seus dados ativos em
  formato portátil e documentado, contendo apenas os próprios registros e
  excluindo segredos e credenciais.
- **FR-104**: O sistema DEVE permitir desconectar uma integração, interrompendo
  novas sincronizações, removendo credenciais e preservando os dados importados
  por padrão, com opção explícita de exclusão.
- **FR-105**: O sistema DEVE permitir excluir a conta após confirmação,
  interrompendo sincronizações, removendo credenciais e dados ativos conforme a
  política documentada, sem afetar outros usuários.
- **FR-106**: A exclusão de conta ou desconexão durante processamento DEVE
  encerrar a operação com segurança, sem corromper ou expor dados.
- **FR-107**: A política de retenção de backups e de dados excluídos DEVE ser
  documentada e comunicada ao usuário antes da exclusão.
- **FR-108**: Eventos relevantes de autenticação, autorização, alteração de
  credenciais, exportação, desconexão e exclusão DEVEM ser registrados de forma
  sanitizada para auditoria e diagnóstico, sem segredos ou conteúdo financeiro
  desnecessário.

#### Experiência, responsividade e acessibilidade

- **FR-109**: A aplicação DEVE ser responsiva, utilizável em desktop e
  dispositivos móveis e atender aos critérios aplicáveis da WCAG 2.1 nível AA.
- **FR-110**: Formulários, dashboards, filtros e listas DEVEM funcionar em telas
  estreitas e com leitores de tela.
- **FR-111**: O sistema DEVE exibir estados claros de carregamento, vazio, erro,
  falha de sincronização, sincronização pendente e autorização expirada.
- **FR-112**: A navegação DEVE ser simples e a linguagem direta em português
  brasileiro.
- **FR-113**: Gráficos e indicadores DEVEM oferecer alternativa textual, rótulos
  associados e não depender exclusivamente de cor.
- **FR-114**: O sistema NÃO DEVE exibir sucesso de integração sem validar
  credenciais e ao menos uma conexão, nem apresentar dados simulados como reais.
- **FR-115**: A interface DEVE identificar dados importados, dados manuais,
  estimativas, dados ausentes, data de referência e última sincronização
  bem-sucedida, conforme o FR-030.

#### Investimentos - fase secundária

- **FR-116**: O sistema DEVE permitir cadastro manual de ativo, ticker ou
  código, quantidade, preço unitário de referência, data de referência,
  corretora e observações.
- **FR-117**: O total de uma posição manual DEVE ser `quantidade × preço
  unitário de referência`; quando o usuário informar apenas o valor total, o
  preço unitário DEVE ser derivado por `total ÷ quantidade`, com arredondamento
  explícito para 8 casas decimais. O total DEVE usar a data e o valor de
  referência informados e ser apresentado como dado do usuário, não como cotação
  de mercado.
- **FR-118**: Importação automática de investimentos só DEVE ser declarada
  funcional após validação real de que o conector e os dados necessários estão
  acessíveis pela modalidade pessoal Meu Pluggy.
- **FR-119**: Cobertura pública de conector direto ou teste em Sandbox NÃO
  constitui prova de disponibilidade no Meu Pluggy.
- **FR-120**: O módulo de investimentos NÃO DEVE oferecer recomendação de
  compra, venda, alocação ou consultoria de investimentos.
- **FR-121**: O módulo de investimentos NÃO DEVE atrasar a integração bancária,
  o dashboard ou os critérios de conclusão do MVP.
- **FR-122**: XP Investimentos e Santander Corretora são instituições de
  interesse da fase secundária, sem promessa de cobertura automática.

#### Patrimônio e metas - fase posterior ao MVP

- **FR-123**: O sistema DEVE prever uma visão consolidada de patrimônio bruto,
  total de passivos e patrimônio líquido calculado como ativos menos passivos.
- **FR-124**: A consolidação patrimonial DEVE incorporar saldos de contas e
  cartões, investimentos quando disponíveis e ativos ou passivos manuais, sem
  duplicar o mesmo valor em fontes diferentes.
- **FR-125**: O sistema DEVE permitir cadastrar ativos e passivos manuais com
  tipo, descrição, valor de referência, data da posição e observações.
- **FR-126**: O sistema DEVE registrar snapshots patrimoniais datados para
  mostrar evolução semanal, mensal, anual ou desde o início do acompanhamento.
- **FR-127**: O sistema DEVE explicar que dados antigos só podem ser
  reconstruídos quando houver histórico suficiente e NÃO DEVE inferir saldos
  passados a partir de dados atuais incompletos.
- **FR-128**: O sistema DEVE distinguir patrimônio de fluxo de caixa e gasto,
  tratar saldos de cartão como passivos e evitar dupla contagem de compras,
  faturas, transferências, dinheiro em contas e posições de investimento.
- **FR-129**: A visão patrimonial DEVE mostrar a data de referência, sinalizar
  estimativas, saldos ausentes ou desatualizados e não tratar valor de
  referência manual como cotação de mercado.
- **FR-130**: O sistema DEVE permitir criar metas com nome, descrição opcional,
  tipo (`EMERGENCY_FUND`, `TRAVEL`, `PLANNED_PURCHASE`, `DEBT_REDUCTION`,
  `NET_WORTH`), valor-alvo, data-alvo opcional, estado (`ACTIVE`, `ACHIEVED`,
  `PAUSED`, `ARCHIVED`, `OVERDUE`, `CHANGED`), valor atual calculado, data de
  criação e observações.
- **FR-131**: Metas DEVEM cobrir, no mínimo, reserva de emergência, viagem,
  compra planejada, redução de dívida e objetivo de patrimônio líquido, sem
  oferecer aconselhamento financeiro. Meta de redução de dívida DEVE registrar a
  dívida inicial na criação e calcular progresso como `(dívida inicial − dívida
  atual) ÷ dívida inicial`, limitado entre 0% e 100%.
- **FR-132**: O sistema DEVE permitir definir o progresso da meta por valor
  alocado manualmente, conjunto de contas ou ativos selecionados ou patrimônio
  líquido, conforme aplicável. Cada alocação DEVE ter modo `FIXED_AMOUNT` ou
  `PERCENTAGE`; exatamente um valor correspondente DEVE ser informado e
  percentuais DEVEM somar no máximo 100%. Cada alocação `FIXED_AMOUNT` é exata;
  o excesso sobre a meta é permitido e o progresso é limitado a 100%. Para
  `PERCENTAGE`, o valor de cada alocação DEVE ser
  `floor(valor-alvo × percentual ÷ 100, 2)` e o resíduo de arredondamento DEVE
  ser atribuído à última alocação percentual. Percentuais podem somar menos de
  100%; o saldo não alocado DEVE ficar visível e NÃO DEVE ser redistribuído.
  Alocações mistas somam valores fixos e percentuais, e o progresso permanece
  limitado a 100%.
- **FR-133**: O mesmo saldo NÃO DEVE ser contado integralmente em várias metas
  sem alocação explícita. A regra que determina o valor acumulado DEVE ficar
  visível, incluindo modo de alocação, percentual, arredondamento e resíduo. A
  soma das alocações explícitas de uma mesma fonte entre metas NÃO DEVE exceder
  o saldo da fonte; o sistema DEVE rejeitar a configuração ou exigir ajuste
  explícito.
- **FR-134**: O progresso DEVE ser recalculado quando os dados de origem forem
  atualizados, com histórico datado e exibição de percentual, valor restante e
  prazo.
- **FR-135**: Metas DEVEM ter critérios explícitos para os estados:
  `ACHIEVED` quando o progresso for >= 100%; `PAUSED` e `ARCHIVED` por ação
  explícita do usuário, com progresso congelado e histórico preservado;
  `OVERDUE` quando a data-alvo for anterior a hoje, o progresso for < 100% e o
  estado não for `PAUSED`/`ARCHIVED`; `CHANGED` quando valor-alvo, prazo, tipo
  ou regra de progresso forem alterados, retornando a `ACTIVE` após o usuário
  visualizar a alteração e mantendo o histórico.
- **FR-136**: Metas NÃO DEVEM mover dinheiro, criar ordens ou executar ações
  bancárias automaticamente.
- **FR-137**: Patrimônio e metas pertencem a uma fase posterior ao MVP e NÃO
  DEVEM ser pré-requisito para a conclusão da primeira versão.

#### Moeda

- **FR-138**: Registros em moeda diferente de BRL DEVEM ser exibidos com a moeda
  e sinalizados; NÃO DEVEM entrar em totais consolidados nem ser convertidos
  automaticamente sem regra explícita de conversão.

#### Operação assistida

- **FR-139**: O sistema DEVE documentar e fornecer um mecanismo operacional
  executável de recuperação assistida de senha, com verificação de identidade
  pelo operador, registro em auditoria, invalidação das sessões existentes e
  isolamento entre usuários.

### Key Entities

- **Usuário**: pessoa autenticada, identificada por nome de usuário único,
  credencial de acesso e preferências; é proprietária exclusiva de seus recursos
  financeiros.
- **Integração pessoal**: configuração de um usuário com o provedor, contendo
  Client ID, Client Secret protegido, estado, última tentativa e última
  sincronização bem-sucedida.
- **Conexão externa (item)**: vínculo autorizado de uma instituição, com
  identificador externo, integração proprietária, instituição, estado e data de
  expiração do consentimento quando disponível.
- **Conta importada**: conta bancária pertencente a um item e a um usuário, com
  tipo, instituição, apelido, saldo disponível, moeda e data de referência.
- **Cartão de crédito**: conta de crédito ou cartão pertencente a um item e a um
  usuário, com instituição, apelido, limite, dia de fechamento, dia de
  vencimento, saldo, crédito acumulado e histórico de faturas.
- **Fatura de cartão**: ciclo de um cartão com data de fechamento, vencimento,
  status, total, crédito aplicado, pagamentos, saldo devedor e composição.
- **Item de fatura**: compra, parcela, juros, encargo, tarifa, crédito ou
  estorno vinculado a uma fatura, com origem importada ou manual, valor, data,
  descrição, vínculo com a transação quando aplicável e histórico de ajustes.
- **Pagamento de fatura**: pagamento total ou parcial vinculado a uma fatura e a
  uma conta do próprio usuário, com data, valor, status e histórico.
- **Conta manual**: conta não vinculada a item externo, pertencente a um usuário,
  com tipo, apelido, saldo de referência, data de referência e observações; o
  tipo é imutável após a criação e a conta pode receber lançamentos manuais e
  transferências.
- **Transação**: movimento financeiro de uma conta ou cartão, com origem
  importada ou manual, identificador externo quando aplicável, descrição, data,
  valor, tipo, estado, categoria, correções do usuário, valores originais da
  origem para auditoria e estado de reconciliação.
- **Entrada de relatório**: item de leitura que compõe um total, com origem
  `TRANSACTION` ou `INVOICE_ITEM`, `origin` normalizada (`IMPORTED` ou `MANUAL`;
  itens de Bill do provedor são normalizados para `IMPORTED`), valor assinado,
  data, categoria, conta, cartão, fatura e vínculo com a transação quando
  aplicável.
- **Correspondência interna**: sugestão de vínculo entre dois movimentos do mesmo
  usuário, como transferência entre contas ou pagamento de fatura, com critérios
  de valor, data e contas, estado pendente, confirmada ou rejeitada e histórico
  da decisão.
- **Categoria**: classificação financeira predefinida ou personalizada,
  pertencente a um usuário.
- **Registro de sincronização**: tentativa de leitura de uma integração ou item,
  com início, fim, resultado, estado, totais processados e erro sanitizado.
- **Assinatura de webhook**: configuração por integração com segredo, token ou
  URL, estado e última validação.
- **Evento de webhook**: `eventId` idempotente, assinatura, item e usuário
  associados, data de recebimento, status de processamento, tentativas e erro
  sanitizado.
- **Posição de investimento**: registro manual de ativo, ticker ou código,
  quantidade, preço unitário de referência, data, corretora e observações,
  pertencente a um usuário.
- **Ativo ou passivo manual**: item patrimonial informado pelo usuário, com tipo,
  descrição, valor de referência, data da posição e observações.
- **Snapshot patrimonial**: fotografia datada de patrimônio bruto, passivos,
  patrimônio líquido e composição, com indicação de lacunas e estimativas.
- **Meta financeira**: objetivo com valor-alvo, prazo opcional, estado, regra de
  progresso, valor atual calculado e histórico datado de progresso.

### Classificação de Dados

| Classe | Exemplos | Tratamento |
|---|---|---|
| Metadados operacionais | IDs locais, timestamps, status, tipo de conta, moeda, datas, `version` | Texto claro e pesquisável; sem conteúdo financeiro. |
| Dados pessoais | Nome de usuário, apelido de conta, observações | Minimizados; observações cifradas; exportação apenas do titular. |
| Dados financeiros sensíveis | Saldos, valores, descrições, faturas, itens, metas, posições e patrimônio | AES-256-GCM com chave externa, AAD e versão de chave; nunca em logs. |
| Identificadores externos | itemId, IDs de transação, `billId`, `provider_item_id` | Cifrados para leitura e HMAC-SHA-256 para busca/dedup. |
| Segredos | Hash de senha, Client Secret, API Key, chaves e tokens de sessão | Hash Argon2id ou AEAD; chaves fora do banco; nunca exportados ou logados. |

A classificação DEVE orientar schema, logs, exportação, backup e testes. Os
campos `[enc]` e `[hmac]` do `data-model.md` são a referência de implementação;
nenhum campo financeiro sensível DEVE ser usado como filtro SQL em texto claro.

### External Dependencies & Constraints

- A integração usa a modalidade pessoal Meu Pluggy; cada usuário precisa ter a
  própria conta, a própria aplicação no Dashboard e as próprias conexões.
- O provedor não oferece descoberta automática de itens nessa modalidade; o
  cadastro explícito de cada itemId é parte obrigatória da configuração da
  integração.
- A atualização das conexões de origem ocorre em janela diária; itens proxy não
  permitem atualização manual e o sistema não pode prometer tempo real.
- A autorização da aplicação pessoal tem prazo documentado (atualmente um ano) e
  expira periodicamente; os consentimentos de cada instituição são independentes.
- O limite de conexões por conta pessoal é definido pelo provedor e pode mudar;
  o sistema deve refletir o limite vigente e alertar o usuário.
- O itemId pode mudar após uma nova autorização; a reconexão deve ser tratada
  como operação de reconciliação, não como cadastro de um novo histórico.
- A credencial temporária de acesso tem validade documentada de duas horas e
  deve ser renovada no servidor; sua duração deve seguir a documentação vigente
  no momento da implementação.
- A documentação oficial e as operações recomendadas pelo provedor devem ser
  verificadas na implementação; operações marcadas como obsoletas não devem ser
  usadas quando houver alternativa recomendada.
- A cobertura de instituições, tipos de conta, saldos e transações varia por
  conector e pode ser incompleta; o sistema deve informar ausências.
- Notificações de atualização podem não estar disponíveis ou não conter todos os
  dados; a sincronização programada é a base de recuperação e reconciliação.
- O provedor pode não fornecer dados completos de fatura, ciclo, parcelamento,
  juros ou encargos; o sistema deve complementar com informação do usuário e não
  inventar dados.
- A classificação de compras e pagamentos de fatura depende dos dados
  disponíveis e da confirmação do usuário; a ausência de dados não pode gerar
  fatura fictícia.
- A disponibilidade de conectores de investimento no Meu Pluggy para XP
  Investimentos e Santander Corretora não é presumida e depende de validação
  real.
- A indisponibilidade, mudança de contrato ou alteração de limites do provedor
  são riscos externos; nenhuma resposta vazia ou falha pode ser tratada como
  exclusão de dados.

### Scope and Phasing

**MVP obrigatório**:

- cadastro, login, logout, alteração de senha e isolamento entre usuários;
- configuração pessoal do Meu Pluggy, validação de credenciais e múltiplos
  itemIds;
- importação de contas, cartões e transações, com deduplicação e reconciliação;
- gestão completa de faturas de cartão, incluindo ciclos, parcelamento,
  pagamentos parciais, juros, encargos e edição manual;
- sincronização programada, estados, recuperação de autorização expirada e
  reconexão;
- contas manuais, lançamentos manuais complementares, correções de transações
  importadas, categorias e filtros;
- classificação assistida de transferências internas e pagamentos de fatura, com
  confirmação do usuário;
- dashboard e relatórios por semana, mês e ano, com detalhamento dos totais;
- exportação, desconexão, exclusão de conta, recuperação assistida de senha,
  segurança, acessibilidade e responsividade;
- operação self-hosted com dados locais disponíveis quando o provedor falhar.

**Fase secundária**:

- registro manual de posições de ações e fundos imobiliários;
- importação automática de investimentos somente após validação real do conector
  e da cobertura no fluxo Meu Pluggy.

**Fase posterior ao MVP**:

- acompanhamento de patrimônio bruto, passivos, patrimônio líquido e snapshots;
- metas financeiras com regras de progresso e histórico.

**Fora de escopo do MVP**:

- uso comercial, oferta como serviço a terceiros ou compartilhamento de dados
  entre familiares;
- sincronização em tempo real ou promessa de atualização imediata da instituição;
- execução de pagamentos, transferências bancárias, ordens de investimento e
  aconselhamento financeiro;
- importação automática de investimentos antes de validação real;
- aplicativos nativos para celular;
- conversão automática entre moedas ou suporte completo a moedas diferentes de
  BRL;
- recuperação de senha por e-mail ou autoatendimento sem operador;
- relatórios fiscais, contábeis ou de imposto de renda;
- administração de dados financeiros de um usuário por outro.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um novo usuário consegue concluir cadastro e primeiro login em
  menos de 2 minutos; 100% dos participantes de um teste de aceitação com pelo
  menos 5 usuários devem concluir o fluxo sem assistência, conforme o protocolo
  definido em `quickstart.md`.
- **SC-002**: Em testes com dois usuários e integrações independentes, nenhuma
  operação cruzada de leitura, escrita, exclusão, exportação ou relatório
  retorna dados de outro usuário; 100% das tentativas devem ser negadas.
- **SC-003**: Após o salvamento, Client Secret e credencial temporária do
  provedor aparecem zero vezes na interface, no armazenamento do navegador, nos
  logs e nas exportações.
- **SC-004**: Uma integração válida importa 100% das contas, cartões e
  transações disponibilizadas pelo provedor, todos associados ao usuário e à
  conexão corretos, sem registros inventados.
- **SC-005**: Executar a mesma sincronização três vezes produz zero transações
  duplicadas e mantém estáveis a contagem e os totais.
- **SC-006**: O usuário encontra a última tentativa, a última sincronização
  bem-sucedida e o estado da conexão em uma única área, sem qualquer indicação de
  atualização em tempo real; ao filtrar o dashboard, a última sincronização
  exibida corresponde às conexões incluídas no filtro ou indica “sem
  sincronização no período”.
- **SC-007**: Em 100% dos testes de credencial inválida, autorização expirada e
  mudança de itemId, o sistema indica ação necessária, preserva os dados
  anteriores e, após a reconexão, não perde nem duplica registros.
- **SC-008**: Após respostas vazias, erros, timeouts ou indisponibilidade do
  provedor, a contagem e o conteúdo do histórico existente permanecem inalterados
  em 100% dos casos testados.
- **SC-009**: Em 100% dos casos de teste de compras no cartão, pagamento de
  fatura, transferências, estornos e reembolsos, os totais obedecem às regras
  financeiras e a soma do detalhamento corresponde ao total exibido; cada
  entrada do detalhamento identifica origem `IMPORTED`/`MANUAL`.
- **SC-010**: Para um cenário de referência de 5 usuários, 5 conexões por usuário
  e 10.000 transações por usuário, o dashboard inicial é exibido em até 3
  segundos e os filtros retornam resultados em até 2 segundos.
- **SC-011**: Um usuário consegue criar um lançamento manual completo em menos
  de 90 segundos, e a categoria aplicada a uma transação importada permanece
  após três sincronizações consecutivas.
- **SC-012**: A exportação contém 100% dos registros ativos do solicitante, zero
  registros de outro usuário e zero segredos; a exclusão bloqueia o acesso
  imediatamente e remove os dados ativos dentro de 24 horas, respeitando a
  política de retenção de backups.
- **SC-013**: 100% dos fluxos críticos — cadastro/login, configuração da
  integração, dashboard e drill-down, lançamento manual, fatura, reconciliação e
  exportação/exclusão — são utilizáveis em tela de 360 pixels de largura, com
  zoom de 200%, apenas com teclado e com leitor de tela, sem bloqueios críticos.
- **SC-014**: Com o provedor indisponível, 100% dos dados já importados,
  lançamentos manuais e relatórios continuam acessíveis e nenhum dado é perdido.
- **SC-015**: Na fase secundária, posições manuais de investimento exibem
  valores e datas de referência corretos, e nenhuma importação automática é
  anunciada sem validação real; a conclusão do MVP bancário não depende desse
  módulo.
- **SC-016**: Na fase posterior, patrimônio líquido corresponde a ativos menos
  passivos em 100% dos casos testados, sem dupla contagem, e histórico
  insuficiente não infere saldos passados. Na fase de metas, a regra de progresso
  fica visível, incluindo modo, percentual, resíduo e saldo não alocado, o mesmo
  saldo não é contado integralmente em metas diferentes sem alocação explícita, e
  metas não movimentam dinheiro nem criam ordens.
- **SC-017**: Um usuário consegue criar uma conta manual e registrar nela uma
  receita, despesa ou transferência; o saldo e os relatórios refletem o saldo de
  referência ajustado pelos lançamentos posteriores, e a conta permanece isolada
  dos demais usuários. O tipo não pode ser alterado após a criação; após a
  desativação, novos lançamentos são bloqueados e o histórico permanece
  disponível.
- **SC-018**: Em 100% dos testes, uma correção manual em transação importada
  permanece após sincronizações posteriores, os valores originais da origem
  continuam auditáveis e os totais usam a correção do usuário.
- **SC-019**: Em 100% dos testes de transferências e pagamentos de fatura
  sugeridos, o sistema só os exclui de receitas e despesas após confirmação do
  usuário, registra a decisão e não duplica a despesa original.
- **SC-020**: Em 100% das telas de dashboard, o resultado do período e o saldo
  atual consolidado são exibidos com rótulos e datas de referência distintos, sem
  que um seja apresentado como se fosse o outro e sem que qualquer um deles seja
  rotulado como fluxo de caixa.
- **SC-021**: Um usuário configura ciclo de fechamento, vencimento e limite de
  um cartão e visualiza faturas com status e totais corretos em 100% dos casos
  de teste.
- **SC-022**: Em 100% dos testes de compras parceladas, cada parcela é
  contabilizada no ciclo correto, sem contar o valor total antecipadamente nem
  duplicar parcelas.
- **SC-023**: Em 100% dos testes de pagamentos totais ou parciais, o pagamento
  reduz o saldo devedor e não é contado como despesa; juros e encargos são
  despesas separadas.
- **SC-024**: Em 100% dos testes de edição manual de faturas, o total e o saldo
  devedor são recalculados, os valores anteriores ficam auditáveis e nenhum
  outro usuário acessa a fatura.
- **SC-025**: Em 100% dos testes, transações `PENDING` não aparecem em `income`,
  `expenses`, `periodResult`, categorias, drill-down ou totais de fatura; são
  exibidas com `pendingCount`, `pendingIncome` e `pendingExpenses` separados, sem
  total líquido. Após a transição para `POSTED`, cada transação entra nesses
  totais exatamente uma vez, sem duplicação.
- **SC-026**: Em 100% dos testes de itens de fatura sem transação,
  juros/encargos/tarifas aumentam `expenses` uma única vez e créditos/estornos
  reduzem `expenses` observado o limite do FR-077 — o menor entre o valor do
  item, o total da fatura antes do item e as despesas do mesmo cartão no período
  do relatório —, sem despesa negativa e sem duplicar itens vinculados a
  transações; o excedente é aplicado como crédito à primeira fatura não paga e,
  se necessário, à seguinte.

## Assumptions

- O cenário de referência é um grupo familiar de aproximadamente 5 usuários, com
  até 5 conexões por usuário, conforme o limite vigente documentado e
  configurado, e cerca de 10.000 transações por usuário; o produto pode crescer
  sem alterar os princípios de isolamento.
- Cada usuário possui conta própria no Meu Pluggy, aplicação pessoal no
  Dashboard e conexões autorizadas; o responsável pelo projeto confirmou a
  viabilidade do cenário familiar não comercial.
- Os usuários acessam o sistema por navegador web moderno em desktop e celular;
  não há aplicativo nativo no MVP.
- A sincronização programada diária é suficiente para o MVP; notificações de
  atualização são opcionais e a consulta manual não força atualização bancária.
- O sistema é operado em servidor particular, sem finalidade comercial, e não é
  oferecido como serviço a terceiros.
- Os dados financeiros são mantidos em BRL, com fuso horário America/Sao_Paulo e
  semana iniciada na segunda-feira como padrão inicial.
- O MVP não faz conversão automática de moedas; registros em moeda diferente de
  BRL devem ser sinalizados e não entram em totais consolidados sem regra
  definida.
- Não há compartilhamento de contas, transações, categorias ou relatórios entre
  familiares; cada usuário vê somente os próprios dados.
- O cadastro é aberto na instância self-hosted privada; não há fluxo de convite
  ou aprovação no MVP, e o operador controla o acesso à rede.
- A recuperação de senha por e-mail ou autoatendimento não faz parte do MVP; um
  usuário que perde a senha depende de procedimento operacional seguro no
  servidor.
- A exclusão de conta remove os dados ativos e programa a expiração dos backups
  em até 30 dias, conforme política documentada; o usuário é informado antes de
  confirmar.
- O formato exato da exportação será definido no planejamento, desde que seja
  portátil, documentado, completo para os dados ativos e livre de segredos.
- Cada cartão de crédito tem ciclo de fechamento e vencimento configurável;
  quando o provedor não fornecer esses dados, o usuário deve completá-los.
- Juros, encargos, tarifas e pagamentos parciais podem ser importados quando
  disponíveis ou informados manualmente; o sistema não calcula juros
  automaticamente.
- Parcelas futuras são projetadas e contabilizadas apenas no ciclo em que vencem;
  a fatura é uma visão de acompanhamento e não movimenta dinheiro.
- Valores de referência de investimentos e de ativos manuais são informados pelo
  usuário e não representam cotação de mercado em tempo real.
- Patrimônio, metas e importação automática de investimentos não fazem parte dos
  critérios de conclusão do MVP bancário.
- A documentação e os limites do Meu Pluggy podem mudar; a implementação deverá
  verificar a versão vigente e registrar a data da verificação.
- O sistema não fornece aconselhamento financeiro, recomendação de investimento
  ou qualquer movimentação automática de dinheiro.
