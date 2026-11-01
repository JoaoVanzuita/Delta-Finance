import { useState } from 'react'

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api/problem'
import { queryKeys } from '@/lib/query-keys'

import { financeApi, type Integration } from '../api'
import { SecretField } from './SecretField'

interface CredentialsDialogProps {
  readonly open: boolean
  readonly onClose: () => void
  readonly integration: Integration | undefined
  readonly onSaved: (integration: Integration) => void
}

/**
 * Credenciais do Meu Pluggy em fluxo próprio.
 *
 * O Client Secret é write-only: nunca é reidratado, nunca vai para o
 * localStorage e é limpo após o envio. Credenciais anteriores permanecem se a
 * validação falhar.
 */
export function CredentialsDialog({ open, onClose, integration, onSaved }: CredentialsDialogProps) {
  const queryClient = useQueryClient()
  const [clientId, setClientId] = useState('')
  const [clientSecret, setClientSecret] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () =>
      financeApi.saveCredentials({
        clientId: clientId.trim(),
        clientSecret,
      }),
    onSuccess: (saved) => {
      setClientId('')
      setClientSecret('')
      setError(null)
      setMessage(null)
      queryClient.setQueryData(queryKeys.integration, saved)
      void queryClient.invalidateQueries({ queryKey: queryKeys.connections })
      onSaved(saved)
      onClose()
    },
    onError: (caught) => {
      setMessage(null)
      setError(
        caught instanceof ApiError
          ? caught.message
          : 'Não foi possível validar as credenciais. Tente novamente.',
      )
    },
  })

  const validate = useMutation({
    mutationFn: () => financeApi.validateCredentials({ clientId: clientId.trim(), clientSecret }),
    onSuccess: () => {
      setError(null)
      setMessage('Credenciais válidas. Salve para ativar a integração.')
    },
    onError: (caught) => {
      setMessage(null)
      setError(
        caught instanceof ApiError
          ? caught.message
          : 'Não foi possível validar as credenciais. Tente novamente.',
      )
    },
  })

  const busy = save.isPending || validate.isPending

  function closeDialog() {
    if (busy) {
      return
    }
    // O segredo não permanece em memória depois que o fluxo é encerrado.
    setClientSecret('')
    setError(null)
    setMessage(null)
    onClose()
  }

  function submit() {
    setError(null)
    setMessage(null)
    if (!clientId.trim() || !clientSecret) {
      setError('Informe Client ID e Client Secret.')
      return
    }
    save.mutate()
  }

  return (
    <Dialog
      open={open}
      onClose={closeDialog}
      title="Credenciais do Meu Pluggy"
      description="Use a aplicação pessoal criada no Meu Pluggy. O Client Secret não é exibido novamente depois de salvo."
      size="md"
      busy={busy}
    >
      <div className="space-y-4">
        {integration?.clientIdMasked ? (
          <p className="text-body-sm text-ink-muted">
            Client ID configurado:{' '}
            <span className="font-medium text-ink">{integration.clientIdMasked}</span>
          </p>
        ) : null}

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

        <form
          noValidate
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault()
            submit()
          }}
        >
          <Field id="pluggy-client-id" label="Client ID">
            <Input
              id="pluggy-client-id"
              value={clientId}
              onChange={(event) => setClientId(event.target.value)}
              autoComplete="off"
              disabled={busy}
            />
          </Field>

          <SecretField
            label="Client Secret"
            value={clientSecret}
            onChange={setClientSecret}
            hint="Write-only: não é exibido novamente após salvar e nunca é armazenado no navegador."
            autoComplete="off"
            disabled={busy}
          />

          <div className="flex flex-wrap justify-end gap-2">
            <Button variant="secondary" onClick={closeDialog} disabled={busy}>
              Cancelar
            </Button>
            <Button
              variant="secondary"
              disabled={busy}
              loading={validate.isPending}
              loadingLabel="Validando…"
              onClick={() => {
                setError(null)
                setMessage(null)
                if (!clientId.trim() || !clientSecret) {
                  setError('Informe Client ID e Client Secret.')
                  return
                }
                validate.mutate()
              }}
            >
              Validar sem salvar
            </Button>
            <Button type="submit" disabled={busy} loading={save.isPending} loadingLabel="Salvando…">
              Salvar credenciais
            </Button>
          </div>
        </form>
      </div>
    </Dialog>
  )
}
