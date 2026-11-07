import { api } from './client'
import { normalizeAdminPage } from './page'
import type { AdminPage, PageDto } from '@/types/api'
import type { AuditLog } from '@/types/audit'

export const DEFAULT_AUDIT_LOG_SORT = 'occurredAt,desc'

export type AuditLogListParams = {
  page: number
  size: number
  actorId?: string
  entityType?: string
  entityId?: string
  action?: string
  sort?: string
}

const queryString = (params: Record<string, string | number | boolean | undefined>) => {
  const query = new URLSearchParams()

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value))
  })

  return query.toString()
}

export const auditLogsApi = {
  list: async (params: AuditLogListParams): Promise<AdminPage<AuditLog>> => {
    const query = queryString({
      page: params.page,
      size: params.size,
      actorId: params.actorId,
      entityType: params.entityType,
      entityId: params.entityId,
      action: params.action,
      sort: params.sort || DEFAULT_AUDIT_LOG_SORT
    })

    const raw = await api.get<PageDto<AuditLog>>(`/api/v1/admin/audit-logs?${query}`)

    return normalizeAdminPage(raw)
  }
}
