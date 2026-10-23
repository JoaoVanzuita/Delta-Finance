import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { financeApi, type Integration } from '../api'
import { ConnectionForm } from '../components/ConnectionForm'
import { ConnectionLimitNotice } from '../components/ConnectionLimitNotice'
import { SecretField } from '../components/SecretField'

const CONNECTION_LIMIT = Number(import.meta.env.VITE_PLUGGY_CONNECTION_LIMIT ?? 5)
const CONNECTION_LIMIT_CHECKED_AT =
  import.meta.env.VITE_PLUGGY_CONNECTION_LIMIT_CHECKED_AT ?? '2026-10-22'

const statusLabels: Record<string, string> = {
  INCOMPLETE: 'Configuração incompleta',
  ACTIVE: 'Ativa',
  SYNCING: 'Sincronizando',
  SYNCED: 'Sincronizada',
  CREDENTIAL_FAILURE: 'Falha de credenciais',
  ACTION_REQUIRED: 'Requer ação',
  AUTHORIZATION_EXPIRED: 'Autorização expirada',
  ITEM_UNAVAILABLE: 'Conexão indisponível',
}

export function IntegrationSetupPage() {
  const queryClient = useQueryClient()
  const [clientId, setClientId] = useState('')
  const [clientSecret, setClientSecret] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const integrationQuery = useQuery({
    queryKey: ['integration'],
    queryFn: financeApi.integration,
  })
  const connectionsQuery = useQuery({
    queryKey: ['connections'],
    queryFn: financeApi.connections,
  })

  const save = useMutation({
    mutationFn: () =>
      financeApi.saveCredentials({
        clientId: clientId.trim(),
        clientSecret,
      }),
    onSuccess: (integration: Integration) => {
      // Segredo nunca é reidratado: limpa os campos após o envio.
      setClientId('')
      setClientSecret('')
      setError(null)
      setMessage('Credenciais validadas e salvas.')
      queryClient.setQueryData(['integration'], integration)
      void queryClient.invalidateQueries({ queryKey: ['connections'] })
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

  const removeConnection = useMutation({
    mutationFn: (id: string) => financeApi.removeConnection(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['connections'] })
      void queryClient.invalidateQueries({ queryKey: ['accounts'] })
      void queryClient.invalidateQueries({ queryKey: ['integration'] })
    },
    onError: (caught) => {
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível remover a conexão.')
    },
  })

  const integration = integrationQuery.data
  const connections = connectionsQuery.data?.items ?? []
  const configured = Boolean(integration?.clientIdMasked)
  const busy = save.isPending || validate.isPending

  return (
    <div className="mx-auto w-full max-w-3xl space-y-8">
      <header className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">
          Configuração pessoal
        </p>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Meu Pluggy</h1>
        <p className="max-w-2xl text-sm leading-relaxed text-ink-muted">
          Cada pessoa configura a própria aplicação pessoal. As credenciais ficam cifradas no
          servidor e o Client Secret nunca é exibido novamente.
        </p>
      </header>

      <section
        aria-labelledby="credentials-heading"
        className="rounded-card border border-line bg-surface p-5 sm:p-6"
      >
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 id="credentials-heading" className="text-lg font-semibold text-ink">
              Credenciais
            </h2>
            <p className="mt-1 text-sm text-ink-muted">
              Client ID e Client Secret da sua aplicação pessoal no Meu Pluggy.
            </p>
          </div>
          <span className="rounded-card border border-line bg-surface-muted px-3 py-1 text-xs font-medium text-ink">
            {integration ? (statusLabels[integration.status] ?? integration.status) : 'Carregando…'}
          </span>
        </div>

        {integration?.clientIdMasked ? (
          <p className="mt-4 text-sm text-ink-muted">
            Client ID configurado:{' '}
            <span className="font-medium text-ink">{integration.clientIdMasked}</span>
          </p>
        ) : null}

        {message ? (
          <p
            role="status"
            className="mt-4 rounded-card border border-income/30 bg-income/5 p-3 text-sm text-ink"
          >
            {message}
          </p>
        ) : null}
        {error ? (
          <p
            role="alert"
            className="mt-4 rounded-card border border-expense/30 bg-expense/5 p-3 text-sm text-ink"
          >
            {error}
          </p>
        ) : null}

        <form
          noValidate
          className="mt-4 space-y-4"
          onSubmit={(event) => {
            event.preventDefault()
            setError(null)
            setMessage(null)
            if (!clientId.trim() || !clientSecret) {
              setError('Informe Client ID e Client Secret.')
              return
            }
            save.mutate()
          }}
        >
          <div>
            <label htmlFor="pluggy-client-id" className="block text-sm font-medium text-ink">
              Client ID
            </label>
            <input
              id="pluggy-client-id"
              type="text"
              value={clientId}
              onChange={(event) => setClientId(event.target.value)}
              autoComplete="off"
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink shadow-sm outline-none transition-colors focus:border-focus"
            />
          </div>

          <SecretField
            label="Client Secret"
            value={clientSecret}
            onChange={setClientSecret}
            hint="Write-only: não é exibido novamente após salvar e nunca é armazenado no navegador."
            autoComplete="off"
          />

          <div className="flex flex-wrap gap-3">
            <button
              type="submit"
              disabled={busy}
              className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-60"
            >
              {save.isPending ? 'Salvando…' : 'Salvar credenciais'}
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => {
                setError(null)
                setMessage(null)
                if (!clientId.trim() || !clientSecret) {
                  setError('Informe Client ID e Client Secret.')
                  return
                }
                validate.mutate()
              }}
              className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink transition-colors hover:bg-surface-muted disabled:cursor-not-allowed disabled:opacity-60"
            >
              {validate.isPending ? 'Validando…' : 'Validar sem salvar'}
            </button>
          </div>
        </form>
      </section>

      <section
        aria-labelledby="connections-heading"
        className="space-y-4 rounded-card border border-line bg-surface p-5 sm:p-6"
      >
        <div>
          <h2 id="connections-heading" className="text-lg font-semibold text-ink">
            Conexões
          </h2>
          <p className="mt-1 text-sm text-ink-muted">
            Cada itemId corresponde a uma autorização bancária. Adicione um item por vez.
          </p>
        </div>

        <ConnectionLimitNotice
          count={connections.length}
          limit={CONNECTION_LIMIT}
          checkedAt={CONNECTION_LIMIT_CHECKED_AT}
        />

        {configured ? (
          <ConnectionForm disabled={connections.length >= CONNECTION_LIMIT} />
        ) : (
          <p className="text-sm text-ink-muted">
            Salve credenciais válidas para adicionar conexões.
          </p>
        )}

        {connections.length === 0 ? (
          <p className="text-sm text-ink-muted">Nenhuma conexão configurada.</p>
        ) : (
          <ul className="divide-y divide-line rounded-card border border-line">
            {connections.map((connection) => (
              <li
                key={connection.id}
                className="flex flex-col gap-2 p-4 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="min-w-0">
                  <p className="font-medium text-ink">
                    {connection.institutionName ?? 'Instituição não informada'}
                  </p>
                  <p className="numeric text-xs text-ink-muted">
                    itemId {connection.itemIdMasked ?? 'não disponível'} · {connection.accountCount}{' '}
                    conta(s) · {statusLabels[connection.status] ?? connection.status}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => removeConnection.mutate(connection.id)}
                  disabled={removeConnection.isPending}
                  className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink transition-colors hover:bg-surface-muted disabled:opacity-60"
                >
                  Remover
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
