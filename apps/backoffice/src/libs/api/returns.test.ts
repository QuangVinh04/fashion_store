import { afterEach, it, expect, vi } from 'vitest'

import { returnsApi } from './returns'

afterEach(() => vi.unstubAllGlobals())
it('uses canonical receive, reject and asynchronous refund contracts', async () => {
  const requests: { url: string; body: unknown }[] = []

  vi.stubGlobal('fetch', async (url: string, init: RequestInit) => {
    requests.push({ url, body: init.body ? JSON.parse(String(init.body)) : undefined })

    return new Response(
      JSON.stringify({ data: { id: 'case', status: 'RECEIVED', refundStartedAt: '2026-10-14T01:00:00Z' } }),
      { status: 202 }
    )
  })
  await returnsApi.receive('case', { qcPassed: true, qcNote: 'Checked', mediaIds: [] })
  await returnsApi.reject('case', { rejectReason: 'Damaged' })
  const result = await returnsApi.approveRefund('case', {})

  expect(requests).toEqual([
    { url: '/api/v1/admin/orders/returns/case/receive', body: { qcPassed: true, qcNote: 'Checked', mediaIds: [] } },
    { url: '/api/v1/admin/orders/returns/case/reject', body: { rejectReason: 'Damaged' } },
    { url: '/api/v1/admin/orders/returns/case/refund', body: {} }
  ])
  expect(result.status).toBe('RECEIVED')
})
it('uses page counts without fabricating missing exact totals', async () => {
  vi.stubGlobal(
    'fetch',
    async () =>
      new Response(JSON.stringify({ data: { items: [{ id: 'case' }], pageNo: 2, pageSize: 20, totalPage: 3 } }))
  )
  const result = await returnsApi.list({ page: 2, size: 20, status: 'REQUESTED' })

  expect(result.items).toHaveLength(1)
  expect(result.totalPage).toBe(3)
  expect(result.totalElements).toBeUndefined()
})
it('uses source-specific ownership checked private evidence URLs', async () => {
  const urls: string[] = []

  vi.stubGlobal('fetch', async (url: string) => {
    urls.push(url)

    return new Response(JSON.stringify({ data: 'https://example.test/signed' }))
  })
  await returnsApi.evidenceUrl('case', 'photo', 'customer')
  await returnsApi.evidenceUrl('case', 'photo', 'qc')
  await returnsApi.evidenceUrl('case', 'photo', 'verification')
  expect(urls).toEqual([
    '/api/v1/admin/orders/returns/case/customer-evidence/photo/url',
    '/api/v1/admin/orders/returns/case/qc-evidence/photo/url',
    '/api/v1/admin/orders/returns/case/verification-evidence/photo/url'
  ])
})
