import type { RefObject } from 'react'

export interface FormIssue {
  readonly fieldId: string
  readonly message: string
}

interface FormErrorSummaryProps {
  readonly id: string
  readonly issues: readonly FormIssue[]
  readonly summaryRef: RefObject<HTMLDivElement | null>
  readonly title?: string
}

/**
 * Resumo de erros focável no topo do formulário.
 *
 * Complementa (não substitui) as mensagens inline: cada item aponta para o
 * campo correspondente e o resumo recebe foco após uma tentativa inválida.
 */
export function FormErrorSummary({
  id,
  issues,
  summaryRef,
  title = 'Não foi possível continuar',
}: FormErrorSummaryProps) {
  if (issues.length === 0) {
    return null
  }

  return (
    <div
      id={id}
      ref={summaryRef}
      tabIndex={-1}
      role="alert"
      aria-labelledby={`${id}-title`}
      className="rounded-card border border-expense/30 bg-expense/5 p-4"
    >
      <h2 id={`${id}-title`} className="text-sm font-semibold text-expense">
        {title}
      </h2>
      <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-ink">
        {issues.map((issue) => (
          <li key={`${issue.fieldId}-${issue.message}`}>
            <a href={`#${issue.fieldId}`} className="underline underline-offset-2">
              {issue.message}
            </a>
          </li>
        ))}
      </ul>
    </div>
  )
}
