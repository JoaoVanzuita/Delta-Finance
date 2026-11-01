import { useState } from 'react'
import type { ReactNode } from 'react'

import { Menu } from 'lucide-react'
import { NavLink, useLocation } from 'react-router'

import { Drawer } from '@/components/ui/drawer'
import { IconButton } from '@/components/ui/icon-button'
import { Tooltip } from '@/components/ui/tooltip'
import { cn } from '@/lib/cn'
import { useMediaQuery } from '@/lib/useMediaQuery'

import { Brand } from './Brand'
import { NAVIGATION_GROUPS } from './navigation'
import { ThemeMenu } from './ThemeMenu'

export interface AppShellProps {
  readonly children: ReactNode
  /** Contadores de ação necessária por rota, quando a tela já os conhece. */
  readonly actionCounts?: Partial<Record<string, number>>
}

function NavigationContent({
  variant,
  actionCounts,
  onNavigate,
  showTheme,
}: {
  readonly variant: 'sidebar' | 'drawer'
  readonly actionCounts?: Partial<Record<string, number>>
  readonly onNavigate?: () => void
  readonly showTheme?: boolean
}) {
  const isWide = useMediaQuery('(min-width: 1024px)')
  const compact = variant === 'sidebar'

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <nav aria-label="Navegação principal" className="min-h-0 flex-1 overflow-y-auto px-2 py-2">
        <ul className="space-y-4">
          {NAVIGATION_GROUPS.map((group, index) => (
            <li
              key={group.label}
              className={cn(index > 0 && 'border-t border-line pt-3', 'md:border-0 md:pt-0')}
            >
              <p
                className={cn(
                  'overline px-3 pb-1 text-ink-muted',
                  compact && 'md:sr-only lg:not-sr-only',
                )}
              >
                {group.label}
              </p>
              <ul className="space-y-1">
                {group.items.map((item) => {
                  const count = actionCounts?.[item.to] ?? 0
                  const link = (
                    <NavLink
                      to={item.to}
                      end={item.end}
                      onClick={onNavigate}
                      className={({ isActive }) =>
                        cn(
                          'flex min-h-11 items-center gap-3 rounded-control px-3 text-body-sm font-medium',
                          'transition-colors duration-[var(--motion-fast)] ease-[var(--ease-standard)]',
                          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-surface',
                          isActive
                            ? 'bg-primary-soft text-ink'
                            : 'text-ink-muted hover:bg-surface-muted hover:text-ink',
                          compact && 'md:justify-center md:px-2 lg:justify-start lg:px-3',
                        )
                      }
                    >
                      {({ isActive }) => (
                        <>
                          <item.icon
                            aria-hidden="true"
                            className={cn(
                              'size-5 shrink-0',
                              isActive ? 'text-primary' : 'text-ink-muted',
                            )}
                          />
                          <span className={cn('truncate', compact && 'md:sr-only lg:not-sr-only')}>
                            {item.label}
                          </span>
                          {count > 0 ? (
                            <span
                              className={cn(
                                'ml-auto rounded-pill bg-primary px-2 py-0.5 text-caption font-semibold text-on-primary',
                                compact && 'md:sr-only lg:not-sr-only',
                              )}
                            >
                              <span className="sr-only">Ação necessária: </span>
                              {count}
                            </span>
                          ) : null}
                        </>
                      )}
                    </NavLink>
                  )

                  return (
                    <li key={item.to}>
                      {compact && !isWide ? (
                        <Tooltip label={item.label} side="right" className="w-full">
                          {link}
                        </Tooltip>
                      ) : (
                        link
                      )}
                    </li>
                  )
                })}
              </ul>
            </li>
          ))}
        </ul>
      </nav>

      <div
        className={cn(
          'flex items-center gap-2 border-t border-line px-3 py-3',
          compact && 'md:justify-center lg:justify-between',
        )}
      >
        {showTheme ? <ThemeMenu placement="top" align="start" /> : null}
        <p
          className={cn(
            'text-caption leading-snug text-ink-muted',
            compact && 'md:sr-only lg:not-sr-only',
          )}
        >
          Instalação privada. Cada pessoa vê somente os próprios dados.
        </p>
      </div>
    </div>
  )
}

/**
 * Layout autenticado.
 *
 * Desktop: sidebar completa (260 px). Tablet: rail de ícones com tooltip. Mobile:
 * top bar + drawer modal. A navegação é renderizada uma única vez em cada viewport.
 */
export function AppShell({ children, actionCounts }: AppShellProps) {
  const isTabletUp = useMediaQuery('(min-width: 768px)')
  const [mobileOpen, setMobileOpen] = useState(false)
  const location = useLocation()
  const currentItem = NAVIGATION_GROUPS.flatMap((group) => group.items).find(
    (item) => location.pathname === item.to || location.pathname.startsWith(`${item.to}/`),
  )

  return (
    <div className="min-h-dvh bg-canvas">
      <a href="#conteudo" className="skip-link">
        Pular para o conteúdo
      </a>

      <div className="md:grid md:min-h-dvh md:grid-cols-[80px_1fr] lg:grid-cols-[260px_1fr]">
        {isTabletUp ? (
          <aside className="sticky top-0 hidden h-dvh flex-col border-r border-line bg-surface md:flex">
            <div className="flex h-16 items-center border-b border-line px-3 lg:px-5">
              <Brand rail />
            </div>
            <NavigationContent variant="sidebar" actionCounts={actionCounts} showTheme />
          </aside>
        ) : (
          <>
            <header className="sticky top-0 z-30 flex min-h-16 items-center justify-between gap-3 border-b border-line bg-surface px-4">
              <div className="min-w-0">
                <Brand />
                {currentItem ? (
                  <p className="mt-0.5 truncate text-caption text-ink-muted">{currentItem.label}</p>
                ) : null}
              </div>
              <IconButton
                label="Abrir navegação"
                aria-haspopup="dialog"
                aria-expanded={mobileOpen}
                onClick={() => setMobileOpen(true)}
              >
                <Menu className="size-5" />
              </IconButton>
            </header>

            <Drawer
              open={mobileOpen}
              onClose={() => setMobileOpen(false)}
              title="Navegação"
              description="Áreas do Delta Finance"
              side="left"
              width="sm"
              bodyClassName="flex min-h-0 flex-col px-0 py-2"
            >
              <NavigationContent
                variant="drawer"
                actionCounts={actionCounts}
                onNavigate={() => setMobileOpen(false)}
                showTheme
              />
            </Drawer>
          </>
        )}

        <div className="flex min-h-dvh min-w-0 flex-col">
          <main
            id="conteudo"
            tabIndex={-1}
            className="flex-1 px-4 py-6 focus-visible:outline-none sm:px-6 md:px-6 lg:px-10 lg:py-10"
          >
            <div className="mx-auto w-full max-w-[var(--content-max)]">{children}</div>
          </main>

          <footer className="border-t border-line px-4 py-4 text-caption text-ink-muted sm:px-6 lg:px-10">
            Delta Finance · dados isolados por usuário · valores em BRL
          </footer>
        </div>
      </div>
    </div>
  )
}
