import { useRef, useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { ApiError } from '@/lib/api/problem'

import { formatMoney } from '@/lib/format'
import { invalidateInvoiceViews } from '@/lib/invalidation'

import { financeApi, type InvoicePaymentInput } from '@/features/integration/api'

interface InvoicePaymentDialogProps {
  readonly invoiceId: string
  readonly outstanding: string
  readonly currency?: string
  readonly onClose: () => void
}

/** Pagamento total ou parcial, sempre em conta ativa do próprio usuário. */
export function InvoicePaymentDialog({
  invoiceId,
  outstanding,
  currency = 'BRL',
  onClose,
}: InvoicePaymentDialogProps) {
  const queryClient = useQueryClient()
  const accountsQuery = useQuery({ queryKey: ['accounts'], queryFn: financeApi.accounts })
  const [paymentAccountId, setPaymentAccountId] = useState('')
  const [amount, setAmount] = useState(outstanding)
  const [paidOn, setPaidOn] = useState(today())
  const [error, setError] = useState<string | null>(null)
  const accountRef = useRef<HTMLSelectElement>(null)
  const parsedAmount = Number(amount.replace(',', '.'))
  const exceedsOutstanding = Number.isFinite(parsedAmount) && parsedAmount > Number(outstanding)
  const paymentAccounts = (accountsQuery.data?.items ?? []).filter(
    (account) =>
      account.status === 'ACTIVE' && account.type !== 'CREDIT' && account.currency === currency,
  )

  const save = useMutation({
    mutationFn: (input: InvoicePaymentInput) => financeApi.addInvoicePayment(invoiceId, input),
    onSuccess: async () => {
      await invalidateInvoiceViews(queryClient, invoiceId)
      onClose()
    },
    onError: (caught) => {
      setError(
        caught instanceof ApiError ? caught.message : 'Não foi possível registrar o pagamento.',
      )
    },
  })

  function submit() {
    setError(null)
    if (!paymentAccountId) {
      setError('Selecione a conta de pagamento.')
      return
    }
    const parsed = Number(amount.replace(',', '.'))
    if (!amount.trim() || Number.isNaN(parsed) || parsed <= 0) {
      setError('Informe um valor positivo.')
      return
    }
    save.mutate({
      paymentAccountId,
      amount: amount.replace(',', '.'),
      paidOn,
    })
  }

  return (
    <Dialog
      open
      onClose={onClose}
      title="Registrar pagamento"
      description="O pagamento reduz o saldo devedor e não cria nova despesa. O valor acima do saldo vira crédito para faturas futuras."
      size="sm"
      busy={save.isPending}
      initialFocusRef={accountRef}
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
        <Field
          id="invoice-payment-account"
          label="Conta de pagamento"
          hint={`Somente contas ativas em ${currency}, exceto cartões de crédito.`}
        >
          <Select
            ref={accountRef}
            id="invoice-payment-account"
            value={paymentAccountId}
            onChange={(event) => setPaymentAccountId(event.target.value)}
          >
            <option value="">Selecione</option>
            {paymentAccounts.map((account) => (
              <option key={account.id} value={account.id}>
                {account.alias ?? account.institutionName ?? account.id}
              </option>
            ))}
          </Select>
        </Field>

        {accountsQuery.isSuccess && paymentAccounts.length === 0 ? (
          <p className="rounded-card border border-warning/35 bg-warning-soft p-3 text-caption text-ink">
            Nenhuma conta ativa compatível com a moeda do cartão. Cadastre ou ative uma conta antes
            de registrar o pagamento.
          </p>
        ) : null}

        <Field
          id="invoice-payment-amount"
          label={`Valor (${currency === 'BRL' ? 'R$' : currency})`}
        >
          <Input
            id="invoice-payment-amount"
            type="text"
            inputMode="decimal"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
          />
        </Field>

        {exceedsOutstanding ? (
          <p className="rounded-card border border-line bg-surface-muted p-2 text-caption text-ink-muted">
            O excedente de {formatMoney((parsedAmount - Number(outstanding)).toFixed(2), currency)}{' '}
            será mantido como crédito do cartão.
          </p>
        ) : null}

        <Field id="invoice-payment-date" label="Data do pagamento">
          <Input
            id="invoice-payment-date"
            type="date"
            value={paidOn}
            onChange={(event) => setPaidOn(event.target.value)}
          />
        </Field>

        <div className="flex flex-wrap justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancelar
          </Button>
          <Button
            type="submit"
            loading={save.isPending}
            loadingLabel="Registrando…"
            disabled={paymentAccounts.length === 0}
          >
            Registrar pagamento
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

function today(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date())
}
