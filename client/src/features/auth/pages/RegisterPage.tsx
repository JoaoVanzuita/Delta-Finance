import { useEffect, useRef, useState } from 'react'

import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'

import { ApiError } from '@/lib/api/problem'

import { FormErrorSummary, type FormIssue } from '../components/FormErrorSummary'
import { useRegister } from '../hooks/useSession'
import { registerSchema, type RegisterFormValues } from '../validation'

const inputClass =
  'mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink shadow-sm outline-none transition-colors focus:border-focus'

export function RegisterPage() {
  const navigate = useNavigate()
  const registerMutation = useRegister()
  const [issues, setIssues] = useState<FormIssue[]>([])
  const summaryRef = useRef<HTMLDivElement>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({
    defaultValues: { username: '', password: '' },
  })

  useEffect(() => {
    if (issues.length > 0) {
      summaryRef.current?.focus()
    }
  }, [issues])

  const onSubmit = handleSubmit(async (values) => {
    setIssues([])
    const parsed = registerSchema.safeParse(values)
    if (!parsed.success) {
      const nextIssues: FormIssue[] = []
      for (const issue of parsed.error.issues) {
        const field = String(issue.path[0] ?? 'username') as keyof RegisterFormValues
        setError(field, { message: issue.message })
        nextIssues.push({ fieldId: `register-${field}`, message: issue.message })
      }
      setIssues(nextIssues)
      return
    }

    try {
      await registerMutation.mutateAsync(parsed.data)
      navigate('/login', {
        state: { registered: true, username: parsed.data.username },
      })
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.fieldErrors.length > 0) {
          const nextIssues: FormIssue[] = []
          for (const fieldError of error.fieldErrors) {
            const field = fieldError.field as keyof RegisterFormValues
            setError(field, { message: fieldError.message })
            nextIssues.push({
              fieldId: `register-${fieldError.field}`,
              message: fieldError.message,
            })
          }
          setIssues(nextIssues)
        } else {
          setIssues([{ fieldId: 'register-username', message: error.message }])
          if (error.code === 'USERNAME_TAKEN') {
            setError('username', { message: error.message })
          }
        }
      } else {
        setIssues([
          {
            fieldId: 'register-username',
            message: 'Não foi possível concluir o cadastro. Tente novamente.',
          },
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
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Criar conta</h1>
        <p className="text-sm leading-relaxed text-ink-muted">
          Sua conta é individual: apenas você verá as suas contas, transações e conexões.
        </p>
      </header>

      <section className="rounded-card border border-line bg-surface p-5 sm:p-6">
        <form noValidate onSubmit={onSubmit} className="space-y-5">
          <FormErrorSummary
            id="register-errors"
            issues={issues}
            summaryRef={summaryRef}
            title="Revise os dados do cadastro"
          />

          <div>
            <label htmlFor="register-username" className="block text-sm font-medium text-ink">
              Nome de usuário
            </label>
            <p id="register-username-hint" className="mt-1 text-xs text-ink-muted">
              Entre 3 e 40 caracteres. Pode ser um apelido.
            </p>
            <input
              id="register-username"
              type="text"
              autoComplete="username"
              aria-invalid={Boolean(errors.username)}
              aria-describedby={
                errors.username
                  ? 'register-username-hint register-username-error'
                  : 'register-username-hint'
              }
              className={inputClass}
              {...register('username')}
            />
            {errors.username ? (
              <p id="register-username-error" className="mt-1 text-sm text-expense">
                {errors.username.message}
              </p>
            ) : null}
          </div>

          <div>
            <label htmlFor="register-password" className="block text-sm font-medium text-ink">
              Senha
            </label>
            <p id="register-password-hint" className="mt-1 text-xs text-ink-muted">
              Pelo menos 12 caracteres. Frases-senha são bem-vindas.
            </p>
            <input
              id="register-password"
              type="password"
              autoComplete="new-password"
              aria-invalid={Boolean(errors.password)}
              aria-describedby={
                errors.password
                  ? 'register-password-hint register-password-error'
                  : 'register-password-hint'
              }
              className={inputClass}
              {...register('password')}
            />
            {errors.password ? (
              <p id="register-password-error" className="mt-1 text-sm text-expense">
                {errors.password.message}
              </p>
            ) : null}
          </div>

          <button
            type="submit"
            disabled={isSubmitting || registerMutation.isPending}
            className="inline-flex min-h-11 w-full items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-60"
          >
            {registerMutation.isPending ? 'Criando conta…' : 'Criar conta'}
          </button>
        </form>
      </section>

      <p className="mt-4 text-sm text-ink-muted">
        Já tem conta?{' '}
        <Link to="/login" className="font-medium text-action underline underline-offset-2">
          Entrar
        </Link>
      </p>
    </div>
  )
}
