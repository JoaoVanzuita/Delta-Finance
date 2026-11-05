# Runbook: gerar e publicar a release

**Escopo**: preparar a versão, rodar os gates, publicar a tag e validar o
artefato no GitHub Releases. Cobre releases finais e pré-releases (`-rc` e
`-beta`).

> Execute na raiz do repositório, na branch `main`. O `prepare-release.sh` **não
> faz push**: o push de `main` e da tag é o que dispara o workflow `Release`.

## 1. Antes de começar

- Branch `main` e árvore limpa (`git status --porcelain` vazio).
- Docker em execução (os gates usam Docker, Trivy e bancos de teste).
- Acesso de push ao repositório e permissão para publicar no GHCR.
- Hooks instalados uma vez: `./deploy/scripts/install-git-hooks.sh`.
- Versão nova, ainda não usada. **Tags são imutáveis**: se uma tag falhar, crie a
  próxima (`-beta.2`), não reutilize a anterior.

Confirme o estado:

```bash
git switch main
git status --porcelain      # deve ficar vazio
git log --oneline -3
```

## 2. Preparar a versão

Exemplo para `0.2.0-beta.1`:

```bash
./deploy/scripts/prepare-release.sh 0.2.0-beta.1
```

O script:

- exige árvore limpa e branch `main`;
- alinha `VERSION`, `server/pom.xml`, `client/package.json` e
  `client/package-lock.json`;
- cria o commit `chore(release): prepare <ver>`;
- cria a tag anotada `v<ver>`.

Ele **não** faz push. Para só conferir sem alterar nada:

```bash
./deploy/scripts/prepare-release.sh 0.2.0-beta.1 --dry-run
```

## 3. Gates completos

Rode no commit de release (o bump de versão altera arquivos):

```bash
./deploy/scripts/verify-local.sh --full
```

Esperado: backend, frontend, E2E, Gitleaks, actionlint e Trivy (filesystem e
imagens) verdes. Se falhar, corrija e apague a tag local antes de repetir:

```bash
git tag -d v<ver>
```

## 4. Ensaio local do stack de release (recomendado)

Valida o runtime de release a partir da árvore atual — build local das imagens,
TLS com CA interna, migrações e smoke test — sem publicar nada:

```bash
./deploy/scripts/test-release-local.sh --stop-dev
```

Detalhes:

- usa portas altas (`18080`/`18443`) e projeto/volumes isolados
  (`delta-finance-local-test`), sem tocar produção;
- `--stop-dev` para o stack de desenvolvimento antes do ensaio (religue depois
  com `docker compose -f compose.yaml up -d`);
- `--keep` mantém o stack no ar para inspeção;
- `--no-build` reutiliza imagens já buildadas.

Saída esperada: 3 serviços `healthy`, migrações confirmadas em
`flyway_schema_history` e `SMOKE TEST OK` (10/10).

## 5. Publicar

```bash
git push --no-verify origin main v<ver>
```

- `--no-verify` evita repetir o hook de pré-push; os gates completos já rodaram
  no passo 3.
- O push da tag `v<ver>` dispara o workflow `Release`.
- Tags contendo `-rc.` ou `-beta` saem como **prerelease**; as demais, como
  release normal.

Acompanhe em **Actions → Release** (ou `gh run list --workflow=release.yml`):

```text
version → backend/frontend/e2e → images (GHCR) → release (kit + GitHub Release)
```

## 6. Validar o artefato publicado

1. No GitHub Release, confira os arquivos `.zip`/`.tar.gz`, os `.sha256` e o
   `release.json`.
2. Confira o checksum do pacote baixado:

```bash
sha256sum -c delta-finance-<ver>-amd64.tar.gz.sha256
```

3. Extraia e valide o kit:

```bash
tar -xzf delta-finance-<ver>-amd64.tar.gz
cd delta-finance-<ver>
sha256sum -c SHA256SUMS
docker compose config --quiet
```

4. Confira o `release.json` (commit, plataforma e digests das imagens).
5. Confirme que os pacotes do GHCR estão públicos (ou documente o login).

## 7. Antes de liberar aos testadores

Estes gates **não** impedem a publicação, mas devem ser cumpridos antes de
distribuir para usuários reais (ver `docs/release-checklist.md`):

- dispositivo real (celular/outro PC) com a CA interna instalada e login real
  (`__Host-delta_session`);
- ensaio em 80/443:
  `./deploy/scripts/test-release-local.sh --http-port 80 --https-port 443`;
- instalação real no Windows (Docker Desktop/WSL2, firewall, CA no trust store);
- Pluggy real (duas aplicações), backup/restore e update/rollback na instância
  beta;
- k6 (desejável).

## 8. Falhas e descarte de tag

- **Falhou antes do push:** apague a tag local e corrija.

```bash
git tag -d v<ver>
```

- **Falhou no workflow depois do push:** não reutilize a tag. Crie a próxima
  (`v<ver>-beta.2` ou outro sufixo) e repita o fluxo. Se for mesmo necessário
  remover a tag remota e a release, seja explícito:

```bash
gh release delete v<ver> --yes
git push origin :refs/tags/v<ver>
```

## 9. Referências

- `docs/release-checklist.md` — gates, versões fixadas e evidências.
- `deploy/scripts/prepare-release.sh` — preparação de versão e tag.
- `deploy/scripts/verify-local.sh` — gates locais.
- `deploy/scripts/test-release-local.sh` — ensaio local do stack de release.
- `deploy/release/README.md` — instalação do kit self-hosted.
- `docs/runbooks/update-rollback.md` — atualização e rollback no host.
