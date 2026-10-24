import { afterEach, expect, it, vi } from 'vitest'

import { customersApi } from './customers'

afterEach(() => vi.restoreAllMocks())

it('gọi đúng đường dẫn và tham số khi lấy danh sách người dùng', async () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(
    async () =>
      new Response(
        JSON.stringify({
          data: {
            pageNo: 0,
            pageSize: 20,
            totalPage: 1,
            totalElements: 1,
            items: [
              {
                id: 'u-1',
                email: 'user@example.com',
                fullName: 'Nguyễn Văn A',
                phone: '0912345678',
                isActive: true,
                isEmailVerified: true,
                createdAt: '2026-10-15T10:00:00'
              }
            ]
          }
        })
      )
  )

  const result = await customersApi.list({
    page: 0,
    size: 20,
    keyword: 'nguyen',
    isActive: true,
    sort: 'createdAt,desc'
  })

  expect(fetchMock).toHaveBeenCalledWith(
    '/api/v1/admin/users?page=0&size=20&keyword=nguyen&isActive=true&sort=createdAt%2Cdesc',
    expect.objectContaining({
      headers: expect.any(Headers)
    })
  )
  expect(result.items).toHaveLength(1)
  expect(result.items[0].email).toBe('user@example.com')
})

it('gọi đúng API lấy chi tiết và cập nhật thông tin người dùng', async () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(
    async () =>
      new Response(
        JSON.stringify({
          data: { id: 'u-1', fullName: 'Trần Văn B', addresses: [] }
        })
      )
  )

  await customersApi.get('u-1')
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/users/u-1')

  await customersApi.update('u-1', { fullName: 'Trần Văn B', phone: '0987654321' })
  expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/admin/users/u-1')
  expect(fetchMock.mock.calls[1][1]?.method).toBe('PUT')
  expect(fetchMock.mock.calls[1][1]?.body).toBe(JSON.stringify({ fullName: 'Trần Văn B', phone: '0987654321' }))
})

it('gọi đúng API khoá / mở khoá tài khoản bằng PATCH /status', async () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(
    async () =>
      new Response(
        JSON.stringify({
          data: { id: 'u-1', isActive: false }
        })
      )
  )

  await customersApi.updateStatus('u-1', false)
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/users/u-1/status')
  expect(fetchMock.mock.calls[0][1]?.method).toBe('PATCH')
  expect(fetchMock.mock.calls[0][1]?.body).toBe(JSON.stringify({ active: false }))
})
