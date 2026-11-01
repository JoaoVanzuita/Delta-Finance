import { useEffect, useRef, useState } from 'react'

import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router'

import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api/problem'

import { FormErrorSummary, type FormIssue } from '../components/FormErrorSummary'
import { useLogin } from '../hooks/useSession'
import { safeRedirectDestination } from '../redirect'
import { loginSchema, type LoginFormValues } from '../validation'

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
      navigate(safeRedirectDestination(state?.from), { replace: true })
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

  const busy = isSubmitting || loginMutation.isPending

  return (
    <div className="mx-auto w-full max-w-[var(--form-max)]">
      <header className="mb-6 space-y-2">
        <p className="overline text-ink-muted">Conta pessoal</p>
        <h1 className="text-page-title font-bold tracking-tight text-ink">Entrar</h1>
        <p className="text-body-sm text-ink-muted">Acesse o seu espaço financeiro privado.</p>
      </header>

      {state?.registered ? (
        <Alert variant="success" className="mb-4">
          Conta criada. Entre com suas credenciais para continuar.
        </Alert>
      ) : null}

      <Card className="p-5 sm:p-6">
        <form noValidate onSubmit={onSubmit} className="space-y-5">
          <FormErrorSummary
            id="login-errors"
            issues={issues}
            summaryRef={summaryRef}
            title="Não foi possível entrar"
          />

          <Field id="login-username" label="Nome de usuário" error={errors.username?.message}>
            <Input
              id="login-username"
              type="text"
              autoComplete="username"
              aria-invalid={Boolean(errors.username)}
              aria-describedby={errors.username ? 'login-username-error' : undefined}
              {...register('username')}
            />
          </Field>

          <Field id="login-password" label="Senha" error={errors.password?.message}>
            <Input
              id="login-password"
              type="password"
              autoComplete="current-password"
              aria-invalid={Boolean(errors.password)}
              aria-describedby={errors.password ? 'login-password-error' : undefined}
              {...register('password')}
            />
          </Field>

          <Button type="submit" fullWidth loading={busy} loadingLabel="Entrando…" disabled={busy}>
            Entrar
          </Button>
        </form>
      </Card>

      <p className="mt-4 text-body-sm text-ink-muted">
        Ainda não tem conta?{' '}
        <Link
          to="/register"
          state={{ from: state?.from }}
          className="font-medium text-action underline underline-offset-4"
        >
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
