import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'

import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { PageHeader } from '@/components/ui/page-header'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api/problem'
import { invalidateAccountViews } from '@/lib/invalidation'
import { queryKeys } from '@/lib/query-keys'

import {
  financeApi,
  type Account,
  type AccountUpdateInput,
  type ManualAccountInput,
} from '@/features/integration/api'

const TYPES = [
  { value: 'CASH', label: 'Dinheiro' },
  { value: 'WALLET', label: 'Carteira digital' },
  { value: 'BANK', label: 'Conta bancária' },
] as const

type ManualType = (typeof TYPES)[number]['value']

function todayReference(): string {
  return `${new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date())}T12:00:00Z`
}

/**
 * Criação/edição de conta manual.
 *
 * O tipo é imutável após a criação; desativar impede novos lançamentos e preserva o histórico.
 * Dados principais e saldo ficam visíveis; observações ficam em divulgação progressiva.
 */
export function ManualAccountPage() {
  const { id } = useParams<{ id: string }>()
  const editing = Boolean(id)
  const accountQuery = useQuery({
    queryKey: queryKeys.account(id ?? ''),
    queryFn: () => financeApi.account(id ?? ''),
    enabled: editing,
  })

  if (editing && accountQuery.isLoading) {
    return (
      <p role="status" className="text-body-sm text-ink-muted">
        Carregando conta…
      </p>
    )
  }

  if (editing && accountQuery.isError) {
    return (
      <Alert variant="danger" title="Não foi possível carregar a conta">
        {accountQuery.error instanceof ApiError
          ? accountQuery.error.message
          : 'Tente novamente em instantes.'}
      </Alert>
    )
  }

  if (editing && accountQuery.data && accountQuery.data.origin !== 'MANUAL') {
    return (
      <Alert variant="warning" title="Conta importada não pode ser editada manualmente">
        Use “Renomear” na tela de detalhes para alterar apenas o nome de exibição. Os dados
        bancários continuam vindo do provedor.
      </Alert>
    )
  }

  return (
    <ManualAccountForm
      key={accountQuery.data?.id ?? 'new'}
      account={editing ? accountQuery.data : undefined}
    />
  )
}

