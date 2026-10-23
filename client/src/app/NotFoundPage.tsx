import { Link } from 'react-router'

/** 404: rota inexistente, sem revelar nada sobre recursos de outros usuários. */
export function NotFoundPage() {
  return (
    <div className="mx-auto max-w-md space-y-4">
      <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">Erro 404</p>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Página não encontrada</h1>
      <p className="text-sm leading-relaxed text-ink-muted">
        O endereço acessado não existe. Se você chegou por um link antigo, volte ao início.
      </p>
      <div className="flex flex-wrap gap-3">
        <Link
          to="/"
          className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover"
        >
          Ir para o início
        </Link>
        <Link
          to="/login"
          className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink transition-colors hover:bg-surface-muted"
        >
          Entrar
        </Link>
      </div>
    </div>
  )
}
