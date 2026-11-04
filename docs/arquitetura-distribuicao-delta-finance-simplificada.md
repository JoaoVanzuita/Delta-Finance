# Arquitetura simplificada de distribuição — Delta Finance

- **Status:** em implementação — TLS com CA interna na branch
  `feat/tls-producao`; acesso por IP fixo no host.
- **Modelo de uso:** cada pessoa mantém sua própria instalação e seus próprios
  dados.
- **Acesso:** computador host e outros dispositivos na mesma rede local (LAN).
- **Atualizações:** manuais, utilizando Docker Compose.
- **Protocolo padrão:** HTTPS com CA interna do Caddy; `lan-http` permanece como
  modo de compatibilidade.

---

## 1. Objetivo e decisões

A distribuição do Delta Finance deve ser simples de instalar, executar, atualizar e recuperar. Não há necessidade de criar um produto adicional para administrar a aplicação.

A arquitetura recomendada é baseada em:

- Docker Compose para executar e administrar os serviços;
- imagens pré-compiladas e versionadas, publicadas no GitHub Container Registry (GHCR);
- o container `client` como único ponto de entrada: ele já é um Caddy que serve a SPA, encaminha `/api/*` para o backend e aplica os headers de segurança;
- acesso HTTPS na LAN, com certificado emitido por uma CA interna do Caddy;
- atualização manual, alterando a versão no arquivo `.env` e executando comandos do Docker Compose;
- volumes persistentes para o PostgreSQL e persistência protegida do key ring;
- procedimento simples e documentado de backup e restauração;
- limites de log e de memória para o host ficar ligado por longos períodos.

Não fazem parte do escopo inicial: launcher em Go, atualizador próprio, rollback automatizado, certificados públicos/ACME, interface desktop, Kubernetes, registry local e inicialização automática específica de sistema operacional. O acesso é por IP fixo configurado no host; não há dependência de DNS, mDNS ou reserva no roteador.

## 2. Arquitetura recomendada

Cada instalação terá três serviços:

| Serviço    | Responsabilidade                                                        |
| ---------- | ----------------------------------------------------------------------- |
| PostgreSQL | Persistência dos dados financeiros.                                     |
| Backend    | API Spring Boot, autenticação, regras financeiras e integração Pluggy.  |
| Client     | SPA React + Caddy: serve os arquivos estáticos e faz proxy de `/api/*`. |

O Docker Compose cria duas redes internas:

- `app`: comunicação entre `client` e `server`;
- `data`: comunicação entre `server` e `db`.

Somente o `client` publica portas no host: `80` para redirect/health e `443` para HTTPS. O backend e o banco ficam acessíveis apenas pelas redes internas do Compose.

```text
Celular/PC na LAN
      │ https://<IP-FIXO>
      ▼
┌─────────────────────────────────────┐
│ client (Caddy)                      │  portas 80/443 no host
│ TLS interno + SPA + /api/*          │
└──────────────────┬──────────────────┘
                   │ rede app
                   ▼
┌─────────────────────────────────────┐
│ server (Spring Boot)                │
└──────────────────┬──────────────────┘
                   │ rede data
                   ▼
┌─────────────────────────────────────┐
│ db (PostgreSQL 18.6)                │
│ volume pgdata                       │
└─────────────────────────────────────┘
```

### 2.1 Por que não há um Caddy separado

O container `client` atual já contém um Caddy com:

- `deploy/client-tls.Caddyfile` para a release e `deploy/client.Caddyfile` para
  o desenvolvimento;
- `admin off`, `skip_install_trust` e `tls internal` na release;
- SPA servida de `/srv`;
- proxy de `/api/*` para `server:8080`;
- `X-Content-Type-Options`, `X-Frame-Options`, CSP, Referrer-Policy e demais headers;
- healthcheck em `/healthz` na porta interna `8080`.

Portanto, um quarto serviço `proxy` seria redundante. Ele adicionaria mais uma imagem, mais um Caddyfile, mais dois volumes e mais uma camada de rede sem benefício funcional.

