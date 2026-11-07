'use client'

import { useQuery } from '@tanstack/react-query'

import { auditLogsApi, type AuditLogListParams } from '@/libs/api/audit-logs'

export function useAuditLogsQuery(params: AuditLogListParams) {
  return useQuery({
    queryKey: ['admin-audit-logs', params],
    queryFn: () => auditLogsApi.list(params)
  })
}
