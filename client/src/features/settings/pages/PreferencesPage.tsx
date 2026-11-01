import { Check, Monitor, Moon, Palette, Sun, type LucideIcon } from 'lucide-react'

import { useTheme } from '@/app/theme-context'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { PageHeader } from '@/components/ui/page-header'
import { Progress } from '@/components/ui/progress'
import { cn } from '@/lib/cn'
import type { Accent, ThemePreference } from '@/lib/theme/preferences'

const THEME_OPTIONS: ReadonlyArray<{
  value: ThemePreference
  label: string
  description: string
  icon: LucideIcon
}> = [
  {
    value: 'system',
    label: 'Sistema',
    description: 'Acompanha a preferência do dispositivo.',
    icon: Monitor,
  },
  {
    value: 'light',
    label: 'Claro',
    description: 'Superfícies claras em qualquer horário.',
    icon: Sun,
  },
  {
    value: 'dark',
    label: 'Escuro',
    description: 'Menos brilho para ambientes com pouca luz.',
    icon: Moon,
  },
]

const ACCENT_OPTIONS: ReadonlyArray<{
  value: Accent
  label: string
  description: string
  swatch: string
}> = [
  {
    value: 'blue',
    label: 'Azul',
    description: 'Padrão do Delta Finance.',
    swatch: 'bg-[var(--df-accent-blue)]',
  },
  {
    value: 'green',
    label: 'Verde',
    description: 'Tonalidade ligada a crescimento.',
    swatch: 'bg-[var(--df-accent-green)]',
  },
  {
    value: 'purple',
    label: 'Roxo',
    description: 'Alternativa de maior contraste.',
    swatch: 'bg-[var(--df-accent-purple)]',
  },
  {
    value: 'amber',
    label: 'Âmbar',
    description: 'Alternativa quente, com contraste revisado.',
    swatch: 'bg-[var(--df-accent-amber)]',
  },
]

/**
 * Preferências visuais.
 *
 * A escolha é aplicada imediatamente e persistida apenas neste navegador, em
 * namespace por usuário. Nenhum dado financeiro ou credencial é gravado.
 */