Um Caddy de borda só volta a fazer sentido se um certificado público for usado no futuro. Nesse caso, ele entra como um overlay opcional na frente do `client`, sem alterar os três serviços principais.

## 3. Acesso pela rede local

O acesso será feito por HTTPS usando o IP fixo do host, configurado em
`DELTA_LAN_HOST`:

| Dispositivo                   | Endereço de exemplo      |
| ----------------------------- | ------------------------ |
| Computador host               | `https://192.168.1.50`   |
| Celular na mesma LAN          | `https://192.168.1.50`   |
| Outro computador na mesma LAN | `https://192.168.1.50`   |

O IP precisa ser **fixo no host** (configurado no sistema operacional, fora da
faixa DHCP do roteador) e repetido em `DELTA_LAN_HOST`. Como não há nome a
resolver, nenhum dispositivo depende de DNS, mDNS, arquivos `hosts` ou reserva
no roteador — basta digitar o IP. O certificado é emitido pela CA interna do
Caddy para esse IP.

### 3.1 TLS e confiança na CA

O Caddy da release termina TLS na porta interna `8443`, publicada como `443` no
host. A CA interna e os certificados ficam no volume
`delta-finance-prod_caddy_data`.

Cada dispositivo precisa confiar no certificado raiz exportado por
`export-ca.sh`. Sem isso, o navegador exibirá aviso de certificado.

O modo `lan-http` permanece disponível como compatibilidade, mas não é o padrão
da release. Ele não deve ser usado em redes não confiáveis.

### 3.2 Limitações e cuidados

- HSTS exige HTTPS na porta padrão `443`.
- Não configure port forwarding no roteador.
- Não use Wi-Fi público, rede compartilhada ou rede de convidados.
- Prefira WPA2/WPA3 e mantenha o host atualizado.
- Restrinja `secrets/`, `.env` e o volume `delta-finance-prod_caddy_data` ao
  usuário que administra a instalação.
- Faça backup da CA; se ela for perdida, todos os dispositivos precisam confiar
  em uma nova CA.

### 3.3 Modo de compatibilidade `lan-http`

O Delta Finance utiliza atualmente o cookie de sessão `__Host-delta_session`. Esse prefixo exige `Secure`, `Path=/` e ausência de `Domain`, sendo incompatível com o acesso HTTP pelo IP da LAN.

Será necessário oferecer um modo de implantação explícito, por exemplo:

```env
DELTA_DEPLOYMENT_MODE=lan-http
```

Nesse modo, a configuração de sessão deverá utilizar um cookie sem o prefixo `__Host-`, por exemplo `delta_session`, com as seguintes propriedades:

| Propriedade | Configuração no modo LAN HTTP |
| ----------- | ----------------------------- |
| Nome        | `delta_session`               |
| `Secure`    | `false`                       |
| `HttpOnly`  | `true`                        |
| `SameSite`  | `Lax`                         |
| `Path`      | `/`                           |
| `Domain`    | Não definido                  |
| CSRF        | Continua habilitado           |
| HSTS        | Desabilitado                  |

O cookie CSRF `XSRF-TOKEN` também deverá funcionar nesse modo, com `Secure=false`. A mudança deve preservar a proteção CSRF existente; não se deve desabilitá-la para facilitar o acesso HTTP.

O modo é implementado pela propriedade `delta.deployment.mode`, lida de `DELTA_DEPLOYMENT_MODE`. Em `lan-http`, o backend usa `delta_session`, força `Secure=false` e não envia HSTS. A implementação não depende de profile do Spring.

A variável `DELTA_COOKIE_SECURE` continua valendo apenas no modo `tls`; em `lan-http` ela é ignorada. O `SecurityConfig` condiciona o HSTS a `secureCookie`, então o header não é enviado no modo HTTP.

### 3.4 Trusted proxy hops

Com apenas o Caddy embutido no `client`, a configuração correta é:

```env
DELTA_TRUSTED_PROXY_HOPS=1
```

