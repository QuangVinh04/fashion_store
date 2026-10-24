import { afterEach, expect, it, vi } from 'vitest'

import { ordersApi } from './orders'

afterEach(() => vi.restoreAllMocks())

it('uses the admin shipment read path and order UUID for details', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => new Response(JSON.stringify({ data: {} })))

  await ordersApi.get('order-uuid')
  await ordersApi.shipment('order-uuid')
  expect(fetch.mock.calls.map(call => call[0])).toEqual([
    '/api/v1/admin/orders/order-uuid',
    '/api/v1/admin/orders/order-uuid/shipment'
  ])
})

it('treats only a missing shipment as empty and preserves access-denied errors', async () => {
  vi.spyOn(globalThis, 'fetch')
    .mockResolvedValueOnce(new Response('{}', { status: 404 }))
    .mockResolvedValueOnce(new Response('{}', { status: 403 }))
  await expect(ordersApi.shipment('id')).resolves.toBeNull()
  await expect(ordersApi.shipment('id')).rejects.toMatchObject({ status: 403 })
})
