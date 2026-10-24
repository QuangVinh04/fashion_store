import { api } from './client'
import type { AdminDashboardData } from '@/types/dashboard'

export const dashboardApi = {
  getStats: (days: number = 30, lowStockThreshold: number = 10) =>
    api.get<AdminDashboardData>(`/api/v1/admin/dashboard?days=${days}&lowStockThreshold=${lowStockThreshold}`)
}
