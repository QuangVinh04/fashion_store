import { api } from './client'
import { normalizeAdminPage } from './page'
import type { PageDto } from '@/types/api'
import type { Promotion, PromotionInput, PromotionTarget } from '@/types/promotions'

const base = '/api/v1/admin/promotions'
const path = (id: string) => `${base}/${encodeURIComponent(id)}`

const queryString = (params: Record<string, string | number | boolean | undefined>) => {
  const query = new URLSearchParams()

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value))
  })

  return query.toString()
}

export const promotionsApi = {
  list: async (params: { page: number; size: number; active?: boolean; target?: PromotionTarget }) => {
    const res = await api.get<PageDto<Promotion>>(`${base}?${queryString(params)}`)

    return normalizeAdminPage({
      ...res,
      totalElements: res.totalElements != null ? res.totalElements : (res.items?.length ?? 0)
    })
  },
  get: (id: string) => api.get<Promotion>(path(id)),
  create: (body: PromotionInput) => api.post<Promotion>(base, body),
  update: (id: string, body: PromotionInput) => api.put<Promotion>(path(id), body),
  setActive: (id: string, active: boolean) => api.patch<void>(`${path(id)}/status?active=${active}`)
}
