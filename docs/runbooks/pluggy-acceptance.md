# Runbook: aceitação real do Meu Pluggy

**Escopo**: T182. Gate manual obrigatório: mocks, Sandbox, WireMock e o stub de
teste **não** contam como aceitação real.

> **Status em 2026-10-24**: executado e aprovado nesta instalação, com duas
> contas Meu Pluggy reais, aplicações pessoais independentes, Client ID/Secret
> próprios e múltiplos itemIds fornecidos pelos titulares. Nenhuma credencial
> real é armazenada no repositório.
>
> A Phase 11 (US8, investimentos manuais) foi implementada antes desta validação
> por decisão do proprietário. O módulo é somente manual e não declara importação
> automática validada; a exceção histórica está registrada em
> `specs/001-finance/tasks.md`.

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
- [x] Nenhuma tela anuncia importação automática de investimentos, patrimônio ou
      metas como disponível; posições manuais de investimento da fase secundária
      são identificadas como registro manual, sem cotação ou recomendação.

## 5. Registro

**Registro desta execução**: gate validado em 2026-10-24. Evidências sanitizadas
mantidas no ticket do proprietário, sem segredos, itemIds ou dados financeiros
no repositório.

Preencha no ticket: data, versão/commit, operador, usuários (nomes fictícios),
quantidade de itemIds, resultados por critério, códigos de erro observados e
links para evidências sanitizadas (sem segredos, itemIds ou dados financeiros).
