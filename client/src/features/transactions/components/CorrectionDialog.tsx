import { useRef, useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api/problem'
import { invalidateTransactionViews } from '@/lib/invalidation'
import { queryKeys } from '@/lib/query-keys'

import { financeApi, type Transaction } from '@/features/integration/api'

interface CorrectionDialogProps {
  readonly transaction: Transaction
  readonly onClose: () => void
}

/** Correção de descrição, data, valor, categoria e observações com auditoria no servidor. */
export function CorrectionDialog({ transaction, onClose }: CorrectionDialogProps) {
  const queryClient = useQueryClient()
  const categoriesQuery = useQuery({
    queryKey: queryKeys.categories,
    queryFn: financeApi.categories,
  })
  const [description, setDescription] = useState(transaction.description ?? '')
  const [postedOn, setPostedOn] = useState(transaction.postedOn)
  const [amount, setAmount] = useState(transaction.amount)
  const [categoryId, setCategoryId] = useState(transaction.categoryId ?? '')
  const [notes, setNotes] = useState(transaction.notes ?? '')
  const [relatedTransactionId, setRelatedTransactionId] = useState(
    transaction.relatedTransactionId ?? '',
  )
  const [error, setError] = useState<string | null>(null)
  const descriptionRef = useRef<HTMLInputElement>(null)
  const refund = transaction.type === 'REFUND' || transaction.type === 'CHARGEBACK'
  const originalsQuery = useQuery({
    queryKey: [...queryKeys.transactions, 'refund-originals', transaction.accountId],
    queryFn: () =>
      financeApi.transactions({
        accountId: transaction.accountId,
        type: 'DEBIT',
        limit: 100,
      }),
    enabled: refund,
  })

  const correct = useMutation({
    mutationFn: () =>
      financeApi.correctTransaction(transaction.id, {
        description: description.trim(),
        postedOn,
        amount: amount.replace(',', '.'),
        categoryId: categoryId || undefined,
        notes,
        relatedTransactionId: relatedTransactionId || undefined,
      }),
    onSuccess: async () => {
      await invalidateTransactionViews(queryClient)
      onClose()
    },
    onError: (caught) => {
      setError(
        caught instanceof ApiError ? caught.message : 'Não foi possível corrigir a transação.',
      )
    },
  })

  return (
    <Dialog
      open
      onClose={onClose}
      title="Corrigir transação"
      description="A correção prevalece sobre sincronizações futuras e preserva o valor original para auditoria."
      size="md"
      busy={correct.isPending}
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
          if (!description.trim()) {
            setError('Informe a descrição.')
            return
          }
          const parsed = Number(amount.replace(',', '.'))
          if (Number.isNaN(parsed) || parsed === 0) {
            setError('Informe um valor diferente de zero.')
            return
          }
          correct.mutate()
        }}
      >
        <Field id="correction-description" label="Descrição">
          <Input
            ref={descriptionRef}
            id="correction-description"
            type="text"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            maxLength={500}
          />
        </Field>

        <div className="grid gap-3 sm:grid-cols-2">
          <Field id="correction-date" label="Data">
            <Input
              id="correction-date"
              type="date"
              value={postedOn}
              onChange={(event) => setPostedOn(event.target.value)}
            />
          </Field>
          <Field id="correction-amount" label="Valor (R$)">
            <Input
              id="correction-amount"
              type="text"
              inputMode="decimal"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
            />
          </Field>
        </div>

        <Field id="correction-category" label="Categoria">
          <Select
            id="correction-category"
            value={categoryId}
            onChange={(event) => setCategoryId(event.target.value)}
          >
            <option value="">Sem categoria</option>
            {(categoriesQuery.data?.items ?? [])
              .filter((category) => category.active)
              .map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
          </Select>
        </Field>

        {refund ? (
          <Field
            id="correction-original"
            label="Despesa original"
            hint="O vínculo limita a redução ao valor da despesa original e herda a categoria."
          >
            <Select
              id="correction-original"
              value={relatedTransactionId}
              onChange={(event) => setRelatedTransactionId(event.target.value)}
            >
              <option value="">Sem vínculo</option>
              {(originalsQuery.data?.items ?? []).map((original) => (
                <option key={original.id} value={original.id}>
                  {original.description ?? original.id} · {original.postedOn} · {original.amount}
                </option>
              ))}
            </Select>
          </Field>
        ) : null}

        <Field id="correction-notes" label="Observações">
          <Textarea
            id="correction-notes"
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            maxLength={2000}
            rows={3}
          />
        </Field>

        <div className="flex flex-wrap justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={correct.isPending}>
            Cancelar
          </Button>
          <Button type="submit" loading={correct.isPending} loadingLabel="Salvando…">
            Salvar correção
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
