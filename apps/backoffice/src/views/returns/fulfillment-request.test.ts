import { it, expect } from 'vitest'

import { saveFulfillmentRequest, readFulfillmentRequest } from './fulfillment-request'

it('restores the exact uncertain carrier request after reload and refuses changed evidence', () => {
  const memory = new Map<string, string>()

  const storage = {
    getItem: (key: string) => memory.get(key) || null,
    setItem: (key: string, value: string) => memory.set(key, value)
  }

  const input = { orderId: 'order', kind: 'carrier' as const, reason: 'Returned parcel', mediaIds: ['photo'] }

  saveFulfillmentRequest(storage, input)
  expect(readFulfillmentRequest(storage)).toEqual(input)
  expect(() => saveFulfillmentRequest(storage, { ...input, mediaIds: ['new-photo'] })).toThrow()
})
