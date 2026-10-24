import { afterEach, describe, expect, it, vi } from 'vitest'

describe('API client failure handling', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('redirects once for concurrent unauthorized writes and never replays them', async () => {
    vi.resetModules()
    const assign = vi.fn()

    vi.stubGlobal('window', { location: { assign } })
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 401 }))
    const { api } = await import('./client')
    const results = await Promise.allSettled([api.post('/admin/a', {}), api.put('/admin/b', {})])

    expect(results.every(result => result.status === 'rejected')).toBe(true)
    expect(fetch).toHaveBeenCalledTimes(2)
    expect(assign).toHaveBeenCalledTimes(1)
  })

  it('keeps indexed field errors and correlation IDs on forbidden responses', async () => {
    const { api } = await import('./client')

    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          code: 2007,
          message: 'Forbidden',
          fieldErrors: { 'variants[0].sku': 'invalid' },
          correlationId: 'trace-123'
        }),
        { status: 403 }
      )
    )
    await expect(api.post('/admin/a', {})).rejects.toMatchObject({
      status: 403,
      correlationId: 'trace-123',
      fieldErrors: { 'variants[0].sku': 'invalid' }
    })
  })

  it('accepts empty 204 and empty 200 mutation responses', async () => {
    const { api } = await import('./client')

    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response('', { status: 200 }))
    await expect(api.delete('/admin/a')).resolves.toBeUndefined()
    await expect(api.post('/admin/a/publish')).resolves.toBeUndefined()
  })

  it('reports network failure with a readable message and preserves cancellation', async () => {
    const { api } = await import('./client')

    vi.spyOn(globalThis, 'fetch')
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockRejectedValueOnce(new DOMException('Aborted', 'AbortError'))
    await expect(api.get('/admin/a')).rejects.toMatchObject({ status: 0, message: expect.stringContaining('kết nối') })
    await expect(api.get('/admin/a')).rejects.toMatchObject({ name: 'AbortError' })
  })
})
