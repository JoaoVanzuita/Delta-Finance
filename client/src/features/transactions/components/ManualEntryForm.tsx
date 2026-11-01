import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDownLeft, ArrowLeftRight, ArrowUpRight, ChevronDown, Info } from 'lucide-react'

import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api/problem'
import { formatMoney } from '@/lib/format'
import { invalidateTransactionViews } from '@/lib/invalidation'
import { queryKeys } from '@/lib/query-keys'

import { financeApi, type ManualTransactionInput } from '@/features/integration/api'

const TYPE_OPTIONS = [
  { value: 'CREDIT', label: 'Receita' },
  { value: 'DEBIT', label: 'Despesa' },
  { value: 'TRANSFER', label: 'Transferência entre contas' },
  { value: 'REFUND', label: 'Reembolso' },
  { value: 'CHARGEBACK', label: 'Chargeback' },
  { value: 'ADJUSTMENT', label: 'Ajuste' },
] as const

type ManualType = (typeof TYPE_OPTIONS)[number]['value']

function today(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date())
}

interface ManualEntryFormProps {
  readonly initialType?: ManualType
  readonly initialAccountId?: string
  readonly onCreated?: () => void
}

/**
 * Formulário de lançamento manual.
 *
 * O tipo vem primeiro e define o efeito esperado. Campos condicionais aparecem só
 * quando aplicáveis; observações ficam em divulgação progressiva. A semântica dos
 * tipos e o sinal do valor não são alterados.
 */
