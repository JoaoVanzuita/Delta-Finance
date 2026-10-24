# Runbook: reconectar e atualizar itemId

**Escopo**: autorização anual expirada, consentimento bancário expirado, mudança
de itemId ou conexão indisponível (FR-030–FR-034).

## 1. Identificar o cenário

| Estado | Significado | Ação |
|---|---|---|
| `AUTHORIZATION_EXPIRED` | autorização anual da aplicação expirou | renovar a aplicação no Dashboard Pluggy e validar credenciais |
| `CONSENT_EXPIRED` | consentimento do item/instituição expirou | reconectar a instituição e obter novo itemId |
| `ITEM_UNAVAILABLE` | item removido/inacessível no provedor | reconectar ou remover a conexão preservando histórico |
| `CREDENTIAL_FAILURE` | Client ID/Secret inválido | corrigir credenciais; não apagar dados |
| `ACTION_REQUIRED` | intervenção no provedor | seguir a mensagem do provedor sem expor detalhes |

## 2. Renovar a aplicação (autorização anual)

1. No Dashboard Pluggy, renove a autorização da aplicação pessoal.
2. Copie o novo Client ID/Secret, se houver.
3. Em **Configurações → Integração**, valide e salve.
4. Execute uma consulta de dados; confirme que o estado volta a `ACTIVE`.
5. O histórico local é preservado.

## 3. Trocar o itemId

1. Obtenha o novo itemId após reconectar a instituição.
2. Abra a conexão em **Configurações → Integração** e use “Atualizar itemId”.
3. O ID local da conexão **não muda**; contas, transações e faturas continuam
   vinculadas.
4. Execute a reconciliação; o sistema compara IDs locais/fingerprint e não cria
   duplicatas.
5. Confirme no histórico que não houve duplicação e que a janela de 12 meses foi
   reconciliada.

## 4. Remover uma conexão

- **Desativar e manter dados (padrão)**: interrompe novas sincronizações, marca
  as contas importadas como indisponíveis e as retira dos saldos correntes;
  contas/transações permanecem no histórico.
- **Excluir conexão e dados**: apaga somente contas de origem `IMPORTED` dessa
  conexão e seus dependentes; contas manuais, lançamentos nelas e categorias
  permanecem.
- Se a mesma conta aparece em duas conexões (por exemplo, duas autorizações do
  mesmo banco), exclua uma delas para eliminar o saldo duplicado. Não adicione a
  mesma conta novamente: use **Atualizar itemId** na conexão existente.
- A remoção gera auditoria sanitizada e não afeta outros usuários.

## 5. Validação

- `GET /api/v1/connections` mostra o novo estado e `lastSyncAt`.
- `/sync/runs` registra a tentativa e o código de erro sanitizado.
- Dashboard continua somando apenas dados do titular.
- Nenhum itemId bruto aparece em log, exportação ou interface.

## 6. Se a reconexão falhar

Não apague dados para “resolver”. Preserve o histórico, registre o código, teste
a credencial no Dashboard Pluggy e, se necessário, abra incidente. Uma falha de
um usuário não deve bloquear os demais.
