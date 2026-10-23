import { Check } from 'lucide-react'

/**
 * Página inicial provisória da fase de fundação.
 *
 * Some quando a autenticação (T044/T045) e o dashboard (T097) entrarem. Não
 * exibe dados, formulários ou links de funcionalidades ainda não implementadas.
 */
export function HomePage() {
  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-8">
      <header className="space-y-3">
        <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">
          Fundação do projeto
        </p>
        <h1 className="text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
          Delta Finance
        </h1>
        <p className="max-w-xl text-base leading-relaxed text-ink-muted">
          Controle financeiro privado para a família, com dados isolados por usuário. Cada pessoa
          configura a própria conexão bancária e vê apenas o próprio espaço.
        </p>
      </header>

      <section
        aria-labelledby="status-heading"
        className="rounded-card border border-line bg-surface p-5 sm:p-6"
      >
        <h2 id="status-heading" className="text-lg font-semibold text-ink">
          Aplicação em construção
        </h2>
        <p className="mt-2 text-sm leading-relaxed text-ink-muted">
          A base de identidade, sessão, criptografia e auditoria já está pronta. As telas de
          cadastro, login e importação bancária entram nas próximas fases. Não insira dados
          financeiros reais nesta versão.
        </p>
        <ul className="mt-4 space-y-2 text-sm text-ink">
          <li className="flex gap-2">
            <Check aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-income" />
            Sessão segura com expiração e proteção CSRF
          </li>
          <li className="flex gap-2">
            <Check aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-income" />
            Senhas com Argon2id e política local de senhas comuns
          </li>
          <li className="flex gap-2">
            <Check aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-income" />
            Campos sensíveis cifrados com chave externa ao banco
          </li>
        </ul>
      </section>

      <p className="text-xs leading-relaxed text-ink-muted">
        Esta é uma instalação self-hosted. Nenhum dado é enviado a serviços de terceiros pela
        interface.
      </p>
    </div>
  )
}
