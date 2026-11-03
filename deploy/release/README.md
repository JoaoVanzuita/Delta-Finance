# Delta Finance — instalação self-hosted

Este kit executa o Delta Finance em Docker Compose, com PostgreSQL, backend e
frontend. O acesso é feito pelo navegador do computador host ou de outro
dispositivo na mesma rede local.

## Requisitos

- PC ou mini PC `amd64` (x86_64);
- Docker Engine + Docker Compose v2 no Linux, ou Docker Desktop com WSL2 no
  Windows 10/11;
- saída para internet para baixar as imagens do GHCR e do Docker Hub;
- porta `8080` livre no host (ou outra definida em `LAN_PORT`).

Não é necessário instalar Java, Node.js, PostgreSQL ou Maven.

## Arquivos do kit

```text
delta-finance-<versao>/
├── compose.yaml
├── .env.example
├── VERSION
├── README.md
├── release.json
└── SHA256SUMS
```

O key ring e o `.env` são criados por você e não vêm no kit. O release publica
o mesmo kit em `.zip` (Windows) e `.tar.gz` (Linux).

Antes de extrair, confira o checksum do arquivo baixado:

```bash
# Linux
sha256sum -c delta-finance-<versao>-amd64.tar.gz.sha256
```

```powershell
# Windows: compare com o valor exibido no arquivo .sha256
Get-FileHash delta-finance-<versao>-amd64.zip -Algorithm SHA256
```

## Instalação

### Linux

```bash
tar -xzf delta-finance-<versao>-amd64.tar.gz
cd delta-finance-<versao>
cp .env.example .env
```

Edite `.env` e troque pelo menos `DELTA_DB_PASSWORD`. Para gerar uma senha
adequada:

```bash
openssl rand -hex 24
```

Depois da primeira subida, mantenha `DELTA_DB_NAME`, `DELTA_DB_USER` e
`DELTA_DB_PASSWORD`; alterar apenas o `.env` não muda a senha já gravada no
volume do PostgreSQL.

Gere o key ring:

```bash
mkdir -p secrets
chmod 700 secrets
umask 077
cat > secrets/crypto-keys.json <<EOF
{
  "data":  [{ "version": 1, "key": "$(openssl rand -base64 32)" }],
  "index": [{ "version": 1, "key": "$(openssl rand -base64 32)" }]
}
EOF
chmod 640 secrets/crypto-keys.json
```

Descubra o grupo do arquivo e ajuste `DELTA_KEYRING_GID` no `.env`:

```bash
stat -c %g secrets/crypto-keys.json
```

Depois:

```bash
docker compose pull
docker compose up -d
docker compose ps
```

### Windows (PowerShell)

```powershell
Expand-Archive delta-finance-<versao>-amd64.zip
cd delta-finance-<versao>
Copy-Item .env.example .env
```

Edite `.env` e troque `DELTA_DB_PASSWORD`. Gere o key ring:

```powershell
function New-Key {
  $bytes = New-Object byte[] 32
  $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
  $rng.GetBytes($bytes)
  $rng.Dispose()
  [Convert]::ToBase64String($bytes)
}

New-Item -ItemType Directory -Force secrets | Out-Null
@{
  data  = @(@{ version = 1; key = (New-Key) })
  index = @(@{ version = 1; key = (New-Key) })
} | ConvertTo-Json -Depth 5 | Set-Content -Encoding ascii secrets\crypto-keys.json

icacls secrets /inheritance:r /grant:r "$env:USERNAME:(OI)(CI)F"
icacls .env /inheritance:r /grant:r "$env:USERNAME:F"
```

Depois:

```powershell
docker compose pull
docker compose up -d
docker compose ps
```

## Acesso

Descubra o IP do host:

```bash
# Linux
hostname -I
```

```powershell
# Windows
ipconfig
```

Abra em outro dispositivo da LAN:

```text
http://IP-DO-HOST:8080
```

No próprio host, use `http://localhost:8080`. Recomenda-se reservar o IP no DHCP
do roteador para que ele não mude.

## Operação diária

```bash
docker compose ps
docker compose logs -f
docker compose stop
docker compose start
docker compose down
```

`docker compose down` não remove volumes. **Nunca use `docker compose down -v`**
em uma instalação com dados.

## Atualização

Antes de atualizar:

1. faça backup do banco;
2. copie `secrets/crypto-keys.json` e `.env` para local seguro;
3. confirme espaço em disco suficiente.

Depois:

1. edite `DELTA_VERSION` no `.env` para a nova versão (sem o `v`);
2. execute:

```bash
docker compose pull
docker compose up -d --remove-orphans
docker compose ps
docker compose logs --tail=100
```

As imagens antigas permanecem no host, o que ajuda no rollback.

## Rollback

Se a versão anterior for compatível com o schema atual:

```bash
# Edite DELTA_VERSION no .env para a versão anterior.
docker compose pull
docker compose up -d --remove-orphans
```

Se a migração não for compatível, restaure o backup feito antes da atualização.
O key ring precisa ser o mesmo do momento do backup.

## Backup

O backup mínimo inclui:

- dump do PostgreSQL;
- `secrets/crypto-keys.json`;
- `.env`;
- versão atual (`VERSION` ou `DELTA_VERSION`);
- `compose.yaml` (recomendado).

### Linux e Windows

Use `docker compose cp` para copiar o dump do container sem redirecionar saída
binária pelo shell:

```bash
mkdir -p backups
docker compose exec -T db sh -c \
  'pg_dump --format=custom --no-owner --no-privileges -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /tmp/delta.dump'
docker compose cp db:/tmp/delta.dump backups/delta-$(date +%Y%m%dT%H%M%S).dump
docker compose exec -T db rm -f /tmp/delta.dump
```

No PowerShell, use um nome de arquivo fixo e renomeie depois:

```powershell
New-Item -ItemType Directory -Force backups | Out-Null
docker compose exec -T db sh -c 'pg_dump --format=custom --no-owner --no-privileges -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /tmp/delta.dump'
docker compose cp db:/tmp/delta.dump backups/delta.dump
docker compose exec -T db rm -f /tmp/delta.dump
```

Copie também o key ring e o `.env` para o mesmo local protegido. Guarde uma
cópia fora do computador que hospeda a instância.

### Restauração

```bash
docker compose stop server
docker compose cp backups/delta.dump db:/tmp/delta.dump
docker compose exec -T db sh -c \
  'pg_restore --clean --if-exists --no-owner --no-privileges -U "$POSTGRES_USER" -d "$POSTGRES_DB" /tmp/delta.dump'
docker compose exec -T db rm -f /tmp/delta.dump
docker compose start server
docker compose ps
```

Valide o login e o dashboard depois da restauração. Sem o key ring original, os
campos cifrados do dump são ilegíveis.

## Segurança

- O acesso é HTTP sem criptografia. Use apenas em uma LAN confiável.
- Não configure port forwarding no roteador.
- Não use Wi-Fi público ou rede de convidados.
- Prefira WPA2/WPA3 e mantenha o host atualizado.
- Restrinja `secrets/` e `.env` ao usuário que administra a instalação.
- Faça backup do key ring separadamente do banco.

## Migração de uma instalação com TLS/proxy

Se você já usa a stack antiga com `proxy` e HTTPS:

1. faça backup e copie `.env` e `secrets/crypto-keys.json`;
2. pare a stack com `docker compose down` (sem `-v`);
3. extraia o kit novo e preserve `DELTA_DB_NAME`, `DELTA_DB_USER`,
   `DELTA_DB_PASSWORD` e o key ring;
4. defina `DELTA_VERSION`, `LAN_PORT` e `DELTA_KEYRING_GID`;
5. execute `docker compose pull` e `docker compose up -d --remove-orphans`;
6. para levar os dados antigos, restaure o backup no novo banco usando o mesmo
   key ring.

O kit de release usa o volume `delta-finance-prod_pgdata`, separado do volume
de desenvolvimento. Ele não reutiliza automaticamente o volume da stack antiga;
sem restauração, o banco novo começa vazio. O acesso passa a ser
`http://IP:8080`; o antigo endereço HTTPS deixa de existir.

## Problemas comuns

| Sintoma                       | Verificação                                                      |
| ----------------------------- | ---------------------------------------------------------------- |
| `docker: command not found`   | Instale Docker Engine/Compose ou abra o Docker Desktop.          |
| Container `server` unhealthy  | `docker compose logs server`; verifique key ring e banco.        |
| Erro de permissão no key ring | Ajuste `DELTA_KEYRING_GID` no Linux ou a ACL no Windows.         |
| `pull access denied` no GHCR  | O pacote precisa ser público ou faça `docker login ghcr.io`.     |
| Porta 8080 ocupada            | Troque `LAN_PORT` no `.env` e execute `docker compose up -d`.    |
| Outro dispositivo não acessa  | Verifique firewall do host e se está na mesma LAN.               |
| Login não mantém a sessão     | Confirme `DELTA_DEPLOYMENT_MODE=lan-http` no Compose de release. |