O `client` encaminha a requisição para o `server` e adiciona o IP real do cliente. Com `2`, o backend leria um hop além do necessário, afetando rate limit, auditoria e logs.

Se um Caddy de borda for adicionado no futuro, o valor volta para `2`.

## 4. Compose de release

A distribuição utilizará um único `compose.yaml`, com imagens pré-compiladas e tags explícitas de versão.

No repositório, para não quebrar o fluxo de desenvolvimento, pode-se manter o `compose.yaml` atual e criar um `compose.release.yaml`. O kit distribuído renomeia esse arquivo para `compose.yaml`; o usuário final vê apenas um Compose.

Exemplo estrutural:

```yaml
name: delta-finance

services:
  db:
    image: postgres:18.6-alpine@sha256:10a5c69a83035ab5239f5620d708520e8b28f5aa1f2b20143e6024532edadfb5
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${DELTA_DB_NAME}
      POSTGRES_USER: ${DELTA_DB_USER}
      POSTGRES_PASSWORD: ${DELTA_DB_PASSWORD}
      TZ: America/Sao_Paulo
      PGTZ: America/Sao_Paulo
    volumes:
      # PostgreSQL 18+ espera o volume em /var/lib/postgresql (dados em
      # subdiretório versionado), não em /var/lib/postgresql/data.
      - pgdata:/var/lib/postgresql
    networks:
      - data
    healthcheck:
      test:
        ["CMD-SHELL", 'pg_isready -U "$${POSTGRES_USER}" -d "$${POSTGRES_DB}"']
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    stop_grace_period: 30s
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "3"

  server:
    image: "ghcr.io/SEU_USUARIO/delta-finance-server:${DELTA_VERSION:?defina DELTA_VERSION}"
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: prod
      # Modo TLS; cookies __Host-, Secure e HSTS.
      DELTA_DEPLOYMENT_MODE: tls
      DELTA_DB_HOST: db
      DELTA_DB_PORT: 5432
      DELTA_DB_NAME: ${DELTA_DB_NAME}
      DELTA_DB_USER: ${DELTA_DB_USER}
      DELTA_DB_PASSWORD: ${DELTA_DB_PASSWORD}
      DELTA_DB_POOL_SIZE: ${DELTA_DB_POOL_SIZE:-10}
      DELTA_TRUSTED_PROXY_HOPS: 1
      DELTA_CRYPTO_KEY_FILE: /run/secrets/crypto-keys.json
      TZ: America/Sao_Paulo
    secrets:
      - source: crypto_keys
        target: crypto-keys.json
    group_add:
      - "${DELTA_KEYRING_GID:-1000}"
    depends_on:
      db:
        condition: service_healthy
    networks:
      - app
      - data
    healthcheck:
      test:
        [
          "CMD-SHELL",
          "curl -fsS http://127.0.0.1:8080/actuator/health/readiness || exit 1",
        ]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 60s
    stop_grace_period: 45s
    mem_limit: ${DELTA_SERVER_MEM_LIMIT:-1g}
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "3"

  client:
    image: "ghcr.io/SEU_USUARIO/delta-finance-client:${DELTA_VERSION:?defina DELTA_VERSION}"
    restart: unless-stopped
    environment:
      DELTA_LAN_HOST: ${DELTA_LAN_HOST:?defina DELTA_LAN_HOST}
    ports:
      - "${LAN_HTTP_PORT:-80}:8080"
      - "${LAN_HTTPS_PORT:-443}:8443"
    volumes:
      - caddy_data:/data
    depends_on:
      server:
        condition: service_healthy
    networks:
      - app
    healthcheck:
      test:
        ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 15s
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "3"

secrets:
  crypto_keys:
    file: ./secrets/crypto-keys.json

volumes:
  pgdata:
    # Nome explícito para produção nunca reutilizar o volume de desenvolvimento.
    name: delta-finance-prod_pgdata
  caddy_data:
    # CA interna e certificados; perder este volume exige reconfiar a CA.
    name: delta-finance-prod_caddy_data

networks:
  app:
  data:
```

