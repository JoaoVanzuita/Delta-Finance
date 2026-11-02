import type { components } from '@/lib/api/schema'

import { api } from '../client'

export type Goal = components['schemas']['Goal']
export type GoalList = components['schemas']['GoalList']
export type GoalInput = components['schemas']['GoalRequest']
export type GoalAllocation = components['schemas']['GoalAllocation']
export type GoalAllocationInput = components['schemas']['GoalAllocationRequest']
export type GoalProgressEntry = components['schemas']['GoalProgressEntry']
export type GoalProgressPage = components['schemas']['GoalProgressPage']

export const goalsApi = {
  goals: () => api.get<GoalList>('/goals'),
  goal: (id: string) => api.get<Goal>(`/goals/${id}`),
  createGoal: (input: GoalInput) => api.post<Goal>('/goals', input),
  updateGoal: (id: string, input: GoalInput) => api.patch<Goal>(`/goals/${id}`, input),
  updateGoalStatus: (id: string, status: 'ACTIVE' | 'PAUSED' | 'ARCHIVED') =>
    api.post<Goal>(`/goals/${id}/status`, { status }),
  deleteGoal: (id: string) => api.delete<void>(`/goals/${id}`),
  goalProgress: (id: string, cursor?: string, limit = 50) => {
    const params = new URLSearchParams({ limit: String(limit) })
    if (cursor) params.set('cursor', cursor)
    return api.get<GoalProgressPage>(`/goals/${id}/progress?${params.toString()}`)
  },
}
