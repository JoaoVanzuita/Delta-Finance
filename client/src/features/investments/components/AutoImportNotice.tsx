/**
 * Aviso obrigatório da fase secundária de investimentos.
 *
 * <p>Nenhuma importação automática é oferecida ou declarada: a disponibilidade do conector no Meu
 * Pluggy não foi validada. O módulo também não recomenda compra, venda ou alocação.
 */
export function AutoImportNotice() {
  return (
    <section
      aria-labelledby="auto-import-notice-heading"
      className="space-y-2 rounded-card border border-warning/40 bg-warning/5 p-4"
    >
      <h2 id="auto-import-notice-heading" className="text-sm font-semibold text-ink">
        Registro manual
      </h2>
      <p className="text-sm leading-relaxed text-ink">
        Não há importação automática de investimentos validada no Meu Pluggy. As posições abaixo são
        informadas por você e usam a data e o valor de referência escolhidos.
      </p>
      <p className="text-sm leading-relaxed text-ink-muted">
        Este módulo não oferece recomendação de compra, venda ou alocação, ranking de ativos ou
        aconselhamento financeiro.
      </p>
    </section>
  )
}
