import { Badge } from '@/components/ui/badge'
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table'
import { formatMoney, formatPercent } from '@/lib/format'
import type { InvestmentContribution, InvestmentContributionFlow } from '@/features/integration/api'

import { formatReferenceDate } from '../format'

const FLOW_KIND_LABELS: Record<string, string> = {
  CONTRIBUTION: 'Aporte',
  WITHDRAWAL: 'Resgate',
  INTERNAL_TRANSFER: 'Transferência interna',
  APPLICATION: 'Aplicação',
  REDEMPTION: 'Venda/resgate de ativo',
  INCOME: 'Provento',
  FEE: 'Custo',
}

const FLOW_STATUS_LABELS: Record<string, string> = {
  CONFIRMED: 'Conciliado',
  SUGGESTED: 'Sugerido',
  PROVIDER: 'Provedor',
}

const FEE_SOURCE_LABELS: Record<string, string> = {
  PROVIDER: 'informados pelo provedor',
  DERIVED: 'derivados da diferença entre débito e compras',
  UNAVAILABLE: 'não identificados',
}

const PERIMETER_SOURCE_LABELS: Record<string, string> = {
  ACCOUNT_FLAG: 'conta marcada como investimento',
  CONNECTION_FALLBACK: 'todas as contas da conexão',
}

function monthLabel(month: string): string {
  const [year, monthNumber] = month.split('-')
  if (!year || !monthNumber) return month
  const date = new Date(Date.UTC(Number(year), Number(monthNumber) - 1, 1))
  return new Intl.DateTimeFormat('pt-BR', {
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  })
    .format(date)
    .replace('.', '')
}

function flowTitle(flow: InvestmentContributionFlow): string {
  if (flow.description?.trim()) return flow.description
  if (flow.investmentName) {
    return flow.investmentCode
      ? `${flow.investmentName} (${flow.investmentCode})`
      : flow.investmentName
  }
  return FLOW_KIND_LABELS[flow.kind] ?? flow.kind
}

function signClass(flow: InvestmentContributionFlow): string {
  if (flow.kind === 'INTERNAL_TRANSFER') return 'text-ink-muted'
  return Number(flow.amount) < 0 ? 'text-expense' : 'text-income'
}

