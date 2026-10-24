# Runbook: rotação de chaves

**Escopo**: T176 / FR-101. Rotação online da chave de dados AES-256-GCM e
procedimento separado para a chave de índice HMAC.

## 1. Pré-requisitos

- Backup verificado (ver `backup.md`) e janela sem sincronizações críticas.
- Acesso ao key ring externo e à versão correspondente do backend.
- Nunca remova a chave antiga antes de concluir a rotação e validar leituras.
- A chave de índice HMAC **não** é rotacionada por este job: ela exige
  reindexação completa, pois os HMACs de unicidade/deduplicação seriam perdidos.

## 2. Adicionar a nova versão

Edite `secrets/crypto-keys.json` mantendo as versões antigas:

```json
{
  "data": [
    { "version": 1, "key": "<base64 32B antiga>" },
    { "version": 2, "key": "<base64 32B nova>" }
  ],
  "index": [
    { "version": 1, "key": "<base64 32B>" }
  ]
}
```

No `.env`, aponte a versão ativa e reinicie o servidor:

```bash
DELTA_CRYPTO_ACTIVE_VERSION=2
DELTA_CRYPTO_KEY_VERSION=2
docker compose up -d server
```

Confirme `/actuator/health/readiness` (o health check valida as versões ativas
sem expor material de chave).

## 3. Executar a rotação

```bash
./deploy/scripts/rotate-keys.sh --yes
```

O script roda um processo efêmero com
`--delta.crypto.rotation.run=true`, executa `KeyRotationJob` e encerra com
código 0 se não houver falhas. O job:

- percorre todos os campos cifrados conhecidos (`pluggy_integration`,
  `financial_account`, `transaction`, `invoice`, `invoice_item`,
  `invoice_payment`, correções, webhook, cartão etc.);
- regrava cada valor com a versão ativa, preservando o AAD
  (`user_id`, tabela, coluna, ID do registro);
- processa em lotes por ID e é idempotente;
- registra apenas `targetVersion`, `rotated` e `failed`.

Saída esperada:

```text
Rotação concluída: targetVersion=2 rotated=<n> failed=0
```

## 4. Validar

1. Repita o comando: `rotated=0 failed=0`.
2. Abra dashboard, uma conta, uma fatura e uma exportação.
3. Confirme que novos logs não contêm valores financeiros.
4. Verifique o health check e uma sincronização de teste.

Consulta auxiliar (não revela texto claro):

```sql
-- Conta campos que ainda estão na versão 1 (deve ser 0 após a rotação).
select count(*) from transaction where get_byte(amount_effective_enc, 1) < 2;
select count(*) from financial_account where get_byte(alias_enc, 1) < 2;
select count(*) from invoice where get_byte(total_enc, 1) < 2;
```

## 5. Remover a chave antiga

Somente após validar todas as leituras e manter um backup recente:

1. remova a entrada `version: 1` de `data`;
2. reinicie o servidor;
3. confirme health e uma leitura de cada módulo;
4. mantenha o backup anterior pelo prazo de retenção.

Se algum campo antigo aparecer como ilegível, restaure o key ring anterior e
execute a rotação novamente antes de remover a chave.

## 6. Índice HMAC

A rotação da chave de índice exige reindexação:

1. pare escritas (modo manutenção);
2. gere a nova chave e mantenha a antiga;
3. recalcule HMACs de `login_attempt`, `category.normalized_name_hash`,
   `external_*_hash`, `dedup_key` e `fingerprint` com a nova versão;
4. valide login, deduplicação e unicidade;
5. só então remova a chave antiga.

Esse procedimento é manual e não faz parte do MVP automatizado; registre a janela
e o responsável no ticket operacional.

## 7. Falhas

| Sintoma | Ação |
|---|---|
| `failed>0` | mantenha ambas as chaves; investigue a linha pelo ID no log sanitizado e repita |
| `CryptoException` de autenticação | AAD/registro divergente ou chave antiga ausente; restaure o key ring e o backup |
| Health readiness vermelho | versão ativa não existe no key ring; corrija o arquivo e reinicie |
| Valores ilegíveis após remover chave | restaure a chave antiga e o backup imediatamente |
