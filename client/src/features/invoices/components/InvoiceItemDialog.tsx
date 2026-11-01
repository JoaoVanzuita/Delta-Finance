import { useMemo, useRef, useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { ApiError } from '@/lib/api/problem'
import { invalidateInvoiceViews } from '@/lib/invalidation'

import { financeApi, type InvoiceItem, type InvoiceItemInput } from '@/features/integration/api'

const KINDS = [
  { value: 'PURCHASE', label: 'Compra' },
  { value: 'INSTALLMENT', label: 'Parcela' },
  { value: 'INTEREST', label: 'Juros' },
  { value: 'FEE', label: 'Encargo' },
  { value: 'TAX', label: 'Tarifa' },
  { value: 'CREDIT', label: 'Crédito' },
  { value: 'REFUND', label: 'Estorno' },
  { value: 'MANUAL_ADJUSTMENT', label: 'Ajuste manual' },
] as const

const EFFECTS = [
  { value: 'EXPENSE_INCREASE', label: 'Aumenta a despesa' },
  { value: 'EXPENSE_DECREASE', label: 'Reduz a despesa' },
  { value: 'NONE', label: 'Sem efeito no total' },
] as const

type Kind = (typeof KINDS)[number]['value']
type Effect = (typeof EFFECTS)[number]['value']

function defaultEffect(kind: Kind): Effect {
  if (kind === 'INTEREST' || kind === 'FEE' || kind === 'TAX') return 'EXPENSE_INCREASE'
  if (kind === 'CREDIT' || kind === 'REFUND') return 'EXPENSE_DECREASE'
  return 'NONE'
}

interface InvoiceItemDialogProps {
  readonly invoiceId: string
  readonly cardAccountId: string
  readonly item?: InvoiceItem
  readonly onClose: () => void
}

/** Item manual ou vínculo de transação, com efeito obrigatório e histórico auditado. */
export function InvoiceItemDialog({
  invoiceId,
  cardAccountId,
  item,
  onClose,
}: InvoiceItemDialogProps) {
  const queryClient = useQueryClient()
  const [kind, setKind] = useState<Kind>((item?.kind as Kind) ?? 'INTEREST')
  const [reportingEffect, setReportingEffect] = useState<Effect>(
    (item?.reportingEffect as Effect) ?? 'EXPENSE_INCREASE',
  )
  const [description, setDescription] = useState(item?.description ?? '')
  const [amount, setAmount] = useState(item?.amount ?? '')
  const [occurredOn, setOccurredOn] = useState(item?.occurredOn ?? today())
  const [transactionId, setTransactionId] = useState(item?.transactionId ?? '')
  const [installmentNumber, setInstallmentNumber] = useState(
    item?.installmentNumber ? String(item.installmentNumber) : '',
  )
  const [totalInstallments, setTotalInstallments] = useState(
    item?.totalInstallments ? String(item.totalInstallments) : '',
  )
  const [error, setError] = useState<string | null>(null)
  const descriptionRef = useRef<HTMLInputElement>(null)

  const transactionsQuery = useQuery({
    queryKey: ['transactions', 'invoice-items', cardAccountId],
    queryFn: () => financeApi.transactions({ limit: 100, accountId: cardAccountId }),
  })
  const candidates = useMemo(
    () =>
      (transactionsQuery.data?.items ?? []).filter(
        (transaction) =>
          !transaction.invoiceId ||
          transaction.invoiceId === invoiceId ||
          transaction.id === item?.transactionId,
      ),
    [transactionsQuery.data, invoiceId, item?.transactionId],
  )

  const save = useMutation({
    mutationFn: (input: InvoiceItemInput) =>
      item
        ? financeApi.updateInvoiceItem(item.id, input)
        : financeApi.addInvoiceItem(invoiceId, input),
    onSuccess: async () => {
      await invalidateInvoiceViews(queryClient, invoiceId)
      onClose()
    },
    onError: (caught) => {
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível salvar o item.')
    },
  })

  const requiresTransaction = kind === 'PURCHASE' || kind === 'INSTALLMENT'

  function submit() {
    setError(null)
    if (!description.trim()) {
      setError('Informe a descrição.')
      return
    }
    const parsed = Number(amount.replace(',', '.'))
    if (!amount.trim() || Number.isNaN(parsed) || parsed <= 0) {
      setError('Informe um valor positivo.')
      return
    }
    if (requiresTransaction && !transactionId) {
      setError('Compras e parcelas exigem uma transação vinculada.')
      return
    }
    save.mutate({
      kind,
      reportingEffect,
      description: description.trim(),
      amount: amount.replace(',', '.'),
      occurredOn,
      transactionId: transactionId || undefined,
      installmentNumber: installmentNumber ? Number(installmentNumber) : undefined,
      totalInstallments: totalInstallments ? Number(totalInstallments) : undefined,
    })
  }

  return (
    <Dialog
      open
      onClose={onClose}
      title={item ? 'Editar item da fatura' : 'Adicionar item à fatura'}
      description="O valor é sempre positivo; o efeito define a direção. Correções preservam o histórico."
      size="lg"
      busy={save.isPending}
      initialFocusRef={descriptionRef}
    >
      {error ? (
        <Alert variant="danger" className="mb-4">
          {error}
        </Alert>
      ) : null}

      <form
        noValidate
        className="space-y-3"
        onSubmit={(event) => {
          event.preventDefault()
          submit()
        }}
      >
        <div className="grid gap-3 sm:grid-cols-2">
          <Field id="invoice-item-kind" label="Tipo">
            <Select
              id="invoice-item-kind"
              value={kind}
              onChange={(event) => {
                const value = event.target.value as Kind
                setKind(value)
                setReportingEffect(defaultEffect(value))
              }}
            >
              {KINDS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          </Field>

          <Field id="invoice-item-effect" label="Efeito">
            <Select
              id="invoice-item-effect"
              value={reportingEffect}
              onChange={(event) => setReportingEffect(event.target.value as Effect)}
            >
              {EFFECTS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          </Field>
        </div>

        <Field id="invoice-item-description" label="Descrição">
          <Input
            ref={descriptionRef}
            id="invoice-item-description"
            type="text"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            maxLength={500}
          />
        </Field>

        <div className="grid gap-3 sm:grid-cols-2">
          <Field
            id="invoice-item-amount"
            label={Number(totalInstallments) > 1 ? 'Valor de cada parcela (R$)' : 'Valor (R$)'}
          >
            <Input
              id="invoice-item-amount"
              type="text"
              inputMode="decimal"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
            />
          </Field>
          <Field id="invoice-item-date" label="Data">
            <Input
              id="invoice-item-date"
              type="date"
              value={occurredOn}
              onChange={(event) => setOccurredOn(event.target.value)}
            />
          </Field>
        </div>

        {requiresTransaction ? (
          <Field
            id="invoice-item-transaction"
            label="Transação vinculada"
            hint="A compra original não é alterada; o item apenas registra a compra na fatura."
          >
            <Select
              id="invoice-item-transaction"
              value={transactionId}
              onChange={(event) => setTransactionId(event.target.value)}
            >
              <option value="">Selecione</option>
              {candidates.map((transaction) => (
                <option key={transaction.id} value={transaction.id}>
                  {transaction.description ?? transaction.id} · {transaction.postedOn} ·{' '}
                  {transaction.amount}
                </option>
              ))}
            </Select>
          </Field>
        ) : null}

        <div className="grid gap-3 sm:grid-cols-2">
          <Field id="invoice-item-installment" label="Parcela">
            <Input
              id="invoice-item-installment"
              type="number"
              min={1}
              value={installmentNumber}
              onChange={(event) => setInstallmentNumber(event.target.value)}
            />
          </Field>
          <Field id="invoice-item-total-installments" label="Total de parcelas">
            <Input
              id="invoice-item-total-installments"
              type="number"
              min={1}
              value={totalInstallments}
              onChange={(event) => setTotalInstallments(event.target.value)}
            />
          </Field>
        </div>
        {!item && Number(totalInstallments) > 1 ? (
          <p className="rounded-card border border-line bg-surface-muted p-3 text-caption text-ink-muted">
            Serão criadas {totalInstallments} parcelas, uma por ciclo. A primeira fica nesta fatura
            e as seguintes nas faturas futuras.
          </p>
        ) : null}

        <div className="flex flex-wrap justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancelar
          </Button>
          <Button type="submit" loading={save.isPending} loadingLabel="Salvando…">
            Salvar item
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

function today(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date())
}
