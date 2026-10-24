# Runbook: conectar o Meu Pluggy pessoal

**Escopo**: configuração inicial da integração bancária (FR-011–FR-020).

## 1. Pré-requisitos

- Conta no Meu Pluggy (`meu.pluggy.ai`) com as instituições conectadas — é lá
  que os consentimentos bancários ficam.
- Aplicação pessoal no Dashboard Pluggy (`dashboard.pluggy.ai`), acessado com a
  **mesma conta** do Meu Pluggy, usando o conector **MeuPluggy**. O aviso de
  trial de 15 dias é do plano empresarial; o uso pessoal via MeuPluggy é
  gratuito, sem due diligence e não expira com o trial (até 5 conexões, uso não
  comercial).
- Client ID, Client Secret e itemIds copiados da página Demo do Dashboard
  (Itens Conectados). O UUID da URL de detalhes da conta no Meu Pluggy é o
  `accountId`, não o `itemId`.
- Nenhuma credencial deve ser compartilhada entre familiares.

## 2. Configurar no Delta Finance

1. Entre na própria conta.
2. Abra **Configurações → Integração**.
3. Informe Client ID e Client Secret da sua aplicação.
4. Valide as credenciais; a API Key é obtida e mantida apenas no servidor.
5. Adicione cada itemId separadamente (não há descoberta automática).
6. Confirme o limite documentado de conexões da modalidade Meu Pluggy.
7. Execute **Consultar dados disponíveis agora**. Isso não força atualização na
   instituição de origem.
8. Acompanhe estado, última tentativa, último sucesso e histórico em
   `GET /api/v1/sync/status` / `/sync/runs`.

## 3. O que verificar

- Client Secret nunca reaparece na interface, exportação ou log.
- API Key e Connect Token não aparecem no navegador.
- Contas importadas têm origem `IMPORTED`; contas manuais continuam separadas.
- A primeira carga usa 12 meses; a rotina diária reprocessa 7 dias com
  sobreposição e a reconciliação mensal cobre 12 meses.
- Duas sincronizações seguidas não duplicam transações.

## 4. Falhas comuns

| Código/estado | Ação |
|---|---|
| `PROVIDER_AUTH_FAILED` (HTTP 502) | revisar Client ID/Secret e validar novamente; a Pluggy responde 400 quando o Client ID não é um UUID |
| `CREDENTIAL_FAILURE` / `LOGIN_ERROR` | revisar Client ID/Secret e validar novamente |
| `AUTHORIZATION_EXPIRED` | renovar a aplicação no Dashboard Pluggy |
| `CONSENT_EXPIRED` | reconectar a instituição e atualizar o itemId |
| `429` / `PROVIDER_RATE_LIMITED` | aguardar `Retry-After`; não reduzir o intervalo do scheduler |
| Lista vazia | não apagar histórico; verificar consentimento e status do item |
| itemId inválido/inexistente (HTTP 422) | o UUID da página de detalhes da **conta** é o `accountId`, não o `itemId`. Copie o UUID na página do **item** em Itens Conectados (Dashboard → sua aplicação → Demo), da mesma aplicação Meu Pluggy do Client ID; a conexão anterior não é alterada |
| `ITEM_UNAVAILABLE` | reconectar o item e atualizar o itemId; a falha não é retentada automaticamente até a correção |

## 5. Segurança

- Não cole Client Secret em ticket/chat.
- Não habilite webhooks sem HTTPS público e segredo por inscrição.
- Em caso de troca de titularidade, desconecte e remova as credenciais antes de
  transferir o host.
