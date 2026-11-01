import { useRef, useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ShieldAlert, Trash2 } from 'lucide-react'
import { useNavigate } from 'react-router'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { PageHeader, SectionHeader } from '@/components/ui/page-header'
import { ApiError } from '@/lib/api/problem'
import { markSessionCleared } from '@/lib/api/client'
import { invalidateIntegrationViews } from '@/lib/invalidation'
import { queryKeys } from '@/lib/query-keys'

import { financeApi, type Integration } from '@/features/integration/api'
import { connectionStatusLabels } from '@/features/integration/syncLabels'
import { privacyApi } from '@/features/privacy/api'

/**
 * Privacidade e ciclo de vida.
 *
 * Exportação gera o ZIP do titular; a desconexão preserva o histórico por padrão
 * e a exclusão exige senha, a palavra EXCLUIR e ciência da retenção. As
 * consequências são resumidas antes de qualquer ação destrutiva.
 */
export function PrivacySettingsPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const integrationQuery = useQuery({
    queryKey: queryKeys.integration,
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
  const passwordRef = useRef<HTMLInputElement>(null)

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
      void invalidateIntegrationViews(queryClient)
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
    <div className="space-y-6">
      <PageHeader
        overline="Configurações"
        title="Privacidade e dados"
        description="Exporte uma cópia portátil dos seus dados, desconecte o provedor bancário ou encerre sua conta. Cada pessoa vê e controla somente os próprios dados."
      />

      <Card className="p-5 sm:p-6">
        <SectionHeader
          title="Exportar meus dados"
          description="ZIP com JSON Lines e CSV contendo perfil, contas, cartões, categorias, transações, faturas, itens, pagamentos, reconciliações, investimentos, patrimônio, metas, histórico de metas, integrações e auditoria."
        />
        <p className="mt-3 text-body-sm text-ink-muted">
          Segredos, credenciais, API Keys, tokens, chaves e hashes não são incluídos.
        </p>
        <Button
          className="mt-4"
          onClick={() => void exportData()}
          loading={exporting}
          loadingLabel="Gerando exportação…"
        >
          Baixar exportação
        </Button>
        {exportMessage ? (
          <Alert variant="success" className="mt-3" role="status">
            {exportMessage}
          </Alert>
        ) : null}
        {exportError ? (
          <Alert variant="danger" className="mt-3" role="alert">
            {exportError}
          </Alert>
        ) : null}
      </Card>

      <Card className="p-5 sm:p-6">
        <SectionHeader
          title="Desconectar integração"
          description="A desconexão remove as credenciais e interrompe novas sincronizações. Escolha se os dados já importados devem ser preservados."
        />

        {integrationQuery.isLoading ? (
          <p role="status" className="mt-3 text-body-sm text-ink-muted">
            Carregando estado da integração…
          </p>
        ) : null}
        {integrationQuery.isError ? (
          <Alert variant="danger" className="mt-3" role="alert">
            Não foi possível consultar a integração.
          </Alert>
        ) : null}
        {integration && !configured ? (
          <Alert variant="neutral" className="mt-3">
            Nenhuma integração com credenciais está configurada.
          </Alert>
        ) : null}

        {integration && configured ? (
          <div className="mt-3 space-y-3">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-body-sm text-ink-muted">Estado atual:</span>
              <Badge variant="neutral">{integrationLabel(integration)}</Badge>
            </div>

            <label className="flex items-start gap-2 rounded-card border border-line bg-surface-muted p-3 text-body-sm text-ink">
              <input
                type="checkbox"
                checked={deleteImportedData}
                onChange={(event) => setDeleteImportedData(event.target.checked)}
                className="mt-1 size-4 accent-[var(--df-expense)]"
              />
              <span>
                Excluir também os dados importados das contas conectadas. Contas manuais,
                lançamentos nessas contas e categorias são sempre preservados.
              </span>
            </label>

            <Button
              variant="secondary"
              onClick={() => {
                setDisconnectError(null)
                setDisconnectMessage(null)
                setConfirmDisconnect(true)
              }}
            >
              Desconectar integração
            </Button>
          </div>
        ) : null}

        {disconnectMessage ? (
          <Alert variant="success" className="mt-3" role="status">
            {disconnectMessage}
          </Alert>
        ) : null}
        {disconnectError ? (
          <Alert variant="danger" className="mt-3" role="alert">
            {disconnectError}
          </Alert>
        ) : null}
      </Card>

      <Card variant="danger" className="p-5 sm:p-6">
        <div className="flex items-start gap-3">
          <span
            aria-hidden="true"
            className="flex size-10 shrink-0 items-center justify-center rounded-control bg-surface text-expense"
          >
            <ShieldAlert className="size-5" />
          </span>
          <div className="min-w-0 flex-1">
            <h2 className="text-section-title font-semibold text-ink">Excluir minha conta</h2>
            <p className="mt-1 text-body-sm text-ink-muted">
              Esta ação é permanente e afeta apenas a sua conta nesta instalação.
            </p>

            <ul className="mt-3 list-disc space-y-1 pl-5 text-body-sm text-ink-muted">
              <li>Os dados ativos são removidos imediatamente e o acesso é encerrado.</li>
              <li>Cópias de backup são expiradas em até 30 dias.</li>
              <li>
                Eventos de auditoria sanitizados são mantidos por 1 ano para segurança e
                diagnóstico.
              </li>
              <li>Integração, jobs e sessões ativas são encerrados.</li>
            </ul>

            <Button
              variant="danger"
              className="mt-4"
              leadingIcon={<Trash2 aria-hidden="true" className="size-4" />}
              onClick={() => {
                setDeleteError(null)
                setDeleteOpen(true)
              }}
            >
              Excluir minha conta
            </Button>
          </div>
        </div>
      </Card>

      <Dialog
        open={confirmDisconnect}
        onClose={() => setConfirmDisconnect(false)}
        title="Confirmar desconexão"
        description={`As credenciais serão removidas e novas sincronizações serão interrompidas.${
          deleteImportedData
            ? ' Os dados importados serão excluídos; contas manuais, lançamentos nessas contas e categorias permanecem.'
            : ' Os dados já importados serão preservados.'
        }`}
        size="sm"
        busy={disconnectMutation.isPending}
      >
        {disconnectError ? (
          <Alert variant="danger" className="mb-4">
            {disconnectError}
          </Alert>
        ) : null}
        <div className="flex flex-wrap justify-end gap-2">
          <Button
            variant="secondary"
            onClick={() => setConfirmDisconnect(false)}
            disabled={disconnectMutation.isPending}
          >
            Cancelar
          </Button>
          <Button
            variant="danger"
            loading={disconnectMutation.isPending}
            loadingLabel="Desconectando…"
            onClick={() => disconnectMutation.mutate()}
          >
            Confirmar desconexão
          </Button>
        </div>
      </Dialog>

      <Dialog
        open={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        title="Excluir conta definitivamente"
        description="Esta ação não pode ser desfeita. Confirme sua senha e digite EXCLUIR."
        size="md"
        busy={deleteMutation.isPending}
        initialFocusRef={passwordRef}
      >
        {deleteError ? (
          <Alert variant="danger" className="mb-4">
            {deleteError}
          </Alert>
        ) : null}
        <div className="mb-4 rounded-card border border-expense/35 bg-expense-soft p-3 text-body-sm text-ink">
          <p className="font-semibold">O que será removido</p>
          <p className="mt-1 text-ink-muted">
            Contas, cartões, transações, faturas, investimentos, patrimônio, metas e histórico.
            Contas manuais criadas por outras pessoas da instalação não são afetadas.
          </p>
        </div>
        <form
          noValidate
          className="space-y-3"
          onSubmit={(event) => {
            event.preventDefault()
            submitDeletion()
          }}
        >
          <Field id="delete-account-password" label="Senha atual">
            <Input
              ref={passwordRef}
              id="delete-account-password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </Field>
          <Field id="delete-account-confirmation" label="Digite EXCLUIR para confirmar">
            <Input
              id="delete-account-confirmation"
              value={confirmation}
              onChange={(event) => setConfirmation(event.target.value)}
            />
          </Field>
          <label className="flex items-start gap-2 text-body-sm text-ink">
            <input
              type="checkbox"
              checked={policyAcknowledged}
              onChange={(event) => setPolicyAcknowledged(event.target.checked)}
              className="mt-1 size-4 accent-[var(--df-expense)]"
            />
            <span>
              Entendi que os dados ativos serão removidos, que backups expiram em até 30 dias e que
              a auditoria sanitizada é mantida por 1 ano.
            </span>
          </label>
          <div className="flex flex-wrap justify-end gap-2 pt-1">
            <Button
              variant="secondary"
              onClick={() => setDeleteOpen(false)}
              disabled={deleteMutation.isPending}
            >
              Cancelar
            </Button>
            <Button
              type="submit"
              variant="danger"
              loading={deleteMutation.isPending}
              loadingLabel="Excluindo…"
            >
              Excluir definitivamente
            </Button>
          </div>
        </form>
      </Dialog>
    </div>
  )
}

function integrationLabel(integration: Integration): string {
  return connectionStatusLabels[integration.status] ?? integration.status
}
