import type { StockKind, StockOperation } from '@/types/inventory'

const key = (variantId: string, kind: StockKind) => `backoffice:pending-stock:${variantId}:${kind}`

export function readPendingStock(variantId: string, kind: StockKind): StockOperation | null {
  const raw = localStorage.getItem(key(variantId, kind))

  if (!raw) return null
  const value = JSON.parse(raw)

  if (
    !value ||
    typeof value.operationId !== 'string' ||
    !value.operationId ||
    !Number.isSafeInteger(value.quantity) ||
    typeof value.reason !== 'string'
  )
    throw new Error('Invalid pending stock operation')

  return Object.freeze(value)
}

export function savePendingStock(variantId: string, kind: StockKind, operation: StockOperation) {
  localStorage.setItem(key(variantId, kind), JSON.stringify(operation))
}

export function clearPendingStock(variantId: string, kind: StockKind) {
  localStorage.removeItem(key(variantId, kind))
}
