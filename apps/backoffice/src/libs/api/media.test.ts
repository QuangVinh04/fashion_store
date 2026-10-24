import { afterEach, describe, expect, it, vi } from 'vitest'

import { mediaApi } from './media'

afterEach(() => vi.restoreAllMocks())

describe('media upload protocol', () => {
  it('signs, uploads bytes without BFF credentials, then completes before returning the image', async () => {
    document.cookie = 'FS_BACKOFFICE_XSRF=csrf; path=/'

    const fetch = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            data: {
              mediaId: 'm1',
              uploadUrl: 'https://storage.example.test/signed',
              contentType: 'image/png',
              uploadHeaders: { 'Content-Type': 'image/png' }
            }
          })
        )
      )
      .mockResolvedValueOnce(new Response(null, { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: { id: 'm1', status: 'ACTIVE', mediaType: 'IMAGE' } })))

    const file = new File(['bytes'], 'shirt.png', { type: 'image/png' })

    expect(await mediaApi.upload(file)).toMatchObject({ id: 'm1', status: 'ACTIVE' })
    expect(fetch.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/files/presign',
      'https://storage.example.test/signed',
      '/api/v1/files/m1/complete'
    ])
    expect(fetch.mock.calls[1][1]).toMatchObject({ method: 'PUT', credentials: 'omit', body: file })
    expect(new Headers(fetch.mock.calls[1][1]?.headers).has('X-XSRF-TOKEN')).toBe(false)
  })

  it('does not complete or select the image when storage rejects the upload', async () => {
    const fetch = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            data: { mediaId: 'm1', uploadUrl: 'https://storage.example.test/signed', uploadHeaders: {} }
          })
        )
      )
      .mockResolvedValueOnce(new Response(null, { status: 403 }))

    await expect(mediaApi.upload(new File(['bytes'], 'shirt.png', { type: 'image/png' }))).rejects.toThrow()
    expect(fetch).toHaveBeenCalledTimes(2)
  })

  it('filters the selectable library to active images', async () => {
    const fetch = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ data: { pageNo: 0, pageSize: 20, totalPage: 0, items: [] } })))

    await mediaApi.list({ page: 0, size: 20, status: 'ACTIVE', mediaType: 'IMAGE', keyword: 'shirt' })
    expect(String(fetch.mock.calls[0][0])).toContain('status=ACTIVE')
    expect(String(fetch.mock.calls[0][0])).toContain('mediaType=IMAGE')
  })
})
