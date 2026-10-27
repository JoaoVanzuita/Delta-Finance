import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'

import { ApiError } from '@/lib/api/problem'
import { markSessionCleared } from '@/lib/api/client'

import { financeApi, type Integration } from '@/features/integration/api'
import { privacyApi } from '@/features/privacy/api'

const cardClass = 'rounded-card border border-line bg-surface p-4 sm:p-5'
const primaryButtonClass =
  'inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover disabled:opacity-60'
const secondaryButtonClass =
  'inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink transition-colors hover:bg-surface-muted disabled:opacity-60'
const dangerButtonClass =
  'inline-flex min-h-11 items-center justify-center rounded-card bg-expense px-4 text-sm font-semibold text-surface transition-colors hover:opacity-90 disabled:opacity-60'

const statusLabels: Record<string, string> = {
  INCOMPLETE: 'Incompleta',
  ACTIVE: 'Ativa',
  SYNCING: 'Sincronizando',
  SYNCED: 'Sincronizada',
  CREDENTIAL_FAILURE: 'Falha de credencial',
  ACTION_REQUIRED: 'Ação necessária',
  AUTHORIZATION_EXPIRED: 'Autorização expirada',
  ITEM_UNAVAILABLE: 'Conexão indisponível',
}

/**
 * Configurações de privacidade.
 *
 * Exportação gera o ZIP do titular; a desconexão preserva o histórico por padrão e a exclusão exige
 * senha, a palavra EXCLUIR e ciência da política de retenção. Nenhum segredo é reidratado na tela.
 */
