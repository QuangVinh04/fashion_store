'use client'

// eslint-disable-next-line import/named
import { useQuery } from '@tanstack/react-query'

import { dashboardApi } from '@/libs/api/dashboard'

export function useDashboard(days: number = 30, lowStockThreshold: number = 10) {
  return useQuery({
    queryKey: ['admin-dashboard', days, lowStockThreshold],
    queryFn: () => dashboardApi.getStats(days, lowStockThreshold),
    staleTime: 30_000,
    refetchOnWindowFocus: true
  })
}
