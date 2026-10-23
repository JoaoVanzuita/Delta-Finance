import { useEffect, useRef, useState } from 'react'

import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'

import { ApiError } from '@/lib/api/problem'

import { FormErrorSummary, type FormIssue } from '../components/FormErrorSummary'
import { useChangePassword, useLogout, useSession } from '../hooks/useSession'
import { passwordChangeSchema, type PasswordChangeFormValues } from '../validation'

const inputClass =
  'mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink shadow-sm outline-none transition-colors focus:border-focus'

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
    <div className="mx-auto w-full max-w-2xl space-y-8">
      <header className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">Conta</p>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Configurações da conta</h1>
        <p className="text-sm leading-relaxed text-ink-muted">
          Gerencie seu acesso. Seus dados financeiros permanecem privados e separados dos demais
          usuários.
        </p>
      </header>

      <section
        aria-labelledby="identity-heading"
        className="rounded-card border border-line bg-surface p-5 sm:p-6"
      >
        <h2 id="identity-heading" className="text-lg font-semibold text-ink">
          Identidade
        </h2>
        <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-[160px_1fr]">
          <dt className="text-ink-muted">Nome de usuário</dt>
          <dd className="font-medium text-ink">{user?.username ?? '—'}</dd>
          <dt className="text-ink-muted">Conta criada em</dt>
          <dd className="text-ink">{formatDate(user?.createdAt)}</dd>
        </dl>
      </section>

      <section
        aria-labelledby="password-heading"
        className="rounded-card border border-line bg-surface p-5 sm:p-6"
      >
        <h2 id="password-heading" className="text-lg font-semibold text-ink">
          Alterar senha
        </h2>
        <p className="mt-1 text-sm text-ink-muted">
          A nova senha segue a mesma política do cadastro: 12 a 128 caracteres. As outras sessões
          serão encerradas.
        </p>

        {status ? (
          <p
            role="status"
            className="mt-4 rounded-card border border-income/30 bg-income/5 p-3 text-sm text-ink"
          >
            {status}
          </p>
        ) : null}

        <form noValidate onSubmit={onSubmit} className="mt-4 space-y-5">
          <FormErrorSummary
            id="password-errors"
            issues={issues}
            summaryRef={summaryRef}
            title="Revise os dados da senha"
          />

          <div>
            <label
              htmlFor="password-currentPassword"
              className="block text-sm font-medium text-ink"
            >
              Senha atual
            </label>
            <input
              id="password-currentPassword"
              type="password"
              autoComplete="current-password"
              aria-invalid={Boolean(errors.currentPassword)}
              aria-describedby={
                errors.currentPassword ? 'password-currentPassword-error' : undefined
              }
              className={inputClass}
              {...register('currentPassword')}
            />
            {errors.currentPassword ? (
              <p id="password-currentPassword-error" className="mt-1 text-sm text-expense">
                {errors.currentPassword.message}
              </p>
            ) : null}
          </div>

          <div>
            <label htmlFor="password-newPassword" className="block text-sm font-medium text-ink">
              Nova senha
            </label>
            <input
              id="password-newPassword"
              type="password"
              autoComplete="new-password"
              aria-invalid={Boolean(errors.newPassword)}
              aria-describedby={errors.newPassword ? 'password-newPassword-error' : undefined}
              className={inputClass}
              {...register('newPassword')}
            />
            {errors.newPassword ? (
              <p id="password-newPassword-error" className="mt-1 text-sm text-expense">
                {errors.newPassword.message}
              </p>
            ) : null}
          </div>

          <div>
            <label
              htmlFor="password-confirmNewPassword"
              className="block text-sm font-medium text-ink"
            >
              Confirmar nova senha
            </label>
            <input
              id="password-confirmNewPassword"
              type="password"
              autoComplete="new-password"
              aria-invalid={Boolean(errors.confirmNewPassword)}
              aria-describedby={
                errors.confirmNewPassword ? 'password-confirmNewPassword-error' : undefined
              }
              className={inputClass}
              {...register('confirmNewPassword')}
            />
            {errors.confirmNewPassword ? (
              <p id="password-confirmNewPassword-error" className="mt-1 text-sm text-expense">
                {errors.confirmNewPassword.message}
              </p>
            ) : null}
          </div>

          <button
            type="submit"
            disabled={isSubmitting || changePassword.isPending}
            className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-60"
          >
            {changePassword.isPending ? 'Salvando…' : 'Salvar nova senha'}
          </button>
        </form>
      </section>

      <section
        aria-labelledby="session-heading"
        className="rounded-card border border-line bg-surface p-5 sm:p-6"
      >
        <h2 id="session-heading" className="text-lg font-semibold text-ink">
          Sessão
        </h2>
        <p className="mt-1 text-sm text-ink-muted">
          Ao sair, os dados em cache neste navegador são descartados.
        </p>
        <button
          type="button"
          onClick={() =>
            logout.mutate(undefined, {
              onSuccess: () => navigate('/login', { replace: true }),
            })
          }
          disabled={logout.isPending}
          className="mt-4 inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink transition-colors hover:bg-surface-muted disabled:cursor-not-allowed disabled:opacity-60"
        >
          {logout.isPending ? 'Saindo…' : 'Sair'}
        </button>
      </section>
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
