export interface Inventory {
  id: string
  variantId: string
  productId: string
  quantity: number
  quantityReserved: number
  quantityAvailable: number
  minThreshold: number
  updatedAt?: string
}

export interface InventoryRow {
  variantId: string
  productId: string
  productName: string
  sku: string
  active: boolean
  quantity: number
  reservedQuantity: number
  availableQuantity: number
  minThreshold: number
  updatedAt?: string
}

export type LedgerType = 'RESERVE' | 'CONFIRM' | 'RELEASE' | 'RESTOCK' | 'ADJUST' | 'IN' | 'OUT'
export interface LedgerRow {
  id: string
  variantId: string
  type: LedgerType
  quantity: number
  refOrderId?: string
  reason?: string
  quantityBefore?: number
  quantityAfter?: number
  reservedBefore?: number
  reservedAfter?: number
  operationId?: string
  createdBy?: string
  createdAt: string
}
export interface ReservationRow {
  orderId: string
  quantity: number
  status: string
  createdAt: string
  updatedAt?: string
}
export type StockOperation = { quantity: number; reason: string; operationId: string }
export type StockKind = 'receive' | 'adjust'