/** Aportes, resgates e aplicações do perímetro de investimento. */
export function InvestmentContributionsSection({
  contributions,
}: {
  readonly contributions: InvestmentContribution
}) {
  const hasFlows = contributions.flows.length > 0
  const difference = Number(contributions.reconciliationDifference ?? 0)
  const hasDifference = contributions.reconciliationDifference != null && difference !== 0
  const fallbackSources = contributions.sources.filter(
    (source) => source.perimeterSource === 'CONNECTION_FALLBACK',
  )

  return (
    <section aria-labelledby="investment-contributions-heading" className="space-y-5">
      <div>
        <h2
          id="investment-contributions-heading"
          className="text-section-title font-semibold text-ink"
        >
          Aportes
        </h2>
        <p className="mt-1 text-caption text-ink-muted">
          Período de {formatReferenceDate(contributions.from)} a{' '}
          {formatReferenceDate(contributions.to)}. Aporte é a entrada de caixa; aplicação é a compra
          do ativo. Um não substitui o outro.
        </p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Metric
          label="Aportes externos"
          value={formatMoney(contributions.contributions, 'BRL')}
          hint={`${contributions.contributionCount} entrada(s) de caixa no período.`}
          tone="income"
        />
        <Metric
          label="Resgates externos"
          value={formatMoney(contributions.withdrawals, 'BRL')}
          hint={`${contributions.withdrawalCount} saída(s) para fora do perímetro.`}
          tone={Number(contributions.withdrawals) > 0 ? 'expense' : 'neutral'}
        />
        <Metric
          label="Aporte líquido"
          value={formatMoney(contributions.netContributions, 'BRL')}
          hint="Aportes menos resgates. Não é receita nem despesa."
          tone={Number(contributions.netContributions) < 0 ? 'expense' : 'neutral'}
        />
        <Metric
          label="Aplicado em ativos"
          value={formatMoney(contributions.applied, 'BRL')}
          hint={`${contributions.applicationCount} compra(s) registradas pelo provedor.`}
        />
        <Metric
          label="Custos"
          value={formatMoney(contributions.fees, 'BRL')}
          hint={FEE_SOURCE_LABELS[contributions.feeSource] ?? 'origem não informada'}
          tone={Number(contributions.fees) > 0 ? 'expense' : 'neutral'}
        />
        <Metric
          label="Saldo ocioso"
          value={formatMoney(contributions.idleCash, 'BRL')}
          hint="Caixa gerado no período e ainda não aplicado."
          tone={Number(contributions.idleCash) < 0 ? 'expense' : 'neutral'}
        />
        <Metric
          label="Posições"
          value={formatMoney(contributions.portfolioValue, 'BRL')}
          hint={`${contributions.positionCount} posição(ões) ativa(s) no perímetro.`}
        />
        <Metric
          label="Resultado desde o início"
          value={
            contributions.result == null
              ? 'Não calculado'
              : formatMoney(contributions.result, 'BRL')
          }
          hint={
            contributions.result == null
              ? 'Disponível quando o período termina hoje e há aportes rastreados.'
              : `${formatPercent(contributions.resultPercentage)} sobre ${formatMoney(
                  contributions.cumulativeContributions,
                  'BRL',
                )} aportados. Inclui caixa e custos.`
          }
          tone={
            contributions.result == null
              ? 'neutral'
              : Number(contributions.result) < 0
                ? 'expense'
                : 'income'
          }
        />
      </div>

      <section
        aria-labelledby="investment-contribution-reconciliation-heading"
        className="rounded-card border border-line bg-surface p-4"
      >
        <h3
          id="investment-contribution-reconciliation-heading"
          className="text-body-sm font-semibold text-ink"
        >
          Conciliação do caixa
        </h3>
        {Number(contributions.settlementDebits) === 0 ? (
          <p className="mt-2 text-body-sm text-ink-muted">
            Nenhum débito de corretagem identificado no período. O relatório usa apenas as
            movimentações de caixa e as compras do provedor.
          </p>
        ) : (
          <dl className="mt-3 grid gap-2 text-body-sm sm:grid-cols-2">
            <ReconciliationRow
              label="Débitos de corretagem"
              value={formatMoney(contributions.settlementDebits, 'BRL')}
            />
            <ReconciliationRow
              label="Compras registradas"
              value={formatMoney(contributions.applied, 'BRL')}
            />
            <ReconciliationRow label="Custos" value={formatMoney(contributions.fees, 'BRL')} />
            <ReconciliationRow
              label="Diferença de conciliação"
              value={
                contributions.reconciliationDifference == null
                  ? '—'
                  : formatMoney(contributions.reconciliationDifference, 'BRL')
              }
              tone={hasDifference ? 'warning' : 'neutral'}
            />
          </dl>
        )}
        {hasDifference ? (
          <p className="mt-3 text-caption text-ink-muted">
            A diferença pode ser saldo ocioso, custo não informado ou compra financiada por caixa
            anterior. Ela não é somada como aporte.
          </p>
        ) : null}
        <p className="mt-3 text-caption text-ink-muted">
          Caixa atual no perímetro: {formatMoney(contributions.cashBalance, 'BRL')}. Saldo ocioso do
          período: {formatMoney(contributions.idleCash, 'BRL')}.
        </p>
      </section>

      {fallbackSources.length > 0 ? (
        <p className="rounded-card border border-warning/40 bg-warning-soft p-3 text-body-sm text-ink">
          {fallbackSources.length === 1
            ? 'Uma conexão não tem conta marcada como investimento.'
            : `${fallbackSources.length} conexões não têm conta marcada como investimento.`}{' '}
          O relatório está usando todas as contas da conexão. Marque a conta de investimento em
          Contas → Configurações para separar aporte de conta de passagem.
        </p>
      ) : null}

      {contributions.sources.length > 0 ? (
        <section aria-labelledby="investment-contribution-sources-heading" className="space-y-2">
          <h3
            id="investment-contribution-sources-heading"
            className="text-body-sm font-semibold text-ink"
          >
            Perímetro incluído
          </h3>
          <ul className="divide-y divide-line rounded-card border border-line bg-surface">
            {contributions.sources.map((source) => (
              <li
                key={source.connectionId}
                className="flex flex-wrap items-center justify-between gap-2 px-3 py-2.5"
              >
                <span className="min-w-0">
                  <span className="block truncate text-body-sm font-medium text-ink">
                    {source.name ?? 'Conexão sem nome'}
                  </span>
                  <span className="text-caption text-ink-muted">
                    {source.accountCount} conta(s) · {source.positionCount} posição(ões) ·{' '}
                    {PERIMETER_SOURCE_LABELS[source.perimeterSource] ?? source.perimeterSource}
                  </span>
                </span>
                <Badge variant={source.perimeterSource === 'ACCOUNT_FLAG' ? 'income' : 'warning'}>
                  {source.perimeterSource === 'ACCOUNT_FLAG' ? 'Conta marcada' : 'Fallback'}
                </Badge>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {!hasFlows ? (
        <div className="rounded-card border border-line bg-surface-muted p-4 text-body-sm leading-relaxed text-ink-muted">
          Nenhum aporte, resgate ou aplicação no período. Se você transferiu dinheiro para a
          corretora, confirme se a conta está marcada como investimento e se o período inclui a data
          da transferência.
        </div>
      ) : (
        <>
          <Table caption="Aportes, resgates e aplicações por mês">
            <THead>
              <TR>
                <TH>Mês</TH>
                <TH className="text-right">Aportes</TH>
                <TH className="text-right">Resgates</TH>
                <TH className="text-right">Líquido</TH>
                <TH className="text-right">Aplicado</TH>
                <TH className="text-right">Custos</TH>
                <TH className="text-right">Saldo ocioso</TH>
              </TR>
            </THead>
            <TBody>
              {contributions.months.map((month) => (
                <TR key={month.month}>
                  <TH scope="row" className="text-left font-medium text-ink">
                    {monthLabel(month.month)}
                  </TH>
                  <TD className="numeric text-right text-income">
                    {formatMoney(month.contributions, 'BRL')}
                  </TD>
                  <TD className="numeric text-right text-expense">
                    {formatMoney(month.withdrawals, 'BRL')}
                  </TD>
                  <TD className="numeric text-right text-ink">
                    {formatMoney(month.netContributions, 'BRL')}
                  </TD>
                  <TD className="numeric text-right text-ink">
                    {formatMoney(month.applied, 'BRL')}
                  </TD>
                  <TD className="numeric text-right text-ink-muted">
                    {formatMoney(month.fees, 'BRL')}
                  </TD>
                  <TD className="numeric text-right text-ink-muted">
                    {formatMoney(month.idleCash, 'BRL')}
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>

          <details className="rounded-card border border-line bg-surface">
            <summary className="min-h-11 cursor-pointer px-3 py-2.5 text-body-sm font-medium text-ink">
              Ver lançamentos do período ({contributions.flows.length})
            </summary>
            <div className="overflow-x-auto border-t border-line">
              <Table caption="Lançamentos de aportes, resgates e aplicações">
                <THead>
                  <TR>
                    <TH>Data</TH>
                    <TH>Tipo</TH>
                    <TH>Descrição</TH>
                    <TH className="text-right">Valor</TH>
                    <TH>Status</TH>
                  </TR>
                </THead>
                <TBody>
                  {contributions.flows.map((flow, index) => (
                    <TR
                      key={`${flow.date}-${flow.kind}-${flow.transactionId ?? flow.investmentId ?? index}`}
                    >
                      <TD className="whitespace-nowrap text-ink-muted">
                        {formatReferenceDate(flow.date)}
                      </TD>
                      <TD className="whitespace-nowrap text-ink">
                        {FLOW_KIND_LABELS[flow.kind] ?? flow.kind}
                      </TD>
                      <TD className="text-ink-muted">{flowTitle(flow)}</TD>
                      <TD className={`numeric text-right font-semibold ${signClass(flow)}`}>
                        {formatMoney(flow.amount, 'BRL')}
                      </TD>
                      <TD>
                        <Badge
                          variant={
                            flow.status === 'CONFIRMED'
                              ? 'income'
                              : flow.status === 'SUGGESTED'
                                ? 'warning'
                                : 'neutral'
                          }
                        >
                          {FLOW_STATUS_LABELS[flow.status] ?? flow.status}
                        </Badge>
                      </TD>
                    </TR>
                  ))}
                </TBody>
              </Table>
            </div>
          </details>
        </>
      )}

      <details className="rounded-card border border-line bg-surface-muted p-4">
        <summary className="min-h-11 cursor-pointer text-body-sm font-semibold text-ink">
          Como este relatório calcula os valores
        </summary>
        <ul className="mt-2 space-y-1 text-body-sm text-ink-muted">
          <li>Aporte é o crédito que entra na conta marcada como investimento vindo de fora.</li>
          <li>Resgate é o débito que sai dessa conta para fora do perímetro.</li>
          <li>
            Aplicação é a compra (BUY) do provedor. Ela usa o caixa aportado e não é um novo aporte.
          </li>
          <li>Transferência entre contas do perímetro não entra nos totais.</li>
          <li>
            Custos vêm de <code>expenses</code> do provedor ou da diferença entre o débito de
            corretagem e a soma das compras.
          </li>
          <li>
            Proventos bancários não são somados aqui para não duplicar o INTEREST do provedor.
          </li>
          <li>
            O resultado usa posições + caixa do perímetro menos os aportes líquidos acumulados.
          </li>
        </ul>
      </details>
    </section>
  )
}

function Metric({
  label,
  value,
  hint,
  tone = 'neutral',
}: {
  readonly label: string
  readonly value: string
  readonly hint: string
  readonly tone?: 'income' | 'expense' | 'neutral'
}) {
  const valueClass =
    tone === 'income' ? 'text-income' : tone === 'expense' ? 'text-expense' : 'text-ink'
  return (
    <div className="rounded-card border border-line bg-surface p-4">
      <p className="text-caption font-semibold tracking-[0.04em] text-ink-muted uppercase">
        {label}
      </p>
      <p className={`numeric mt-2 text-metric-sm font-semibold ${valueClass}`}>{value}</p>
      <p className="mt-2 text-caption leading-relaxed text-ink-muted">{hint}</p>
    </div>
  )
}

function ReconciliationRow({
  label,
  value,
  tone = 'neutral',
}: {
  readonly label: string
  readonly value: string
  readonly tone?: 'warning' | 'neutral'
}) {
  return (
    <div className="flex items-baseline justify-between gap-3 border-b border-line py-1 last:border-b-0">
      <dt className="text-ink-muted">{label}</dt>
      <dd className={`numeric font-semibold ${tone === 'warning' ? 'text-warning' : 'text-ink'}`}>
        {value}
      </dd>
    </div>
  )
}
