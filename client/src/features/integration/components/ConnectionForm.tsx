import { useState } from 'react'

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { Button } from '@/components/ui/button'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { ApiError } from '@/lib/api/problem'
import { invalidateIntegrationViews } from '@/lib/invalidation'

import { financeApi } from '../api'

interface ConnectionFormProps {
  readonly disabled?: boolean
  readonly onAdded?: () => void
}

/** Adiciona um itemId já autorizado pelo usuário no Meu Pluggy. */
export function ConnectionForm({ disabled, onAdded }: ConnectionFormProps) {
  const queryClient = useQueryClient()
  const [itemId, setItemId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const addConnection = useMutation({
    mutationFn: (value: string) => financeApi.addConnection(value),
    onSuccess: () => {
      setItemId('')
      setError(null)
      void invalidateIntegrationViews(queryClient)
      onAdded?.()
    },
    onError: (caught) => {
      setError(
        caught instanceof ApiError
          ? (caught.fieldMessage('itemId') ?? caught.message)
          : 'Não foi possível validar a conexão. Tente novamente.',
      )
    },
  })

  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        if (!itemId.trim()) {
          setError('Informe o itemId da conexão.')
          return
        }
        addConnection.mutate(itemId.trim())
      }}
      className="space-y-4"
    >
      <Field
        id="connection-item-id"
        label="itemId da conexão"
        hint={
          <>
            Copie o itemId da conexão autorizada na sua conta Meu Pluggy. A validação é feita no
            servidor antes de salvar. Uma mesma conta deve aparecer em apenas uma conexão; se você
            reconectou a instituição e o itemId mudou, use <strong>Atualizar autorização</strong> na
            conexão existente em vez de adicionar outra.
          </>
        }
        error={error}
      >
        <Input
          id="connection-item-id"
          value={itemId}
          onChange={(event) => setItemId(event.target.value)}
          disabled={disabled || addConnection.isPending}
          aria-invalid={Boolean(error)}
          aria-describedby={
            error ? 'connection-item-id-hint connection-item-id-error' : 'connection-item-id-hint'
          }
          autoComplete="off"
        />
      </Field>

      <Button
        type="submit"
        disabled={disabled}
        loading={addConnection.isPending}
        loadingLabel="Validando conexão…"
      >
        Adicionar conexão
      </Button>
    </form>
  )
}
