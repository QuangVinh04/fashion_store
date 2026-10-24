import { afterEach, expect, it, vi } from 'vitest'

import { inventoryApi } from './inventory'
import { createStockOperation, validateStockOperation, executeStockOperation } from '@/views/inventory/stock-operation'

afterEach(() => vi.restoreAllMocks())

it('keeps one immutable operation identifier and body for retries while new operations have new IDs', async () => {
  const first = createStockOperation(2, 'Receive shipment')
  const next = createStockOperation(2, 'Receive shipment')
  const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => new Response(JSON.stringify({ data: {} })))

  await inventoryApi.receive('v1', first)
  await inventoryApi.receive('v1', first)
  expect(fetch.mock.calls[0][1]?.body).toBe(fetch.mock.calls[1][1]?.body)
  expect(first.operationId).not.toBe(next.operationId)
  expect(fetch.mock.calls[0][0]).toBe('/admin/inventory/variants/v1/receipts')
})

it('distinguishes receipt and absolute stock count validations', () => {
  expect(validateStockOperation('receive', 0, '', 0)).not.toEqual({})
  expect(validateStockOperation('adjust', 2, 'Count', 3)).toHaveProperty('quantity')
  expect(validateStockOperation('adjust', 0, 'Count', 0)).toEqual({})
})

it('reads ledger and current stock after a lost response before reporting a receipt succeeded', async () => {
  const payload = createStockOperation(2, 'Receive')

  vi.spyOn(globalThis, 'fetch')
    .mockRejectedValueOnce(new TypeError('Lost response'))
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          data: {
            pageNo: 0,
            pageSize: 50,
            totalPage: 1,
            totalElements: 1,
            items: [{ operationId: payload.operationId }]
          }
        })
      )
    )
    .mockResolvedValueOnce(new Response(JSON.stringify({ data: { variantId: 'v1', quantity: 12 } })))
  await expect(executeStockOperation('v1', 'receive', payload)).resolves.toMatchObject({ quantity: 12 })
})
