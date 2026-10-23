/**
 * Página inicial provisória da fase de fundação.
 *
 * Some quando a autenticação (T044/T045) e o dashboard (T097) entrarem. Não
 * exibe dados, formulários ou links de funcionalidades ainda não implementadas.
 */
export function HomePage() {
  return (
    <main className="mx-auto flex min-h-dvh max-w-2xl flex-col justify-center gap-6 px-6 py-12">
      <div className="space-y-3">
        <p className="text-sm font-medium tracking-wide text-slate-500 uppercase">
          Fundação do projeto
        </p>
        <h1 className="text-4xl font-semibold tracking-tight text-slate-900">Delta Finance</h1>
        <p className="text-lg text-slate-700">
          Controle financeiro privado para a família, com dados isolados por usuário.
        </p>
      </div>

      <section
        aria-labelledby="status-heading"
        className="rounded-xl border border-amber-200 bg-amber-50 p-5"
      >
        <h2 id="status-heading" className="text-base font-semibold text-amber-900">
          Aplicação em construção
        </h2>
        <p className="mt-2 text-sm text-amber-900">
          Esta é a fase 1 (fundação). Ainda não há cadastro, login ou importação bancária. Não
          insira dados financeiros reais.
        </p>
      </section>
    </main>
  )
}
