import { useState } from 'react'

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { financeApi, type CardProfile } from '@/features/integration/api'

interface CardCycleSettingsProps {
  readonly accountId: string
  readonly profile: CardProfile | null | undefined
}

/** Configuração de fechamento, vencimento e limite do cartão; não reescreve faturas passadas. */
export function CardCycleSettings({ accountId, profile }: CardCycleSettingsProps) {
  const queryClient = useQueryClient()
  const [closingDay, setClosingDay] = useState(String(profile?.closingDay ?? 1))
  const [dueDay, setDueDay] = useState(String(profile?.dueDay ?? 1))
  const [creditLimit, setCreditLimit] = useState(profile?.creditLimit ?? '')
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () =>
      financeApi.updateCardProfile(accountId, {
        closingDay: Number(closingDay),
        dueDay: Number(dueDay),
        creditLimit: creditLimit.trim() ? creditLimit.replace(',', '.') : undefined,
      }),
    onSuccess: () => {
      setError(null)
      setMessage('Ciclo do cartão atualizado. Faturas já fechadas ou pagas não são reescritas.')
      void queryClient.invalidateQueries({ queryKey: ['account', accountId] })
      void queryClient.invalidateQueries({ queryKey: ['accounts'] })
      void queryClient.invalidateQueries({ queryKey: ['invoices', accountId] })
      void queryClient.invalidateQueries({ queryKey: ['cards', 'overview'] })
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
        <h2 id="card-cycle-heading" className="text-lg font-semibold text-ink">
          Ciclo do cartão
        </h2>
        <p className="mt-1 text-sm text-ink-muted">
          {profile
            ? 'Fechamento, vencimento e limite usados para organizar as faturas por ciclo.'
            : 'Este cartão ainda não tem ciclo configurado. Sem ciclo, as compras ficam não alocadas.'}
        </p>
      </div>

      {message ? (
        <p
          role="status"
          className="rounded-card border border-income/30 bg-income/5 p-3 text-sm text-ink"
        >
          {message}
        </p>
      ) : null}
      {error ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense/5 p-3 text-sm text-ink"
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
          <label className="block text-sm">
            <span className="font-medium text-ink">Dia de fechamento</span>
            <input
              type="number"
              min={1}
              max={31}
              value={closingDay}
              onChange={(event) => setClosingDay(event.target.value)}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            />
          </label>
          <label className="block text-sm">
            <span className="font-medium text-ink">Dia de vencimento</span>
            <input
              type="number"
              min={1}
              max={31}
              value={dueDay}
              onChange={(event) => setDueDay(event.target.value)}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            />
          </label>
          <label className="block text-sm">
            <span className="font-medium text-ink">Limite (R$)</span>
            <input
              type="text"
              inputMode="decimal"
              value={creditLimit}
              onChange={(event) => setCreditLimit(event.target.value)}
              placeholder="Opcional"
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            />
          </label>
        </div>

        <button
          type="submit"
          disabled={save.isPending}
          className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover disabled:opacity-60"
        >
          {save.isPending ? 'Salvando…' : 'Salvar ciclo'}
        </button>
      </form>
    </section>
  )
}
