import { FileText, ReceiptText } from 'lucide-react'
import { Link } from 'react-router'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Drawer } from '@/components/ui/drawer'
import { formatDate, formatMoney } from '@/lib/format'
import { cn } from '@/lib/cn'

import type { Transaction } from '@/features/integration/api'

import { DeleteManualTransaction } from './DeleteManualTransaction'

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pendente',
  POSTED: 'Efetivada',
  SOURCE_REMOVED: 'Removida na origem',
  RECONCILED: 'Reconciliada',
}

const TYPE_LABELS: Record<string, string> = {
  DEBIT: 'Despesa',
  CREDIT: 'Receita',
  TRANSFER: 'Transferência',
  REFUND: 'Reembolso',
  CHARGEBACK: 'Chargeback',
  ADJUSTMENT: 'Ajuste',
}

const FIELD_LABELS: Record<string, string> = {
  description: 'Descrição',
  amount: 'Valor',
  postedOn: 'Data',
  categoryId: 'Categoria',
  notes: 'Observações',
  relatedTransactionId: 'Transação original',
}

const RECONCILIATION_LABELS: Record<string, string> = {
  NONE: 'Nenhuma correspondência',
  PENDING_MATCH: 'Aguardando confirmação',
  MATCHED: 'Movimento interno conciliado',
  SOURCE_REMOVED: 'Removida na origem',
}

export function TransactionDetailDrawer({
  transaction,
  open,
  onClose,
  accountName,
  categoryName,
  onCorrect,
}: {
  readonly transaction: Transaction | null
  readonly open: boolean
  readonly onClose: () => void
  readonly accountName?: string
  readonly categoryName?: string
  readonly onCorrect: (transaction: Transaction) => void
}) {
  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Detalhes da transação"
      description="Informações da transação, origem e correções. O contexto da lista é preservado."
      width="md"
      footer={
        <div className="flex flex-wrap justify-between gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Fechar
          </Button>
          {transaction ? (
            <div className="flex flex-wrap gap-2">
              {transaction.invoiceId ? (
                <Link
                  to={`/invoices/${transaction.invoiceId}`}
                  className="inline-flex min-h-11 items-center gap-2 rounded-control border border-line-strong bg-surface px-4 text-body-sm font-semibold text-ink hover:bg-surface-muted"
                >
                  <ReceiptText aria-hidden="true" className="size-4" />
                  Abrir fatura
                </Link>
              ) : null}
              <Button type="button" onClick={() => onCorrect(transaction)}>
                Corrigir transação
              </Button>
            </div>
          ) : null}
        </div>
      }
    >
      {transaction ? (
        <div className="space-y-5">
          <section>
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                <h3 className="text-section-title font-semibold text-ink">
                  {transaction.description || 'Sem descrição'}
                </h3>
                <p className="mt-1 text-caption text-ink-muted">
                  {formatDate(transaction.postedOn)} ·{' '}
                  {TYPE_LABELS[transaction.type] ?? transaction.type}
                </p>
              </div>
              <p
                className={cn(
                  'numeric text-metric-sm font-semibold',
                  Number(transaction.amount) < 0 ? 'text-expense' : 'text-income',
                )}
              >
                {formatMoney(transaction.amount, transaction.currency)}
              </p>
            </div>
            <div className="mt-3 flex flex-wrap gap-1">
              <Badge variant={transaction.status === 'PENDING' ? 'warning' : 'neutral'}>
                {STATUS_LABELS[transaction.status] ?? transaction.status}
              </Badge>
              <Badge variant="neutral">
                {transaction.source === 'IMPORTED' ? 'Importada' : 'Manual'}
              </Badge>
              {transaction.currency !== 'BRL' ? (
                <Badge variant="warning">Moeda {transaction.currency}</Badge>
              ) : null}
            </div>
          </section>

          <dl className="grid gap-x-4 gap-y-2 rounded-card border border-line bg-surface-muted p-4 text-body-sm sm:grid-cols-[140px_1fr]">
            <dt className="text-ink-muted">Conta</dt>
            <dd className="text-ink">{accountName ?? 'Conta não identificada'}</dd>
            <dt className="text-ink-muted">Categoria</dt>
            <dd className="text-ink">{categoryName ?? 'Sem categoria'}</dd>
            <dt className="text-ink-muted">Origem</dt>
            <dd className="text-ink">
              {transaction.source === 'IMPORTED' ? 'Importada' : 'Manual'}
            </dd>
            <dt className="text-ink-muted">Moeda</dt>
            <dd className="text-ink">{transaction.currency}</dd>
            <dt className="text-ink-muted">Estado</dt>
            <dd className="text-ink">{STATUS_LABELS[transaction.status] ?? transaction.status}</dd>
            <dt className="text-ink-muted">Conciliação</dt>
            <dd className="text-ink">
              {RECONCILIATION_LABELS[transaction.reconciliationState ?? ''] ?? 'Não informada'}
            </dd>
            {transaction.relatedTransactionId ? (
              <>
                <dt className="text-ink-muted">Transação original</dt>
                <dd className="text-ink">Vinculada para reembolso/chargeback</dd>
              </>
            ) : null}
            {transaction.invoiceId ? (
              <>
                <dt className="text-ink-muted">Fatura</dt>
                <dd className="text-ink">Vinculada a uma fatura</dd>
              </>
            ) : null}
          </dl>

          {transaction.notes ? (
            <section>
              <h4 className="text-body-sm font-semibold text-ink">Observações</h4>
              <p className="mt-1 text-body-sm text-ink-muted">{transaction.notes}</p>
            </section>
          ) : null}

          {(transaction.editedFields ?? []).length > 0 ? (
            <details className="rounded-card border border-line">
              <summary className="flex min-h-11 cursor-pointer items-center gap-2 px-3 py-2 text-body-sm font-medium text-ink">
                <FileText aria-hidden="true" className="size-4 text-ink-muted" />
                Dados de correção
              </summary>
              <div className="border-t border-line px-3 py-2 text-caption text-ink-muted">
                Campos corrigidos manualmente:{' '}
                {(transaction.editedFields ?? [])
                  .map((field) => FIELD_LABELS[field] ?? field)
                  .join(', ') || 'nenhum'}
              </div>
            </details>
          ) : null}

          <DeleteManualTransaction transaction={transaction} />
        </div>
      ) : null}
    </Drawer>
  )
}
