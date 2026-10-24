# Runbook: backup e restauração

**Escopo**: T175 / FR-107. Backup diário cifrado, retenção de 30 dias,
restauração em banco limpo e comprovação de que o dump sem key ring não revela
campos protegidos.

## 1. Modelo

- `deploy/scripts/backup.sh`: `pg_dump -Fc` no container `db`, cifrado com
  `age` para um destinatário público, checksum SHA-256 e retenção de 30 dias.
- `deploy/scripts/restore.sh`: valida checksum, decifra com a identidade `age`,
  para o serviço `server`, restaura com `pg_restore --clean --if-exists` e
  reinicia.
- O **key ring** (`secrets/crypto-keys.json`) não vai no dump. Sem ele, os
  campos cifrados são ilegíveis. Guarde-o em local separado do backup.
- RPO alvo: 24 h. RTO alvo: 2 h.

## 2. Execução diária

```bash
export AGE_RECIPIENT='age1...'          # chave pública do operador
export DELTA_BACKUP_RETENTION_DAYS=30
./deploy/scripts/backup.sh
```

Agende com `systemd`/cron do host. O script falha se `age` não estiver
disponível, se o dump estiver vazio ou se o checksum não puder ser gerado.
`ALLOW_UNENCRYPTED_BACKUP=1` existe apenas para laboratório.

Verifique:

```bash
ls -l backups/
sha256sum -c backups/delta-*.dump.age.sha256
```

## 3. Ensaio de retenção

```bash
./deploy/scripts/tests/backup-retention-test.sh
```

Resultado esperado: `OK: retenção de 30 dias removeu expirados e preservou
vigentes e key ring.` O teste usa a mesma função de retenção de `backup.sh`
(`deploy/scripts/lib/backup-retention.sh`).

## 4. Restauração em banco limpo

1. Faça backup do estado atual antes de restaurar.
2. Confirme o arquivo mais recente e o checksum.
3. Execute:

```bash
export AGE_IDENTITY_FILE="$HOME/age-key.txt"
./deploy/scripts/restore.sh --latest --yes
```

4. Verifique `/actuator/health/readiness` e os logs de migração.
5. Valide contagens e leitura de campos cifrados com o key ring **correto**:

```sql
select count(*) from app_user;
select count(*) from financial_account;
select count(*) from transaction;
select count(*) from invoice;
select max(version) from flyway_schema_history;
```

6. Faça login com uma conta de teste e abra o dashboard.

## 5. Prova de ilegibilidade sem key ring

O ensaio automatizado `BackupRestoreIT` executa este roteiro em PostgreSQL 18.6
(Testcontainers):

1. cria usuário, conta/categoria com ciphertext real;
2. `pg_dump -Fc` e `pg_restore` em banco vazio;
3. confirma contagens e histórico Flyway;
4. confirma que o ciphertext restaurado não contém o texto claro;
5. confirma que uma instância com **outro** key ring não consegue decifrar
   (`CryptoException`).

Execução local:

```bash
cd server
./mvnw -Dit.test=BackupRestoreIT -DfailIfNoTests=false verify
```

Resultado registrado em 2026-10-24: 1 teste, 0 falhas.

## 6. Expiração em 30 dias

`backup.sh` aplica `find ... -mtime +30 -delete` somente a arquivos
`delta-*.dump`, `delta-*.dump.age` e seus checksums. O key ring e outros
arquivos do diretório não são tocados. O teste da seção 3 comprova a remoção de
um backup com 31 dias e a preservação de um backup atual e do key ring.

## 7. Falhas comuns

| Sintoma | Causa provável | Ação |
|---|---|---|
| `age não encontrado` | binário ausente | instalar `age`; não usar backup sem cifra |
| `dump vazio` | `db` indisponível | verificar `docker compose ps db` e repetir |
| `checksum inválido` | arquivo corrompido/truncado | descartar e usar outro backup |
| Restauração com erro | migração incompatível ou conexões abertas | manter `server` parado, revisar log e repetir |
| Campos ilegíveis após restaurar | key ring diferente/ausente | restaurar o key ring correspondente; sem ele os dados são irrecuperáveis |
| Disco cheio | retenção não aplicada | verificar cron do backup e permissões do diretório |

## 8. Rollback e comunicação

Não há rollback parcial de uma restauração. Se o banco restaurado não passar no
smoke test, restaure o backup feito no passo 1 e comunique o incidente. Nunca
edite migração já aplicada nem restaure um dump sem o key ring correspondente.
