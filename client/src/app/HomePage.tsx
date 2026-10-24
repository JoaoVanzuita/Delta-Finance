import { Check } from 'lucide-react'
import { Link } from 'react-router'

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
        <div className="flex flex-wrap gap-3 pt-1">
          <Link
            to="/login"
            className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover"
          >
            Entrar
          </Link>
          <Link
            to="/register"
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink transition-colors hover:bg-surface-muted"
          >
            Criar conta
          </Link>
        </div>
      </header>

      <section
        aria-labelledby="status-heading"
        className="rounded-card border border-line bg-surface p-5 sm:p-6"
      >
        <h2 id="status-heading" className="text-lg font-semibold text-ink">
          Aplicação em construção
        </h2>
        <p className="mt-2 text-sm leading-relaxed text-ink-muted">
          A base de identidade, sessão, criptografia e auditoria está pronta, e o cadastro, o login,
          a importação bancária, os lançamentos manuais, as faturas e o dashboard já funcionam.
          Novas fases continuam sendo adicionadas sem expor telas vazias.
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
          <li className="flex gap-2">
            <Check aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-income" />
            Isolamento entre usuários verificado no servidor
          </li>
          <li className="flex gap-2">
            <Check aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-income" />
            Dashboard com resultado do período e saldo atual em rótulos distintos
          </li>
        </ul>
        <Link
          to="/dashboard"
          className="mt-5 inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink transition-colors hover:bg-surface-muted"
        >
          Abrir dashboard
        </Link>
      </section>

      <p className="text-xs leading-relaxed text-ink-muted">
        Esta é uma instalação self-hosted. Nenhum dado é enviado a serviços de terceiros pela
        interface.
      </p>
    </div>
  )
}
