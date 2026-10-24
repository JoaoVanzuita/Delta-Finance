# Segurança e Privacidade

## Connect Token

O Connect Token não faz parte do MVP. O sistema não deve solicitar, chamar
`/connect_token` nem persistir esse token no perfil pessoal. O fluxo pessoal usa
apenas Client ID, Client Secret e itemIds fornecidos pelo usuário, com API Key
temporária obtida no servidor por `POST /auth`.

## Limites da proteção

Campos financeiros sensíveis são protegidos com criptografia autenticada e
chaves externas ao PostgreSQL e à imagem Docker. Um dump isolado do banco, sem
as chaves, não deve revelar os campos protegidos; isso não equivale a proteção
contra um administrador que controle simultaneamente o backend e as chaves.

Detalhes de classificação de campos, rotação, backup e recuperação estão em
`specs/001-finance/data-model.md` e
`specs/001-finance/research.md`.

## Modelo de ameaça e fronteiras

- O isolamento entre usuários é lógico e verificado no servidor: `user_id` vem
  sempre da sessão autenticada, nunca do payload, da URL ou de um filtro.
- O backend descriptografa dados para sincronizar e calcular relatórios. A
  promessa é proteger contra vazamento do banco/dump sem as chaves, não contra um
  administrador que controle simultaneamente o backend e o key ring.
- Não há conhecimento zero nem criptografia ponta a ponta no navegador.
- Nenhum dado de outro usuário é retornado, mesmo com IDs manipulados
  (coberto por testes com dois usuários e duas integrações independentes).

## Autenticação e sessão

- Senhas armazenadas apenas como Argon2id (m=19 MiB, t=2, p=1, salt 16 B, hash
  32 B), calibrado para 100–250 ms no hardware de referência.
- Política: 12–128 caracteres, sem composição obrigatória; rejeita a senha igual
  ou contendo o nome de usuário normalizado e senhas da lista local versionada
  de 10.000+ entradas comuns, sem chamada externa.
- Sessão: cookie `HttpOnly`, `Secure`, `SameSite=Lax`, 30 minutos de inatividade
  e 12 horas absolutas; CSRF por cookie + header; logout invalida a sessão no
  servidor.
- Login limitado a 5 falhas por 15 minutos por usuário+IP, com bloqueio de 15
  minutos e contador zerado em sucesso.
- Mensagens de erro de autenticação são genéricas e não revelam se o usuário
  existe.

## Segredos e criptografia

- Client ID/Secret da Pluggy são cifrados com AES-256-GCM; o Client Secret é
  write-only (nunca retorna à interface).
- A API Key da Pluggy é temporária (2 h), obtida por `POST /auth` no servidor,
  mantida em cache por integração (TTL 110 min) e nunca devolvida ao navegador,
  registrada em log ou persistida por padrão.
- O key ring fica fora do banco e da imagem, versionado e com rotação
  documentada. O texto cifrado carrega a versão da chave.
- Fixtures, exemplos, Compose e CI não contêm segredos reais.

## Logs, erros e auditoria

- Logs JSON estruturados e sanitizados: sem bodies, sem valores financeiros, sem
  descrições, sem Client Secret, sem API Key.
- Erros usam `ProblemDetail` (RFC 9457) com código estável; respostas brutas do
  provedor não são repassadas ao cliente.
- `audit_event` registra eventos de autenticação, senha, exportação, desconexão,
  exclusão e sincronização sem conteúdo financeiro desnecessário.
- `Idempotency-Key` evita duplicação de mutações; respostas são limitadas a 24 h
  de retenção.

## Exportação de dados

- `GET /api/v1/me/export` gera, em streaming, um ZIP com JSON Lines e CSV por
  conjunto de dados ativo do titular: perfil, contas, cartões, categorias,
  transações, faturas, itens, pagamentos, correções, reconciliações, integrações
  (somente metadados), conexões (sem itemId/HMAC), execuções de sincronização,
  jobs, webhooks e auditoria sanitizada.
- O arquivo `manifest.json` documenta formato (`delta-finance-export`, versão 1),
  data, fuso `America/Sao_Paulo`, moeda `BRL` e contagem por arquivo. Os nomes
  `*.jsonl` e `*.csv` são estáveis para uso portátil.
- Valores financeiros, descrições, apelidos e nomes de categoria são decifrados
  para o titular. Nunca são exportados: Client Secret, Client ID, API Key,
  `password_hash`, tokens, chaves, HMACs, itens externos brutos e payloads de
  webhook.
- Cada exportação registra `audit_event` do tipo `EXPORT_REQUESTED` com formato e
  versão, sem valores, descrições ou segredos. A consulta é sempre escopada por
  `user_id` da sessão; não há parâmetro para escolher outro titular.

## Retenção e exclusão

| Dado | Prazo/regra |
|---|---|
| `login_attempt` | 30 dias |
| `sync_run` | 90 dias |
| `webhook_event` | 90 dias |
| `audit_event` | 1 ano |
| `idempotency_key` | 24 h após `expires_at` |
| Backups criptografados | 30 dias |
| Dados ativos após exclusão | removidos imediatamente |
| Tombstone de identidade | mantido em `app_user` com status `DELETED`; senha substituída por hash inutilizável |
| Eventos de auditoria da exclusão | mantidos por 1 ano |

O job diário `RetentionJob` executa o expurgo e registra apenas contadores
sanitizados. A desconexão padrão remove credenciais, interrompe jobs e preserva
contas, transações e histórico; `deleteImportedData=true` apaga somente os dados
de origem importada, mantendo contas manuais, lançamentos nessas contas e categorias.

