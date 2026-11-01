import { useState } from 'react'

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Drawer } from '@/components/ui/drawer'
import { ApiError } from '@/lib/api/problem'
import { invalidateGoalViews } from '@/lib/invalidation'
import { formatMoney, formatPercent } from '@/lib/format'
import type { Goal } from '@/features/integration/api'
import { financeApi } from '@/features/integration/api'

import {
  MODE_LABELS,
  RULE_LABELS,
  SOURCE_LABELS,
  STATUS_LABELS,
  TYPE_LABELS,
  formatDate,
} from '../format'

export function GoalDetailDrawer({
  goal,
  open,
  onClose,
  onEdit,
}: {
  readonly goal: Goal | null
  readonly open: boolean
  readonly onClose: () => void
  readonly onEdit: (goal: Goal) => void
}) {
  const queryClient = useQueryClient()
  const [current, setCurrent] = useState<Goal | null>(goal)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const statusMutation = useMutation({
    mutationFn: (status: 'ACTIVE' | 'PAUSED' | 'ARCHIVED') =>
      financeApi.updateGoalStatus(current?.id ?? '', status),
    onSuccess: async (updated) => {
      setCurrent(updated)
      setError(null)
      await invalidateGoalViews(queryClient)
    },
    onError: (caught) => {
      setError(
        caught instanceof ApiError ? caught.message : 'Não foi possível alterar o estado da meta.',
      )
    },
  })

  const deleteMutation = useMutation({
    mutationFn: () => financeApi.deleteGoal(current?.id ?? ''),
    onSuccess: async () => {
      setConfirmingDelete(false)
      await invalidateGoalViews(queryClient)
      onClose()
    },
    onError: (caught) => {
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível excluir a meta.')
    },
  })

  if (!current) {
    return (
      <Drawer open={open} onClose={onClose} title="Detalhes da meta">
        {null}
      </Drawer>
    )
  }

  const percentage = Math.min(100, Math.max(0, Number(current.percentage)))
  const frozen = current.status === 'PAUSED' || current.status === 'ARCHIVED'
  const busy = statusMutation.isPending || deleteMutation.isPending

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={current.name}
      description="Metas representam planejamento. Alocações não movimentam dinheiro."
      width="lg"
      busy={busy}
      bodyClassName="space-y-5"
      footer={
        <div className="flex flex-wrap justify-between gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Fechar
          </Button>
          <div className="flex flex-wrap gap-2">
            {current.status === 'CHANGED' ? (
              <Button type="button" onClick={() => statusMutation.mutate('ACTIVE')} disabled={busy}>
                Entendi a alteração
              </Button>
            ) : null}
            {frozen ? (
              <Button type="button" onClick={() => statusMutation.mutate('ACTIVE')} disabled={busy}>
                Reativar
              </Button>
            ) : (
              <>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => statusMutation.mutate('PAUSED')}
                  disabled={busy}
                >
                  Pausar
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => statusMutation.mutate('ARCHIVED')}
                  disabled={busy}
                >
                  Arquivar
                </Button>
              </>
            )}
            <Button type="button" onClick={() => onEdit(current)} disabled={busy}>
              Editar meta
            </Button>
          </div>
        </div>
      }
    >
      {error ? (
        <Alert variant="danger" role="alert">
          {error}
        </Alert>
      ) : null}

      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="text-body-sm text-ink-muted">
            {TYPE_LABELS[current.type] ?? current.type} · Prazo: {formatDate(current.targetDate)}
          </p>
          <p className="text-caption text-ink-muted">
            Regra: {RULE_LABELS[current.progressRule] ?? current.progressRule}
          </p>
        </div>
        <Badge variant={current.status === 'ACTIVE' ? 'income' : 'neutral'}>
          {STATUS_LABELS[current.status] ?? current.status}
        </Badge>
      </div>

      <div className="space-y-2">
        <div
          role="progressbar"
          aria-label={`Progresso de ${current.name}`}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Number(current.percentage)}
          className="h-3 overflow-hidden rounded-pill bg-surface-muted"
        >
          <div className="h-full rounded-pill bg-primary" style={{ width: `${percentage}%` }} />
        </div>
        <div className="flex flex-wrap justify-between gap-2 text-caption text-ink-muted">
          <span>{formatPercent(current.percentage)} concluído</span>
          <span>
            {current.progressRule === 'ALLOCATIONS'
              ? 'Progresso por alocações planejadas'
              : 'Progresso pelo patrimônio líquido atual'}
          </span>
        </div>
      </div>

      <dl className="grid grid-cols-2 gap-3 rounded-card border border-line bg-surface-muted p-4 text-body-sm sm:grid-cols-3">
        <Value label="Valor atual" value={formatMoney(current.currentValue)} />
        <Value label="Alvo" value={formatMoney(current.targetAmount)} />
        <Value label="Restante" value={formatMoney(current.remainingAmount)} />
      </dl>

      {current.description ? <p className="text-body-sm text-ink">{current.description}</p> : null}
      {Number(current.unallocatedAmount) > 0 ? (
        <Alert variant="info" title="Saldo não alocado">
          {formatMoney(current.unallocatedAmount)} do alvo ainda não foram distribuídos entre
          fontes.
        </Alert>
      ) : null}
      {Number(current.excessAmount) > 0 ? (
        <Alert variant="success" title="Acima da meta">
          Excesso de {formatMoney(current.excessAmount)}; o progresso permanece limitado a 100%.
        </Alert>
      ) : null}

      <section className="space-y-2">
        <h3 className="text-body-sm font-semibold text-ink">Alocações planejadas</h3>
        <p className="text-caption text-ink-muted">
          Alocações representam planejamento e não são movimentações financeiras reais.
        </p>
        {current.allocations.length === 0 ? (
          <p className="text-body-sm text-ink-muted">
            Nenhuma alocação explícita; o progresso usa a regra selecionada.
          </p>
        ) : (
          <ul className="divide-y divide-line rounded-card border border-line">
            {current.allocations.map((allocation) => (
              <li key={allocation.id} className="space-y-1 px-3 py-2.5 text-body-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-medium text-ink">
                    {SOURCE_LABELS[allocation.sourceType] ?? allocation.sourceType}
                    {allocation.sourceName ? ` · ${allocation.sourceName}` : ''}
                  </span>
                  <span className="numeric text-ink">
                    {allocation.allocationMode === 'PERCENTAGE'
                      ? formatPercent(allocation.percentage)
                      : formatMoney(allocation.allocatedAmount)}
                  </span>
                </div>
                <p className="text-caption text-ink-muted">
                  {MODE_LABELS[allocation.allocationMode] ?? allocation.allocationMode} ·
                  contribuição {formatMoney(allocation.contributionAmount)}
                </p>
                {allocation.capacityExceeded ? (
                  <p className="text-caption text-expense">
                    Capacidade da fonte excedida. Ajuste esta alocação explicitamente.
                  </p>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </section>

      {current.progressHistory.length > 0 ? (
        <details className="rounded-card border border-line">
          <summary className="min-h-11 cursor-pointer px-3 py-2.5 text-body-sm font-medium text-ink">
            Histórico de progresso
          </summary>
          <div className="border-t border-line px-3 py-2">
            <p className="text-caption text-ink-muted">
              Registro sob demanda; dias sem consulta não geram ponto.
            </p>
            <ul className="mt-2 space-y-1 text-caption text-ink-muted">
              {current.progressHistory.map((entry) => (
                <li key={entry.referenceDate}>
                  {formatDate(entry.referenceDate)} · {formatPercent(entry.percentage)} · atual{' '}
                  {formatMoney(entry.currentValue)} · restante {formatMoney(entry.remainingAmount)}
                </li>
              ))}
            </ul>
          </div>
        </details>
      ) : null}

      {current.notes ? (
        <section>
          <h3 className="text-body-sm font-semibold text-ink">Observações</h3>
          <p className="mt-1 text-body-sm text-ink-muted">{current.notes}</p>
        </section>
      ) : null}

      <section className="space-y-2 rounded-card border border-expense/30 bg-expense-soft p-4">
        <h3 className="text-body-sm font-semibold text-ink">Excluir meta</h3>
        <p className="text-caption text-ink-muted">
          Remove alocações, histórico e progresso desta meta. Nenhuma movimentação financeira é
          desfeita.
        </p>
        {confirmingDelete ? (
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="danger"
              loading={deleteMutation.isPending}
              loadingLabel="Excluindo…"
              onClick={() => deleteMutation.mutate()}
            >
              Confirmar exclusão
            </Button>
            <Button type="button" variant="secondary" onClick={() => setConfirmingDelete(false)}>
              Cancelar
            </Button>
          </div>
        ) : (
          <Button type="button" variant="ghost" onClick={() => setConfirmingDelete(true)}>
            Excluir meta
          </Button>
        )}
      </section>
    </Drawer>
  )
}

function Value({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-caption text-ink-muted">{label}</dt>
      <dd className="numeric mt-0.5 text-body-sm font-semibold text-ink">{value}</dd>
    </div>
  )
}
