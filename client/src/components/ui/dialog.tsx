import type { ReactNode, RefObject } from 'react'

import { OverlayPanel } from './overlay-panel'

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
  return (
    <OverlayPanel
      open={open}
      onClose={onClose}
      title={title}
      description={description}
      dismissible={dismissible}
      closeOnEscape={closeOnEscape}
      busy={busy}
      initialFocusRef={initialFocusRef}
      portalClassName="flex items-end justify-center sm:items-center sm:p-4"
      panelClassName={[
        'max-h-[92dvh] w-full overflow-hidden border border-line',
        'rounded-t-panel sm:rounded-panel',
        sizes[size],
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      bodyClassName={bodyClassName}
      footerClassName="flex flex-wrap items-center justify-end gap-2 border-t border-line bg-surface px-5 py-4"
      footer={footer}
    >
      {children}
    </OverlayPanel>
  )
}
