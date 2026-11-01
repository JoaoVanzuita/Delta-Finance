import { useEffect, useRef, useState } from 'react'

import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router'

import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api/problem'

import { FormErrorSummary, type FormIssue } from '../components/FormErrorSummary'
import { useRegister } from '../hooks/useSession'
import { registerSchema, type RegisterFormValues } from '../validation'

export function RegisterPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
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
        state: { registered: true, username: parsed.data.username, from },
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

  const busy = isSubmitting || registerMutation.isPending

  return (
    <div className="mx-auto w-full max-w-[var(--form-max)]">
      <header className="mb-6 space-y-2">
        <p className="overline text-ink-muted">Conta pessoal</p>
        <h1 className="text-page-title font-bold tracking-tight text-ink">Criar conta</h1>
        <p className="text-body-sm text-ink-muted">
          Sua conta é individual: apenas você verá as suas contas, transações e conexões.
        </p>
      </header>

      <Card className="p-5 sm:p-6">
        <form noValidate onSubmit={onSubmit} className="space-y-5">
          <FormErrorSummary
            id="register-errors"
            issues={issues}
            summaryRef={summaryRef}
            title="Revise os dados do cadastro"
          />

          <Field
            id="register-username"
            label="Nome de usuário"
            hint="Entre 3 e 40 caracteres. Pode ser um apelido."
            error={errors.username?.message}
          >
            <Input
              id="register-username"
              type="text"
              autoComplete="username"
              aria-invalid={Boolean(errors.username)}
              aria-describedby={
                errors.username
                  ? 'register-username-hint register-username-error'
                  : 'register-username-hint'
              }
              {...register('username')}
            />
          </Field>

          <Field
            id="register-password"
            label="Senha"
            hint="Pelo menos 12 caracteres. Frases-senha são bem-vindas."
            error={errors.password?.message}
          >
            <Input
              id="register-password"
              type="password"
              autoComplete="new-password"
              aria-invalid={Boolean(errors.password)}
              aria-describedby={
                errors.password
                  ? 'register-password-hint register-password-error'
                  : 'register-password-hint'
              }
              {...register('password')}
            />
          </Field>

          <Button
            type="submit"
            fullWidth
            loading={busy}
            loadingLabel="Criando conta…"
            disabled={busy}
          >
            Criar conta
          </Button>
        </form>
      </Card>

      <p className="mt-4 text-body-sm text-ink-muted">
        Já tem conta?{' '}
        <Link
          to="/login"
          state={{ from }}
          className="font-medium text-action underline underline-offset-4"
        >
          Entrar
        </Link>
      </p>
    </div>
  )
}
