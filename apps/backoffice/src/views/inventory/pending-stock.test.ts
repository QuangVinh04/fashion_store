import { beforeEach, expect, it } from 'vitest'

import { createStockOperation } from './stock-operation'
import { readPendingStock, savePendingStock, clearPendingStock } from './pending-stock'

beforeEach(() => localStorage.clear())

it('restores exactly the same receipt after remount/reload and isolates variant and kind', () => {
  const original = createStockOperation(5, 'Shipment received')

  savePendingStock('sku-a', 'receive', original)
  const restored = readPendingStock('sku-a', 'receive')

  expect(restored).toEqual(original)
  expect(Object.isFrozen(restored)).toBe(true)
  expect(readPendingStock('sku-b', 'receive')).toBeNull()
  expect(readPendingStock('sku-a', 'adjust')).toBeNull()
  clearPendingStock('sku-a', 'receive')
  expect(readPendingStock('sku-a', 'receive')).toBeNull()
})

it('rejects damaged pending data rather than silently issuing a new operation', () => {
  localStorage.setItem('backoffice:pending-stock:sku-a:receive', '{"quantity":5}')
  expect(() => readPendingStock('sku-a', 'receive')).toThrow()
})
