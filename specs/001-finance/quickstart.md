# Quickstart: MVP de Finanças Pessoais Familiares

**Data**: 2026-10-22

**Spec**: [spec.md](./spec.md)

**Plano**: [plan.md](./plan.md)

**Contratos**: [contracts/openapi.yaml](./contracts/openapi.yaml)

Este guia valida o MVP de ponta a ponta. Ele não substitui a especificação nem
o plano; serve para provar que o sistema sobe, isola usuários, importa dados,
calcula relatórios, gerencia faturas e recupera dados.

## 1. Pré-requisitos

- Docker Engine 29.1 e Docker Compose 2.40 (ou versões compatíveis fixadas).
- Git.
- Java 25.0.4.1+1 e Maven 3.9.16 para execução local do backend.
- Node.js 24.22.0 e npm 11 para execução local do frontend.
- Duas contas Meu Pluggy com aplicações pessoais e itemIds próprios para a
  validação real.
- Acesso HTTPS público apenas se webhooks forem habilitados; a sincronização
  programada funciona sem webhooks.

## 2. Preparar o ambiente

```bash
cp .env.example .env
mkdir -p secrets
chmod 700 secrets
```

Preencha `.env` com valores locais. Nunca versionar `.env`, dumps ou o key ring.
O `.env.example` contém apenas placeholders.

Gere duas chaves de 32 bytes para o key ring (uma de criptografia e uma de
índice) e crie `secrets/crypto-keys.json` no formato documentado em
`deploy/README.md`. Exemplo de geração:

```bash
openssl rand -base64 32
openssl rand -base64 32
```

O arquivo deve ficar fora do Git, com permissão `600`, e ser montado como Docker
secret/arquivo read-only. Sem esse arquivo, os campos cifrados não podem ser
lidos.

## 3. Subir o stack

```bash
docker compose pull
docker compose up -d --build
docker compose ps
```

Serviços esperados:

- `db`: PostgreSQL 18.6, healthy.
- `server`: Spring Boot, healthy em `/actuator/health/readiness`.
- `client`: SPA React, healthy em `/healthz`.
- `proxy`: Caddy, HTTPS na porta 443 (ou HTTP local, conforme configuração).

Verificações:

```bash
docker compose ps
curl -fsS https://SEU_DOMINIO/healthz
```

O health do `server` aparece no `docker compose ps`. Em desenvolvimento, se a
porta 8080 estiver publicada apenas em localhost, use:

```bash
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
```

Em produção, use o domínio configurado e HTTPS. Em desenvolvimento local, o
cookie `Secure` pode ser desativado apenas no perfil local.

## 4. Validar conta e isolamento

1. Abra a aplicação e crie o usuário A.
2. Saia e crie o usuário B.
3. Como A, crie uma conta manual "Carteira", registre um lançamento, confirme
   que o tipo não pode ser alterado, desative-a e confirme que novos lançamentos
   são bloqueados e o histórico permanece.
4. Como B, tente acessar a conta/transação de A por URL, ID ou filtro.
5. Confirme que B recebe 404/403 sem dados de A.
6. Teste logout, expiração de sessão (30 minutos de inatividade e 12 horas
   absolutas, com configuração de teste quando necessário) e alteração de senha.
7. Confirme que a alteração de senha encerra as outras sessões.
8. Teste a política de senha: 12 caracteres é o mínimo, 128 o máximo, uma
   frase-senha longa é aceita, uma senha comum é rejeitada e uma senha contendo
   o nome de usuário é rejeitada.
9. Teste o limite de login: após 5 falhas em 15 minutos, novas tentativas são
   bloqueadas por 15 minutos; uma autenticação bem-sucedida depois do bloqueio
   zera o contador.

Resultado esperado: nenhum dado cruza a fronteira entre A e B, inclusive em
exportação, relatórios e faturas.

## 5. Configurar o Meu Pluggy pessoal

Para cada usuário, repetir a configuração oficial:

1. Entrar no Meu Pluggy e conectar as instituições.
2. No Dashboard Pluggy, usar a mesma conta, habilitar o conector **MeuPluggy**
   e conectar pelo fluxo Demo → Conectar Conta → MeuPluggy.