function ManualAccountForm({ account }: { readonly account?: Account }) {
  const editing = Boolean(account)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [type, setType] = useState<ManualType>(() => {
    if (account?.type === 'CASH' || account?.type === 'WALLET' || account?.type === 'BANK') {
      return account.type
    }
    return 'CASH'
  })
  const [alias, setAlias] = useState(account?.alias ?? '')
  const [referenceBalance, setReferenceBalance] = useState(
    account?.referenceBalance ?? account?.currentBalance ?? '0.00',
  )
  const [referenceDate, setReferenceDate] = useState(
    account?.balanceReferenceAt ?? todayReference(),
  )
  const [status, setStatus] = useState<'ACTIVE' | 'INACTIVE'>(
    account?.status === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE',
  )
  const [notes, setNotes] = useState(account?.notes ?? '')
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const create = useMutation({
    mutationFn: (input: ManualAccountInput) => financeApi.createAccount(input),
    onSuccess: async (created) => {
      await invalidateAccountViews(queryClient)
      navigate(`/accounts/${created.id}`)
    },
    onError: (caught) => {
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível criar a conta.')
    },
  })

  const update = useMutation({
    mutationFn: (input: AccountUpdateInput) => financeApi.updateAccount(account?.id ?? '', input),
    onSuccess: async (updated) => {
      setMessage('Conta atualizada.')
      setError(null)
      queryClient.setQueryData(queryKeys.account(updated.id), updated)
      await invalidateAccountViews(queryClient)
    },
    onError: (caught) => {
      setMessage(null)
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível atualizar a conta.')
    },
  })

  function submit() {
    setError(null)
    setMessage(null)
    if (!alias.trim()) {
      setError('Informe o apelido da conta.')
      return
    }
    const normalizedBalance = referenceBalance.replace(',', '.')
    const parsed = Number(normalizedBalance)
    if (Number.isNaN(parsed)) {
      setError('Saldo de referência inválido.')
      return
    }
    if (editing) {
      update.mutate({
        alias: alias.trim(),
        referenceBalance: normalizedBalance,
        referenceDate,
        status,
        notes,
      })
    } else {
      create.mutate({
        type,
        alias: alias.trim(),
        referenceBalance: normalizedBalance,
        referenceDate,
        currency: 'BRL',
        notes,
      })
    }
  }

  const busy = create.isPending || update.isPending

  return (
    <div className="mx-auto w-full max-w-2xl space-y-6">
      <Link
        to={editing && account ? `/accounts/${account.id}` : '/accounts'}
        className="inline-flex min-h-11 items-center text-body-sm font-medium text-action underline underline-offset-4 hover:text-action-hover"
      >
        ← {editing ? 'Voltar para a conta' : 'Contas'}
      </Link>

      <PageHeader
        overline="Conta manual"
        title={editing ? 'Editar conta manual' : 'Nova conta manual'}
        description={
          editing
            ? 'Atualize os dados de referência e o estado da conta. O tipo permanece imutável.'
            : 'Informe o saldo de referência que você conhece. O saldo atual soma os lançamentos efetivados a partir dessa data.'
        }
      />

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
        onSubmit={(event) => {
          event.preventDefault()
          submit()
        }}
        className="space-y-4"
      >
        <Card className="space-y-4 p-5 sm:p-6">
          <div>
            <h2 className="text-section-title font-semibold text-ink">Dados principais</h2>
            <p className="text-caption text-ink-muted">
              O tipo define como a conta aparece nos relatórios.
            </p>
          </div>

          <Field
            id="manual-account-type"
            label="Tipo"
            hint={editing ? 'O tipo é imutável após a criação.' : undefined}
          >
            <Select
              id="manual-account-type"
              value={type}
              onChange={(event) => setType(event.target.value as ManualType)}
              disabled={editing}
            >
              {TYPES.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          </Field>

          <Field
            id="manual-account-alias"
            label="Apelido"
            hint="Nome exibido nas listas e relatórios."
            required
          >
            <Input
              id="manual-account-alias"
              type="text"
              value={alias}
              onChange={(event) => setAlias(event.target.value)}
              maxLength={120}
            />
          </Field>

          {editing ? (
            <Field id="manual-account-status" label="Estado">
              <Select
                id="manual-account-status"
                value={status}
                onChange={(event) => setStatus(event.target.value as 'ACTIVE' | 'INACTIVE')}
              >
                <option value="ACTIVE">Ativa</option>
                <option value="INACTIVE">Inativa (não recebe novos lançamentos)</option>
              </Select>
            </Field>
          ) : null}
        </Card>

        <Card className="space-y-4 p-5 sm:p-6">
          <div>
            <h2 className="text-section-title font-semibold text-ink">Saldo de referência</h2>
            <p className="text-caption text-ink-muted">
              Ponto de partida para calcular o saldo atual. Não é uma movimentação financeira.
            </p>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <Field id="manual-account-balance" label="Saldo de referência (R$)">
              <Input
                id="manual-account-balance"
                type="text"
                inputMode="decimal"
                value={referenceBalance}
                onChange={(event) => setReferenceBalance(event.target.value)}
              />
            </Field>

            <Field id="manual-account-date" label="Data de referência">
              <Input
                id="manual-account-date"
                type="date"
                value={referenceDate.slice(0, 10)}
                onChange={(event) =>
                  setReferenceDate(
                    event.target.value ? `${event.target.value}T12:00:00Z` : todayReference(),
                  )
                }
              />
            </Field>
          </div>
        </Card>

        <details className="group rounded-card border border-line bg-surface">
          <summary className="flex min-h-12 cursor-pointer list-none items-center justify-between px-5 py-3 text-body-sm font-semibold text-ink marker:content-none">
            Observações e detalhes
            <span className="text-caption font-normal text-ink-muted">Opcional</span>
          </summary>
          <div className="border-t border-line p-5">
            <Field id="manual-account-notes" label="Observações">
              <Textarea
                id="manual-account-notes"
                value={notes}
                onChange={(event) => setNotes(event.target.value)}
                maxLength={500}
                rows={3}
              />
            </Field>
          </div>
        </details>

        <div className="flex flex-wrap gap-2">
          <Button type="submit" loading={busy} loadingLabel="Salvando…">
            {editing ? 'Salvar alterações' : 'Criar conta manual'}
          </Button>
          <Link
            to={editing && account ? `/accounts/${account.id}` : '/accounts'}
            className="inline-flex min-h-11 items-center justify-center rounded-control border border-line-strong bg-surface px-4 text-body-sm font-semibold text-ink hover:bg-surface-muted"
          >
            Cancelar
          </Link>
        </div>
      </form>
    </div>
  )
}
