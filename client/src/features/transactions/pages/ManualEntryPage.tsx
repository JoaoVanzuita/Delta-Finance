import { Link } from 'react-router'

import { ManualEntryForm } from '../components/ManualEntryForm'

export function ManualEntryPage() {
  return (
    <div className="mx-auto w-full max-w-3xl space-y-6">
      <Link
        to="/transactions"
        className="text-sm font-medium text-action underline underline-offset-2"
      >
        ← Transações
      </Link>

      <header className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">
          Complemento manual
        </p>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Novo lançamento</h1>
        <p className="max-w-2xl text-sm leading-relaxed text-ink-muted">
          Registre receitas, despesas, transferências, estornos, reembolsos e ajustes. Lançamentos
          manuais continuam disponíveis quando a integração bancária falha.
        </p>
      </header>

      <ManualEntryForm />
    </div>
  )
}
