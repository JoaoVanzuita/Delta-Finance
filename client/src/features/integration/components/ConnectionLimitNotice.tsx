interface ConnectionLimitNoticeProps {
  readonly count: number
  readonly limit: number
  readonly checkedAt: string
}

/** Aviso versionado e datado do limite da modalidade Meu Pluggy (FR-022). */
export function ConnectionLimitNotice({ count, limit, checkedAt }: ConnectionLimitNoticeProps) {
  const reached = count >= limit
  const date = new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'medium',
    timeZone: 'America/Sao_Paulo',
  }).format(new Date(`${checkedAt}T12:00:00Z`))

  return (
    <div
      role="status"
      className={[
        'rounded-card border p-4 text-sm',
        reached
          ? 'border-warning/40 bg-warning/5 text-ink'
          : 'border-line bg-surface-muted text-ink',
      ].join(' ')}
    >
      <p className="font-medium">
        {count} de {limit} conexões configuradas
      </p>
      <p className="mt-1 text-ink-muted">
        Limite da modalidade Meu Pluggy verificado em {date}. O limite é por conta pessoal e não se
        confunde com a quantidade de contas ou cartões retornados por uma conexão.
      </p>
      {reached ? (
        <p className="mt-2 font-medium text-warning">
          Limite atingido. Remova uma conexão para adicionar outra.
        </p>
      ) : null}
    </div>
  )
}