3. Copiar o Client ID, o Client Secret e o itemId de cada conexão.
4. No sistema, abrir Configurações → Integração e informar Client ID e Client
   Secret.
5. Adicionar cada itemId separadamente.
6. Validar credenciais e ao menos uma conexão.
7. Confirmar que o Client Secret não é exibido novamente e que nenhuma API Key
   aparece na interface.

O sistema não descobre itens automaticamente; o cadastro de itemId é explícito.
Cada banco pode exigir autorização independente.

## 6. Validar importação e sincronização

1. Execute "Consultar dados disponíveis agora".
2. Confirme que a interface explica que isso não força atualização na
   instituição de origem.
3. Verifique contas, cartões e transações importadas.
4. Repita a sincronização três vezes e confirme que não há duplicatas.
5. Confirme que transações importadas e manuais são distintas.
6. Confirme que a última tentativa e a última sincronização bem-sucedida são
   exibidas.
7. Simule (em ambiente de teste) credencial inválida, timeout, `429` e resposta
   vazia; confirme que o histórico não é apagado.
8. Simule uma transação `SOURCE_REMOVED` e confirme `CONFIRM_REMOVAL` e
   `RESTORE`, sem duplicar a transação.
9. Se usar webhooks, confirme a inscrição por integração, a validação do
   segredo, a resposta 202 e o processamento assíncrono.
10. Confirme que nenhum Connect Token é solicitado, chamado ou armazenado no
    perfil pessoal.

Resultado esperado: dados importados corretamente, sem duplicação, com estados
claros e histórico preservado.

## 7. Validar faturas de cartão

1. Configure dia de fechamento, vencimento e limite do cartão.
2. Confirme que compras aparecem no ciclo correto.
3. Cadastre uma compra parcelada e confirme que cada parcela entra no ciclo
   correspondente, sem contar o valor total antecipadamente.
4. Registre um pagamento parcial e confirme a redução do saldo devedor sem nova
   despesa.
5. Registre juros, encargo ou tarifa e confirme que aparece como despesa
   separada.
6. Edite um item manual e confirme o recálculo, o histórico em
   `invoice_item_correction` (via `GET /invoice-items/{id}/corrections`) e os
   valores anteriores.
7. Confirme que o pagamento da fatura não duplica as compras.
8. Registre juros/encargo sem transação com `reportingEffect=EXPENSE_INCREASE`
   e confirme que entram em `expenses` uma única vez; registre um
   crédito/estorno com `EXPENSE_DECREASE` e confirme que reduz despesas até o
   limite das despesas do mesmo cartão no período, com o excedente virando
   `creditBalance` do cartão. O crédito acumulado é aplicado à primeira fatura não
   paga em ordem de vencimento; se exceder a fatura, o residual segue para a
   seguinte, sem reabrir faturas pagas; confirme `appliedCredit` na fatura.
9. Se o provedor não fornecer Bills, confirme que a fatura local continua
   funcional e que o sistema não inventa dados.
10. Altere o dia de fechamento/vencimento e confirme que faturas já fechadas ou
    pagas não são reescritas.
11. Registre uma transação após o fechamento e confirme que ela vai para o ciclo
    seguinte ou para um ajuste explícito.
12. Confirme que um cartão sem ciclo exibe transações não alocadas, sem inventar
    fatura.
13. Aplique um ajuste manual em fatura fechada/paga e confirme que o status de
    pagamento permanece e que o indicador de ajustes é exibido.

## 8. Validar dashboard e relatórios

1. Abra o dashboard e confirme a distinção entre resultado do período e saldo
   atual consolidado; o saldo atual soma apenas contas `BANK`/`CASH`/`WALLET`,
   cartões aparecem separados como passivo/fatura, e nenhum dos dois é
   apresentado como fluxo de caixa.
2. Aplique filtros por período, conta, cartão, categoria, tipo, origem e faixa
   de valor; confirme que “gastos por categoria” é a distribuição do mesmo
   total de despesas, não um total paralelo.
3. Abra o drill-down de uma categoria e confirme que a soma de
   `/reports/entries` corresponde ao total, que cada entrada indica origem
   `IMPORTED`/`MANUAL` e que itens de fatura sem transação aparecem quando
   aplicável.