A exclusão de conta exige senha + a palavra `EXCLUIR`, revoga todas as sessões,
cancela jobs, apaga credenciais e dados ativos e mantém o tombstone. Cópias de
backup expiram em até 30 dias; o usuário é informado disso antes de confirmar.
Outros usuários da instalação não são afetados. O procedimento operacional está
em `docs/runbooks/deletion.md`.

## Cabeçalhos, cookies e CORS

- HTTPS obrigatório na rede, terminado pelo proxy Caddy; HSTS por 1 ano com
  `includeSubDomains`.
- CSP restritiva na SPA e na API (`default-src 'self'`, `frame-ancestors
  'none'`, `object-src 'none'`), `X-Frame-Options: DENY`,
  `X-Content-Type-Options: nosniff`, `Referrer-Policy` restritiva,
  `Permissions-Policy` sem câmera/microfone/geolocalização/pagamento,
  `Cross-Origin-Opener-Policy` e `Cross-Origin-Resource-Policy` same-origin.
- CORS desabilitado no backend: a SPA é servida na mesma origem pelo Caddy. Não
  existe `Access-Control-Allow-Origin` para outra origem; desenvolvimento local
  usa proxy do Vite, não CORS.
- Cookie de sessão `__Host-delta_session` (`HttpOnly`, `Secure`, `SameSite=Lax`,
  path `/`) e CSRF por cookie `XSRF-TOKEN` + header `X-XSRF-TOKEN`. Mutações sem
  token válido retornam `403`.
- Rate limits: login com 5 falhas/15 min por usuário+IP e bloqueio de 15 min;
  chamadas à Pluggy usam o limitador do provedor (`RateLimit-Reset`/
  `Retry-After`). Não há limite genérico por IP na API além desses controles.
- Actuator exposto apenas na rede interna do Compose; Swagger desabilitado em
  produção.

## Classificação de campos pesquisáveis (FR-100)

O PostgreSQL não recebe texto claro dos campos financeiros sensíveis. A tabela
completa está em `specs/001-finance/data-model.md` e `research.md`; resumo:

| Classe | Campos | Pesquisa/índice |
|---|---|---|
| Texto claro | `user_id`, IDs locais, datas, tipo, status, moeda, origem, ciclo, timestamps | índices B-tree e filtros SQL |
| HMAC | `username_hash`, `ip_hash`, `dedup_key`, `fingerprint`, hashes de IDs externos e de nome de categoria | igualdade/unicidade; não reversível |
| Cifrado | valores, saldos, descrições, apelidos, instituição, nomes de categoria, totais de fatura, juros, pagamentos, observações, Client ID/Secret, segredo de webhook | decifrado em memória no backend; sem `LIKE`, soma ou ordenação no banco |
| Hash de senha | `password_hash` | somente verificação Argon2id |
| Metadado de auditoria | evento, resultado, código, contadores | sanitizado, retenção de 1 ano |

Consequências: a busca textual livre não faz parte do MVP; filtros por valor são
aplicados após a decifragem; relatórios percorrem candidatos filtrados por
metadados. A promessa é proteger o dump do banco sem o key ring, não contra um
administrador que controle backend e chaves; não há conhecimento zero.

## Dependências e verificação

- Versões fixadas, lockfiles commitados e builds reproduzíveis.
- Secret scanning (gitleaks), dependency scanning (Trivy `fs`) e container
  scanning (Trivy `image`) executam em `.github/workflows/security.yml`.
- OWASP ZAP baseline é executado por `deploy/scripts/zap-baseline.sh`; os
  relatórios ficam em `docs/security/zap/` e o resumo é registrado abaixo.
- Gate de cobertura: 80% de linhas no domínio e 100% dos caminhos críticos de
  autorização e dinheiro (`Money`, `OwnershipGuard`, `AuthenticatedUser`) em
  `server/pom.xml`.

### Resultado do ZAP baseline

Execução de 2026-10-24 contra a imagem de produção do cliente servida pelo Caddy
(`delta-finance/client:zap`, porta local 4174), sem backend:

- **FAIL: 0**; 5 avisos (WARN): `style-src 'unsafe-inline'` exigido por estilos
  inline de gráficos/React, comentários de biblioteca no bundle, conteúdo
  dinâmico não cacheável, detecção de SPA e versão do próprio ZAP.
- Headers verificados: CSP, `X-Frame-Options`, `nosniff`, `Referrer-Policy`,
  `Permissions-Policy`, COOP, CORP e COEP.
- Relatório: `docs/security/zap/zap-127.0.0.1_4174-20261024T003619.*`.

O `style-src 'unsafe-inline'` é uma decisão consciente para não quebrar os
gráficos; scripts continuam sem `unsafe-inline`/`unsafe-eval`. Rotas
autenticadas não fazem parte do baseline passivo e são cobertas por
`SecurityHeadersIT`, `IsolationIT` e `FinalIsolationIT`.

## Recuperação assistida de senha

Não há e-mail transacional. O operador executa
`deploy/scripts/reset-password.sh` apenas após verificar a identidade por canal
independente. O mecanismo exige `DELTA_OPERATOR_RESET_TOKEN`, gera senha
temporária aleatória, revoga todas as sessões, limpa bloqueios de login e grava
`PASSWORD_RESET_ASSISTED` sem a senha. Procedimento e walkthrough em
`docs/runbooks/password-reset.md`.


## Reportar vulnerabilidades

Não abra issue pública com detalhes exploráveis. Contate o operador da
instalação por canal privado, informe versão/commit, impacto e passos de
reprodução, e não inclua dados reais de terceiros.