O exemplo é estrutural, não um arquivo pronto para execução. Nomes de usuário no GHCR, variáveis e limites devem ser ajustados ao projeto real.

Regras importantes:

- manter `name: delta-finance` e os volumes explícitos
  `delta-finance-prod_pgdata` e `delta-finance-prod_caddy_data` para produção
  não depender do nome da pasta nem reutilizar dados de desenvolvimento;
- manter os nomes `db`, `server` e `client`; o Caddyfile embutido no `client` aponta para `server:8080`;
- manter `DELTA_DB_*` e `crypto-keys.json` para não quebrar scripts e configuração existentes;
- fixar imagens base por digest no Compose/Dockerfile e atualizar
  `docs/release-checklist.md` junto;
- não usar `pull_policy: never`, pois as atualizações vêm do GHCR;
- não publicar portas de `db` ou `server`;
- não usar `docker compose down -v` em atualizações.

### 4.1 Caddyfile

Não é necessário distribuir um `Caddyfile` separado. O
`deploy/client-tls.Caddyfile` é copiado para dentro da imagem de release do
`client` durante o build; o desenvolvimento usa `deploy/client.Caddyfile`.

O arquivo de release contém:

- TLS interno com `tls internal` na porta interna `8443`;
- redirect HTTP e healthcheck na porta interna `8080`;
- SPA com fallback para `index.html`;
- proxy de `/api/*` para `server:8080`;
- headers de segurança, incluindo HSTS para os assets e o HTML;
- cache dos assets com hash;
- `admin off` e `skip_install_trust`.

A CA e os certificados ficam no volume `caddy_data`. O host exporta a CA com
`export-ca.sh`; não edita o Caddyfile.

### 4.2 Regras de exposição

Somente o serviço `client` deverá declarar `ports`.

| Serviço    | Exposição no host                  |
| ---------- | ---------------------------------- |
| Client     | Portas 80 e 443, acessíveis pela LAN |
| Backend    | Somente rede Docker `app` e `data` |
| PostgreSQL | Somente rede Docker `data`         |

No Linux, as regras de firewall precisam ser compatíveis com a forma como o Docker publica portas. Não se deve presumir que uma regra padrão do UFW seja suficiente. A instalação deve ser testada a partir de outro dispositivo da LAN e também de uma rede externa.

No Windows, o firewall do Docker Desktop ou do Windows precisa permitir as portas 80 e 443 apenas em redes privadas.

### 4.3 Logs e memória

Como o host pode ficar ligado por longos períodos, todos os serviços devem ter limite de log:

```yaml
logging:
  driver: json-file
  options:
    max-size: "10m"
    max-file: "3"
```

O backend usa `-XX:MaxRAMPercentage=75`. Sem um limite de memória no container, a JVM pode considerar toda a RAM do host. Para um mini PC, defina um limite explícito, por exemplo `mem_limit: 1g`, ou ajuste `JAVA_TOOL_OPTIONS` com `-Xmx`.

## 5. Instalações independentes

Cada pessoa terá uma instalação independente do Delta Finance, com banco, chaves e configurações próprios.

Cada instalação deverá possuir:

- arquivo `.env` próprio;
- volume PostgreSQL próprio;
- key ring próprio;
- diretório de backup próprio;
- configuração Compose própria.

Não haverá compartilhamento de banco, credenciais ou dados financeiros entre as instalações.

## 6. Estrutura dos arquivos da instalação

```text
delta-finance/
├── compose.yaml
├── .env
├── .env.example
├── VERSION
├── README.md
├── scripts/
│   ├── install.sh
│   ├── install.ps1
│   ├── backup.sh
│   ├── backup.ps1
│   ├── restore.sh
│   └── restore.ps1
├── secrets/
│   └── crypto-keys.json
└── backups/
```

O `.env` real e o key ring não devem ser versionados em repositório público. O `.env.example` conterá apenas nomes de variáveis e valores ilustrativos, sem segredos reais.

