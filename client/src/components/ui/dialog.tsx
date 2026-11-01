import { useId, useRef } from 'react'
import type { ReactNode, RefObject } from 'react'

import { createPortal } from 'react-dom'
import { X } from 'lucide-react'

import { cn } from '@/lib/cn'

import { IconButton } from './icon-button'
import { useOverlayBehavior } from './overlay-behavior'

const sizes = {
  sm: 'sm:max-w-md',
  md: 'sm:max-w-lg',
  lg: 'sm:max-w-2xl',
  xl: 'sm:max-w-4xl',
} as const

export interface DialogProps {
  readonly open: boolean
  readonly onClose: () => void
  readonly title: string
  readonly description?: ReactNode
  readonly children: ReactNode
  readonly size?: keyof typeof sizes
  readonly dismissible?: boolean
  readonly closeOnEscape?: boolean
  /** Enquanto ocupado, fecha apenas quando a operação terminar. */
  readonly busy?: boolean
  readonly initialFocusRef?: RefObject<HTMLElement | null>
  readonly className?: string
  readonly bodyClassName?: string
  readonly footer?: ReactNode
}

/**
 * Dialog modal acessível.
 *
 * Implementação própria (sem dependência nova): portal, foco inicial, trap de
 * foco, Escape opcional, fundo inerte, scroll lock e retorno de foco ao gatilho.
 * O formulário permanece montado em caso de erro, preservando os dados.
 */
export function Dialog({
  open,
  onClose,
  title,
  description,
  children,
  size = 'md',
  dismissible = true,
  closeOnEscape = true,
  busy = false,
  initialFocusRef,
  className,
  bodyClassName,
  footer,
}: DialogProps) {
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
    closeOnEscape,
    busy,
  })

  if (!open) {
    return null
  }

  return createPortal(
    <div
      ref={portalRef}
      className="fixed inset-0 z-50 flex items-end justify-center sm:items-center sm:p-4"
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
          'relative flex max-h-[92dvh] w-full flex-col overflow-hidden border border-line bg-surface-raised shadow-[var(--shadow-overlay)]',
          'rounded-t-panel sm:rounded-panel',
          sizes[size],
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
          <footer className="flex flex-wrap items-center justify-end gap-2 border-t border-line bg-surface px-5 py-4">
            {footer}
          </footer>
        ) : null}
      </div>
    </div>,
    document.body,
  )
}