export function ManualEntryForm({
  initialType = 'DEBIT',
  initialAccountId,
  onCreated,
}: ManualEntryFormProps) {
  const queryClient = useQueryClient()
  const [type, setType] = useState<ManualType>(initialType)
  const [accountId, setAccountId] = useState(initialAccountId ?? '')
  const [transferAccountId, setTransferAccountId] = useState('')
  const [relatedTransactionId, setRelatedTransactionId] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [amount, setAmount] = useState('')
  const [postedOn, setPostedOn] = useState(today())
  const [description, setDescription] = useState('')
  const [adjustmentReason, setAdjustmentReason] = useState('')
  const [notes, setNotes] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const isTransfer = type === 'TRANSFER'
  const needsOriginal = type === 'REFUND' || type === 'CHARGEBACK'
  const isAdjustment = type === 'ADJUSTMENT'

  const accountsQuery = useQuery({ queryKey: queryKeys.accounts, queryFn: financeApi.accounts })
  const categoriesQuery = useQuery({
    queryKey: queryKeys.categories,
    queryFn: financeApi.categories,
  })
  const transactionsQuery = useQuery({
    queryKey: [...queryKeys.transactions, 'refund-originals', accountId],
    queryFn: () =>
      financeApi.transactions({ limit: 100, accountId, type: 'DEBIT', status: 'POSTED' }),
    enabled: needsOriginal && Boolean(accountId),
  })

  const accounts = accountsQuery.data?.items ?? []
  const categories = (categoriesQuery.data?.items ?? []).filter((category) => category.active)
  const originCandidates = transactionsQuery.data?.items ?? []
  const parsedAmount = Number(amount.replace(',', '.'))
  const validAmount = amount.trim() && Number.isFinite(parsedAmount) ? parsedAmount : null
  const amountPreview = amount.trim().startsWith('-') ? amount.trim().slice(1) : amount.trim()

  const create = useMutation({
    mutationFn: (input: ManualTransactionInput) => financeApi.createTransaction(input),
    onSuccess: async () => {
      setMessage('Lançamento registrado.')
      setError(null)
      setAmount('')
      setDescription('')
      setNotes('')
      setAdjustmentReason('')
      setRelatedTransactionId('')
      await invalidateTransactionViews(queryClient)
      onCreated?.()
    },
    onError: (caught) => {
      setMessage(null)
      setError(
        caught instanceof ApiError ? caught.message : 'Não foi possível registrar o lançamento.',
      )
    },
  })

  function submit() {
    setError(null)
    setMessage(null)
    if (!accountId) {
      setError('Selecione a conta.')
      return
    }
    if (!description.trim()) {
      setError('Informe a descrição.')
      return
    }
    if (!amount.trim() || validAmount === null || validAmount === 0) {
      setError('Informe um valor diferente de zero.')
      return
    }
    if (isTransfer && (!transferAccountId || transferAccountId === accountId)) {
      setError('Selecione uma conta de destino diferente da origem.')
      return
    }
    if (isAdjustment && !adjustmentReason.trim()) {
      setError('Informe o motivo do ajuste.')
      return
    }
    if (!postedOn) {
      setError('Informe a data.')
      return
    }

    create.mutate({
      accountId,
      type,
      postedOn,
      amount: amount.replace(',', '.'),
      description: description.trim(),
      categoryId: categoryId || undefined,
      transferAccountId: isTransfer ? transferAccountId : undefined,
      relatedTransactionId:
        needsOriginal && relatedTransactionId ? relatedTransactionId : undefined,
      adjustmentReason: isAdjustment ? adjustmentReason.trim() : undefined,
      notes: notes.trim() || undefined,
    })
  }

  const effect = expectedEffect(type, validAmount)
  const targetAccount = accounts.find((account) => account.id === transferAccountId)

  const selectedAccount = accounts.find((account) => account.id === accountId)
  const isImportedAccount = selectedAccount?.origin === 'IMPORTED'

  return (
    <form
      noValidate
      className="space-y-5"
      onSubmit={(event) => {
        event.preventDefault()
        submit()
      }}
    >
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

      <Card className="space-y-4 p-5 sm:p-6">
        <div>
          <p className="overline text-ink-muted">Primeiro passo</p>
          <h2 className="mt-1 text-section-title font-semibold text-ink">Tipo de movimentação</h2>
        </div>
        <Field
          id="manual-entry-type"
          label="Tipo"
          hint="O tipo define o efeito nos relatórios e os campos seguintes."
        >
          <Select
            id="manual-entry-type"
            value={type}
            onChange={(event) => {
              setType(event.target.value as ManualType)
              setRelatedTransactionId('')
            }}
          >
            {TYPE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>

        <div className="flex items-start gap-3 rounded-control border border-primary/25 bg-primary-soft p-3">
          {effect.icon}
          <div>
            <p className="text-body-sm font-semibold text-ink">{effect.label}</p>
            <p className="text-caption text-ink-muted">
              {effect.description}
              {validAmount !== null && effect.kind !== 'transfer'
                ? ` Valor: ${formatMoney(amountPreview, 'BRL')}.`
                : ''}
            </p>
          </div>
        </div>
      </Card>

      <Card className="space-y-4 p-5 sm:p-6">
        <div>
          <h2 className="text-section-title font-semibold text-ink">Dados da movimentação</h2>
          <p className="text-caption text-ink-muted">
            Conta, valor, data e descrição são obrigatórios.
          </p>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="manual-entry-account" label="Conta">
            <Select
              id="manual-entry-account"
              value={accountId}
              onChange={(event) => {
                setAccountId(event.target.value)
                setRelatedTransactionId('')
              }}
            >
              <option value="">Selecione</option>
              {accounts.map((account) => (
                <option key={account.id} value={account.id} disabled={account.status !== 'ACTIVE'}>
                  {account.alias ?? account.institutionName ?? account.id}
                  {account.status !== 'ACTIVE' ? ' (inativa)' : ''}
                </option>
              ))}
            </Select>
          </Field>

          <Field id="manual-entry-amount" label="Valor (R$)">
            <Input
              id="manual-entry-amount"
              type="text"
              inputMode="decimal"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
            />
          </Field>

          <Field id="manual-entry-date" label="Data">
            <Input
              id="manual-entry-date"
              type="date"
              value={postedOn}
              onChange={(event) => setPostedOn(event.target.value)}
            />
          </Field>

          <Field id="manual-entry-description" label="Descrição">
            <Input
              id="manual-entry-description"
              type="text"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              maxLength={500}
            />
          </Field>

          <Field id="manual-entry-category" label="Categoria" className="sm:col-span-2">
            <Select
              id="manual-entry-category"
              value={categoryId}
              onChange={(event) => setCategoryId(event.target.value)}
            >
              <option value="">Sem categoria</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </Select>
          </Field>
        </div>

        {isImportedAccount ? (
          <Alert variant="neutral">
            Esta conta é importada. O lançamento entra no histórico e nos relatórios, mas o saldo
            atual continua sendo o informado pela instituição; o saldo do provedor não é ajustado
            por lançamentos manuais.
          </Alert>
        ) : null}

        {isTransfer ? (
          <Field
            id="manual-entry-transfer-account"
            label="Conta de destino"
            hint="A transferência cria duas pernas e não é receita nem despesa."
          >
            <Select
              id="manual-entry-transfer-account"
              value={transferAccountId}
              onChange={(event) => setTransferAccountId(event.target.value)}
            >
              <option value="">Selecione</option>
              {accounts
                .filter((account) => account.id !== accountId)
                .map((account) => (
                  <option
                    key={account.id}
                    value={account.id}
                    disabled={account.status !== 'ACTIVE'}
                  >
                    {account.alias ?? account.institutionName ?? account.id}
                    {account.status !== 'ACTIVE' ? ' (inativa)' : ''}
                  </option>
                ))}
            </Select>
          </Field>
        ) : null}

        {isTransfer && targetAccount ? (
          <p className="text-caption text-ink-muted">
            Destino: {targetAccount.alias ?? targetAccount.institutionName ?? 'Conta'}.
          </p>
        ) : null}

        {needsOriginal ? (
          <Field
            id="manual-entry-original"
            label="Transação original"
            hint="Opcional. O vínculo permite reembolso parcial e limita a redução ao valor da despesa original."
          >
            <Select
              id="manual-entry-original"
              value={relatedTransactionId}
              onChange={(event) => setRelatedTransactionId(event.target.value)}
            >
              <option value="">Correspondência automática</option>
              {originCandidates.map((transaction) => (
                <option key={transaction.id} value={transaction.id}>
                  {transaction.description ?? transaction.id} · {transaction.postedOn}
                </option>
              ))}
            </Select>
          </Field>
        ) : null}

        {isAdjustment ? (
          <Field
            id="manual-entry-adjustment-reason"
            label="Motivo do ajuste"
            hint="O motivo fica registrado para auditoria."
          >
            <Input
              id="manual-entry-adjustment-reason"
              type="text"
              value={adjustmentReason}
              onChange={(event) => setAdjustmentReason(event.target.value)}
              maxLength={500}
            />
          </Field>
        ) : null}
      </Card>

      <details className="group rounded-card border border-line bg-surface">
        <summary className="flex min-h-12 cursor-pointer list-none items-center justify-between px-5 py-3 text-body-sm font-semibold text-ink marker:content-none">
          <span className="inline-flex items-center gap-2">
            <Info aria-hidden="true" className="size-4 text-ink-muted" />
            Observações
          </span>
          <span className="inline-flex items-center gap-1 text-caption font-normal text-ink-muted">
            Opcional
            <ChevronDown
              aria-hidden="true"
              className="size-4 transition-transform group-open:rotate-180"
            />
          </span>
        </summary>
        <div className="border-t border-line p-5">
          <Field id="manual-entry-notes" label="Observações">
            <Textarea
              id="manual-entry-notes"
              value={notes}
              onChange={(event) => setNotes(event.target.value)}
              maxLength={2000}
              rows={3}
            />
          </Field>
        </div>
      </details>

      <Button type="submit" loading={create.isPending} loadingLabel="Registrando…">
        Registrar lançamento
      </Button>
    </form>
  )
}

function expectedEffect(type: ManualType, amount: number | null) {
  if (type === 'TRANSFER') {
    return {
      kind: 'transfer' as const,
      label: 'Transferência',
      description: 'Sai de uma conta e entra em outra; não altera receitas ou despesas.',
      icon: <ArrowLeftRight aria-hidden="true" className="size-5 text-primary" />,
    }
  }
  if (type === 'CREDIT' || type === 'REFUND' || type === 'CHARGEBACK') {
    return {
      kind: 'income' as const,
      label: 'Entrada',
      description:
        'Aumenta o saldo da conta e pode compor receitas ou reduzir despesas vinculadas.',
      icon: <ArrowDownLeft aria-hidden="true" className="size-5 text-income" />,
    }
  }
  if (type === 'ADJUSTMENT' && amount !== null && amount > 0) {
    return {
      kind: 'income' as const,
      label: 'Entrada por ajuste',
      description: 'Ajuste positivo com motivo explícito.',
      icon: <ArrowDownLeft aria-hidden="true" className="size-5 text-income" />,
    }
  }
  return {
    kind: 'expense' as const,
    label: 'Saída',
    description: 'Reduz o saldo da conta e pode compor despesas.',
    icon: <ArrowUpRight aria-hidden="true" className="size-5 text-expense" />,
  }
}