Os scripts são opcionais, mas recomendados. Eles não são um launcher: apenas automatizam os passos mais arriscados (gerar chaves, fazer backup e restaurar) em cada sistema operacional. A primeira versão do kit pode operar sem eles, usando os comandos manuais do `README.md`; os scripts entram como conveniência.

### 6.1 Permissões

No Linux:

```bash
chmod 700 secrets backups
chmod 640 secrets/crypto-keys.json
chmod 600 .env
```

O container do backend roda como usuário `app` e recebe o grupo do arquivo via `DELTA_KEYRING_GID`. Descubra o grupo com:

```bash
stat -c %g secrets/crypto-keys.json
```

No Windows, restrinja a pasta `secrets` ao usuário que administra a instalação usando ACL do NTFS:

```powershell
icacls secrets /inheritance:r /grant:r "$env:USERNAME:(OI)(CI)F"
icacls .env /inheritance:r /grant:r "$env:USERNAME:F"
```

O Docker Desktop precisa conseguir ler o arquivo; teste o bind mount antes de considerar a instalação concluída.

### 6.2 Geração do key ring

Linux, com `openssl`:

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

Windows, com PowerShell:

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
```

Se `openssl` não estiver disponível, também é possível gerar as chaves com uma imagem Docker que contenha `openssl` e montar o resultado em `secrets/crypto-keys.json`.

O key ring nunca deve ser regenerado em uma atualização. Sem o arquivo original, os campos cifrados do banco são irrecuperáveis.

## 7. Publicação e distribuição das imagens

As imagens de backend e client (frontend) serão pré-compiladas e publicadas no GitHub Container Registry (GHCR).

Exemplos de tags:

```text
ghcr.io/SEU_USUARIO/delta-finance-server:0.1.0
ghcr.io/SEU_USUARIO/delta-finance-client:0.1.0
```

O arquivo `.env` controlará a versão utilizada:

```env
DELTA_VERSION=0.1.0
```

As imagens devem utilizar versões explícitas, evitando `latest`, para que cada instalação tenha uma versão identificável e reproduzível.

### 7.1 Visibilidade e autenticação

Pacotes publicados no GHCR nascem privados por padrão. Para a instalação ser simples:

- torne os pacotes públicos após o primeiro push; ou
- documente `docker login ghcr.io` com um PAT que tenha `read:packages`.

A primeira opção é recomendada para uso familiar. Se os pacotes forem privados, cada instalação precisará de autenticação no registry, o que adiciona fricção.

Nomes de imagem no GHCR precisam ser minúsculos. O workflow deve normalizar o owner do repositório para lowercase.

### 7.2 Versionamento e release

O release será disparado por uma tag Git `v*`:

1. `VERSION` na raiz contém a versão, por exemplo `0.1.0`;
2. `server/pom.xml` usa a mesma versão;
3. `client/package.json` usa a mesma versão;
4. a tag é `v0.1.0`;
5. as imagens são publicadas como `0.1.0` (sem o `v`);
6. o workflow falha se `VERSION`, `pom.xml`, `package.json` e a tag não coincidirem.

O workflow de release deve:

- rodar os gates de backend e frontend;
- construir apenas `linux/amd64`;
- publicar `delta-finance-server` e `delta-finance-client` no GHCR;
- usar `permissions: packages: write` e `GITHUB_TOKEN`;
- anexar ao GitHub Release um kit pequeno, em `.zip` e `.tar.gz`, com `compose.yaml`, `.env.example`, `VERSION`, `README.md`, `release.json` e scripts;
- manter as tags anteriores para permitir rollback.

As imagens não precisam ir dentro do kit. O usuário baixa o kit pequeno e o Docker baixa as imagens do GHCR. A imagem do PostgreSQL continua vindo do Docker Hub; se isso for um problema de rate limit, ela pode ser espelhada no GHCR no futuro.

## 8. Atualização manual

Não será implementado um atualizador próprio. A atualização será feita manualmente pelos comandos do Docker Compose.

Antes de atualizar:

1. criar um backup do banco;
2. copiar o key ring e o `.env` para local seguro;
3. registrar a versão atual e a versão do Flyway;
4. confirmar que não há sincronização em andamento;
5. confirmar espaço em disco suficiente.

Procedimento:

1. Alterar `DELTA_VERSION` no `.env` para a nova versão.
2. Baixar as novas imagens.
3. Recriar os serviços.
4. Verificar o estado dos containers e os logs.

Exemplo:

```bash
# 1. Fazer backup antes deste procedimento.

