import type { ReactNode } from 'react'

import { Link, Outlet } from 'react-router'

import { buttonVariants } from '@/components/ui/button-variants'
import { cn } from '@/lib/cn'

import { Brand } from './Brand'
import { ThemeMenu } from './ThemeMenu'

/**
 * Layout público: Home, login, cadastro e 404 de visitante.
 * Nunca renderiza a sidebar autenticada.
 */
export function PublicLayout({ children }: { children?: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col bg-canvas">
      <a href="#conteudo" className="skip-link">
        Pular para o conteúdo
      </a>

      <header className="border-b border-line bg-surface">
        <div className="mx-auto flex min-h-16 w-full max-w-[var(--content-max)] items-center justify-between gap-3 px-4 sm:px-6 lg:px-10">
          <Link
            to="/"
            className="min-w-0 rounded-control focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-surface"
          >
            <Brand />
          </Link>

          <nav aria-label="Navegação pública" className="flex items-center gap-2">
            <ThemeMenu />
            <Link to="/login" className={cn(buttonVariants({ variant: 'secondary' }))}>
              Entrar
            </Link>
            <Link
              to="/register"
              className={cn(buttonVariants({ variant: 'primary' }), 'hidden sm:inline-flex')}
            >
              Criar conta
            </Link>
          </nav>
        </div>
      </header>

      <main
        id="conteudo"
        tabIndex={-1}
        className="flex-1 px-4 py-8 focus-visible:outline-none sm:px-6 sm:py-12 lg:px-10"
      >
        <div className="mx-auto w-full max-w-[var(--content-max)]">{children ?? <Outlet />}</div>
      </main>

      <footer className="border-t border-line px-4 py-4 text-caption text-ink-muted sm:px-6 lg:px-10">
        <div className="mx-auto w-full max-w-[var(--content-max)]">
          Delta Finance · instalação self-hosted · dados isolados por usuário
        </div>
      </footer>
    </div>
  )
}
