# Runbook: incidentes

**Escopo**: indisponibilidade, suspeita de vazamento, falha de sincronização e
falha de backup. Prioriza contenção, evidência sanitizada e comunicação.

## 1. Classificação inicial

| Sinal | Gravidade | Primeira ação |
|---|---|---|
| Stack fora do ar | alta | verificar `docker compose ps`, health e logs |
| Suspeita de vazamento de credencial/dados | crítica | revogar credenciais, isolar host, preservar logs |
| Sincronização falhando para todos | alta | verificar provedor, rate limit e fila |
| Falha repetida de backup | alta | corrigir e gerar backup manual cifrado |
| Erro 5xx isolado | média | correlação por `requestId` |
| Acesso cruzado suspeito | crítica | bloquear sessões, revisar `audit_event`, seguir isolamento |

## 2. Contenção

- Não registre dados financeiros, Client Secret, API Key, senha ou corpo de
  requisição em ticket/chat.
- Em suspeita de vazamento: rotacione Client Secret/credenciais, invalide
  sessões (`logout`/expiração) e, se necessário, pare o serviço.
- Preserve logs e o banco para análise; não edite migrações nem apague
  `audit_event` dentro do prazo.
- Verifique se o key ring foi exposto; se sim, trate como comprometimento e
  execute rotação conforme `key-rotation.md`.

## 3. Diagnóstico rápido

```bash
docker compose ps
docker compose logs --tail=200 server client proxy db
curl -fsS http://localhost:8080/actuator/health/readiness
```

Consultas sem dados sensíveis:

```sql
select event_type, count(*) from audit_event
 where created_at > now() - interval '1 hour' group by event_type;
select status, error_code, count(*) from sync_run
 where started_at > now() - interval '1 hour' group by status, error_code;
select status, count(*) from sync_job group by status;
```

## 4. Recuperação

- Indisponibilidade de banco: seguir `restore.md` após backup do estado atual.
- Fila acumulada: verificar scheduler, concorrência e `Retry-After`.
- Falha de backup: corrigir `age`/disco e executar `backup.sh` manualmente.
- Credencial comprometida: desconectar integração, remover credenciais, rotacionar
  e reconfigurar; preservar dados importados.

## 5. Comunicação

Informe aos usuários, sem detalhes exploráveis: o que está indisponível, o que
foi preservado, o prazo estimado e o que eles devem fazer (por exemplo, trocar a
senha). Nunca confirme dados de outra pessoa.

## 6. Pós-incidente

- Registre linha do tempo, causa raiz, impacto, dados afetados e ações.
- Verifique retenção/auditoria e integridade dos backups.
- Atualize este runbook e os testes que deveriam ter detectado o problema.