# 2. Alterar DELTA_VERSION no arquivo .env.

# 3. Baixar as imagens da versão configurada.
docker compose pull

# 4. Atualizar os serviços e remover containers órfãos.
docker compose up -d --remove-orphans

# 5. Verificar os containers.
docker compose ps

# 6. Consultar logs recentes.
docker compose logs --tail=100

# Consultar as imagens associadas aos serviços.
docker compose images
```

Não utilizar `docker compose down -v` durante atualizações, pois a opção `-v` remove volumes e pode apagar os dados persistentes.

### 8.1 Rollback manual

Se a versão anterior continuar compatível com o schema atual do banco, pode-se alterar `DELTA_VERSION` para a versão anterior e executar novamente:

```bash
docker compose pull
docker compose up -d --remove-orphans
```

Se a imagem anterior ainda estiver local, o `pull` pode ser omitido. Manter as imagens antigas no host ajuda o rollback.

Isso não é seguro para migrações incompatíveis ou destrutivas. Nesses casos, será necessário restaurar o backup criado antes da atualização, incluindo banco, key ring e configurações associadas.

A documentação de cada release deverá informar mudanças de banco que afetem a possibilidade de rollback.

### 8.2 Migração da instalação atual

Se já existir uma instalação com o `compose.yaml` atual (com `proxy` e TLS), a migração para o modelo simplificado deve:

1. fazer backup com a stack atual;
2. copiar `secrets/crypto-keys.json` e `.env`;
3. parar a stack com `docker compose down` (sem `-v`);
4. extrair o kit novo e manter o mesmo `DELTA_DB_NAME`, `DELTA_DB_USER`, `DELTA_DB_PASSWORD` e o mesmo key ring;
5. definir `DELTA_VERSION`, `DELTA_LAN_HOST` (o IP fixo do host),
   `LAN_HTTP_PORT=80`, `LAN_HTTPS_PORT=443` e `DELTA_KEYRING_GID` no novo
   `.env`;
6. executar `docker compose pull` e `docker compose up -d --remove-orphans`;
7. exportar a CA com `bash export-ca.sh` e confiá-la nos dispositivos;
8. validar login, dashboard, sincronização e health checks.

O kit de release usa `name: delta-finance` e o volume explícito
`delta-finance-prod_pgdata`. Ele não reutiliza automaticamente o volume de
desenvolvimento `delta-finance-dev_pgdata` nem o volume legado
`delta-finance_pgdata`. Para migrar dados, restaure o backup no novo volume com
o mesmo key ring.

O antigo serviço `proxy` deixa de existir. A CA e os certificados passam a ficar
no volume `delta-finance-prod_caddy_data`. O volume legado
`delta-finance_pgdata` também pode ser mantido
como backup até a validação da migração.

O novo acesso é `https://<IP-FIXO>`; HTTP na porta `80` redireciona para
HTTPS. Se o navegador tiver HSTS em cache para um hostname antigo, limpe o HSTS
antes de testar.

## 9. Backup e restauração

Backup é uma proteção essencial, mesmo sem um gerenciador próprio.

O conjunto mínimo para recuperação deve conter:

| Elemento                                  | Necessidade               |
| ----------------------------------------- | ------------------------- |
| Dump do PostgreSQL                        | Obrigatório               |
| Key ring                                  | Obrigatório               |
| `.env` e configurações necessárias        | Obrigatório               |
| Versão da aplicação e metadados do schema | Obrigatório como metadado |
| `compose.yaml`                            | Recomendado               |