4. Confirme que compras de cartão, pagamento de fatura e transferências não são
   contados duas vezes.
5. Confirme que saldos ausentes/desatualizados são sinalizados e que a última
   sincronização exibida corresponde às conexões incluídas no filtro; sem
   conexão aplicável, a interface informa “sem sincronização no período”.
6. Crie uma transação pendente e confirme que ela aparece em bloco separado com
   `pendingCount`, `pendingIncome` e `pendingExpenses`, sem total líquido, fora
   de receitas, despesas, resultado, categorias e drill-down. Depois, efetive-a
   e confirme que entra nos totais exatamente uma vez.
7. Registre uma transação em moeda diferente de BRL e confirme que ela é
   sinalizada e não entra nos totais consolidados nem em conversão automática.
8. Teste em 360 px de largura, zoom de 200%, teclado e leitor de tela.

## 9. Validar reconciliação e correções

1. Crie um par de movimentos compatíveis entre contas próprias.
2. Confirme que o sistema sugere a transferência, mas não a classifica sem
   confirmação.
3. Confirme a sugestão e verifique que os dois lados saem de receitas/despesas.
4. Rejeite outra sugestão e confirme que os lançamentos permanecem normais.
5. Corrija descrição, data, valor e categoria de uma transação importada.
6. Execute nova sincronização e confirme que a correção prevalece e os valores
   originais continuam auditáveis.
7. Registre uma transferência manual e confirme que existem duas pernas
   vinculadas, sem impacto em receitas/despesas.
8. Registre um reembolso/chargeback vinculado, inclusive parcial, e confirme que
   ele reduz despesas no período em que ocorre, sem alterar a transação original.

## 10. Validar privacidade e ciclo de vida

1. Exporte os dados de A e confirme que o ZIP contém apenas dados de A, sem
   segredos.
2. Desconecte a integração de A e confirme que novas sincronizações param, as
   credenciais são removidas e os dados importados permanecem.
3. Exclua a conta de um usuário de teste e confirme que o acesso é bloqueado e
   os dados ativos são removidos em até 24 horas.
4. Confirme que o outro usuário não é afetado.
5. Confirme que backups e chaves têm procedimento documentado e que o backup
   expira em até 30 dias.

## 11. Testes automatizados

Backend:

```bash
cd server
./mvnw test
./mvnw verify
```

Frontend:

```bash
cd client
npm ci
npm run typecheck
npm run lint
npm test
npm run test:e2e
```

Os testes de integração usam Testcontainers com PostgreSQL 18.6. Os testes de
API usam dois usuários e o contrato OpenAPI. Os testes E2E usam um servidor
Pluggy stub apenas em ambiente de teste; dados de stub não contam como validação
real.

## 12. Validação real do caminho Meu Pluggy

Este gate é manual e obrigatório para considerar a integração bancária concluída:

- [ ] Dois usuários com pares Client ID/Client Secret distintos.
- [ ] Cada usuário com múltiplos itemIds próprios.
- [ ] Credenciais e itens validados antes de ativar a conexão.
- [ ] API Key obtida e renovada apenas no servidor.
- [ ] Nenhum Connect Token é solicitado, chamado ou armazenado.
- [ ] Contas, cartões e transações importados corretamente.
- [ ] Segunda sincronização sem duplicação.
- [ ] Falha de um usuário sem bloquear os demais.
- [ ] Nenhum acesso cruzado.
- [ ] Expiração anual, mudança de itemId e reconexão sem perda histórica.
- [ ] Estado, última tentativa e último sucesso compreensíveis.
- [ ] Operação local com dados importados quando a Pluggy estiver indisponível.
- [ ] Backup/restauração com chaves externas validado.

Sandbox, WireMock e dados sintéticos não substituem esse gate.

## 13. Backup e restauração

```bash
./deploy/scripts/backup.sh
./deploy/scripts/restore.sh --confirm backups/SEU_ARQUIVO.age
```

O backup usa `pg_dump -Fc` e criptografia com `age`. O key ring deve ser
backupado separadamente. Teste a restauração em um banco limpo e confirme:

