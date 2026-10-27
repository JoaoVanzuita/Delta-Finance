import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { financeApi, type Goal, type GoalInput } from '@/features/integration/api'

import { GoalForm } from '../components/GoalForm'
import {
  MODE_LABELS,
  RULE_LABELS,
  SOURCE_LABELS,
  STATUS_LABELS,
  TYPE_LABELS,
  formatDate,
  formatMoney,
} from '../format'

const GOALS_KEY = ['goals'] as const

function messageFrom(caught: unknown, fallback: string): string {
  return caught instanceof ApiError ? caught.message : fallback
}

/**
 * Metas financeiras.
 *
 * <p>Metas apenas calculam progresso sobre alocações explícitas ou patrimônio líquido, sem
 * movimentar dinheiro. Nenhuma ação cria ordem ou executa operação bancária.
 */
export function GoalsPage() {
  const queryClient = useQueryClient()
  const goalsQuery = useQuery({ queryKey: GOALS_KEY, queryFn: () => financeApi.goals() })
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [formVersion, setFormVersion] = useState(0)

  const invalidate = () => queryClient.invalidateQueries({ queryKey: GOALS_KEY })

  const create = useMutation({
    mutationFn: (input: GoalInput) => financeApi.createGoal(input),
    onSuccess: () => {
      setError(null)
      setMessage('Meta criada.')
      setFormVersion((version) => version + 1)
      void invalidate()
    },
    onError: (caught) => {
      setMessage(null)
      setError(messageFrom(caught, 'Não foi possível criar a meta.'))
    },
  })

  const goals = goalsQuery.data?.items ?? []

  return (
    <div className="mx-auto w-full max-w-5xl space-y-8">
      <header className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">
          Fase posterior
        </p>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Metas</h1>
        <p className="max-w-2xl text-sm leading-relaxed text-ink-muted">
          Acompanhe objetivos com regra de progresso transparente. Metas não movimentam dinheiro,
          não criam ordens e não executam ações bancárias.
        </p>
      </header>

      {message ? (
        <p
          role="status"
          aria-live="polite"
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

      <section
        aria-labelledby="new-goal-heading"
        className="space-y-4 rounded-card border border-line bg-surface p-4 sm:p-5"
      >
        <div className="space-y-1">
          <h2 id="new-goal-heading" className="text-lg font-semibold text-ink">
            Nova meta
          </h2>
          <p className="text-sm leading-relaxed text-ink-muted">
            Escolha o tipo, o valor-alvo e a regra de progresso. Alocações explícitas tornam a
            divisão entre metas visível e evitam contar o mesmo saldo integralmente em todas.
          </p>
        </div>
        <GoalForm
          key={formVersion}
          pending={create.isPending}
          submitLabel="Criar meta"
          onSubmit={(input) => create.mutate(input)}
        />
      </section>

      <section aria-labelledby="goals-heading" className="space-y-3">
        <h2 id="goals-heading" className="text-lg font-semibold text-ink">
          Metas registradas
        </h2>

        {goalsQuery.isLoading ? (
          <p role="status" aria-live="polite" className="text-sm text-ink-muted">
            Carregando metas…
          </p>
        ) : null}
        {goalsQuery.isError ? (
          <p
            role="alert"
            className="rounded-card border border-expense/30 bg-expense/5 p-4 text-sm text-ink"
          >
            {messageFrom(goalsQuery.error, 'Não foi possível carregar as metas.')}
          </p>
        ) : null}
        {goalsQuery.isSuccess && goals.length === 0 ? (
          <div className="rounded-card border border-line bg-surface p-6">
            <p className="font-medium text-ink">Nenhuma meta registrada</p>
            <p className="mt-1 text-sm text-ink-muted">
              Use o formulário acima para criar a primeira meta.
            </p>
          </div>
        ) : null}

        <div className="space-y-4">
          {goals.map((goal) => (
            <GoalCard key={goal.id} goal={goal} onChanged={() => void invalidate()} />
          ))}
        </div>
      </section>
    </div>
  )
}

function GoalCard({ goal, onChanged }: { readonly goal: Goal; readonly onChanged: () => void }) {
  const [editing, setEditing] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const headingId = `goal-${goal.id}-heading`
  const percentage = Math.min(100, Math.max(0, Number(goal.percentage)))

  const update = useMutation({
    mutationFn: (input: GoalInput) => financeApi.updateGoal(goal.id, input),
    onSuccess: () => {
      setEditing(false)
      setError(null)
      onChanged()
    },
    onError: (caught) => {
      setError(messageFrom(caught, 'Não foi possível salvar a meta.'))
    },
  })

  const changeStatus = useMutation({
    mutationFn: (status: 'ACTIVE' | 'PAUSED' | 'ARCHIVED') =>
      financeApi.updateGoalStatus(goal.id, status),
    onSuccess: () => {
      setError(null)
      onChanged()
    },
    onError: (caught) => {
      setError(messageFrom(caught, 'Não foi possível alterar o estado da meta.'))
    },
  })

  const remove = useMutation({
    mutationFn: () => financeApi.deleteGoal(goal.id),
    onSuccess: () => {
      setConfirming(false)
      setError(null)
      onChanged()
    },
    onError: (caught) => {
      setError(messageFrom(caught, 'Não foi possível excluir a meta.'))
    },
  })

  const frozen = goal.status === 'PAUSED' || goal.status === 'ARCHIVED'
  const unallocated = Number(goal.unallocatedAmount)
  const excess = Number(goal.excessAmount)

  return (
    <article
      aria-labelledby={headingId}
      className="space-y-4 rounded-card border border-line bg-surface p-4 sm:p-5"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h3 id={headingId} className="text-lg font-semibold text-ink">
            {goal.name}
          </h3>
          <p className="text-sm text-ink-muted">
            {TYPE_LABELS[goal.type] ?? goal.type} · Prazo: {formatDate(goal.targetDate)}
          </p>
          {goal.description ? <p className="text-sm text-ink">{goal.description}</p> : null}
          {goal.notes ? <p className="text-sm text-ink-muted">{goal.notes}</p> : null}
        </div>
        <span className="rounded-card border border-line bg-surface-muted px-3 py-1 text-sm font-semibold text-ink">
          {STATUS_LABELS[goal.status] ?? goal.status}
        </span>
      </div>

      <div className="space-y-2">
        <div
          role="progressbar"
          aria-label={`Progresso de ${goal.name}`}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Number(goal.percentage)}
          className="h-3 w-full overflow-hidden rounded-card bg-surface-muted"
        >
          <div className="h-full bg-primary" style={{ width: `${percentage}%` }} />
        </div>
        <p className="text-sm font-medium text-ink">{goal.percentage}% concluído</p>
      </div>

      <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-card border border-line bg-surface-muted p-3">
          <dt className="text-xs text-ink-muted">Valor atual</dt>
          <dd className="numeric mt-1 text-sm font-semibold text-ink">
            {formatMoney(goal.currentValue)}
          </dd>
        </div>
        <div className="rounded-card border border-line bg-surface-muted p-3">
          <dt className="text-xs text-ink-muted">Alvo</dt>
          <dd className="numeric mt-1 text-sm font-semibold text-ink">
            {formatMoney(goal.targetAmount)}
          </dd>
        </div>
        <div className="rounded-card border border-line bg-surface-muted p-3">
          <dt className="text-xs text-ink-muted">Percentual</dt>
          <dd className="numeric mt-1 text-sm font-semibold text-ink">{goal.percentage}%</dd>
        </div>
        <div className="rounded-card border border-line bg-surface-muted p-3">
          <dt className="text-xs text-ink-muted">Restante</dt>
          <dd className="numeric mt-1 text-sm font-semibold text-ink">
            {formatMoney(goal.remainingAmount)}
          </dd>
        </div>
      </dl>

      <p className="text-sm text-ink-muted">
        Regra: {RULE_LABELS[goal.progressRule] ?? goal.progressRule}
      </p>
      {unallocated > 0 ? (
        <p className="text-sm text-ink">Saldo não alocado: {formatMoney(goal.unallocatedAmount)}</p>
      ) : null}
      {excess > 0 ? (
        <p className="text-sm text-ink">Excesso sobre a meta: {formatMoney(goal.excessAmount)}</p>
      ) : null}

      {goal.allocations.length > 0 ? (
        <div className="space-y-2">
          <h4 className="text-sm font-semibold text-ink">Alocações</h4>
          <ul className="divide-y divide-line">
            {goal.allocations.map((allocation) => (
              <li
                key={allocation.id}
                className="flex flex-col gap-1 py-2 text-sm sm:flex-row sm:items-center sm:justify-between"
              >
                <span className="text-ink">
                  {SOURCE_LABELS[allocation.sourceType] ?? allocation.sourceType}
                  {allocation.sourceName ? ` · ${allocation.sourceName}` : ''} ·{' '}
                  {MODE_LABELS[allocation.allocationMode] ?? allocation.allocationMode}
                </span>
                <span className="text-ink-muted">
                  {allocation.allocationMode === 'PERCENTAGE'
                    ? `${allocation.percentage}%`
                    : formatMoney(allocation.allocatedAmount)}
                </span>
                <span className="numeric font-medium text-ink">
                  Contribuição: {formatMoney(allocation.contributionAmount)}
                </span>
                {allocation.capacityExceeded ? (
                  <span className="w-full text-sm text-warning sm:w-auto">
                    Capacidade da fonte excedida. Ajuste esta alocação explicitamente.
                  </span>
                ) : null}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {goal.progressHistory.length > 0 ? (
        <details>
          <summary className="cursor-pointer text-sm font-medium text-ink">
            Histórico de progresso
          </summary>
          <p className="mt-2 text-xs text-ink-muted">
            Registro sob demanda: um snapshot por dia em que a meta é consultada ou alterada. Dias
            sem acesso não geram ponto no histórico.
          </p>
          <ul className="mt-2 space-y-1 text-sm text-ink-muted">
            {goal.progressHistory.map((entry) => (
              <li key={entry.referenceDate}>
                {formatDate(entry.referenceDate)} · {entry.percentage}% · atual{' '}
                {formatMoney(entry.currentValue)} · restante {formatMoney(entry.remainingAmount)}
              </li>
            ))}
          </ul>
        </details>
      ) : null}

      {editing ? (
        <div className="rounded-card border border-line bg-surface-muted p-3">
          <GoalForm
            initial={goal}
            pending={update.isPending}
            submitLabel="Salvar meta"
            onSubmit={(input) => update.mutate(input)}
            onCancel={() => setEditing(false)}
          />
        </div>
      ) : null}

      {confirming ? (
        <div className="space-y-2 rounded-card border border-warning/40 bg-warning/5 p-3">
          <p className="text-sm text-ink">
            Excluir esta meta? Alocações, histórico e progresso serão removidos.
          </p>
          <button
            type="button"
            disabled={remove.isPending}
            onClick={() => remove.mutate()}
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-expense/50 bg-expense/5 px-3 text-sm font-semibold text-expense disabled:opacity-60"
          >
            Confirmar exclusão
          </button>
        </div>
      ) : null}

      {error ? (
        <p role="alert" className="text-sm text-expense">
          {error}
        </p>
      ) : null}

      <div className="flex flex-wrap gap-2">
        {goal.status === 'CHANGED' ? (
          <button
            type="button"
            disabled={changeStatus.isPending}
            onClick={() => changeStatus.mutate('ACTIVE')}
            className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface disabled:opacity-60"
          >
            Entendi a alteração
          </button>
        ) : null}
        {frozen ? (
          <button
            type="button"
            disabled={changeStatus.isPending}
            onClick={() => changeStatus.mutate('ACTIVE')}
            className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface disabled:opacity-60"
          >
            Reativar
          </button>
        ) : (
          <>
            <button
              type="button"
              disabled={changeStatus.isPending}
              onClick={() => changeStatus.mutate('PAUSED')}
              className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-medium text-ink hover:bg-surface-muted disabled:opacity-60"
            >
              Pausar
            </button>
            <button
              type="button"
              disabled={changeStatus.isPending}
              onClick={() => changeStatus.mutate('ARCHIVED')}
              className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-medium text-ink hover:bg-surface-muted disabled:opacity-60"
            >
              Arquivar
            </button>
          </>
        )}
        <button
          type="button"
          onClick={() => {
            setEditing((value) => !value)
            setConfirming(false)
          }}
          className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-medium text-ink hover:bg-surface-muted"
        >
          {editing ? 'Cancelar edição' : 'Editar'}
        </button>
        <button
          type="button"
          onClick={() => {
            setConfirming((value) => !value)
            setEditing(false)
          }}
          className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-medium text-ink hover:bg-surface-muted"
        >
          {confirming ? 'Cancelar exclusão' : 'Excluir'}
        </button>
      </div>
    </article>
  )
}
