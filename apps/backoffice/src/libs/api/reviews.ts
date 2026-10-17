import { api } from './client'
import { normalizeAdminPage } from './page'
import type { PageDto } from '@/types/api'
import type { Review, ReviewStatus } from '@/types/reviews'

const base = '/api/v1/admin/reviews'

const queryString = (params: Record<string, string | number | undefined>) => {
  const query = new URLSearchParams()

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value))
  })

  return query.toString()
}

export const reviewsApi = {
  list: async (params: { page: number; size: number; rating?: number; status?: ReviewStatus; keyword?: string }) =>
    normalizeAdminPage(await api.get<PageDto<Review>>(`${base}?${queryString(params)}`)),
  updateStatus: (reviewId: string, status: ReviewStatus) =>
    api.patch<Review>(`${base}/${encodeURIComponent(reviewId)}/status`, { status }),
  reply: (reviewId: string, reply: string) =>
    api.post<Review>(`${base}/${encodeURIComponent(reviewId)}/reply`, { reply }),
  deleteReply: (reviewId: string) =>
    api.delete<Review>(`${base}/${encodeURIComponent(reviewId)}/reply`),
  removeAdmin: (reviewId: string) =>
    api.delete<void>(`${base}/${encodeURIComponent(reviewId)}`),
  remove: (productId: string, reviewId: string) =>
    api.delete<void>(`/api/v1/products/${encodeURIComponent(productId)}/reviews/${encodeURIComponent(reviewId)}`)
}
