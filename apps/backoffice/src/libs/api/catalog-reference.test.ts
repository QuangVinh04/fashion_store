import { afterEach, describe, expect, it, vi } from 'vitest'

import { referenceApi } from './catalog-reference'
import { getColors, getSizes } from './catalog'

afterEach(() => vi.restoreAllMocks())

describe('catalog reference contracts', () => {
  it('uses canonical admin and active-option endpoints', async () => {
    const fetch = vi
      .spyOn(globalThis, 'fetch')
      .mockImplementation(
        async () =>
          new Response(JSON.stringify({ code: 1000, data: { pageNo: 0, pageSize: 20, totalPage: 1, items: [] } }))
      )

    await referenceApi.listColors({ page: 0, size: 20 })
    await referenceApi.listSizes({ page: 0, size: 20 })
    await getColors()
    await getSizes()
    expect(fetch.mock.calls.map(call => call[0])).toEqual([
      '/admin/color-options?page=0&size=20',
      '/admin/size-options?page=0&size=20',
      '/api/v1/color-options',
      '/api/v1/size-options'
    ])
  })

  it('sends colorHex and does not send invented hexCode or IDs in write payloads', async () => {
    const fetch = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ code: 1000, data: { id: 'c1' } })))

    await referenceApi.save('colors', { id: 'c1', name: 'Đen', colorHex: '#000000', active: true, displayOrder: 0 })
    expect(fetch.mock.calls[0][0]).toBe('/admin/color-options/c1')
    expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual({
      name: 'Đen',
      colorHex: '#000000',
      active: true,
      displayOrder: 0
    })
  })

  it('propagates a refused deletion rather than reporting success', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ message: 'Màu đang được sử dụng', code: 4001 }), { status: 409 })
    )
    await expect(referenceApi.remove('colors', 'c1')).rejects.toMatchObject({ status: 409 })
  })
})
