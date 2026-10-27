# Runbook: aceitação real do Meu Pluggy

**Escopo**: T182. Gate manual obrigatório: mocks, Sandbox, WireMock e o stub de
teste **não** contam como aceitação real.

> **Status em 2026-10-24**: executado e aprovado nesta instalação, com duas
> contas Meu Pluggy reais, aplicações pessoais independentes, Client ID/Secret
> próprios e múltiplos itemIds fornecidos pelos titulares. Nenhuma credencial
> real é armazenada no repositório.
>
> **Investimentos em 2026-10-27**: a importação foi validada nesta instalação
> para uma conexão XP via Meu Pluggy. Foram verificados 4 itens; apenas 1
> declarou recursos de investimento (`VARIABLE_INCOME`, 6 recursos `AVAILABLE`) e
> retornou 6 posições (3 ativas e 3 encerradas). Nenhum `INTEREST`,
> `amountProfit`, `amountOriginal` ou taxa de rentabilidade foi retornado; o
> módulo deve exibir "não informado" e aceitar provento/custo manual. Santander e
> outras instituições seguem não validadas.

## 1. Preparação

- Duas pessoas (A e B), cada uma com a própria conta Meu Pluggy.
- Cada pessoa cria uma aplicação pessoal no Dashboard Pluggy e conecta ao menos
  duas instituições/itemIds próprios.
- Instância limpa, com HTTPS ou localhost seguro e key ring configurado.
- Nenhum dado de teste visível.

## 2. Roteiro por usuário

1. Criar conta e entrar.
2. Configurar Client ID/Secret **da própria** aplicação em
   Configurações → Integração.
3. Validar credenciais; confirmar que o Client Secret não reaparece.
4. Adicionar cada itemId separadamente.
5. Consultar dados disponíveis agora; confirmar que a interface explica que
   isso não força atualização na origem.
6. Confirmar contas, cartões e transações importados.
7. Confirmar a cobertura de investimentos: itens com recursos
   `BANK_FIXED_INCOME`/`CREDIT_FIXED_INCOME`/`VARIABLE_INCOME`/`TREASURE_TITLE`/
   `FUND` devem importar posições; itens sem esses recursos devem explicar que a
   instituição não compartilha investimentos, sem criar posições.
8. Confirmar posições ativas, encerradas (`TOTAL_WITHDRAWAL`) e indisponíveis;
   encerradas não entram no valor atual.
9. Confirmar que proventos e rentabilidade exibem a origem ou "não informado" e
   que provento/custo manual não sobrescreve o provedor.
10. Repetir a sincronização três vezes; confirmar ausência de duplicatas.
11. Confirmar estado, última tentativa e último sucesso.
12. Confirmar que nenhuma API Key ou Connect Token aparece no navegador, log ou
   exportação.
13. Confirmar que A não vê dados de B e vice-versa, mesmo com IDs manipulados.

## 3. Casos de falha

- Credencial inválida → estado `CREDENTIAL_FAILURE`, mensagem genérica.
- Timeout/`429` → retry com backoff; histórico preservado.
- Lista vazia → `EMPTY`, sem marcar remoções.
- Consentimento expirado por item → `AUTHORIZATION_EXPIRED`/`CONSENT_EXPIRED`.
- Mudança de itemId → vínculo local preservado e reconciliação sem duplicar.
- Provedor indisponível → operação local com dados já importados.

## 4. Critérios de aprovação

- [x] Dois usuários com pares Client ID/Secret distintos.
- [x] Cada usuário com múltiplos itemIds próprios.
- [x] Credenciais e itens validados antes de ativar a conexão.
- [x] API Key obtida/renovada apenas no servidor.
- [x] Nenhum Connect Token solicitado/chamado/armazenado.
- [x] Contas, cartões e transações importados corretamente.
- [x] Segunda sincronização sem duplicação.
- [x] Falha de um usuário sem bloquear o outro.
- [x] Nenhum acesso cruzado em leitura, escrita, exclusão, exportação e
      relatórios.
- [x] Expiração anual, mudança de itemId e reconexão sem perda histórica.
- [x] Backup/restauração com key ring externo validado.
- [ ] Importação de investimentos anunciada apenas para itens com recursos de
      investimento; itens sem cobertura são explicados sem criar posições.
      **Pendente da Phase 11A**; a leitura real de 2026-10-27 validou a
      disponibilidade dos dados, não a tela.
- [ ] Posições encerradas ficam fora do valor atual e permanecem no histórico;
      proventos/rentabilidade mostram origem ou "não informado". **Pendente da
      Phase 11A.**
- [ ] Falha de investimento não interrompe contas, cartões e transações.
      **Pendente da Phase 11A.**
- [x] Nenhuma tela anuncia patrimônio ou metas como disponível; posições manuais
      são identificadas como registro do usuário, sem cotação ou recomendação.
- [x] Nenhum módulo oferece recomendação de compra, venda ou alocação.

## 5. Registro

**Registro desta execução**: gate validado em 2026-10-24. A importação de
investimentos foi validada separadamente em 2026-10-27 para XP via Meu Pluggy,
sem transformar a validação em promessa de cobertura para outras instituições.
Evidências sanitizadas mantidas no ticket do proprietário, sem segredos, itemIds
ou dados financeiros no repositório.

Preencha no ticket: data, versão/commit, operador, usuários (nomes fictícios),
quantidade de itemIds, resultados por critério, códigos de erro observados e
links para evidências sanitizadas (sem segredos, itemIds ou dados financeiros).
