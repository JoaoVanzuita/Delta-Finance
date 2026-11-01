import { useRef, useState } from 'react'

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Field } from '@/components/ui/field'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api/problem'
import { invalidateInvoiceViews } from '@/lib/invalidation'

import { formatMoney } from '@/features/accounts/format'
import { financeApi } from '@/features/integration/api'

interface ProviderReconciliationDialogProps {
  readonly invoiceId: string
  /** Ajuste a aplicar: positivo aumenta o total, negativo reduz. */
  readonly adjustment: string
  readonly onClose: () => void
}

/**
 * Ajuste explícito entre o total local e o total informado pelo provedor.
 *
 * O valor é lançado como item manual auditado; nenhum item existente é reescrito. Em fatura paga o
 * status é preservado.
 */
export function ProviderReconciliationDialog({
  invoiceId,
  adjustment,
  onClose,
}: ProviderReconciliationDialogProps) {
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('Divergência entre a fatura local e o total do provedor')
  const [error, setError] = useState<string | null>(null)
  const reasonRef = useRef<HTMLTextAreaElement>(null)
  const numeric = Number(adjustment)

  const save = useMutation({
    mutationFn: () => financeApi.reconcileInvoiceProvider(invoiceId, { reason: reason.trim() }),
    onSuccess: async () => {
      await invalidateInvoiceViews(queryClient, invoiceId)
      onClose()
    },
    onError: (caught) => {
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível conciliar a fatura.')
    },
  })

  function submit() {
    setError(null)
    if (!reason.trim()) {
      setError('Informe o motivo do ajuste.')
      return
    }
    save.mutate()
  }

  return (
    <Dialog
      open
      onClose={onClose}
      title="Conciliar com o total do provedor"
      description={
        numeric >= 0
          ? `O total desta fatura será aumentado em ${formatMoney(adjustment, 'BRL')}. O ajuste é um item manual auditado; compras e pagamentos existentes não são reescritos.`
          : `O total desta fatura será reduzido em ${formatMoney(adjustment.replace('-', ''), 'BRL')}. O ajuste é um item manual auditado; compras e pagamentos existentes não são reescritos.`
      }
      size="sm"
      busy={save.isPending}
      initialFocusRef={reasonRef}
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
        <Field id="provider-reconciliation-reason" label="Motivo do ajuste">
          <Textarea
            ref={reasonRef}
            id="provider-reconciliation-reason"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            maxLength={300}
            rows={3}
          />
        </Field>

        <div className="flex flex-wrap justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancelar
          </Button>
          <Button type="submit" loading={save.isPending} loadingLabel="Conciliando…">
            Registrar ajuste
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
