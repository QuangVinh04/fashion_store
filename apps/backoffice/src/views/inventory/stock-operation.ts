import { inventoryApi } from '@/libs/api/inventory'
import { ApiError } from '@/libs/api/client'
import type { StockKind, StockOperation } from '@/types/inventory'

export function createStockOperation(quantity: number, reason: string): StockOperation {
  return Object.freeze({ quantity, reason: reason.trim(), operationId: crypto.randomUUID() })
}

export function validateStockOperation(kind: StockKind, quantity: number, reason: string, reserved: number) {
  const errors: Record<string, string> = {}

  if (!Number.isSafeInteger(quantity) || quantity < (kind === 'receive' ? 1 : 0))
    errors.quantity = kind === 'receive' ? 'Nhập số nguyên lớn hơn 0.' : 'Nhập số nguyên từ 0 trở lên.'
  else if (kind === 'adjust' && quantity < reserved)
    errors.quantity = `Tồn thực tế không thể thấp hơn ${reserved} đơn vị đang giữ.`
  if (!reason.trim() || reason.length > 500) errors.reason = 'Nhập lý do từ 1 đến 500 ký tự.'

  return errors
}

export async function executeStockOperation(id: string, kind: StockKind, operation: StockOperation) {
  try {
    return await inventoryApi[kind](id, operation)
  } catch (error) {
    if (error instanceof ApiError && (error.status === 0 || error.status >= 500)) {
      try {
        const ledger = await inventoryApi.ledger({
          page: 0,
          size: 50,
          variantId: id,
          type: kind === 'receive' ? 'IN' : 'ADJUST'
        })

        if (ledger.items.some(row => row.operationId === operation.operationId))
          return await inventoryApi.getVariant(id)
      } catch {
        /* Preserve the original uncertain result if verification fails. */
      }
    }

    throw error
  }
}
