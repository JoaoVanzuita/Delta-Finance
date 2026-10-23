import { api, type User } from '@/lib/api/client'

export interface RegisterInput {
  readonly username: string
  readonly password: string
}

export interface LoginInput {
  readonly username: string
  readonly password: string
}

export interface PasswordChangeInput {
  readonly currentPassword: string
  readonly newPassword: string
}

/** Operações de identidade usadas pelas telas de autenticação. */
export const authApi = {
  register: (input: RegisterInput) => api.post<User>('/auth/register', input),
  login: (input: LoginInput) => api.post<User>('/auth/login', input),
  logout: () => api.post<void>('/auth/logout'),
  me: () => api.get<User>('/auth/me'),
  changePassword: (input: PasswordChangeInput) => api.put<void>('/auth/password', input),
}
