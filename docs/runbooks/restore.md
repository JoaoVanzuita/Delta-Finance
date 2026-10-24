# Runbook: restauração

**Escopo**: restaurar um backup verificado em banco limpo. Complementa
`backup.md`, que descreve geração, cifra e retenção.

## 1. Antes

1. Confirme o incidente e o horário-alvo (RPO de 24 h).
2. Faça um backup do estado atual, mesmo que degradado.
3. Localize o par backup + key ring usado na época.
4. Avise os usuários sobre a indisponibilidade (RTO de 2 h).

## 2. Restaurar

```bash
export AGE_IDENTITY_FILE="$HOME/age-key.txt"
./deploy/scripts/restore.sh --latest --yes
# ou
./deploy/scripts/restore.sh --file backups/SEU_ARQUIVO.dump.age --yes
```

O script valida o checksum, decifra, para o serviço `server`, executa
`pg_restore --clean --if-exists`, valida o histórico Flyway e reinicia.

## 3. Validar

```bash
curl -fsS http://localhost:8080/actuator/health/readiness
docker compose logs --tail=100 server
```

```sql
select max(version) from flyway_schema_history;
select count(*) from app_user;
select count(*) from financial_account;
select count(*) from transaction;
select count(*) from invoice;
```

- Login com conta de teste.
- Dashboard e drill-down.
- Leitura de um campo cifrado (apelido/descrição).
- Uma sincronização com dados já importados.

## 4. Sem key ring

Se o key ring correto não estiver disponível, os campos cifrados permanecem
ilegíveis e **não há recuperação**. Não tente quebrar a cifra; restaure o key
ring do backup separado. O ensaio `BackupRestoreIT` comprova essa propriedade.

## 5. Falhas

| Sintoma | Ação |
|---|---|
| checksum inválido | usar outro backup; não ignorar |
| `pg_restore` com erro | manter `server` parado, revisar log, repetir |
| readiness vermelho | verificar datasource, Flyway e key ring |
| dados ilegíveis | key ring divergente; restaurar o correspondente |
| migração pendente | subir a versão de código compatível com o dump |

## 6. Encerrar

- Reative o scheduler e confirme a fila.
- Rode o backup pós-restauração.
- Registre no incidente: backup usado, key ring, horário, contagens e validações.
