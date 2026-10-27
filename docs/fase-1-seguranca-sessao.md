# Fase 1 — Segurança, Sessão e Autenticação

**Status:** Concluída em 2026-10-25  
**Itens:** `C01`, `C02`, `C03`, `C04`, `C25`, `C26`  
**Plano:** [`plano-implementacao-correcoes.md`](plano-implementacao-correcoes.md)  
**Baseline anterior:** [`fase-0-baseline-e-inventario.md`](fase-0-baseline-e-inventario.md)

> Esta fase fecha os itens de segurança da Fase 1. As decisões foram tomadas
> pelo caminho mais simples compatível com a implantação de referência
> (instância única, não distribuída publicamente).

## 1. Decisões aplicadas

- [ADR-0003](adr/0003-rate-limit-cadastro.md): rate limit de cadastro em
  **memória**, 5 tentativas por hora por IP.
- `V017__login_block.sql` criada e aplicada; `V016`, `V018` e `V019` continuam
  reservadas.
- O reset de senha revoga sessões por **JDBC**, sem depender de contexto web.
- Nenhuma dependência nova e nenhuma alteração de contrato OpenAPI.

## 2. O que mudou

### C01 — Expiração de sessão no cliente

`client/src/features/auth/hooks/useSession.ts` agora:

- expira após **30 minutos sem atividade** ou **12 horas** desde o login;
- revalida a sessão em `focus`, `visibilitychange` e `pageshow`;
- limpa todo o cache financeiro em 401, logout, timeout e troca de usuário;
- mantém a consulta de sessão e remove apenas as demais consultas do cache;
- não dispara expiração para visitante anônimo.

Teste: `client/src/features/auth/__tests__/SessionExpiry.test.tsx` (5 casos) e
`AuthFlow.test.tsx` preservado.

### C02 — Bloqueio de login persistido

- `V017__login_block.sql` adiciona `login_attempt.blocked_until`.
- `LoginAttemptService` grava o fim do bloqueio na falha que atinge o limite.
- O bloqueio sobrevive ao fim da janela deslizante e a reinícios.
- Ao expirar, as falhas antigas são removidas para não bloquear novamente.
- IP e usuário continuam apenas como HMAC; nenhum valor bruto é logado.

Testes em `AuthSessionIT`: quinta falha grava o bloqueio, bloqueio sobrevive à
janela, bloqueio expirado libera o login e outro IP não herda o bloqueio.

**Limitação aceita:** o `trusted_proxies` do Caddy não foi alterado. O servidor
não é exposto diretamente; o IP encaminhado é usado apenas para o limite. Se a
topologia mudar, essa decisão precisa ser revista.

### C03 — Rate limit de cadastro

- `RegistrationRateLimiter` em memória, 5 tentativas por hora por IP.
- A tentativa é contada no controller, **antes** do Argon2id.
- Excesso retorna `429` com `Retry-After`.
- IP é indexado apenas por HMAC.
- Configuração: `DELTA_REGISTRATION_MAX_ATTEMPTS` e `DELTA_REGISTRATION_WINDOW`.

Testes: `RegistrationRateLimiterTest` (rajada, janela e IP distinto) e
`RegistrationRateLimitIT` (integração com limite reduzido).

### C04 — Idempotência HTTP

> **Nota de 2026-10-26 (S-07):** o filtro foi restrito a transações, itens de
> fatura e pagamentos; as demais mutações ignoram `Idempotency-Key`. O restante
> desta seção descreve o comportamento original.

- Respostas **2xx e 4xx** passam a ser persistidas e reproduzidas.
- Respostas **5xx** liberam a reserva para retry.
- Conflito por corpo/método/caminho e reserva em andamento continuam em `409`.
- O replay de erro usa `application/problem+json`.
- O filtro permanece registrado após o `CsrfFilter`.

Testes em `IdempotencyIT`: replay 2xx, replay 4xx, corpo divergente, isolamento
por usuário, reserva em andamento, TTL, chave inválida e retry com nova chave.

### C25 — Reset de senha executável

- `AuthService` e `AssistedPasswordResetService` não dependem mais de
  `FindByIndexNameSessionRepository`.
- Sessões são contadas e revogadas diretamente em `spring_session`.
- A propriedade principal passou a ser `delta.operator.reset.token`, alimentada
  por `DELTA_OPERATOR_RESET_TOKEN`; o nome anterior continua aceito.
- O runner segue funcionando com `--spring.main.web-application-type=none`.

Testes: `PasswordResetIT` e o novo `PasswordResetNonWebIT`, que sobe o contexto
sem servlet, cria uma sessão real e confirma a revogação por JDBC.

### C26 — Auditoria de autorização e CSRF

- Novos eventos: `AUTHORIZATION_DENIED` e `CSRF_REJECTED`.
- `JsonAccessDeniedHandler` audita CSRF inválido e acesso negado.
- `OwnershipGuard` audita tentativa de acesso cruzado.
- Metadados contêm apenas método, caminho sanitizado e motivo; nenhum corpo,
  valor financeiro ou segredo.
- A resposta ao cliente continua `403`/`404` sem revelar existência.

Testes: `OwnershipGuardTest` e `SecurityHeadersIT` (CSRF autenticado e anônimo).

## 3. Validação

| Suite | Resultado |
|---|---|
| Backend `./mvnw verify` | **153 testes**, 0 falhas |
| JaCoCo / Spotless / Checkstyle | Verdes |
| Frontend `npm test` | **71 testes / 18 arquivos** |
| `typecheck`, `lint`, `format:check`, `build` | Verdes |
| `MigrationIT` | Verde, incluindo `V017` |

## 4. Limitações conhecidas

- Rate limit de cadastro é por instância; reiniciar zera a janela. Para escalar
  horizontalmente, migrar para PostgreSQL ou Redis (ADR-0003).
- O timer absoluto do cliente reinicia ao recarregar a página; o servidor continua
  aplicando o limite real de 12 horas.
- O script `reset-password.sh` não é executado dentro do Docker na suíte; o
  caminho de contexto não web é coberto por `PasswordResetNonWebIT` e o
  walkthrough permanece no runbook.
- Replay de 4xx exige uma nova `Idempotency-Key` para corrigir o corpo; isso é
  intencional e evita reutilizar uma chave com conteúdo diferente.

## 5. Próxima fase

Fase 2 — integridade da importação e correções:

- `C05` fingerprint apenas na reconciliação de `itemId`;
- `C06` dados ausentes e tipo `UNKNOWN` (cria `V018`);
- `C07` reembolsos determinísticos (ADR-0001 aceita: sem vínculo não reduz despesas);
- `C08` correção de transferência em duas pernas.

## 6. Referências

- [`plano-implementacao-correcoes.md`](plano-implementacao-correcoes.md)
- [`adr/0003-rate-limit-cadastro.md`](adr/0003-rate-limit-cadastro.md)
- [`security.md`](security.md)
- [`operations.md`](operations.md)
- [`runbooks/password-reset.md`](runbooks/password-reset.md)
