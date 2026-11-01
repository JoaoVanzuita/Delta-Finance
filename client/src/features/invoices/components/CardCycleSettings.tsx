import { useEffect, useRef, useState } from 'react'

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { Button } from '@/components/ui/button'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api/problem'

import { financeApi, type CardProfile } from '@/features/integration/api'
import { invalidateCardViews } from '@/lib/invalidation'

interface CardCycleSettingsProps {
  readonly accountId: string
  readonly profile: CardProfile | null | undefined
  readonly currency?: string
}

/** Configuração de fechamento, vencimento e limite do cartão; não reescreve faturas passadas. */
export function CardCycleSettings({
  accountId,
  profile,
  currency = 'BRL',
}: CardCycleSettingsProps) {
  const queryClient = useQueryClient()
  const [closingDay, setClosingDay] = useState(String(profile?.closingDay ?? 1))
  const [dueDay, setDueDay] = useState(String(profile?.dueDay ?? 1))
  const [creditLimit, setCreditLimit] = useState(profile?.creditLimit ?? '')
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const hydratedProfile = useRef<string | null>(null)

  /**
   * Hidrata quando o perfil chega depois da montagem (ex.: consulta do cartão em
   * andamento). Não sobrescreve edições do usuário em refetches com os mesmos
   * valores do servidor.
   */
  useEffect(() => {
    if (!profile) {
      return
    }
    const key = [accountId, profile.closingDay, profile.dueDay, profile.creditLimit].join('|')
    if (hydratedProfile.current === key) {
      return
    }
    hydratedProfile.current = key
    setClosingDay(String(profile.closingDay))
    setDueDay(String(profile.dueDay))
    setCreditLimit(profile.creditLimit ?? '')
  }, [accountId, profile])

  const save = useMutation({
    mutationFn: () =>
      financeApi.updateCardProfile(accountId, {
        closingDay: Number(closingDay),
        dueDay: Number(dueDay),
        creditLimit: creditLimit.trim() ? creditLimit.replace(',', '.') : undefined,
      }),
    onSuccess: async () => {
      setError(null)
      setMessage('Ciclo do cartão atualizado. Faturas já fechadas ou pagas não são reescritas.')
      await invalidateCardViews(queryClient)
    },
    onError: (caught) => {
      setMessage(null)
      setError(
        caught instanceof ApiError
          ? caught.message
          : 'Não foi possível atualizar o ciclo do cartão.',
      )
    },
  })

  function submit() {
    setError(null)
    setMessage(null)
    const closing = Number(closingDay)
    const due = Number(dueDay)
    if (!Number.isInteger(closing) || closing < 1 || closing > 31) {
      setError('O dia de fechamento deve estar entre 1 e 31.')
      return
    }
    if (!Number.isInteger(due) || due < 1 || due > 31) {
      setError('O dia de vencimento deve estar entre 1 e 31.')
      return
    }
    if (creditLimit.trim()) {
      const limit = Number(creditLimit.replace(',', '.'))
      if (Number.isNaN(limit) || limit < 0) {
        setError('O limite deve ser maior ou igual a zero.')
        return
      }
    }
    save.mutate()
  }

  return (
    <section
      aria-labelledby="card-cycle-heading"
      className="space-y-4 rounded-card border border-line bg-surface p-5 sm:p-6"
    >
      <div>
        <h2 id="card-cycle-heading" className="text-section-title font-semibold text-ink">
          Ciclo do cartão
        </h2>
        <p className="mt-1 text-body-sm text-ink-muted">
          {profile
            ? 'Fechamento, vencimento e limite usados para organizar as faturas por ciclo.'
            : 'Este cartão ainda não tem ciclo configurado. Sem ciclo, as compras ficam não alocadas.'}
        </p>
      </div>

      {message ? (
        <p
          role="status"
          className="rounded-card border border-income/30 bg-income-soft p-3 text-body-sm text-ink"
        >
          {message}
        </p>
      ) : null}
      {error ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense-soft p-3 text-body-sm text-ink"
        >
          {error}
        </p>
      ) : null}

      <form
        noValidate
        onSubmit={(event) => {
          event.preventDefault()
          submit()
        }}
        className="space-y-4"
      >
        <div className="grid gap-3 sm:grid-cols-3">
          <Field id="card-closing-day" label="Dia de fechamento">
            <Input
              id="card-closing-day"
              type="number"
              min={1}
              max={31}
              value={closingDay}
              onChange={(event) => setClosingDay(event.target.value)}
            />
          </Field>
          <Field id="card-due-day" label="Dia de vencimento">
            <Input
              id="card-due-day"
              type="number"
              min={1}
              max={31}
              value={dueDay}
              onChange={(event) => setDueDay(event.target.value)}
            />
          </Field>
          <Field id="card-credit-limit" label={`Limite (${currency === 'BRL' ? 'R$' : currency})`}>
            <Input
              id="card-credit-limit"
              type="text"
              inputMode="decimal"
              value={creditLimit}
              onChange={(event) => setCreditLimit(event.target.value)}
              placeholder="Opcional"
            />
          </Field>
        </div>

        <Button type="submit" loading={save.isPending} loadingLabel="Salvando…">
          Salvar ciclo
        </Button>
      </form>
    </section>
  )
}
