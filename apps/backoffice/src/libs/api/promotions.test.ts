import { afterEach, expect, it, vi } from 'vitest'

import { promotionsApi } from './promotions'

afterEach(() => vi.restoreAllMocks())

const page = {
  pageNo: 0,
  pageSize: 20,
  totalPage: 1,
  totalElements: 1,
  items: [
    {
      id: 'p1',
      code: 'SALE10',
      type: 'PERCENT',
      value: 10,
      startAt: '2026-01-01T00:00:00',
      endAt: '2026-01-31T23:59:59',
      scopeType: 'ALL',
      active: true
    }
  ]
}

it('lists promotions with active filter and paging', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ data: page })))

  const result = await promotionsApi.list({ page: 0, size: 20, active: true })

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/promotions?page=0&size=20&active=true')
  expect(result.items).toHaveLength(1)
  expect(result.totalElements).toBe(1)
})

it('omits empty params when listing', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ data: page })))

  await promotionsApi.list({ page: 0, size: 20 })

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/promotions?page=0&size=20')
})

it('creates a promotion with serialized input', async () => {
  const fetch = vi
    .spyOn(globalThis, 'fetch')
    .mockResolvedValue(new Response(JSON.stringify({ data: page.items[0] })))

  const input = {
    code: 'SALE10',
    type: 'PERCENT' as const,
    value: 10,
    startAt: '2026-01-01T00:00',
    endAt: '2026-01-31T23:59',
    scopeType: 'ALL' as const,
    active: true
  }

  await promotionsApi.create(input)

  const [url, init] = fetch.mock.calls[0]

  expect(url).toBe('/api/v1/admin/promotions')
  expect(JSON.parse(String(init?.body))).toMatchObject({ code: 'SALE10', value: 10 })
})

it('toggles active status via PATCH query param', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ data: null })))

  await promotionsApi.setActive('p1', false)

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/promotions/p1/status?active=false')
  expect(fetch.mock.calls[0][1]?.method).toBe('PATCH')
})
