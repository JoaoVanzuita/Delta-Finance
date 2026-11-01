import { clsx, type ClassValue } from 'clsx'
import { extendTailwindMerge } from 'tailwind-merge'

/**
 * `tailwind-merge` não conhece as escalas customizadas de `@theme`.
 * Sem este mapa, `text-body-sm` (tamanho) era confundido com `text-on-primary`
 * (cor), e a cor de texto sobre a marca era removida silenciosamente.
 */
const mergeTailwind = extendTailwindMerge({
  extend: {
    classGroups: {
      'font-size': [
        {
          text: [
            'display',
            'page-title',
            'section-title',
            'body',
            'body-sm',
            'caption',
            'overline',
            'metric-xl',
            'metric-lg',
            'metric-md',
            'metric-sm',
            'label',
          ],
        },
      ],
      'text-color': [
        {
          text: [
            'canvas',
            'surface',
            'surface-muted',
            'surface-raised',
            'ink',
            'ink-muted',
            'line',
            'line-strong',
            'primary',
            'primary-hover',
            'on-primary',
            'primary-soft',
            'action',
            'action-hover',
            'income',
            'on-income',
            'income-soft',
            'expense',
            'on-expense',
            'expense-soft',
            'warning',
            'on-warning',
            'warning-soft',
            'info',
            'on-info',
            'info-soft',
            'focus',
            'scrim',
            'disabled',
            'on-disabled',
          ],
        },
      ],
    },
  },
})

/**
 * Compõe classes utilitárias resolvendo conflitos do Tailwind.
 *
 * Use sempre em primitivas e componentes compartilhados para permitir que o
 * consumidor sobrescreva espaçamento/estado sem depender da ordem do CSS.
 */
export function cn(...inputs: ClassValue[]): string {
  return mergeTailwind(clsx(inputs))
}
