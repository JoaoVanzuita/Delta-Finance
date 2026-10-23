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

## Cabeçalhos e implantação

- HTTPS obrigatório na rede, terminado pelo proxy Caddy.
- HSTS, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy` e CSP
  compatível com a SPA são revisados em T171.
- CORS restrito em desenvolvimento; same-origin em produção.
- Actuator exposto apenas na rede interna.

## Dependências e verificação

- Versões fixadas, lockfiles commitados e builds reproduzíveis.
- Secret scanning, dependency scanning e container scanning entram no CI em
  T172; OWASP ZAP baseline em T173.
- Gate de cobertura: 80% de linhas no domínio e 100% dos caminhos críticos de
  autorização e dinheiro com testes dedicados (T199).

## Reportar vulnerabilidades

Não abra issue pública com detalhes exploráveis. Contate o operador da
instalação por canal privado, informe versão/commit, impacto e passos de
reprodução, e não inclua dados reais de terceiros.

