import { api, ApiError } from './client'
import { normalizeAdminPage } from './page'
import type { PageDto } from '@/types/api'
import type { OrderDetail, OrderSummary, OrderStatus, OrderHistory, OrderSaga, Shipment, CarrierVerificationInput, CarrierVerification } from '@/types/orders'

const path = (id: string) => `/api/v1/admin/orders/${encodeURIComponent(id)}`

export const ordersApi = {
  list: async (params: {
    page: number
    size: number
    status?: string
    userId?: string
    search?: string
    paymentMethod?: string
    createdFrom?: string
    createdTo?: string
    sort?: string
  }) => {
    const query = new URLSearchParams()

    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') query.set(key, String(value))
    })

    return normalizeAdminPage(await api.get<PageDto<OrderSummary>>(`/api/v1/admin/orders?${query}`))
  },
  get: (id: string) => api.get<OrderDetail>(path(id)),
  cancel: (id:string,body:{operationId:string;reason:string})=>api.post<OrderDetail>(`${path(id)}/cancel`,body),
  retryCancellation: (id:string,body:{operationId:string;reason:string})=>api.post<OrderDetail>(`${path(id)}/cancellation/retry`,body),
  verifyCancellationCarrier:(id:string,body:CarrierVerificationInput)=>api.post<OrderDetail>(`${path(id)}/cancellation/verify-carrier`,body),
  cancellationVerification:(id:string)=>api.get<CarrierVerification|null>(`${path(id)}/cancellation/carrier-verification`),
  cancellationEvidenceUrl:(id:string,mediaId:string)=>api.get<string>(`${path(id)}/cancellation/evidence/${encodeURIComponent(mediaId)}`),
  history: (id: string) => api.get<OrderHistory[]>(`${path(id)}/history`),
  saga: (id: string) => api.get<OrderSaga>(`${path(id)}/saga`),
  shipment: async (id: string): Promise<Shipment | null> => {
    try {
      return await api.get<Shipment>(`${path(id)}/shipment`)
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) return null
      throw error
    }
  },
  createShipment: (id: string, note?: string) =>
    api.post<Shipment>(`/api/v1/orders/${encodeURIComponent(id)}/shipment`, {
      provider: 'GHN',
      note: note?.trim() || undefined
    }),
  updateStatus: (id: string, status: OrderStatus, reason?: string) =>
    api.put<OrderDetail>(`/api/v1/orders/${encodeURIComponent(id)}/status`, {
      status,
      reason: reason?.trim() || undefined
    })
}
