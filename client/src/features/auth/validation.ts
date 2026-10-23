import { z } from 'zod'

/**
 * Espelha a política do servidor para feedback imediato. O servidor continua
 * sendo a autoridade: a validação local nunca substitui a resposta 422.
 */
export const USERNAME_MIN = 3
export const USERNAME_MAX = 40
export const PASSWORD_MIN = 12
export const PASSWORD_MAX = 128

const username = z
  .string()
  .trim()
  .min(USERNAME_MIN, 'O nome de usuário deve ter entre 3 e 40 caracteres.')
  .max(USERNAME_MAX, 'O nome de usuário deve ter entre 3 e 40 caracteres.')

const password = z
  .string()
  .min(PASSWORD_MIN, 'A senha deve ter pelo menos 12 caracteres.')
  .max(PASSWORD_MAX, 'A senha deve ter no máximo 128 caracteres.')

export const registerSchema = z.object({
  username,
  password,
})

export const loginSchema = z.object({
  username: z.string().trim().min(1, 'Informe o nome de usuário.'),
  password: z.string().min(1, 'Informe a senha.'),
})

export const passwordChangeSchema = z
  .object({
    currentPassword: z.string().min(1, 'Informe a senha atual.'),
    newPassword: password,
    confirmNewPassword: z.string().min(1, 'Confirme a nova senha.'),
  })
  .refine((values) => values.newPassword === values.confirmNewPassword, {
    message: 'As senhas não coincidem.',
    path: ['confirmNewPassword'],
  })

export type RegisterFormValues = z.infer<typeof registerSchema>
export type LoginFormValues = z.infer<typeof loginSchema>
export type PasswordChangeFormValues = z.infer<typeof passwordChangeSchema>
