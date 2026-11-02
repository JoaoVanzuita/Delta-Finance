import type { components } from '@/lib/api/schema'

import { api } from '../client'

export type Category = components['schemas']['Category']
export type CategoryList = components['schemas']['CategoryList']
export type CategoryInput = components['schemas']['CategoryRequest']

export const categoriesApi = {
  categories: () => api.get<CategoryList>('/categories'),
  createCategory: (input: CategoryInput) => api.post<Category>('/categories', input),
  updateCategory: (id: string, input: CategoryInput) =>
    api.patch<Category>(`/categories/${id}`, input),
  deactivateCategory: (id: string, reassignTo?: string) => {
    const query = reassignTo ? `?reassignTo=${encodeURIComponent(reassignTo)}` : ''
    return api.delete<void>(`/categories/${id}${query}`)
  },
}
