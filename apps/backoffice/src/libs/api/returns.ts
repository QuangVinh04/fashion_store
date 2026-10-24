import { api } from './client'
import type { PageDto } from '@/types/api'
import type { EvidenceSource, ReceiveReturnInput, ReturnCase } from '@/types/returns'

const path = (id: string) => `/api/v1/admin/orders/returns/${encodeURIComponent(id)}`

export const returnsApi = {
  list: async (params: { page: number; size: number; status?: string }) => {
    const query = new URLSearchParams({ page: String(params.page), size: String(params.size) })

    if (params.status) query.set('status', params.status)
    const data = await api.get<PageDto<ReturnCase>>(`/api/v1/admin/orders/returns?${query}`)

    if (
      !Array.isArray(data.items) ||
      !Number.isSafeInteger(data.pageNo) ||
      data.pageNo < 0 ||
      !Number.isSafeInteger(data.pageSize) ||
      data.pageSize <= 0 ||
      !Number.isSafeInteger(data.totalPage) ||
      data.totalPage < 0
    ) {
      throw new Error('Dữ liệu phân trang hồ sơ trả hàng không hợp lệ.')
    }

    return data
  },
  get: (id: string, signal?: AbortSignal) => api.get<ReturnCase>(path(id), { signal }),
  accept: (id: string) => api.post<ReturnCase>(`${path(id)}/approve`),
  reject: (id: string, body: { rejectReason: string }) => api.post<ReturnCase>(`${path(id)}/reject`, body),
  receive: (id: string, body: ReceiveReturnInput) => api.post<ReturnCase>(`${path(id)}/receive`, body),
  approveRefund: (id: string, body: { amount?: string } = {}) => api.post<ReturnCase>(`${path(id)}/refund`, body),
  cancel: (id: string, body: { reason: string }) => api.post<ReturnCase>(`${path(id)}/cancel`, body),
  closeWithoutRefund: (id: string, body: { reason: string }) =>
    api.post<ReturnCase>(`${path(id)}/close-without-refund`, body),
  create: (orderId: string, kind: 'carrier' | 'lost', body: { reason: string; mediaIds: string[] }) =>
    api.post<ReturnCase>(`/api/v1/admin/orders/returns/${kind}/${encodeURIComponent(orderId)}`, body),
  evidenceUrl: (id: string, mediaId: string, source: EvidenceSource = 'customer') =>
    api.get<string>(`${path(id)}/${source}-evidence/${encodeURIComponent(mediaId)}/url`)
}
