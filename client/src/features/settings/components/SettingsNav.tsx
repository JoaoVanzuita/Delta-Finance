import { Palette, Plug, ShieldCheck, UserRound, type LucideIcon } from 'lucide-react'
import { NavLink } from 'react-router'

import { cn } from '@/lib/cn'

interface SettingsDestination {
  readonly to: string
  readonly label: string
  readonly description: string
  readonly icon: LucideIcon
}

const DESTINATIONS: readonly SettingsDestination[] = [
  {
    to: '/settings/preferences',
    label: 'Preferências',
    description: 'Tema e cor principal',
    icon: Palette,
  },
  {
    to: '/settings/integration',
    label: 'Integração',
    description: 'Meu Pluggy e conexões',
    icon: Plug,
  },
  {
    to: '/settings/privacy',
    label: 'Privacidade',
    description: 'Exportação e exclusão',
    icon: ShieldCheck,
  },
  {
    to: '/account',
    label: 'Conta',
    description: 'Identidade e senha',
    icon: UserRound,
  },
]

/**
 * Navegação secundária das configurações.
 *
 * Cada área mantém URL própria e deep link. A navegação usa links reais (não
 * abas ARIA) porque troca de página, não de painel dentro da mesma página.
 */
export function SettingsNav() {
  return (
    <nav aria-label="Áreas das configurações" className="border-b border-line">
      <ul className="-mb-px grid grid-cols-2 gap-1 sm:flex sm:flex-wrap">
        {DESTINATIONS.map((destination) => (
          <li key={destination.to} className="shrink-0">
            <NavLink
              to={destination.to}
              className={({ isActive }) =>
                cn(
                  'flex min-h-12 items-center gap-2 border-b-2 px-3 py-2 text-body-sm font-semibold',
                  'transition-colors duration-[var(--motion-fast)] ease-[var(--ease-standard)]',
                  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-canvas',
                  isActive
                    ? 'border-primary text-ink'
                    : 'border-transparent text-ink-muted hover:border-line-strong hover:text-ink',
                )
              }
            >
              {({ isActive }) => (
                <>
                  <destination.icon
                    aria-hidden="true"
                    className={cn('size-4', isActive ? 'text-primary' : 'text-ink-muted')}
                  />
                  <span>{destination.label}</span>
                  <span className="sr-only"> — {destination.description}</span>
                </>
              )}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}
