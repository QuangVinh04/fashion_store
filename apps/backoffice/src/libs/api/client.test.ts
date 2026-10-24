import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

import { api, ApiError } from './client'

describe('API Client', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'FS_BACKOFFICE_XSRF=test-xsrf-token; path=/'
  })

  afterEach(() => {
    document.cookie = 'FS_BACKOFFICE_XSRF=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('attaches X-XSRF-TOKEN on mutation requests', async () => {
    const fetchSpy = vi.spyOn(global, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ code: 1000, data: { success: true } }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    )

    const res = await api.post('/api/v1/test', { name: 'test' })

    expect(res).toEqual({ success: true })
    expect(fetchSpy).toHaveBeenCalledTimes(1)
    const [calledUrl, calledOptions] = fetchSpy.mock.calls[0]

    expect(calledUrl).toBe('/api/v1/test')
    expect(calledOptions?.method).toBe('POST')

    const headers = calledOptions?.headers as Headers

    expect(headers.get('X-XSRF-TOKEN')).toBe('test-xsrf-token')
    expect(headers.get('Content-Type')).toBe('application/json')
  })

  it('unwraps ApiResponse.data on successful response', async () => {
    vi.spyOn(global, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ code: 1000, data: [{ id: 1, name: 'Product' }] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    )

    const result = await api.get<{ id: number; name: string }[]>('/api/v1/products')

    expect(result).toEqual([{ id: 1, name: 'Product' }])
  })

  it('throws ApiError with fieldErrors on HTTP 400', async () => {
    vi.spyOn(global, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          code: 2001,
          status: 400,
          message: 'Dữ liệu không hợp lệ',
          fieldErrors: { sku: 'SKU đã tồn tại' }
        }),
        {
          status: 400,
          headers: { 'Content-Type': 'application/json' }
        }
      )
    )

    await expect(api.post('/api/v1/products', {})).rejects.toThrow(ApiError)
  })

  it('handles 401 by redirecting to Keycloak authorization endpoint', async () => {
    const assignMock = vi.fn()

    delete (window as any).location
    window.location = { assign: assignMock } as any

    vi.spyOn(global, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ message: 'Unauthorized' }), {
        status: 401
      })
    )

    await expect(api.get('/api/v1/protected')).rejects.toThrow()
    expect(assignMock).toHaveBeenCalledWith('/oauth2/authorization/keycloak')
  })
})
