# Checklist de review

Use este checklist em todo endpoint, tela ou migração novo. Ele complementa os
testes automatizados e o `verify`.

## Backend — endpoint

- [ ] O controller recebe `AuthenticatedUser` e o serviço usa
      `requireId()`; nenhum ID de usuário vem do corpo/query.
- [ ] Toda consulta financeira é escopada por `user_id`; não existe
      `findById`/`deleteById` sem usuário em repositório financeiro.
- [ ] Há teste de isolamento entre dois titulares (404 para o recurso alheio).
- [ ] Entrada vazia do provedor é tratada como inconclusiva quando a API não
      prova cobertura completa.
- [ ] Valores monetários usam `BigDecimal`/`Money`, com a moeda correta.
- [ ] Valores em moeda estrangeira não entram em totais BRL por acidente.
- [ ] GET não escreve, não gera fatura e não recalcula agregado.
- [ ] Erros retornam `ProblemDetail`, sem stack/SQL/segredo.
- [ ] Mutação crítica tem idempotência e auditoria quando aplicável.
- [ ] Contrato OpenAPI foi atualizado e `npm run openapi:check` passa.
- [ ] Comentário/Javadoc descreve a regra, não um ID de tarefa.

## Backend — dados

- [ ] Migração é versionada, idempotente quando possível e documentada.
- [ ] Campos sensíveis são cifrados com `CryptoContext` correto.
- [ ] Leitura cifrada usa `EncryptedValueReader`; corrupção tem política
      explícita.
- [ ] A migração funciona em banco vazio e em banco já existente.
- [ ] Exclusão/retensão preserva o histórico exigido.

## Frontend

- [ ] `queryKey` vem de `lib/query-keys` (sem array literal).
- [ ] Datas, números e moeda usam `lib/format`; não há `new Intl` fora dele.
- [ ] Formulários usam `normalizeMoneyInput`/`parseMoneyInput`.
- [ ] Valores monetários não passam por `Number` para cálculo financeiro.
- [ ] Todas as rotas protegidas têm tratamento de erro e loading.
- [ ] A tela tem estado vazio, erro e sucesso; não mostra zero artificial.
- [ ] `npm run typecheck`, `npm run lint`, `npm run test` e `npm run build`
      passam.

## Performance

- [ ] Novas leituras quentes têm orçamento de query coberto por
      `QueryBudgetIT` quando aplicável.
- [ ] Não foi introduzido N+1 em listas/dashboards.
- [ ] Paginação usa cursor e não carrega histórico inteiro em memória.

## Segurança e privacidade

- [ ] Nenhum segredo, token, hash ou valor financeiro vai para log/auditoria.
- [ ] Rate limit cobre novos pontos de autenticação/reauth.
- [ ] CORS/CSRF/cookies continuam coerentes com o deployment.
- [ ] Exportação não inclui identificadores externos nem credenciais.
