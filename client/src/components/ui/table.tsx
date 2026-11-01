import type { HTMLAttributes, TableHTMLAttributes, TdHTMLAttributes, ThHTMLAttributes } from 'react'

import { cn } from '@/lib/cn'

export interface TableProps extends TableHTMLAttributes<HTMLTableElement> {
  readonly caption: string
  readonly captionClassName?: string
}

/**
 * Tabela semântica com `caption` obrigatório.
 * No mobile, consumidores devem oferecer a versão em lista (`dl`) equivalente.
 */
export function Table({ caption, captionClassName, className, children, ...props }: TableProps) {
  return (
    <div className="w-full overflow-x-auto">
      <table className={cn('w-full border-collapse text-body-sm', className)} {...props}>
        <caption className={cn('sr-only', captionClassName)}>{caption}</caption>
        {children}
      </table>
    </div>
  )
}

export function THead({ className, ...props }: HTMLAttributes<HTMLTableSectionElement>) {
  return <thead className={cn('border-b border-line', className)} {...props} />
}

export function TBody({ className, ...props }: HTMLAttributes<HTMLTableSectionElement>) {
  return <tbody className={cn('divide-y divide-line', className)} {...props} />
}

export function TR({ className, ...props }: HTMLAttributes<HTMLTableRowElement>) {
  return <tr className={cn('transition-colors hover:bg-surface-muted', className)} {...props} />
}

export function TH({ className, ...props }: ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th
      scope="col"
      className={cn(
        'px-3 py-2 text-left text-caption font-semibold whitespace-nowrap text-ink-muted',
        className,
      )}
      {...props}
    />
  )
}

export function TD({ className, ...props }: TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={cn('px-3 py-2 align-middle text-ink', className)} {...props} />
}