export function PreferencesPage() {
  const { theme, accent, resolvedTheme, setTheme, setAccent } = useTheme()

  return (
    <div className="space-y-6">
      <PageHeader
        overline="Configurações"
        title="Preferências"
        description="Escolha como o Delta Finance aparece para você. A preferência vale apenas neste navegador e não altera os dados financeiros."
        meta={
          <Badge variant="neutral">
            {resolvedTheme === 'dark' ? 'Tema escuro em uso' : 'Tema claro em uso'}
          </Badge>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(280px,360px)]">
        <div className="space-y-6">
          <Card>
            <fieldset className="p-5 sm:p-6">
              <legend className="text-section-title font-semibold text-ink">Tema</legend>
              <p className="mt-1 text-body-sm text-ink-muted">
                “Sistema” acompanha automaticamente a configuração do dispositivo.
              </p>
              <div className="mt-4 grid gap-2 sm:grid-cols-3">
                {THEME_OPTIONS.map((option) => {
                  const Icon = option.icon
                  const selected = theme === option.value
                  return (
                    <label
                      key={option.value}
                      className={cn(
                        'flex cursor-pointer flex-col gap-2 rounded-card border p-3',
                        'transition-colors duration-[var(--motion-fast)] ease-[var(--ease-standard)]',
                        'focus-within:outline-none focus-within:ring-2 focus-within:ring-focus focus-within:ring-offset-2 focus-within:ring-offset-surface',
                        selected
                          ? 'border-primary bg-primary-soft'
                          : 'border-line bg-surface hover:bg-surface-muted',
                      )}
                    >
                      <input
                        type="radio"
                        name="theme-preference"
                        value={option.value}
                        checked={selected}
                        onChange={() => setTheme(option.value)}
                        className="sr-only"
                      />
                      <span className="flex items-center justify-between gap-2">
                        <Icon
                          aria-hidden="true"
                          className={cn('size-5', selected ? 'text-primary' : 'text-ink-muted')}
                        />
                        {selected ? (
                          <Check aria-hidden="true" className="size-4 text-primary" />
                        ) : null}
                      </span>
                      <span className="text-body-sm font-semibold text-ink">{option.label}</span>
                      <span className="text-caption text-ink-muted">{option.description}</span>
                    </label>
                  )
                })}
              </div>
            </fieldset>
          </Card>

          <Card>
            <fieldset className="p-5 sm:p-6">
              <legend className="flex items-center gap-2 text-section-title font-semibold text-ink">
                <Palette aria-hidden="true" className="size-5 text-ink-muted" />
                Cor principal
              </legend>
              <p className="mt-1 text-body-sm text-ink-muted">
                A paleta altera a cor de marca, botões e destaques. Receitas, despesas, avisos e
                erros mantêm sempre o mesmo significado.
              </p>
              <div className="mt-4 grid gap-2 sm:grid-cols-2">
                {ACCENT_OPTIONS.map((option) => {
                  const selected = accent === option.value
                  return (
                    <label
                      key={option.value}
                      className={cn(
                        'flex cursor-pointer items-center gap-3 rounded-card border p-3',
                        'transition-colors duration-[var(--motion-fast)] ease-[var(--ease-standard)]',
                        'focus-within:outline-none focus-within:ring-2 focus-within:ring-focus focus-within:ring-offset-2 focus-within:ring-offset-surface',
                        selected
                          ? 'border-primary bg-primary-soft'
                          : 'border-line bg-surface hover:bg-surface-muted',
                      )}
                    >
                      <input
                        type="radio"
                        name="theme-accent"
                        value={option.value}
                        checked={selected}
                        onChange={() => setAccent(option.value)}
                        className="sr-only"
                      />
                      <span
                        aria-hidden="true"
                        className={cn(
                          'flex size-9 shrink-0 items-center justify-center rounded-pill border border-line',
                          option.swatch,
                        )}
                      >
                        {selected ? <Check className="size-4 text-white" /> : null}
                      </span>
                      <span className="min-w-0">
                        <span className="block text-body-sm font-semibold text-ink">
                          {option.label}
                        </span>
                        <span className="block text-caption text-ink-muted">
                          {option.description}
                        </span>
                      </span>
                    </label>
                  )
                })}
              </div>
            </fieldset>
          </Card>
        </div>

        <aside aria-labelledby="preview-heading" className="space-y-3">
          <div className="flex items-baseline justify-between gap-2">
            <h2 id="preview-heading" className="text-section-title font-semibold text-ink">
              Pré-visualização
            </h2>
            <span className="text-caption text-ink-muted">Valores ilustrativos</span>
          </div>
          <Card className="overflow-hidden">
            <div className="border-b border-line bg-surface-muted px-4 py-3">
              <p className="overline text-ink-muted">Exemplo de painel</p>
              <p className="numeric mt-1 text-metric-md font-semibold text-ink">R$ 12.480,00</p>
              <p className="text-caption text-ink-muted">Saldo em contas · exemplo</p>
            </div>
            <div className="space-y-4 p-4">
              <div className="grid grid-cols-2 gap-2">
                <div className="rounded-control bg-income-soft p-3">
                  <p className="text-caption text-ink-muted">Receitas</p>
                  <p className="numeric text-body-sm font-semibold text-income">R$ 6.200,00</p>
                </div>
                <div className="rounded-control bg-expense-soft p-3">
                  <p className="text-caption text-ink-muted">Despesas</p>
                  <p className="numeric text-body-sm font-semibold text-expense">R$ 2.140,00</p>
                </div>
              </div>
              <Progress value={62} label="Meta de exemplo" tone="primary" />
              <div className="flex flex-wrap gap-2">
                <Button size="sm">Ação principal</Button>
                <Badge variant="warning">Atenção</Badge>
              </div>
            </div>
          </Card>
          <p className="text-caption leading-relaxed text-ink-muted">
            A pré-visualização usa as cores do tema ativo. Contraste de texto e foco são mantidos em
            todas as paletas. As preferências ficam salvas somente neste navegador, separadas por
            usuário autenticado; não há sincronização entre dispositivos.
          </p>
        </aside>
      </div>
    </div>
  )
}
