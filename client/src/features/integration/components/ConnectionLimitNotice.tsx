import { Alert } from '@/components/ui/alert'

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
    <Alert variant={reached ? 'warning' : 'neutral'}>
      <p className="font-semibold text-ink">
        {count} de {limit} conexões configuradas
      </p>
      <p className="mt-1">
        Limite da modalidade Meu Pluggy verificado em {date}. O limite é por conta pessoal e não se
        confunde com a quantidade de contas ou cartões retornados por uma conexão.
      </p>
      {reached ? (
        <p className="mt-2 font-medium text-ink">
          Limite atingido. Remova uma conexão para adicionar outra.
        </p>
      ) : null}
    </Alert>
  )
}
