import {
  ArrowLeftRight,
  LayoutDashboard,
  ShieldCheck,
  Target,
  TrendingUp,
  Wallet,
} from 'lucide-react'
import { Link } from 'react-router'

import { buttonVariants } from '@/components/ui/button-variants'
import { Card } from '@/components/ui/card'
import { cn } from '@/lib/cn'

const MODULES = [
  {
    icon: Wallet,
    title: 'Contas e cartões',
    description: 'Saldos, faturas e limites organizados por instituição e ciclo.',
  },
  {
    icon: ArrowLeftRight,
    title: 'Transações e categorias',
    description: 'Movimentações importadas ou manuais, com correção e auditoria.',
  },
  {
    icon: TrendingUp,
    title: 'Investimentos e patrimônio',
    description: 'Posições, proventos e a composição entre ativos e passivos.',
  },
  {
    icon: Target,
    title: 'Metas e reconciliação',
    description: 'Planejamento explícito e conferência entre o local e o provedor.',
  },
] as const

/**
 * Home pública independente. Visitantes autenticados são encaminhados ao
 * Dashboard pela rota, sem renderizar a sidebar autenticada aqui.
 */
export function HomePage() {
  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-10">
      <header className="space-y-4">
        <p className="overline text-ink-muted">Controle financeiro pessoal</p>
        <div className="max-w-2xl space-y-3">
          <h1 className="text-display font-bold tracking-tight text-ink">Delta Finance</h1>
          <div aria-hidden="true" className="period-thread w-24" />
          <p className="text-body text-ink-muted">
            Um painel privado para acompanhar quanto você tem, quanto entrou, quanto saiu e o que
            precisa de atenção. Cada pessoa vê somente o próprio espaço.
          </p>
        </div>
        <div className="flex flex-wrap gap-3 pt-1">
          <Link to="/login" className={cn(buttonVariants({ variant: 'primary', size: 'lg' }))}>
            Entrar
          </Link>
          <Link to="/register" className={cn(buttonVariants({ variant: 'secondary', size: 'lg' }))}>
            Criar conta
          </Link>
        </div>
      </header>

      <section aria-labelledby="modules-heading" className="space-y-4">
        <div className="space-y-1">
          <h2 id="modules-heading" className="text-section-title font-semibold text-ink">
            O que você encontra
          </h2>
          <p className="max-w-prose text-body-sm text-ink-muted">
            A base de identidade, sessão e isolamento por usuário protege todos os fluxos abaixo.
          </p>
        </div>

        <ul className="grid gap-3 sm:grid-cols-2">
          {MODULES.map((module) => (
            <li key={module.title}>
              <Card className="flex h-full gap-3 p-4">
                <span
                  aria-hidden="true"
                  className="flex size-10 shrink-0 items-center justify-center rounded-control bg-primary-soft text-primary"
                >
                  <module.icon className="size-5" />
                </span>
                <div className="space-y-1">
                  <h3 className="text-body-sm font-semibold text-ink">{module.title}</h3>
                  <p className="text-caption text-ink-muted">{module.description}</p>
                </div>
              </Card>
            </li>
          ))}
        </ul>
      </section>

      <Card variant="muted" className="flex flex-wrap items-center justify-between gap-3 p-4">
        <div className="flex items-center gap-3">
          <ShieldCheck aria-hidden="true" className="size-5 shrink-0 text-income" />
          <p className="text-body-sm text-ink">
            Instalação self-hosted. Nenhum dado é enviado a serviços de terceiros pela interface.
          </p>
        </div>
        <Link to="/dashboard" className={cn(buttonVariants({ variant: 'ghost', size: 'sm' }))}>
          Abrir Dashboard
          <LayoutDashboard aria-hidden="true" className="size-4" />
        </Link>
      </Card>
    </div>
  )
}
