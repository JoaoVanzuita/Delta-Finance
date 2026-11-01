import { useState } from 'react'

import { useQuery } from '@tanstack/react-query'
import { KeyRound } from 'lucide-react'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { SectionHeader, PageHeader } from '@/components/ui/page-header'
import { queryKeys } from '@/lib/query-keys'

import { financeApi } from '../api'
import { ConnectionForm } from '../components/ConnectionForm'
import { ConnectionLimitNotice } from '../components/ConnectionLimitNotice'
import { ConnectionsList } from '../components/ConnectionsList'
import { CredentialsDialog } from '../components/CredentialsDialog'
import { SyncStatusPanel } from '../components/SyncStatusPanel'
import { connectionStatusLabels, overallSyncState } from '../syncLabels'

/**
 * Integração bancária (Meu Pluggy).
 *
 * A página prioriza estado geral, conexões, ações e histórico. Credenciais e
 * limites ficam em seção própria. O Client Secret permanece write-only e nunca
 * é gravado no navegador.
 */
export function IntegrationSetupPage() {
  const [credentialsOpen, setCredentialsOpen] = useState(false)
  const [savedMessage, setSavedMessage] = useState<string | null>(null)

  const integrationQuery = useQuery({
    queryKey: queryKeys.integration,
    queryFn: financeApi.integration,
  })
  const connectionsQuery = useQuery({
    queryKey: queryKeys.connections,
    queryFn: financeApi.connections,
  })

  const integration = integrationQuery.data
  const connections = connectionsQuery.data?.items ?? []
  const configured = Boolean(integration?.clientIdMasked)
  const overall = overallSyncState(integration, connections)
  const connectionLimit = integration?.connectionLimit
  const connectionCount = integration?.connectionCount ?? connections.length
  const connectionLimitReached = connectionLimit !== undefined && connectionCount >= connectionLimit

  function handleSaved() {
    setSavedMessage('Credenciais validadas e salvas.')
  }

  return (
    <div className="space-y-6">
      <PageHeader
        overline="Configurações"
        title="Integração bancária"
        description="Conecte sua conta Meu Pluggy para importar contas, cartões, transações e investimentos. Cada pessoa configura a própria aplicação pessoal."
        meta={
          <Badge
            variant={
              overall.tone === 'attention'
                ? 'warning'
                : overall.tone === 'positive'
                  ? 'income'
                  : 'neutral'
            }
          >
            {overall.label}
          </Badge>
        }
        actions={
          <Button
            leadingIcon={<KeyRound aria-hidden="true" className="size-4" />}
            onClick={() => {
              setSavedMessage(null)
              setCredentialsOpen(true)
            }}
          >
            {configured ? 'Atualizar credenciais' : 'Configurar credenciais'}
          </Button>
        }
      />

      {savedMessage ? (
        <Alert variant="success" role="status">
          {savedMessage}
        </Alert>
      ) : null}

      {integrationQuery.isError ? (
        <Alert variant="danger" role="alert">
          Não foi possível consultar a configuração da integração. Tente atualizar a página.
        </Alert>
      ) : null}

      {connectionsQuery.isError ? (
        <Alert variant="danger" role="alert">
          Não foi possível carregar as conexões.
        </Alert>
      ) : null}

      <SyncStatusPanel connections={connections} integrationConfigured={configured} />

      <ConnectionsList
        connections={connections}
        configured={configured}
        connectionLimit={connectionLimit}
      />

      <Card className="p-5 sm:p-6">
        <SectionHeader
          title="Adicionar conexão"
          description="Cada itemId corresponde a uma autorização bancária criada no Meu Pluggy. Adicione um item por vez."
        />

        {connectionLimit !== undefined && integration?.connectionLimitCheckedAt ? (
          <div className="mt-4">
            <ConnectionLimitNotice
              count={connectionCount}
              limit={connectionLimit}
              checkedAt={integration.connectionLimitCheckedAt}
            />
          </div>
        ) : null}

        <div className="mt-4">
          {configured ? (
            <ConnectionForm
              disabled={connectionLimitReached}
              onAdded={() =>
                setSavedMessage(
                  'Conexão adicionada. A primeira sincronização pode levar alguns instantes.',
                )
              }
            />
          ) : (
            <Alert variant="neutral">Salve credenciais válidas para adicionar conexões.</Alert>
          )}
        </div>
      </Card>

      <Card className="p-5 sm:p-6">
        <SectionHeader
          title="Meu Pluggy"
          description="Credenciais e limites da aplicação pessoal. O Client Secret é write-only e não é exibido novamente."
          actions={
            <Badge variant={configured ? 'income' : 'neutral'}>
              {integration
                ? (connectionStatusLabels[integration.status] ?? integration.status)
                : 'Carregando…'}
            </Badge>
          }
        />

        <dl className="mt-4 grid gap-3 text-body-sm sm:grid-cols-2">
          <div>
            <dt className="text-caption text-ink-muted">Client ID</dt>
            <dd className="mt-0.5 font-medium text-ink">
              {integration?.clientIdMasked ?? 'Não configurado'}
            </dd>
          </div>
          <div>
            <dt className="text-caption text-ink-muted">Conexões</dt>
            <dd className="numeric mt-0.5 text-ink">
              {connectionCount}
              {connectionLimit !== undefined ? ` de ${connectionLimit}` : ''}
            </dd>
          </div>
        </dl>

        <p className="mt-4 text-caption leading-relaxed text-ink-muted">
          As credenciais ficam cifradas no servidor. O Client Secret não é devolvido pela API, não é
          gravado no localStorage e não aparece em exportações.
        </p>
      </Card>

      <CredentialsDialog
        open={credentialsOpen}
        onClose={() => setCredentialsOpen(false)}
        integration={integration}
        onSaved={handleSaved}
      />
    </div>
  )
}
