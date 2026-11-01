import { Link } from 'react-router'

import { buttonVariants } from '@/components/ui/button-variants'
import { cn } from '@/lib/cn'

/** 404: não revela nada sobre recursos de outros usuários. */
export function NotFoundPage({ authenticated = false }: { authenticated?: boolean }) {
  return (
    <div className="mx-auto max-w-md space-y-4">
      <p className="overline text-ink-muted">Página não encontrada</p>
      <h1 className="text-page-title font-bold tracking-tight text-ink">
        Não encontramos este endereço
      </h1>
      <p className="text-body-sm text-ink-muted">
        O endereço acessado não existe ou foi movido. Se você chegou por um link antigo, use um dos
        caminhos abaixo.
      </p>
      <div className="flex flex-wrap gap-3 pt-1">
        {authenticated ? (
          <>
            <Link to="/dashboard" className={cn(buttonVariants({ variant: 'primary' }))}>
              Ir para o Dashboard
            </Link>
            <Link to="/accounts" className={cn(buttonVariants({ variant: 'secondary' }))}>
              Ver contas
            </Link>
          </>
        ) : (
          <>
            <Link to="/" className={cn(buttonVariants({ variant: 'primary' }))}>
              Ir para o início
            </Link>
            <Link to="/login" className={cn(buttonVariants({ variant: 'secondary' }))}>
              Entrar
            </Link>
          </>
        )}
      </div>
    </div>
  )
}
