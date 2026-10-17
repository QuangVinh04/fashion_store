import { afterEach, expect, it, vi } from 'vitest'

import { reviewsApi } from './reviews'

afterEach(() => vi.restoreAllMocks())

const mockReviewPage = {
  pageNo: 0,
  pageSize: 20,
  totalPage: 1,
  totalElements: 1,
  items: [
    {
      id: 'rev-1',
      productId: 'prod-1',
      productName: 'Áo Polo Nam',
      userId: 'user-100',
      customerName: 'Nguyễn Văn A',
      orderId: 'ord-200',
      rating: 5,
      comment: 'Sản phẩm rất đẹp',
      status: 'VISIBLE',
      verifiedPurchase: true,
      createdAt: '2026-10-17T12:00:00'
    }
  ]
}

it('lists reviews with keyword, rating, status filter and pagination', async () => {
  const fetch = vi
    .spyOn(globalThis, 'fetch')
    .mockResolvedValue(new Response(JSON.stringify({ data: mockReviewPage })))

  const result = await reviewsApi.list({ page: 0, size: 20, rating: 5, status: 'VISIBLE', keyword: 'Polo' })

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/reviews?page=0&size=20&rating=5&status=VISIBLE&keyword=Polo')
  expect(result.items).toHaveLength(1)
  expect(result.totalElements).toBe(1)
  expect(result.items[0].productName).toBe('Áo Polo Nam')
  expect(result.items[0].customerName).toBe('Nguyễn Văn A')
})

it('omits rating when not provided', async () => {
  const fetch = vi
    .spyOn(globalThis, 'fetch')
    .mockResolvedValue(new Response(JSON.stringify({ data: mockReviewPage })))

  await reviewsApi.list({ page: 0, size: 20 })

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/reviews?page=0&size=20')
})

it('calls updateStatus endpoint', async () => {
  const fetch = vi
    .spyOn(globalThis, 'fetch')
    .mockResolvedValue(new Response(JSON.stringify({ data: { id: 'rev-1', status: 'HIDDEN' } })))

  await reviewsApi.updateStatus('rev-1', 'HIDDEN')

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/reviews/rev-1/status')
  expect(fetch.mock.calls[0][1]?.method).toBe('PATCH')
  expect(JSON.parse(fetch.mock.calls[0][1]?.body as string)).toEqual({ status: 'HIDDEN' })
})

it('calls reply endpoint', async () => {
  const fetch = vi
    .spyOn(globalThis, 'fetch')
    .mockResolvedValue(new Response(JSON.stringify({ data: { id: 'rev-1', replyComment: 'Cảm ơn bạn' } })))

  await reviewsApi.reply('rev-1', 'Cảm ơn bạn')

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/reviews/rev-1/reply')
  expect(fetch.mock.calls[0][1]?.method).toBe('POST')
  expect(JSON.parse(fetch.mock.calls[0][1]?.body as string)).toEqual({ reply: 'Cảm ơn bạn' })
})

it('calls deleteReply endpoint', async () => {
  const fetch = vi
    .spyOn(globalThis, 'fetch')
    .mockResolvedValue(new Response(JSON.stringify({ data: { id: 'rev-1' } })))

  await reviewsApi.deleteReply('rev-1')

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/reviews/rev-1/reply')
  expect(fetch.mock.calls[0][1]?.method).toBe('DELETE')
})

it('calls removeAdmin endpoint', async () => {
  const fetch = vi
    .spyOn(globalThis, 'fetch')
    .mockResolvedValue(new Response(JSON.stringify({ data: null })))

  await reviewsApi.removeAdmin('rev-1')

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/reviews/rev-1')
  expect(fetch.mock.calls[0][1]?.method).toBe('DELETE')
})
