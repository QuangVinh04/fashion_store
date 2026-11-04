'use client'

// eslint-disable-next-line import/named
import { useQuery } from '@tanstack/react-query'

import { Permission } from '@/configs/permissions'
import { useAuth } from '@/context/AuthContext'
import { dashboardApi } from '@/libs/api/dashboard'
import type { AdminDashboardData, OperationsDashboardData } from '@/types/dashboard'

export function useDashboard(days: number = 30, lowStockThreshold: number = 10) {
  const { hasPermission } = useAuth()
  const hasRevenue = hasPermission(Permission.REVENUE_READ)

  const query = useQuery<AdminDashboardData | OperationsDashboardData>({
    queryKey: ['admin-dashboard', hasRevenue ? 'stats' : 'operations', days, lowStockThreshold],
    queryFn: () => (hasRevenue ? dashboardApi.getStats(days, lowStockThreshold) : dashboardApi.getOperations(days, lowStockThreshold)),
    staleTime: 30_000,
    refetchOnWindowFocus: true
  })

  return {
    ...query,
    hasRevenue
  }
}
