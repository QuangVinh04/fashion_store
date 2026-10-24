import { api } from './client'
import { normalizeAdminPage } from './page'
import type { PageDto } from '@/types/api'
import type { Inventory, InventoryRow, LedgerRow, ReservationRow, StockOperation } from '@/types/inventory'

const variantPath = (id: string) => `/admin/inventory/variants/${encodeURIComponent(id)}`

const queryString = (params: Record<string, string | number | boolean | undefined>) => {
  const query = new URLSearchParams()

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value))
  })

  return query.toString()
}

export const inventoryApi = {
  list: async (params: { page: number; size: number; query?: string; state?: string; includeInactive?: boolean }) =>
    normalizeAdminPage(await api.get<PageDto<InventoryRow>>(`/admin/inventory?${queryString(params)}`)),
  getVariant: (id: string) => api.get<Inventory>(`/api/v1/inventory/variants/${encodeURIComponent(id)}`),
  receive: (id: string, operation: StockOperation) => api.post<Inventory>(`${variantPath(id)}/receipts`, operation),
  adjust: (id: string, operation: StockOperation) =>
    api.put<Inventory>(`/api/v1/inventory/variants/${encodeURIComponent(id)}`, operation),
  threshold: (id: string, minThreshold: number) => api.put<Inventory>(`${variantPath(id)}/threshold`, { minThreshold }),
  ledger: async (params: {
    page: number
    size: number
    variantId?: string
    type?: string
    fromTime?: string
    toTime?: string
  }) => normalizeAdminPage(await api.get<PageDto<LedgerRow>>(`/admin/inventory/ledger?${queryString(params)}`)),
  reservations: async (id: string, params: { page: number; size: number; status?: string }) =>
    normalizeAdminPage(await api.get<PageDto<ReservationRow>>(`${variantPath(id)}/reservations?${queryString(params)}`))
}