O backup do key ring deve ser protegido, pois perder as chaves pode tornar os dados financeiros criptografados irrecuperáveis. Os arquivos de backup devem ficar em local protegido e, preferencialmente, também em uma mídia ou destino externo ao computador que hospeda a instância.

### 9.1 Backup no Linux

O script `backup.sh` atual pode ser usado como base, mas ele exige `age` por padrão. No kit simplificado, o backup padrão deve funcionar sem dependências externas; `age` fica como opção para cópias fora do host. Para um backup manual:

```bash
mkdir -p backups
docker compose exec -T db sh -c \
  'pg_dump --format=custom --no-owner --no-privileges -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /tmp/delta.dump'
docker compose cp db:/tmp/delta.dump "backups/delta-$(date +%Y%m%dT%H%M%S).dump"
docker compose exec -T db rm -f /tmp/delta.dump
```

Esse fluxo também funciona no Windows e evita redirecionamento binário pelo shell.

### 9.2 Backup no Windows

Não redirecione a saída binária do `pg_dump` com `>` no PowerShell; isso pode corromper o dump. Use `pg_dump -f` dentro do container e copie o arquivo com `docker compose cp`, que preserva bytes:

```powershell
New-Item -ItemType Directory -Force backups | Out-Null
docker compose exec -T db sh -c 'pg_dump --format=custom --no-owner --no-privileges -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /tmp/delta.dump'
docker compose cp db:/tmp/delta.dump backups/delta.dump
docker compose exec -T db rm -f /tmp/delta.dump
```

O mesmo padrão com `docker compose cp` funciona no Linux e evita depender de `age` ou Bash. Se o kit incluir `backup.ps1`, ele deve seguir esse fluxo.

### 9.3 Restauração

Restaurar substitui o conteúdo atual do banco. O procedimento deve:

1. parar o serviço `server`;
2. verificar o checksum do dump, se existir;
3. executar `pg_restore --clean --if-exists --no-owner --no-privileges`;
4. validar o histórico do Flyway;
5. reiniciar o `server`;
6. verificar `/actuator/health/readiness`;
7. confirmar que o key ring é o mesmo do momento do backup.

O script `restore.sh` atual já segue essa sequência no Linux. A versão PowerShell deve fazer o equivalente no Windows.

Na primeira versão, o procedimento pode ser documentado e executado manualmente. O requisito essencial é testar a restauração em uma instalação separada antes de considerar o processo confiável.

Criptografia do backup com `age` é opcional no Linux. Se for usada, a chave privada do `age` precisa ser guardada separadamente; sem ela, o backup também é irrecuperável.

## 10. Inicialização e operação diária

Na primeira versão, não é necessário implementar inicialização automática específica para Windows ou Linux.

O uso manual pode ser:

```bash
# Iniciar

docker compose up -d

# Verificar estado

docker compose ps

# Acompanhar logs

docker compose logs -f

# Parar sem remover volumes

docker compose down
```

No Linux, o Docker Engine inicia no boot e a política `restart: unless-stopped` faz o stack voltar sozinho. No Windows, o Docker Desktop precisa estar em execução; `restart: unless-stopped` não inicia o Docker Desktop. O usuário deve configurar o Docker Desktop para iniciar no login ou aceitar que o sistema só sobe após abrir o Docker.

Após validar a experiência, poderá ser adicionada uma configuração simples de inicialização no boot, se isso for realmente necessário para os usuários.

## 11. Etapas de implementação

| Fase | Entrega                                                                             |
| ---- | ----------------------------------------------------------------------------------- |
| 1    | Compose de release com três serviços, redes internas, limites de log e memória      |
| 2    | TLS no `client` com CA interna, modo `tls`, cookie `Secure` e HSTS                 |
| 3    | Gerar key ring e backup/restauração para Linux e Windows                            |
| 4    | Publicar imagens versionadas no GHCR e criar o workflow de release                  |
| 5    | Alinhar versões (`VERSION`, `pom.xml`, `package.json`) e criar a tag                |
| 6    | Criar README autocontido com instalação, atualização, rollback e migração           |
| 7    | Validar instalação em Windows e Linux e acesso a partir de outro dispositivo da LAN |

