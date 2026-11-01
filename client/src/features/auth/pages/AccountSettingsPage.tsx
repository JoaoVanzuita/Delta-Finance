import { useEffect, useRef, useState } from 'react'

import { useForm } from 'react-hook-form'
import { KeyRound, LogOut, UserRound } from 'lucide-react'
import { useNavigate } from 'react-router'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { PageHeader, SectionHeader } from '@/components/ui/page-header'
import { ApiError } from '@/lib/api/problem'

import { FormErrorSummary, type FormIssue } from '../components/FormErrorSummary'
import { useChangePassword, useLogout, useSession } from '../hooks/useSession'
import { passwordChangeSchema, type PasswordChangeFormValues } from '../validation'

/**
 * Configurações da conta.
 *
 * Identidade, alteração de senha e encerramento de sessão. Não sugere recuperação
 * de senha porque esse fluxo não existe no produto.
 */
export function AccountSettingsPage() {
  const navigate = useNavigate()
  const { data: user } = useSession()
  const changePassword = useChangePassword()
  const logout = useLogout()
  const [issues, setIssues] = useState<FormIssue[]>([])
  const [status, setStatus] = useState<string | null>(null)
  const summaryRef = useRef<HTMLDivElement>(null)
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<PasswordChangeFormValues>({
    defaultValues: { currentPassword: '', newPassword: '', confirmNewPassword: '' },
  })

  useEffect(() => {
    if (issues.length > 0) {
      summaryRef.current?.focus()
    }
  }, [issues])

  const onSubmit = handleSubmit(async (values) => {
    setIssues([])
    setStatus(null)
    const parsed = passwordChangeSchema.safeParse(values)
    if (!parsed.success) {
      const nextIssues: FormIssue[] = []
      for (const issue of parsed.error.issues) {
        const field = String(issue.path[0] ?? 'newPassword') as keyof PasswordChangeFormValues
        setError(field, { message: issue.message })
        nextIssues.push({ fieldId: `password-${field}`, message: issue.message })
      }
      setIssues(nextIssues)
      return
    }

    try {
      await changePassword.mutateAsync({
        currentPassword: parsed.data.currentPassword,
        newPassword: parsed.data.newPassword,
      })
      reset()
      setStatus('Senha alterada. As outras sessões foram encerradas.')
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors.length > 0) {
        const nextIssues: FormIssue[] = []
        for (const fieldError of error.fieldErrors) {
          const field = fieldError.field as keyof PasswordChangeFormValues
          setError(field, { message: fieldError.message })
          nextIssues.push({
            fieldId: `password-${fieldError.field}`,
            message: fieldError.message,
          })
        }
        setIssues(nextIssues)
      } else if (error instanceof ApiError) {
        setIssues([{ fieldId: 'password-currentPassword', message: error.message }])
      } else {
        setIssues([
          {
            fieldId: 'password-currentPassword',
            message: 'Não foi possível alterar a senha. Tente novamente.',
          },
        ])
      }
    }
  })

  return (
    <div className="space-y-6">
      <PageHeader
        overline="Configurações"
        title="Configurações da conta"
        description="Gerencie seu acesso. Seus dados financeiros permanecem privados e separados dos demais usuários."
      />

      <Card className="p-5 sm:p-6">
        <SectionHeader
          title="Identidade"
          description="Informações da sua conta nesta instalação."
          actions={
            <span
              aria-hidden="true"
              className="flex size-9 items-center justify-center rounded-control bg-surface-muted text-ink-muted"
            >
              <UserRound className="size-5" />
            </span>
          }
        />
        <dl className="mt-4 grid gap-3 text-body-sm sm:grid-cols-[180px_1fr]">
          <dt className="text-ink-muted">Nome de usuário</dt>
          <dd className="font-medium text-ink">{user?.username ?? '—'}</dd>
          <dt className="text-ink-muted">Conta criada em</dt>
          <dd className="text-ink">{formatDate(user?.createdAt)}</dd>
          <dt className="text-ink-muted">Sessão</dt>
          <dd>
            <Badge variant="income">Autenticado</Badge>
          </dd>
        </dl>
      </Card>

      <Card className="p-5 sm:p-6">
        <SectionHeader
          title="Alterar senha"
          description="A nova senha segue a mesma política do cadastro: 12 a 128 caracteres. As outras sessões serão encerradas."
          actions={
            <span
              aria-hidden="true"
              className="flex size-9 items-center justify-center rounded-control bg-surface-muted text-ink-muted"
            >
              <KeyRound className="size-5" />
            </span>
          }
        />

        {status ? (
          <Alert variant="success" className="mt-4" role="status">
            {status}
          </Alert>
        ) : null}

        <form noValidate onSubmit={onSubmit} className="mt-4 max-w-xl space-y-4">
          <FormErrorSummary
            id="password-errors"
            issues={issues}
            summaryRef={summaryRef}
            title="Revise os dados da senha"
          />

          <Field
            id="password-currentPassword"
            label="Senha atual"
            error={errors.currentPassword?.message}
          >
            <Input
              id="password-currentPassword"
              type="password"
              autoComplete="current-password"
              aria-invalid={Boolean(errors.currentPassword)}
              aria-describedby={
                errors.currentPassword ? 'password-currentPassword-error' : undefined
              }
              {...register('currentPassword')}
            />
          </Field>

          <Field id="password-newPassword" label="Nova senha" error={errors.newPassword?.message}>
            <Input
              id="password-newPassword"
              type="password"
              autoComplete="new-password"
              aria-invalid={Boolean(errors.newPassword)}
              aria-describedby={errors.newPassword ? 'password-newPassword-error' : undefined}
              {...register('newPassword')}
            />
          </Field>

          <Field
            id="password-confirmNewPassword"
            label="Confirmar nova senha"
            error={errors.confirmNewPassword?.message}
          >
            <Input
              id="password-confirmNewPassword"
              type="password"
              autoComplete="new-password"
              aria-invalid={Boolean(errors.confirmNewPassword)}
              aria-describedby={
                errors.confirmNewPassword ? 'password-confirmNewPassword-error' : undefined
              }
              {...register('confirmNewPassword')}
            />
          </Field>

          <Button
            type="submit"
            disabled={isSubmitting || changePassword.isPending}
            loading={changePassword.isPending}
            loadingLabel="Salvando…"
          >
            Salvar nova senha
          </Button>
        </form>
      </Card>

      <Card className="p-5 sm:p-6">
        <SectionHeader
          title="Sessão"
          description="Ao sair, os dados em cache neste navegador são descartados."
          actions={
            <span
              aria-hidden="true"
              className="flex size-9 items-center justify-center rounded-control bg-surface-muted text-ink-muted"
            >
              <LogOut className="size-5" />
            </span>
          }
        />
        <Button
          variant="secondary"
          className="mt-4"
          loading={logout.isPending}
          loadingLabel="Saindo…"
          onClick={() =>
            logout.mutate(undefined, {
              onSuccess: () => navigate('/login', { replace: true }),
            })
          }
        >
          Sair
        </Button>
      </Card>
    </div>
  )
}

function formatDate(value?: string): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '—'
  }
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'long',
    timeZone: 'America/Sao_Paulo',
  }).format(date)
}
