import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronRight, Plus, Target } from 'lucide-react'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Drawer } from '@/components/ui/drawer'
import { EmptyState } from '@/components/ui/empty-state'
import { PageHeader } from '@/components/ui/page-header'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api/problem'
import { invalidateGoalViews } from '@/lib/invalidation'
import { queryKeys } from '@/lib/query-keys'

import { financeApi, type Goal, type GoalInput } from '@/features/integration/api'

import { GoalDetailDrawer } from '../components/GoalDetailDrawer'
import { GoalForm } from '../components/GoalForm'
import { STATUS_LABELS, TYPE_LABELS, formatDate, formatMoney } from '../format'
import { formatPercent } from '@/lib/format'

function messageFrom(caught: unknown, fallback: string): string {
  return caught instanceof ApiError ? caught.message : fallback
}

/**
 * Metas financeiras: listagem enxuta e detalhes em drawer.
 * Nenhuma ação movimenta dinheiro ou altera o GoalProgressService.
 */
export function GoalsPage() {
  const queryClient = useQueryClient()
  const goalsQuery = useQuery({ queryKey: queryKeys.goals, queryFn: () => financeApi.goals() })
  const [form, setForm] = useState<'new' | Goal | null>(null)
  const [detail, setDetail] = useState<Goal | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: (input: GoalInput) =>
      form && form !== 'new' ? financeApi.updateGoal(form.id, input) : financeApi.createGoal(input),
    onSuccess: async () => {
      setError(null)
      setFormError(null)
      setMessage(form === 'new' ? 'Meta criada.' : 'Meta atualizada.')
      setForm(null)
      await invalidateGoalViews(queryClient)
    },
    onError: (caught) => {
      setMessage(null)
      setFormError(messageFrom(caught, 'Não foi possível salvar a meta.'))
    },
  })

  const goals = goalsQuery.data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        overline="Planejar"
        title="Metas"
        description="Objetivos com regra de progresso transparente. Alocações são planejamento e não movimentam dinheiro."
        actions={
          <Button
            type="button"
            onClick={() => {
              setFormError(null)
              setForm('new')
            }}
            leadingIcon={<Plus className="size-4" />}
          >
            Nova meta
          </Button>
        }
      />

      {message ? (
        <Alert variant="success" role="status">
          {message}
        </Alert>
      ) : null}
      {error ? (
        <Alert variant="danger" role="alert">
          {error}
        </Alert>
      ) : null}

      {goalsQuery.isLoading ? (
        <div className="space-y-3" role="status" aria-live="polite">
          <span className="sr-only">Carregando metas…</span>
          <Skeleton className="h-40 rounded-card" />
          <Skeleton className="h-40 rounded-card" />
        </div>
      ) : null}
      {goalsQuery.isError ? (
        <Alert variant="danger" title="Não foi possível carregar as metas">
          {messageFrom(goalsQuery.error, 'Tente novamente em instantes.')}
        </Alert>
      ) : null}
      {goalsQuery.isSuccess && goals.length === 0 ? (
        <EmptyState
          icon={<Target className="size-5" />}
          title="Nenhuma meta registrada"
          description="Crie a primeira meta para acompanhar progresso por alocações planejadas ou patrimônio líquido."
          action={
            <Button type="button" onClick={() => setForm('new')}>
              Criar meta
            </Button>
          }
        />
      ) : null}

      {goals.length > 0 ? (
        <section aria-labelledby="goals-heading" className="space-y-3">
          <h2 id="goals-heading" className="text-section-title font-semibold text-ink">
            Metas registradas
          </h2>
          <ul className="grid gap-3 md:grid-cols-2">
            {goals.map((goal) => (
              <li key={goal.id}>
                <GoalCard goal={goal} onOpen={() => setDetail(goal)} />
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      <Drawer
        open={form !== null}
        onClose={() => setForm(null)}
        title={form === 'new' || form === null ? 'Nova meta' : 'Editar meta'}
        description="Progresso e alocações são planejamento; nenhuma ação movimenta dinheiro."
        width="lg"
        busy={save.isPending}
      >
        {formError ? (
          <Alert variant="danger" className="mb-4">
            {formError}
          </Alert>
        ) : null}
        {form ? (
          <GoalForm
            key={form === 'new' ? 'new' : form.id}
            initial={form === 'new' ? undefined : form}
            pending={save.isPending}
            submitLabel={form === 'new' ? 'Criar meta' : 'Salvar meta'}
            onSubmit={(input) => save.mutate(input)}
            onCancel={() => setForm(null)}
          />
        ) : null}
      </Drawer>

      <GoalDetailDrawer
        key={`${detail?.id ?? 'none'}-${detail?.status ?? ''}-${detail?.percentage ?? ''}`}
        goal={detail}
        open={Boolean(detail)}
        onClose={() => setDetail(null)}
        onEdit={(goal) => {
          setDetail(null)
          setFormError(null)
          setForm(goal)
        }}
      />
    </div>
  )
}

function GoalCard({ goal, onOpen }: { readonly goal: Goal; readonly onOpen: () => void }) {
  const percentage = Math.min(100, Math.max(0, Number(goal.percentage)))

  return (
    <article
      aria-labelledby={`goal-${goal.id}-heading`}
      className="flex h-full flex-col gap-3 rounded-card border border-line bg-surface p-4"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 id={`goal-${goal.id}-heading`} className="truncate text-body font-semibold text-ink">
            {goal.name}
          </h3>
          <p className="mt-0.5 text-caption text-ink-muted">
            {TYPE_LABELS[goal.type] ?? goal.type} · prazo {formatDate(goal.targetDate)}
          </p>
        </div>
        <Badge
          variant={
            goal.status === 'ACTIVE'
              ? 'income'
              : goal.status === 'OVERDUE' || goal.status === 'CHANGED'
                ? 'warning'
                : 'neutral'
          }
        >
          {STATUS_LABELS[goal.status] ?? goal.status}
        </Badge>
      </div>

      <div className="space-y-1.5">
        <div className="flex items-baseline justify-between gap-2 text-caption text-ink-muted">
          <span>
            {goal.progressRule === 'ALLOCATIONS' ? 'Progresso por alocações' : 'Patrimônio líquido'}
          </span>
          <span className="numeric font-semibold text-ink">{formatPercent(goal.percentage)}</span>
        </div>
        <div
          role="progressbar"
          aria-label={`Progresso de ${goal.name}`}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Number(goal.percentage)}
          className="h-2.5 overflow-hidden rounded-pill bg-surface-muted"
        >
          <div className="h-full rounded-pill bg-primary" style={{ width: `${percentage}%` }} />
        </div>
      </div>

      <dl className="grid grid-cols-3 gap-2 text-caption">
        <div>
          <dt className="text-ink-muted">Alvo</dt>
          <dd className="numeric mt-0.5 font-semibold text-ink">
            {formatMoney(goal.targetAmount)}
          </dd>
        </div>
        <div>
          <dt className="text-ink-muted">Atual</dt>
          <dd className="numeric mt-0.5 font-semibold text-ink">
            {formatMoney(goal.currentValue)}
          </dd>
        </div>
        <div>
          <dt className="text-ink-muted">Restante</dt>
          <dd className="numeric mt-0.5 font-semibold text-ink">
            {formatMoney(goal.remainingAmount)}
          </dd>
        </div>
      </dl>

      <button
        type="button"
        onClick={onOpen}
        className="mt-auto inline-flex min-h-11 items-center justify-between rounded-control border border-line bg-surface px-3 text-body-sm font-semibold text-ink hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
      >
        Ver detalhes
        <ChevronRight aria-hidden="true" className="size-4" />
      </button>
    </article>
  )
}
