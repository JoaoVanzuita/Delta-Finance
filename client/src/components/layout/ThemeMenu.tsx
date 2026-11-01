import { Palette } from 'lucide-react'

import { useOptionalTheme } from '@/app/theme-context'
import { Popover } from '@/components/ui/popover'
import { IconButton } from '@/components/ui/icon-button'
import { cn } from '@/lib/cn'
import {
  ACCENTS,
  THEME_PREFERENCES,
  type Accent,
  type ThemePreference,
} from '@/lib/theme/preferences'

const THEME_LABELS: Record<ThemePreference, string> = {
  light: 'Claro',
  dark: 'Escuro',
  system: 'Automático',
}

const ACCENT_LABELS: Record<Accent, string> = {
  blue: 'Azul',
  green: 'Verde',
  purple: 'Roxo',
  amber: 'Âmbar',
}

const ACCENT_SWATCHES: Record<Accent, string> = {
  blue: 'bg-[var(--df-accent-blue)]',
  green: 'bg-[var(--df-accent-green)]',
  purple: 'bg-[var(--df-accent-purple)]',
  amber: 'bg-[var(--df-accent-amber)]',
}

/**
 * Controle de aparência: tema e paleta curada.
 * Aparece no rodapé da sidebar e no cabeçalho público.
 */
export function ThemeMenu({
  placement = 'bottom',
  align = 'end',
}: {
  placement?: 'top' | 'bottom'
  align?: 'start' | 'end'
}) {
  const current = useOptionalTheme()
  if (!current) {
    return null
  }
  const { theme, accent, setTheme, setAccent, resolvedTheme } = current

  return (
    <Popover
      label="Aparência"
      placement={placement}
      align={align}
      trigger={({ open, id, toggle, triggerRef }) => (
        <IconButton
          ref={triggerRef}
          label="Aparência"
          aria-expanded={open}
          aria-controls={id}
          onClick={toggle}
        >
          <Palette className="size-5" />
        </IconButton>
      )}
    >
      <div className="space-y-4">
        <fieldset>
          <legend className="overline text-ink-muted">Tema</legend>
          <div className="mt-2 grid grid-cols-3 gap-1">
            {THEME_PREFERENCES.map((option) => (
              <label
                key={option}
                className={cn(
                  'cursor-pointer rounded-control border px-2 py-2 text-center text-caption font-medium',
                  'focus-within:outline-none focus-within:ring-2 focus-within:ring-focus focus-within:ring-offset-1 focus-within:ring-offset-surface-raised',
                  theme === option
                    ? 'border-primary bg-primary-soft text-ink'
                    : 'border-line text-ink-muted hover:bg-surface-muted',
                )}
              >
                <input
                  type="radio"
                  name="theme-preference"
                  value={option}
                  checked={theme === option}
                  onChange={() => setTheme(option)}
                  className="sr-only"
                />
                {THEME_LABELS[option]}
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset>
          <legend className="overline text-ink-muted">Paleta</legend>
          <div className="mt-2 grid grid-cols-4 gap-1.5">
            {ACCENTS.map((option) => (
              <label
                key={option}
                className={cn(
                  'flex cursor-pointer flex-col items-center gap-1 rounded-control border px-1 py-1.5',
                  'focus-within:outline-none focus-within:ring-2 focus-within:ring-focus focus-within:ring-offset-1 focus-within:ring-offset-surface-raised',
                  accent === option
                    ? 'border-primary'
                    : 'border-transparent hover:bg-surface-muted',
                )}
              >
                <input
                  type="radio"
                  name="theme-accent"
                  value={option}
                  checked={accent === option}
                  onChange={() => setAccent(option)}
                  className="sr-only"
                />
                <span
                  aria-hidden="true"
                  className={cn('size-5 rounded-pill border border-line', ACCENT_SWATCHES[option])}
                />
                <span className="text-caption text-ink-muted">{ACCENT_LABELS[option]}</span>
              </label>
            ))}
          </div>
        </fieldset>

        <p className="text-caption text-ink-muted">
          Tema {THEME_LABELS[theme].toLowerCase()} · {THEME_LABELS[resolvedTheme]}
        </p>
      </div>
    </Popover>
  )
}
