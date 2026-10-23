import { useEffect, useRef, useState } from 'react'

import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router'

import { ApiError } from '@/lib/api/problem'

import { FormErrorSummary, type FormIssue } from '../components/FormErrorSummary'
import { useLogin } from '../hooks/useSession'
import { loginSchema, type LoginFormValues } from '../validation'

const inputClass =
  'mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink shadow-sm outline-none transition-colors focus:border-focus'

interface LoginLocationState {
  readonly registered?: boolean
  readonly username?: string
  readonly from?: string
}

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = (location.state ?? null) as LoginLocationState | null
  const loginMutation = useLogin()
  const [issues, setIssues] = useState<FormIssue[]>([])
  const summaryRef = useRef<HTMLDivElement>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    defaultValues: { username: state?.username ?? '', password: '' },
  })

  useEffect(() => {
    if (issues.length > 0) {
      summaryRef.current?.focus()
    }
  }, [issues])

  const onSubmit = handleSubmit(async (values) => {
    setIssues([])
    const parsed = loginSchema.safeParse(values)
    if (!parsed.success) {
      const nextIssues: FormIssue[] = []
      for (const issue of parsed.error.issues) {
        const field = String(issue.path[0] ?? 'username') as keyof LoginFormValues
        setError(field, { message: issue.message })
        nextIssues.push({ fieldId: `login-${field}`, message: issue.message })
      }
      setIssues(nextIssues)
      return
    }

    try {
      await loginMutation.mutateAsync(parsed.data)
      navigate(state?.from ?? '/account', { replace: true })
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.fieldErrors.length > 0) {
          const nextIssues: FormIssue[] = error.fieldErrors.map((fieldError) => ({
            fieldId: `login-${fieldError.field}`,
            message: fieldError.message,
          }))
          for (const fieldError of error.fieldErrors) {
            setError(fieldError.field as keyof LoginFormValues, { message: fieldError.message })
          }
          setIssues(nextIssues)
        } else {
          // Mensagem genérica do servidor: não distingue usuário inexistente de senha errada.
          setIssues([{ fieldId: 'login-username', message: mensagemDeLogin(error) }])
        }
      } else {
        setIssues([
          { fieldId: 'login-username', message: 'Não foi possível entrar. Tente novamente.' },
        ])
      }
    }
  })

  return (
    <div className="mx-auto w-full max-w-md">
      <header className="mb-6 space-y-2">
        <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">
          Conta pessoal
        </p>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Entrar</h1>
        <p className="text-sm leading-relaxed text-ink-muted">
          Acesse o seu espaço financeiro privado.
        </p>
      </header>

      {state?.registered ? (
        <p
          role="status"
          className="mb-4 rounded-card border border-income/30 bg-income/5 p-3 text-sm text-ink"
        >
          Conta criada. Entre com suas credenciais para continuar.
        </p>
      ) : null}

      <section className="rounded-card border border-line bg-surface p-5 sm:p-6">
        <form noValidate onSubmit={onSubmit} className="space-y-5">
          <FormErrorSummary
            id="login-errors"
            issues={issues}
            summaryRef={summaryRef}
            title="Não foi possível entrar"
          />

          <div>
            <label htmlFor="login-username" className="block text-sm font-medium text-ink">
              Nome de usuário
            </label>
            <input
              id="login-username"
              type="text"
              autoComplete="username"
              aria-invalid={Boolean(errors.username)}
              aria-describedby={errors.username ? 'login-username-error' : undefined}
              className={inputClass}
              {...register('username')}
            />
            {errors.username ? (
              <p id="login-username-error" className="mt-1 text-sm text-expense">
                {errors.username.message}
              </p>
            ) : null}
          </div>

          <div>
            <label htmlFor="login-password" className="block text-sm font-medium text-ink">
              Senha
            </label>
            <input
              id="login-password"
              type="password"
              autoComplete="current-password"
              aria-invalid={Boolean(errors.password)}
              aria-describedby={errors.password ? 'login-password-error' : undefined}
              className={inputClass}
              {...register('password')}
            />
            {errors.password ? (
              <p id="login-password-error" className="mt-1 text-sm text-expense">
                {errors.password.message}
              </p>
            ) : null}
          </div>

          <button
            type="submit"
            disabled={isSubmitting || loginMutation.isPending}
            className="inline-flex min-h-11 w-full items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-60"
          >
            {loginMutation.isPending ? 'Entrando…' : 'Entrar'}
          </button>
        </form>
      </section>

      <p className="mt-4 text-sm text-ink-muted">
        Ainda não tem conta?{' '}
        <Link to="/register" className="font-medium text-action underline underline-offset-2">
          Criar conta
        </Link>
      </p>
    </div>
  )
}

function mensagemDeLogin(error: ApiError): string {
  if (error.status === 429) {
    const seconds = error.retryAfterSeconds ?? 900
    const minutes = Math.max(1, Math.ceil(seconds / 60))
    return `Muitas tentativas de login. Tente novamente em ${minutes} minuto(s).`
  }
  return error.message
}
