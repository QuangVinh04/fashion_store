import { api } from './client'
import { normalizeAdminPage } from './page'
import type { PageDto } from '@/types/api'
import type { AdminUser, AdminUserDetail, AdminUpdateUserRequest } from '@/types/customer'

const queryString = (params: Record<string, string | number | boolean | undefined>) => {
  const query = new URLSearchParams()

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value))
  })

  return query.toString()
}

export const customersApi = {
  list: async (params: { page: number; size: number; keyword?: string; isActive?: boolean | string; sort?: string }) => {
    const query = queryString({
      page: params.page,
      size: params.size,
      keyword: params.keyword,
      isActive: params.isActive === 'all' || params.isActive === undefined ? undefined : params.isActive,
      sort: params.sort || 'createdAt,desc'
    })

    const raw = await api.get<PageDto<AdminUser>>(`/api/v1/admin/users?${query}`)

    const totalElements =
      raw.totalElements != null && Number.isSafeInteger(raw.totalElements)
        ? raw.totalElements
        : raw.totalPage <= 1
          ? raw.items.length
          : raw.totalPage * raw.pageSize

    return normalizeAdminPage({
      ...raw,
      totalElements
    })
  },

  get: (id: string) => api.get<AdminUserDetail>(`/api/v1/admin/users/${encodeURIComponent(id)}`),

  update: (id: string, data: AdminUpdateUserRequest) =>
    api.put<AdminUser>(`/api/v1/admin/users/${encodeURIComponent(id)}`, data),

  updateStatus: (id: string, active: boolean) =>
    api.patch<AdminUser>(`/api/v1/admin/users/${encodeURIComponent(id)}/status`, { active })
}
