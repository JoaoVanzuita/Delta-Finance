import { useEffect, useId, useRef, useState, type FormEvent } from 'react'

import { ApiError } from '@/lib/api/problem'

interface DisplayNameEditorProps {
  readonly currentName: string | null
  readonly originalName?: string | null
  readonly customized: boolean
  readonly disabled?: boolean
  readonly onRename: (displayName: string) => Promise<unknown>
  readonly onReset?: () => Promise<unknown>
}

/**
 * Edição inline do nome de exibição.
 *
 * O nome original do provedor nunca é alterado e continua disponível para
 * restauração. As telas exibem esse nome separadamente, com menor destaque.
 */
export function DisplayNameEditor({
  currentName,
  originalName,
  customized,
  disabled = false,
  onRename,
  onReset,
}: DisplayNameEditorProps) {
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState(currentName ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const renameButtonRef = useRef<HTMLButtonElement>(null)
  const fieldId = useId()
  const hintId = `${fieldId}-hint`
  const errorId = `${fieldId}-error`

  useEffect(() => {
    if (!editing) {
      return
    }
    inputRef.current?.focus()
    inputRef.current?.select()
  }, [editing])

  function openEditor() {
    setValue(currentName ?? '')
    setError(null)
    setMessage(null)
    setEditing(true)
  }

  function cancelEditing() {
    if (busy) {
      return
    }
    setEditing(false)
    setError(null)
    requestAnimationFrame(() => renameButtonRef.current?.focus())
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = value.trim()
    if (!trimmed) {
      setError('Informe o nome de exibição.')
      inputRef.current?.focus()
      return
    }
    if (trimmed.length > 120) {
      setError('O nome de exibição deve ter no máximo 120 caracteres.')
      inputRef.current?.focus()
      return
    }

    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await onRename(trimmed)
      setEditing(false)
      setMessage('Nome de exibição atualizado.')
      requestAnimationFrame(() => renameButtonRef.current?.focus())
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível atualizar o nome.')
    } finally {
      setBusy(false)
    }
  }

  async function resetName() {
    if (!onReset) {
      return
    }
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await onReset()
      setMessage('Nome original restaurado.')
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível restaurar o nome.')
    } finally {
      setBusy(false)
    }
  }

  if (editing) {
    return (
      <form
        noValidate
        onSubmit={submit}
        className="w-full space-y-2 rounded-card border border-line bg-surface-muted p-3 sm:min-w-80"
      >
        <label htmlFor={fieldId} className="block text-sm font-medium text-ink">
          Nome de exibição
        </label>
        <input
          ref={inputRef}
          id={fieldId}
          type="text"
          value={value}
          maxLength={120}
          disabled={busy}
          aria-invalid={Boolean(error)}
          aria-describedby={`${hintId}${error ? ` ${errorId}` : ''}`}
          onChange={(event) => setValue(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              event.preventDefault()
              cancelEditing()
            }
          }}
          className="block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink disabled:opacity-60"
        />
        <p id={hintId} className="text-xs leading-relaxed text-ink-muted">
          Este nome vale só neste sistema; o nome original do Pluggy continua salvo.
        </p>
        {error ? (
          <p id={errorId} role="alert" className="text-xs text-expense">
            {error}
          </p>
        ) : null}
        <div className="flex flex-wrap gap-2">
          <button
            type="submit"
            disabled={busy}
            className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-3 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover disabled:opacity-60"
          >
            {busy ? 'Salvando…' : 'Salvar'}
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={cancelEditing}
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink transition-colors hover:bg-surface-muted disabled:opacity-60"
          >
            Cancelar
          </button>
        </div>
      </form>
    )
  }

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-2">
        <button
          ref={renameButtonRef}
          type="button"
          disabled={disabled || busy}
          onClick={openEditor}
          className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink transition-colors hover:bg-surface-muted disabled:opacity-60"
        >
          Renomear
        </button>
        {customized && originalName && onReset ? (
          <button
            type="button"
            disabled={disabled || busy}
            onClick={resetName}
            title={originalName ? `Restaurar "${originalName}"` : undefined}
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink transition-colors hover:bg-surface-muted disabled:opacity-60"
          >
            {busy ? 'Restaurando…' : 'Restaurar nome original do Pluggy'}
          </button>
        ) : null}
      </div>
      {message ? (
        <p role="status" className="text-xs text-income">
          {message}
        </p>
      ) : null}
      {error ? (
        <p role="alert" className="text-xs text-expense">
          {error}
        </p>
      ) : null}
    </div>
  )
}