## 12. Testes antes de distribuir

| Cenário                         | Resultado esperado                                                                         |
| ------------------------------- | ------------------------------------------------------------------------------------------ |
| Instalação limpa em Linux       | Stack inicia com os passos documentados                                                    |
| Instalação limpa em Windows     | Docker Desktop disponível e stack inicia corretamente                                      |
| Geração do key ring em Linux    | Arquivo criado com permissões restritas e backend fica saudável                            |
| Geração do key ring em Windows  | Arquivo legível pelo container e backend fica saudável                                     |
| Acesso pelo host                | Aplicação acessível em `https://<IP-FIXO>`                                                 |
| Acesso pelo celular na LAN      | Aplicação acessível com a CA interna confiada                                              |
| Login e logout por HTTPS        | Sessão `__Host-` e CSRF funcionam no modo `tls`                                            |
| Reinicialização do PostgreSQL   | Banco recupera-se e backend volta a ficar saudável                                         |
| Atualização manual              | Dados e configurações permanecem íntegros                                                  |
| Falha após atualização          | É possível retornar à versão anterior quando o schema for compatível ou restaurar o backup |
| Backup e restauração no Windows | Dump não é corrompido e restauração funciona                                               |
| Restauração em outra instalação | Dados continuam descriptografáveis com o key ring recuperado                               |
| Migração da stack atual         | Volume, `.env` e key ring são preservados                                                  |
| Acesso de rede externa          | Serviço não fica publicamente acessível                                                    |
| Disco sem espaço                | Operação falha com segurança, sem corromper os dados existentes                            |
| Rotação de logs                 | Logs antigos são descartados conforme `max-size`/`max-file`                                |

## 13. Escopo final da primeira versão

### Incluído

- Docker Compose com PostgreSQL, backend e client (SPA + Caddy);
- imagens pré-compiladas e versionadas no GHCR;
- acesso HTTPS na LAN com CA interna;
- volumes persistentes;
- key ring protegido;
- modo de autenticação compatível com HTTP na LAN, mantendo CSRF;
- backup e procedimento de restauração para Linux e Windows;
- limites de log e memória;
- README autocontido com instalação, inicialização, atualização, rollback e migração;
- scripts pequenos de instalação, backup e restauração;
- workflow de release disparado por tag.

### Fora do escopo

- launcher Go;
- atualizador próprio;
- rollback automatizado;
- certificados internos e DNS personalizado;
- interface desktop;
- Kubernetes;
- registry local;
- bundle de imagens;
- Caddy separado;
- inicialização automática específica do sistema operacional na primeira versão.

## 14. Referências técnicas

- Docker Compose — `pull`: https://docs.docker.com/reference/cli/docker/compose/pull/
- Docker Compose — `up`: https://docs.docker.com/reference/cli/docker/compose/up/
- Docker — Packet filtering and firewalls: https://docs.docker.com/engine/network/packet-filtering-firewalls/
- Docker — Logging drivers: https://docs.docker.com/engine/logging/configure/
- Docker — Volumes: https://docs.docker.com/engine/storage/volumes/
- Caddy — Caddyfile concepts: https://caddyserver.com/docs/caddyfile/concepts
- GitHub Container Registry: https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry
- Spring Boot — Externalized configuration: https://docs.spring.io/spring-boot/reference/features/external-config.html
- MDN — Cookies and cookie prefixes: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie

---

**Recomendação final:** adotar Docker Compose + imagens prontas no GHCR + três serviços, usando o Caddy já embutido no `client` como único proxy. Acesso pelo IP da LAN em HTTP, atualização manual e backup/restauração testados. Não construir launcher, bundle de imagens ou atualizador próprio enquanto os comandos nativos do Docker Compose atenderem ao uso pretendido.
