# Delta Finance — instalação self-hosted

Este kit executa o Delta Finance em Docker Compose, com PostgreSQL, backend e
frontend. O acesso é feito pelo navegador do computador host ou de outro
dispositivo na mesma rede local.

## Requisitos

- PC ou mini PC `amd64` (x86_64);
- Docker Engine + Docker Compose v2 no Linux, ou Docker Desktop com WSL2 no
  Windows 10/11;
- saída para internet para baixar as imagens do GHCR e do Docker Hub;
- portas `80` e `443` livres no host, ou outras definidas em `LAN_HTTP_PORT` e
  `LAN_HTTPS_PORT`;
- um IP fixo na LAN, configurado no host e definido em `DELTA_LAN_HOST`; o
  certificado da CA interna é emitido para esse IP.

Não é necessário instalar Java, Node.js, PostgreSQL ou Maven.

## Arquivos do kit

```text
delta-finance-<versao>/
├── compose.yaml
├── .env.example
├── export-ca.sh
├── smoke-test.sh
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

## Fixar o IP do host

O `DELTA_LAN_HOST` é o endereço pelo qual a família acessa a aplicação e o
certificado da CA interna é emitido para ele. Se esse endereço mudar, o
certificado deixa de valer e o acesso quebra. Por isso, **fixe o IP do host
antes do primeiro `docker compose up`** e use o mesmo valor no `.env`.

Escolha um IP da mesma rede que o roteador, mas **fora da faixa DHCP** para não
conflitar com outros aparelhos. Exemplo: se o roteador entrega `192.168.1.100` a
`192.168.1.199`, use `192.168.1.50` ou `192.168.1.250`. Em caso de dúvida,
consulte a faixa DHCP no painel do roteador — é só para conferir, não é preciso
configurar nada lá.

### Linux

Identifique a interface e o gateway (o IP depois de `via`):

```bash
ip route
# default via 192.168.1.1 dev enp3s0 ...
```

Use os valores do exemplo abaixo como referência e ajuste para a sua rede
(substitua a interface, o gateway e o IP escolhido).

**Com NetworkManager** (Ubuntu Desktop, Fedora, Debian Desktop e similares):

```bash
nmcli con show                          # nome da conexão ativa (coluna NAME)
sudo nmcli con mod "Conexão com fio" \
  ipv4.method manual \
  ipv4.addresses 192.168.1.50/24 \
  ipv4.gateway 192.168.1.1 \
  ipv4.dns "192.168.1.1"
sudo nmcli con up "Conexão com fio"
```

**Com netplan** (Ubuntu Server e derivados): edite o arquivo em
`/etc/netplan/` (ex.: `50-cloud-init.yaml` ou `01-network-manager-all.yaml`):

```yaml
network:
  version: 2
  ethernets:
    enp3s0:
      dhcp4: false
      addresses:
        - 192.168.1.50/24
      routes:
        - to: default
          via: 192.168.1.1
      nameservers:
        addresses: [192.168.1.1]
```

Aplique com:

```bash
sudo netplan apply
```

Confirme que o IP fixo está ativo:

```bash
ip -4 addr show
```

### Windows

1. Abra **Configurações → Rede e Internet** e clique nas propriedades da conexão
   ativa (Ethernet ou Wi-Fi).
2. Anote **endereço IPv4**, **máscara de sub-rede**, **gateway** e **servidores
   DNS** atuais (você vai reaproveitá-los).
3. Em **Atribuição de IP**, clique em **Editar** e mude para **Manual → IPv4 →
   Ativado**.
4. Preencha:
   - **Endereço IP:** o IP fixo escolhido (ex.: `192.168.1.50`);
   - **Máscara de sub-rede:** a anotada (em casa, quase sempre `255.255.255.0`);
   - **Gateway:** o anotado (ex.: `192.168.1.1`);
   - **DNS preferencial:** o anotado (pode usar `192.168.1.1`).
5. Salve e confirme no PowerShell:

```powershell
ipconfig
```

Depois de fixar o IP, coloque **esse mesmo IP** em `DELTA_LAN_HOST` no `.env`.

## Instalação

### Linux

```bash
tar -xzf delta-finance-<versao>-amd64.tar.gz
cd delta-finance-<versao>
cp .env.example .env
```

Edite `.env` e troque pelo menos `DELTA_DB_PASSWORD` e `DELTA_LAN_HOST`. Para
gerar uma senha adequada:

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

Antes de subir, confirme que `DELTA_LAN_HOST` no `.env` é exatamente o IP fixo
configurado no host. O Caddy usa uma CA interna e emite o certificado para esse
IP na primeira subida.

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

Edite `.env` e troque `DELTA_DB_PASSWORD` e `DELTA_LAN_HOST`. Gere o key ring:

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

Abra em outro dispositivo da LAN:

```text
https://192.168.1.50
```

O endereço é o IP fixo configurado em `DELTA_LAN_HOST` — sem esquema e sem
porta. O HTTP na porta `80` redireciona para HTTPS. Se `LAN_HTTPS_PORT` não for
`443`, a URL incluirá a porta e o HSTS não deve ser usado.

No próprio host, use o mesmo IP (`https://192.168.1.50`). O navegador mostrará
aviso de certificado até a CA interna ser instalada.

### Confiar na CA interna

Exporte o certificado raiz:

```bash
bash export-ca.sh
```