export function PrivacySettingsPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const integrationQuery = useQuery({
    queryKey: ['integration'],
    queryFn: financeApi.integration,
    retry: false,
  })

  const [exporting, setExporting] = useState(false)
  const [exportMessage, setExportMessage] = useState<string | null>(null)
  const [exportError, setExportError] = useState<string | null>(null)

  const [deleteImportedData, setDeleteImportedData] = useState(false)
  const [confirmDisconnect, setConfirmDisconnect] = useState(false)
  const [disconnectMessage, setDisconnectMessage] = useState<string | null>(null)
  const [disconnectError, setDisconnectError] = useState<string | null>(null)

  const [deleteOpen, setDeleteOpen] = useState(false)
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [policyAcknowledged, setPolicyAcknowledged] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const integration = integrationQuery.data
  const configured = Boolean(integration?.clientIdMasked)

  async function exportData() {
    setExporting(true)
    setExportError(null)
    setExportMessage(null)
    try {
      const blob = await privacyApi.exportData()
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = 'delta-finance-export.zip'
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(url)
      setExportMessage('Exportação gerada. O download deve iniciar automaticamente.')
    } catch (error) {
      setExportError(
        error instanceof ApiError
          ? error.message
          : 'Não foi possível gerar a exportação. Tente novamente.',
      )
    } finally {
      setExporting(false)
    }
  }

  const disconnectMutation = useMutation({
    mutationFn: () => financeApi.disconnect(deleteImportedData),
    onSuccess: () => {
      setConfirmDisconnect(false)
      setDisconnectError(null)
      setDisconnectMessage(
        'Integração desconectada. Novas sincronizações foram interrompidas e as credenciais removidas.',
      )
      void queryClient.invalidateQueries({ queryKey: ['integration'] })
      void queryClient.invalidateQueries({ queryKey: ['connections'] })
      void queryClient.invalidateQueries({ queryKey: ['syncStatus'] })
      void queryClient.invalidateQueries({ queryKey: ['syncRuns'] })
      void queryClient.invalidateQueries({ queryKey: ['accounts'] })
      void queryClient.invalidateQueries({ queryKey: ['transactions'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard-summary'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard-categories'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard-timeseries'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard-entries'] })
      void queryClient.invalidateQueries({ queryKey: ['cards', 'overview'] })
    },
    onError: (error) => {
      setDisconnectError(
        error instanceof ApiError ? error.message : 'Não foi possível desconectar a integração.',
      )
    },
  })

  const deleteMutation = useMutation({
    mutationFn: () => privacyApi.deleteAccount(password, 'EXCLUIR'),
    onSuccess: () => {
      markSessionCleared()
      queryClient.clear()
      navigate('/login', { replace: true, state: { accountDeleted: true } })
    },
    onError: (error) => {
      setDeleteError(
        error instanceof ApiError
          ? error.message
          : 'Não foi possível excluir a conta. Tente novamente.',
      )
    },
  })

  function submitDeletion() {
    setDeleteError(null)
    if (!password) {
      setDeleteError('Informe sua senha para confirmar.')
      return
    }
    if (confirmation.trim().toUpperCase() !== 'EXCLUIR') {
      setDeleteError('Digite EXCLUIR (em maiúsculas) para confirmar.')
      return
    }
    if (!policyAcknowledged) {
      setDeleteError('Confirme que entendeu a política de retenção e exclusão.')
      return
    }
    deleteMutation.mutate()
  }

  return (
    <div className="mx-auto w-full max-w-3xl space-y-6">
      <header className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">Conta</p>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Privacidade e dados</h1>
        <p className="max-w-2xl text-sm leading-relaxed text-ink-muted">
          Exporte uma cópia portátil dos seus dados, desconecte o provedor bancário ou encerre sua
          conta. Cada pessoa vê e controla somente os próprios dados.
        </p>
      </header>

      <section aria-labelledby="export-heading" className={cardClass}>
        <h2 id="export-heading" className="text-lg font-semibold text-ink">
          Exportar meus dados
        </h2>
        <p className="mt-1 text-sm leading-relaxed text-ink-muted">
          ZIP com JSON Lines e CSV contendo perfil, contas, cartões, categorias, transações,
          faturas, itens, pagamentos, reconciliações, investimentos, patrimônio, metas, histórico de
          metas, integrações e auditoria. Segredos, credenciais, API Keys, tokens, chaves e hashes
          não são incluídos.
        </p>
        <button
          type="button"
          onClick={() => void exportData()}
          disabled={exporting}
          className={`${primaryButtonClass} mt-4`}
        >
          {exporting ? 'Gerando exportação…' : 'Baixar exportação'}
        </button>
        {exportMessage ? (
          <p role="status" className="mt-3 text-sm text-income">
            {exportMessage}
          </p>
        ) : null}
        {exportError ? (
          <p role="alert" className="mt-3 text-sm text-expense">
            {exportError}
          </p>
        ) : null}
      </section>

      <section aria-labelledby="disconnect-heading" className={cardClass}>
        <h2 id="disconnect-heading" className="text-lg font-semibold text-ink">
          Desconectar integração
        </h2>
        {integrationQuery.isLoading ? (
          <p role="status" className="mt-2 text-sm text-ink-muted">
            Carregando estado da integração…
          </p>
        ) : null}
        {integrationQuery.isError ? (
          <p role="alert" className="mt-2 text-sm text-expense">
            Não foi possível consultar a integração.
          </p>
        ) : null}
        {integration && !configured ? (
          <p className="mt-2 text-sm text-ink-muted">
            Nenhuma integração com credenciais está configurada.
          </p>
        ) : null}
        {integration && configured ? (
          <>
            <p className="mt-2 text-sm text-ink-muted">
              Estado atual: <strong className="text-ink">{integrationLabel(integration)}</strong>. A
              desconexão remove as credenciais e interrompe novas sincronizações.
            </p>
            <label className="mt-3 flex items-start gap-2 text-sm text-ink">
              <input
                type="checkbox"
                checked={deleteImportedData}
                onChange={(event) => setDeleteImportedData(event.target.checked)}
                className="mt-1 size-4"
              />
              <span>
                Excluir também os dados importados das contas conectadas. Contas manuais,
                lançamentos nessas contas e categorias são sempre preservados.
              </span>
            </label>
            <button
              type="button"
              onClick={() => {
                setDisconnectError(null)
                setConfirmDisconnect(true)
              }}
              className={`${secondaryButtonClass} mt-4`}
            >
              Desconectar integração
            </button>
          </>
        ) : null}
        {disconnectMessage ? (
          <p role="status" className="mt-3 text-sm text-income">
            {disconnectMessage}
          </p>
        ) : null}
        {disconnectError ? (
          <p role="alert" className="mt-3 text-sm text-expense">
            {disconnectError}
          </p>
        ) : null}
      </section>

      <section
        aria-labelledby="delete-heading"
        className="rounded-card border border-expense/40 bg-expense/5 p-4 sm:p-5"
      >
        <h2 id="delete-heading" className="text-lg font-semibold text-ink">
          Excluir minha conta
        </h2>
        <p className="mt-1 text-sm leading-relaxed text-ink-muted">
          Os dados ativos são removidos imediatamente e o acesso é encerrado. Cópias de backup são
          expiradas em até 30 dias. Eventos de auditoria sanitizados são mantidos por 1 ano para
          segurança e diagnóstico. A operação não afeta outras pessoas da instalação.
        </p>
        <button
          type="button"
          onClick={() => {
            setDeleteError(null)
            setDeleteOpen(true)
          }}
          className={`${dangerButtonClass} mt-4`}
        >
          Excluir minha conta
        </button>
      </section>

      {confirmDisconnect ? (
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink/40 p-4 sm:items-center">
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="disconnect-dialog-heading"
            className="w-full max-w-md rounded-card border border-line bg-surface p-5 shadow-xl"
          >
            <h2 id="disconnect-dialog-heading" className="text-lg font-semibold text-ink">
              Confirmar desconexão
            </h2>
            <p className="mt-2 text-sm leading-relaxed text-ink-muted">
              As credenciais serão removidas e novas sincronizações serão interrompidas.
              {deleteImportedData
                ? ' Os dados importados serão excluídos; contas manuais, lançamentos nessas contas e categorias permanecem.'
                : ' Os dados já importados serão preservados.'}
            </p>
            <div className="mt-4 flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => disconnectMutation.mutate()}
                disabled={disconnectMutation.isPending}
                className={dangerButtonClass}
              >
                {disconnectMutation.isPending ? 'Desconectando…' : 'Confirmar desconexão'}
              </button>
              <button
                type="button"
                onClick={() => setConfirmDisconnect(false)}
                className={secondaryButtonClass}
              >
                Cancelar
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {deleteOpen ? (
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink/40 p-4 sm:items-center">
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="delete-dialog-heading"
            className="max-h-[90dvh] w-full max-w-lg overflow-y-auto rounded-card border border-line bg-surface p-5 shadow-xl"
          >
            <div className="flex items-start justify-between gap-3">
              <h2 id="delete-dialog-heading" className="text-lg font-semibold text-ink">
                Excluir conta definitivamente
              </h2>
              <button
                type="button"
                onClick={() => setDeleteOpen(false)}
                className={secondaryButtonClass}
              >
                Fechar
              </button>
            </div>
            <p className="mt-2 text-sm leading-relaxed text-ink-muted">
              Esta ação não pode ser desfeita. Confirme sua senha e digite <strong>EXCLUIR</strong>.
            </p>
            {deleteError ? (
              <p role="alert" className="mt-3 text-sm text-expense">
                {deleteError}
              </p>
            ) : null}
            <form
              noValidate
              className="mt-4 space-y-3"
              onSubmit={(event) => {
                event.preventDefault()
                submitDeletion()
              }}
            >
              <label className="block text-sm">
                <span className="font-medium text-ink">Senha atual</span>
                <input
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
                />
              </label>
              <label className="block text-sm">
                <span className="font-medium text-ink">Digite EXCLUIR para confirmar</span>
                <input
                  value={confirmation}
                  onChange={(event) => setConfirmation(event.target.value)}
                  className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
                />
              </label>
              <label className="flex items-start gap-2 text-sm text-ink">
                <input
                  type="checkbox"
                  checked={policyAcknowledged}
                  onChange={(event) => setPolicyAcknowledged(event.target.checked)}
                  className="mt-1 size-4"
                />
                <span>
                  Entendi que os dados ativos serão removidos, que backups expiram em até 30 dias e
                  que a auditoria sanitizada é mantida por 1 ano.
                </span>
              </label>
              <div className="flex flex-wrap gap-2 pt-1">
                <button
                  type="submit"
                  disabled={deleteMutation.isPending}
                  className={dangerButtonClass}
                >
                  {deleteMutation.isPending ? 'Excluindo…' : 'Excluir definitivamente'}
                </button>
                <button
                  type="button"
                  onClick={() => setDeleteOpen(false)}
                  className={secondaryButtonClass}
                >
                  Cancelar
                </button>
              </div>
            </form>
          </div>
        </div>
      ) : null}
    </div>
  )
}

function integrationLabel(integration: Integration): string {
  return statusLabels[integration.status] ?? integration.status
}
