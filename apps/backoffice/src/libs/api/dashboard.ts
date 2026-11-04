import { api } from './client'
import type { AdminDashboardData, OperationsDashboardData } from '@/types/dashboard'

export const dashboardApi = {
  getStats: (days: number = 30, lowStockThreshold: number = 10) =>
    api.get<AdminDashboardData>(`/api/v1/admin/dashboard?days=${days}&lowStockThreshold=${lowStockThreshold}`),
  getOperations: (days: number = 30, lowStockThreshold: number = 10) =>
    api.get<OperationsDashboardData>(`/api/v1/admin/dashboard/operations?days=${days}&lowStockThreshold=${lowStockThreshold}`)
}