No Windows sem Bash, o equivalente é:

```powershell
docker compose cp client:/data/caddy/pki/authorities/local/root.crt delta-finance-ca.crt
```

O script gera `delta-finance-ca.crt` e mostra o fingerprint SHA-256. Instale
esse arquivo como Autoridade Certificadora de confiança nos dispositivos:

- **Windows:** `certutil -addstore -f Root delta-finance-ca.crt`
- **macOS:** instale no Keychain e marque como confiável
- **iPhone/iPad:** instale o perfil e habilite a confiança total em Ajustes
- **Android:** instale como certificado de CA
- **Linux:** copie para `/usr/local/share/ca-certificates/` e execute
  `update-ca-certificates`
- **Firefox:** pode ser necessário importar separadamente nas preferências

A CA privada fica no volume `delta-finance-prod_caddy_data`. Faça backup desse
volume ou, no mínimo, guarde o `delta-finance-ca.crt`. Se a CA for perdida, será
preciso instalar uma nova CA em todos os dispositivos.

## Teste rápido (smoke test)

Com a stack em execução, valide o acesso HTTPS, o certificado e os headers de
segurança de uma só vez:

```bash
bash smoke-test.sh
```

O script confere, em sequência:

- redirect HTTP → HTTPS;
- container `client` saudável;
- SPA servida na raiz (com a cadeia TLS validada pela CA interna);
- HSTS, `X-Frame-Options`, `nosniff` e CSP;
- certificado emitido para o IP de `DELTA_LAN_HOST` (SAN);
- `/api/v1/auth/csrf` respondendo na mesma origem.

Saída verde (`SMOKE TEST OK`) significa que o acesso está íntegro; qualquer
`[FALHA]` indica o que revisar. Requer `curl`, `openssl` e `docker compose`. No
Windows, execute no Git Bash ou no WSL.

## Operação diária

```bash
docker compose ps
docker compose logs -f
docker compose stop
docker compose start
docker compose down
```

`docker compose down` não remove volumes. **Nunca use `docker compose down -v`**
em uma instalação com dados: isso apaga o banco e também a CA/certificados do
Caddy.

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
- certificado `delta-finance-ca.crt`; para preservar a mesma CA, faça backup do
  volume `delta-finance-prod_caddy_data`;
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

- O acesso é HTTPS com certificado emitido pela CA interna do Caddy.
- Instale a CA apenas nos dispositivos da família; remover a confiança depois é
  possível.
- Não configure port forwarding no roteador.
- Não use Wi-Fi público ou rede de convidados.
- Prefira WPA2/WPA3 e mantenha o host atualizado.
- Restrinja `secrets/`, `.env` e o volume `delta-finance-prod_caddy_data` ao
  usuário que administra a instalação.
- Faça backup do key ring e da CA separadamente do banco.
- HSTS exige HTTPS na porta padrão `443`; não use HSTS com `LAN_HTTPS_PORT`
  diferente de `443`.

## Migração de uma instalação HTTP

Se você já usa a stack de release com HTTP:

1. faça backup do banco, do `.env`, do key ring e do certificado da CA;
2. pare a stack com `docker compose down` (sem `-v`);
3. extraia o kit novo e preserve `DELTA_DB_NAME`, `DELTA_DB_USER`,
   `DELTA_DB_PASSWORD` e o key ring;
4. defina `DELTA_VERSION`, `DELTA_LAN_HOST`, `LAN_HTTP_PORT=80`,
   `LAN_HTTPS_PORT=443` e `DELTA_KEYRING_GID`;
5. execute `docker compose pull` e `docker compose up -d --remove-orphans`;
6. exporte a nova CA com `bash export-ca.sh` e instale-a nos dispositivos;
7. para levar os dados antigos, restaure o backup no novo banco usando o mesmo
   key ring.

O kit de release usa os volumes `delta-finance-prod_pgdata` e
`delta-finance-prod_caddy_data`, separados do ambiente de desenvolvimento. Ele
não reutiliza automaticamente o volume da stack antiga; sem restauração, o banco
novo começa vazio. O antigo endereço `http://IP:8080` deixa de existir; o novo
acesso é `https://<IP-FIXO>`, com redirect HTTP na porta `80`.

## Problemas comuns

| Sintoma                       | Verificação                                                      |
| ----------------------------- | ---------------------------------------------------------------- |
| `docker: command not found`   | Instale Docker Engine/Compose ou abra o Docker Desktop.          |
| Container `server` unhealthy  | `docker compose logs server`; verifique key ring e banco.        |
| Erro de permissão no key ring | Ajuste `DELTA_KEYRING_GID` no Linux ou a ACL no Windows.         |
| `pull access denied` no GHCR  | O pacote precisa ser público ou faça `docker login ghcr.io`.     |
| Porta 80/443 ocupada          | Ajuste `LAN_HTTP_PORT`/`LAN_HTTPS_PORT`; HSTS exige HTTPS em 443. |
| Aviso de certificado          | Execute `bash export-ca.sh` e confie na CA nos dispositivos.      |
| Host não responde              | Confira `DELTA_LAN_HOST` no `.env` e o IP fixo configurado no host. |
| Outro dispositivo não acessa  | Verifique firewall do host e se está na mesma LAN.               |
| Login não mantém a sessão     | Confirme `DELTA_DEPLOYMENT_MODE=tls` no Compose de release.      |