- contagem de usuários, contas, transações e faturas;
- totais do dashboard;
- leitura de campos cifrados com o key ring correto;
- falha esperada ao tentar ler sem o key ring;
- migrações pendentes aplicadas;
- backup expira em até 30 dias;
- smoke test de login e sincronização.

## 14. Atualização e rollback

1. Fazer backup verificado.
2. Baixar a nova versão fixada por tag/digest.
3. Executar `docker compose up -d --build`.
4. Verificar health checks e migrações.
5. Executar smoke test de login, dashboard e uma sincronização.
6. Em caso de falha, restaurar a imagem anterior e o backup; nunca editar
   migração já aplicada.

## 15. Troubleshooting

| Sintoma | Verificação |
|---|---|
| Dados não atualizam imediatamente | Meu Pluggy atualiza a origem a cada 24 h; a consulta manual não força atualização. |
| Item com lista vazia | Verificar `consentExpiresAt`, `status` e `lastErrorCode`; não apagar histórico. |
| `USER_AUTHORIZATION_EXPIRED` | Renovar no Dashboard Pluggy, revisar itemIds e reconciliar. |
| `LOGIN_ERROR` | Validar credenciais e reconectar; não expor detalhes do provedor. |
| `429` | Respeitar `RateLimit-Reset`/`Retry-After`; reduzir concorrência. |
| `itemId` mudou | Atualizar o vínculo local e reconciliar por IDs locais/fingerprint. |
| Webhook não chega | Verificar HTTPS público, segredo, status da inscrição e logs sanitizados. |
| Fatura divergente | Conferir ciclo, `billId`, parcelas, pagamentos e itens manuais; não inventar valores. |

## 16. Protocolo SC-001

1. Recrutar 5 participantes que nunca usaram o sistema.
2. Usar uma instância limpa, sem dados de teste visíveis.
3. O facilitador informa apenas a URL e que o participante deve criar uma conta
   e entrar.
4. O cronômetro começa quando o participante inicia o cadastro e termina no
   primeiro render do dashboard autenticado.
5. O facilitador não pode orientar, corrigir, preencher campos ou sugerir
   caminhos.
6. Qualquer intervenção conta como falha do participante.
7. Registrar tempo, resultado e observações por participante; a meta é 100% sem
   assistência e menos de 2 minutos.

## 17. Definition of Done operacional

- [ ] Stack sobe com `docker compose up -d`.
- [ ] Health checks verdes.
- [ ] Migrações aplicadas em banco vazio e em banco existente, com
      `MigrationIT` verde.
- [ ] Matriz de acesso cruzado com dois usuários e duas integrações independentes
      cobrindo leitura, escrita, exclusão, exportação e relatórios.
- [ ] Política de senha e limite de login aplicados.
- [ ] Transações `PENDING` fora dos totais e contabilizadas uma única vez após
      efetivação.
- [ ] Drill-down via `/reports/entries` com soma igual ao total, incluindo itens
      de fatura sem transação.
- [ ] `CONFIRM_REMOVAL` e `RESTORE` de `SOURCE_REMOVED` sem duplicação.
- [ ] Job de retenção expurgando `login_attempt`, `sync_run`, `webhook_event`,
      `audit_event` e `idempotency_key` conforme prazos.
- [ ] Dump do PostgreSQL sem key ring não revela campos protegidos.
- [ ] Runbook de recuperação assistida de senha documentado.
- [ ] Importação real validada com dois Meu Pluggy independentes.
- [ ] Sincronização idempotente e sem perda histórica.
- [ ] Faturas completas funcionando com dados locais e Bills quando disponíveis.
- [ ] Dashboard com resultado do período, saldo atual sem cartões e drill-down.
- [ ] Cinco usuários concluem cadastro e primeiro login em menos de 2 minutos.
- [ ] Um usuário conclui lançamento manual em menos de 90 segundos.
- [ ] Exportação, desconexão e exclusão testadas, com dados ativos removidos em
      até 24 horas.
- [ ] Nenhuma tela exibe recomendação de compra, venda, alocação ou
      aconselhamento de investimento.
- [ ] Backup/restauração, key ring e expiração de backup em 30 dias testados.
- [ ] Responsividade e acessibilidade verificadas.
- [ ] Documentação de operação, riscos e limitações atualizada.
