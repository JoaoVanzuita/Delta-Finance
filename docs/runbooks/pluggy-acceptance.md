# Runbook: aceitação real do Meu Pluggy

**Escopo**: T182. Gate manual obrigatório: mocks, Sandbox, WireMock e o stub de
teste **não** contam como aceitação real.

> **Status em 2026-10-24**: não executado nesta instalação. Requer duas contas
> Meu Pluggy reais com aplicações pessoais independentes, Client ID/Secret e
> múltiplos itemIds fornecidos pelos titulares. Nenhuma credencial real é
> armazenada no repositório.
>
> Em 2026-10-24 a Phase 11 (US8, investimentos manuais) foi implementada por
> decisão do proprietário com este gate ainda pendente. A exceção está registrada
> em `specs/001-finance/tasks.md` e não substitui a aceitação real abaixo.

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
7. Repetir a sincronização três vezes; confirmar ausência de duplicatas.
8. Confirmar estado, última tentativa e último sucesso.
9. Confirmar que nenhuma API Key ou Connect Token aparece no navegador, log ou
   exportação.
10. Confirmar que A não vê dados de B e vice-versa, mesmo com IDs manipulados.

## 3. Casos de falha

- Credencial inválida → estado `CREDENTIAL_FAILURE`, mensagem genérica.
- Timeout/`429` → retry com backoff; histórico preservado.
- Lista vazia → `EMPTY`, sem marcar remoções.
- Consentimento expirado por item → `AUTHORIZATION_EXPIRED`/`CONSENT_EXPIRED`.
- Mudança de itemId → vínculo local preservado e reconciliação sem duplicar.
- Provedor indisponível → operação local com dados já importados.

## 4. Critérios de aprovação

- [ ] Dois usuários com pares Client ID/Secret distintos.
- [ ] Cada usuário com múltiplos itemIds próprios.
- [ ] Credenciais e itens validados antes de ativar a conexão.
- [ ] API Key obtida/renovada apenas no servidor.
- [ ] Nenhum Connect Token solicitado/chamado/armazenado.
- [ ] Contas, cartões e transações importados corretamente.
- [ ] Segunda sincronização sem duplicação.
- [ ] Falha de um usuário sem bloquear o outro.
- [ ] Nenhum acesso cruzado em leitura, escrita, exclusão, exportação e
      relatórios.
- [ ] Expiração anual, mudança de itemId e reconexão sem perda histórica.
- [ ] Backup/restauração com key ring externo validado.
- [ ] Nenhuma tela anuncia importação automática de investimentos, patrimônio ou
      metas como disponível; posições manuais de investimento da fase secundária
      são identificadas como registro manual, sem cotação ou recomendação.

## 5. Registro

Preencha no ticket: data, versão/commit, operador, usuários (nomes fictícios),
quantidade de itemIds, resultados por critério, códigos de erro observados e
links para evidências sanitizadas (sem segredos, itemIds ou dados financeiros).
