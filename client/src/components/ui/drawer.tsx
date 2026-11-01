import { useId, useRef } from 'react'
import type { ReactNode, RefObject } from 'react'

import { createPortal } from 'react-dom'
import { X } from 'lucide-react'

import { cn } from '@/lib/cn'

import { IconButton } from './icon-button'
import { useOverlayBehavior } from './overlay-behavior'

export interface DrawerProps {
  readonly open: boolean
  readonly onClose: () => void
  readonly title: string
  readonly description?: ReactNode
  readonly children: ReactNode
  readonly footer?: ReactNode
  readonly side?: 'left' | 'right'
  readonly width?: 'sm' | 'md' | 'lg'
  readonly dismissible?: boolean
  readonly busy?: boolean
  readonly initialFocusRef?: RefObject<HTMLElement | null>
  readonly className?: string
  readonly bodyClassName?: string
}

const widths = {
  sm: 'sm:max-w-sm',
  md: 'sm:max-w-md',
  lg: 'sm:max-w-lg',
} as const

/**
 * Drawer contextual com a mesma base acessível do Dialog.
 * Ocupa a largura disponível no mobile e mantém header/footer visíveis.
 */
export function Drawer({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  side = 'right',
  width = 'md',
  dismissible = true,
  busy = false,
  initialFocusRef,
  className,
  bodyClassName,
}: DrawerProps) {
  const panelRef = useRef<HTMLDivElement>(null)
  const portalRef = useRef<HTMLDivElement>(null)
  const titleId = useId()
  const descriptionId = useId()

  useOverlayBehavior({
    open,
    panelRef,
    portalRef,
    initialFocusRef,
    onClose,
    dismissible,
    closeOnEscape: true,
    busy,
  })

  if (!open) {
    return null
  }

  return createPortal(
    <div
      ref={portalRef}
      className={cn('fixed inset-0 z-50 flex', side === 'right' ? 'justify-end' : 'justify-start')}
    >
      <div
        aria-hidden="true"
        className="absolute inset-0 bg-scrim"
        onMouseDown={(event) => {
          if (event.target === event.currentTarget && dismissible && !busy) {
            onClose()
          }
        }}
      />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        aria-busy={busy || undefined}
        tabIndex={-1}
        className={cn(
          'relative flex h-full w-full flex-col border-line bg-surface-raised shadow-[var(--shadow-overlay)]',
          side === 'right' ? 'border-l' : 'border-r',
          widths[width],
          className,
        )}
      >
        <header className="flex items-start justify-between gap-3 border-b border-line px-5 py-4">
          <div className="min-w-0 space-y-1">
            <h2 id={titleId} className="text-section-title font-semibold text-ink">
              {title}
            </h2>
            {description ? (
              <p id={descriptionId} className="text-body-sm text-ink-muted">
                {description}
              </p>
            ) : null}
          </div>
          {dismissible ? (
            <IconButton label="Fechar" onClick={onClose} disabled={busy} className="-mr-2 -mt-1">
              <X className="size-5" />
            </IconButton>
          ) : null}
        </header>

        <div className={cn('min-h-0 flex-1 overflow-y-auto px-5 py-4', bodyClassName)}>
          {children}
        </div>

        {footer ? (
          <footer className="border-t border-line bg-surface px-5 py-4">{footer}</footer>
        ) : null}
      </div>
    </div>,
    document.body,
  )
}
