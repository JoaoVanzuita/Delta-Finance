/**
 * Contrato de erro da API (RFC 9457) e tipo de erro usado pelo cliente.
 *
 * Nenhuma mensagem do servidor é interpretada como confiável para decisões de
 * autorização: o cliente apenas exibe `detail` e usa `code`/`status`.
 */

export interface ProblemFieldError {
  readonly field: string
  readonly message: string
}

export interface ProblemDetails {
  readonly type: string
  readonly title: string
  readonly status: number
  readonly detail?: string
  readonly instance?: string
  readonly code?: string
  readonly requestId?: string
  readonly errors?: readonly ProblemFieldError[]
}

export const NETWORK_ERROR = 'NETWORK_ERROR'

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly requestId?: string
  readonly fieldErrors: readonly ProblemFieldError[]
  readonly retryAfterSeconds?: number
  readonly problem?: ProblemDetails

  constructor(
    status: number,
    code: string,
    message: string,
    options: {
      requestId?: string
      fieldErrors?: readonly ProblemFieldError[]
      retryAfterSeconds?: number
      problem?: ProblemDetails
    } = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.requestId = options.requestId
    this.fieldErrors = options.fieldErrors ?? []
    this.retryAfterSeconds = options.retryAfterSeconds
    this.problem = options.problem
  }

  static network(_cause: unknown): ApiError {
    return new ApiError(0, NETWORK_ERROR, 'Não foi possível conectar ao servidor.', {
      problem: undefined,
      requestId: undefined,
    })
  }

  get isUnauthenticated(): boolean {
    return this.status === 401
  }

  get isForbidden(): boolean {
    return this.status === 403
  }

  get isValidation(): boolean {
    return this.status === 422
  }

  fieldMessage(field: string): string | undefined {
    return this.fieldErrors.find((error) => error.field === field)?.message
  }
}

export function isProblemDetails(value: unknown): value is ProblemDetails {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.title === 'string' &&
    typeof candidate.status === 'number' &&
    typeof candidate.type === 'string'
  )
}

export function parseRetryAfter(value: string | null): number | undefined {
  if (!value) {
    return undefined
  }
  const seconds = Number.parseInt(value, 10)
  return Number.isFinite(seconds) && seconds > 0 ? seconds : undefined
}
