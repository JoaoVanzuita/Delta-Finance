import type { ReactNode } from 'react'

import { NavLink } from 'react-router'

interface NavigationItem {
  readonly to: string
  readonly label: string
  readonly end?: boolean
}

/**
 * Navegação inicial. Novas rotas entram junto com as histórias correspondentes;
 * nenhum item é exibido antes de a tela existir.
 */
const navigation: readonly NavigationItem[] = [
  { to: '/', label: 'Início', end: true },
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/accounts', label: 'Contas' },
  { to: '/transactions', label: 'Transações' },
  { to: '/categories', label: 'Categorias' },
  { to: '/reconciliation', label: 'Reconciliação' },
  { to: '/settings/integration', label: 'Integração' },
  { to: '/settings/privacy', label: 'Privacidade' },
  { to: '/account', label: 'Conta' },
]

function Brand() {
  return (
    <div className="flex items-center gap-3">
      <span
        aria-hidden="true"
        className="flex size-9 items-center justify-center rounded-card border border-line bg-primary text-base font-semibold text-surface"
      >
        Δ
      </span>
      <span className="leading-tight">
        <span className="block text-sm font-semibold tracking-tight text-ink">Delta Finance</span>
        <span className="block text-xs text-ink-muted">Finanças da família</span>
      </span>
    </div>
  )
}

/**
 * Layout base da aplicação.
 *
 * Desktop: sidebar com marca, navegação e aviso de instalação privada.
 * Mobile: top bar com a mesma navegação em linha, sem drawer enquanto houver
 * apenas um destino. A navegação é renderizada uma única vez e reflui por CSS,
 * evitando landmarks duplicados.
 */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[248px_1fr] lg:grid-rows-[auto_1fr_auto]">
      <a href="#conteudo" className="skip-link">
        Pular para o conteúdo
      </a>

      <div className="border-b border-line bg-surface px-4 py-3 lg:col-start-1 lg:row-start-1 lg:border-r lg:px-5 lg:py-5">
        <Brand />
      </div>

      <nav
        aria-label="Navegação principal"
        className="border-b border-line bg-surface lg:col-start-1 lg:row-start-2 lg:border-r lg:border-b-0 lg:py-4"
      >
        <ul className="flex items-center gap-1 overflow-x-auto px-2 pb-2 lg:flex-col lg:px-3 lg:pb-0">
          {navigation.map((item) => (
            <li key={item.to}>
              <NavLink
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  [
                    'flex min-h-11 items-center rounded-card px-3 text-sm font-medium transition-colors',
                    isActive
                      ? 'bg-surface-muted text-ink'
                      : 'text-ink-muted hover:bg-surface-muted hover:text-ink',
                  ].join(' ')
                }
              >
                {item.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      <p className="hidden border-t border-line bg-surface px-5 py-4 text-xs leading-relaxed text-ink-muted lg:col-start-1 lg:row-start-3 lg:block">
        Instalação privada. Cada pessoa vê somente os próprios dados.
      </p>

      <main
        id="conteudo"
        tabIndex={-1}
        className="flex-1 px-4 py-8 sm:px-6 lg:col-start-2 lg:row-span-2 lg:row-start-1 lg:px-10 lg:py-10"
      >
        {children}
      </main>

      <footer className="border-t border-line px-4 py-4 text-xs text-ink-muted sm:px-6 lg:col-start-2 lg:row-start-3 lg:px-10">
        Delta Finance · dados isolados por usuário · valores em BRL
      </footer>
    </div>
  )
}
