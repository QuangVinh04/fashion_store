import { afterEach, describe, expect, it, vi } from 'vitest'

import { metadataApi } from './catalog-metadata'
import { validateSizeRow } from '@/views/catalog/size-charts/validation'

afterEach(() => vi.restoreAllMocks())

describe('catalog metadata contracts', () => {
  it('always supplies an initial variants array when creating an attribute', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ data: { id: 'a1' } })))

    await metadataApi.saveAttribute({ name: 'Chất liệu', displayName: 'Chất liệu', published: true })
    expect(fetch.mock.calls[0][0]).toBe('/admin/product-attributes')
    expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toMatchObject({ name: 'Chất liệu', variants: [], published: true })
  })

  it('uses the real option write fields and scoped URL', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ data: { id: 'o1' } })))

    await metadataApi.saveOption('a1', { id: 'o1', value: 'Cotton', published: false })
    expect(fetch.mock.calls[0][0]).toBe('/admin/product-attributes/a1/options/o1')
    expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toMatchObject({ name: 'Cotton', value: 'Cotton', published: false })
  })

  it('loads charts by ID and sends measurement rows to the chart endpoint', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => new Response(JSON.stringify({ data: { id: 'chart-1' } })))

    await metadataApi.getChart('chart-1')
    await metadataApi.saveRow('chart-1', { sizeCode: 'M', chest: 92, heightMin: 160, heightMax: 175 })
    expect(fetch.mock.calls.map(call => call[0])).toEqual(['/api/v1/size-charts/chart-1', '/admin/size-charts/chart-1/rows'])
  })

  it('propagates a duplicate row error without altering the submitted draft', async () => {
    const row = { sizeCode: 'M', chest: 92 }

    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ message: 'Duplicate size code' }), { status: 409 }))
    await expect(metadataApi.saveRow('chart-1', row)).rejects.toMatchObject({ status: 409 })
    expect(row).toEqual({ sizeCode: 'M', chest: 92 })
  })

  it('rejects blank codes, nonfinite measurements and inverted fit ranges', () => {
    expect(validateSizeRow({ sizeCode: '', heightMin: 170, heightMax: 160, chest: NaN })).toMatchObject({ sizeCode: expect.any(String), heightMax: expect.any(String), chest: expect.any(String) })
    expect(validateSizeRow({ sizeCode: 'M', heightMin: 0 })).toHaveProperty('heightMin')
    expect(validateSizeRow({ sizeCode: 'M', chest: 92, heightMin: 160, heightMax: 175 })).toEqual({})
  })
})
