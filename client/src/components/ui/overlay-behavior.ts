import { useEffect, useRef } from 'react'
import type { RefObject } from 'react'

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'area[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  'iframe',
  '[contenteditable="true"]',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

function focusableElements(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter(
    (element) =>
      !element.hasAttribute('disabled') &&
      !element.hasAttribute('hidden') &&
      element.getAttribute('aria-hidden') !== 'true' &&
      !element.closest('[inert]'),
  )
}

interface OverlayBehaviorOptions {
  readonly open: boolean
  readonly panelRef: RefObject<HTMLElement | null>
  readonly portalRef: RefObject<HTMLElement | null>
  readonly initialFocusRef?: RefObject<HTMLElement | null>
  readonly onClose: () => void
  readonly dismissible: boolean
  readonly closeOnEscape: boolean
  readonly busy: boolean
}

interface InertSnapshot {
  readonly element: HTMLElement
  readonly hadAttribute: boolean
}

/**
 * Comportamento comum de dialogs/drawers:
 * foco inicial, trap de foco, Escape, retorno de foco, fundo inerte e scroll lock.
 */
export function useOverlayBehavior({
  open,
  panelRef,
  portalRef,
  initialFocusRef,
  onClose,
  dismissible,
  closeOnEscape,
  busy,
}: OverlayBehaviorOptions): void {
  const latest = useRef({ onClose, dismissible, closeOnEscape, busy })

  useEffect(() => {
    latest.current = { onClose, dismissible, closeOnEscape, busy }
  })

  useEffect(() => {
    if (!open) {
      return
    }
    const panel = panelRef.current
    const portal = portalRef.current
    if (!panel || !portal) {
      return
    }

    const previouslyFocused =
      document.activeElement instanceof HTMLElement ? document.activeElement : null
    const inertSnapshots: InertSnapshot[] = []

    for (const child of Array.from(document.body.children)) {
      if (!(child instanceof HTMLElement) || child.contains(portal)) {
        continue
      }
      inertSnapshots.push({ element: child, hadAttribute: child.hasAttribute('inert') })
      child.setAttribute('inert', '')
    }

    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const focusInitial = () => {
      const target = initialFocusRef?.current ?? focusableElements(panel)[0] ?? panel
      target.focus({ preventScroll: true })
    }
    focusInitial()

    const onKeyDown = (event: KeyboardEvent) => {
      const current = latest.current
      if (event.key === 'Escape') {
        if (current.closeOnEscape && current.dismissible && !current.busy) {
          event.preventDefault()
          event.stopPropagation()
          current.onClose()
        }
        return
      }

      if (event.key !== 'Tab') {
        return
      }

      const focusables = focusableElements(panel)
      if (focusables.length === 0) {
        event.preventDefault()
        panel.focus({ preventScroll: true })
        return
      }

      const first = focusables[0]
      const last = focusables[focusables.length - 1]
      if (!first || !last) {
        return
      }

      const active = document.activeElement
      if (!panel.contains(active)) {
        event.preventDefault()
        first.focus()
        return
      }
      if (event.shiftKey && active === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && active === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', onKeyDown, true)

    return () => {
      document.removeEventListener('keydown', onKeyDown, true)
      document.body.style.overflow = previousOverflow
      for (const snapshot of inertSnapshots) {
        if (!snapshot.hadAttribute) {
          snapshot.element.removeAttribute('inert')
        }
      }
      if (previouslyFocused && previouslyFocused.isConnected) {
        previouslyFocused.focus({ preventScroll: true })
      }
    }
    // O componente só é montado quando aberto; as referências são estáveis.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])
}
