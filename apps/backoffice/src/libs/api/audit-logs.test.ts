import { afterEach, expect, it, vi } from 'vitest'

import { auditLogsApi } from './audit-logs'

afterEach(() => vi.restoreAllMocks())

it('gọi đúng URL và bỏ qua các tham số rỗng hoặc undefined', async () => {
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
                id: 'log-1',
                sourceService: 'identity-service',
                actorId: 'usr-123',
                action: 'USER_LOCK',
                entityType: 'USER',
                entityId: 'target-456',
                summary: 'Khóa tài khoản vi phạm',
                occurredAt: '2026-11-07T12:00:00'
              }
            ]
          }
        })
      )
  )

  const result = await auditLogsApi.list({
    page: 0,
    size: 20,
    actorId: 'usr-123',
    entityType: '',
    action: undefined,
    entityId: 'target-456'
  })

  expect(fetchMock).toHaveBeenCalledWith(
    '/api/v1/admin/audit-logs?page=0&size=20&actorId=usr-123&entityId=target-456&sort=occurredAt%2Cdesc',
    expect.objectContaining({
      headers: expect.any(Headers)
    })
  )
  expect(result.items).toHaveLength(1)
  expect(result.items[0].action).toBe('USER_LOCK')
  expect(result.totalElements).toBe(1)
})

it('hỗ trợ tùy biến tham số sắp xếp sort', async () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(
    async () =>
      new Response(
        JSON.stringify({
          data: {
            pageNo: 1,
            pageSize: 10,
            totalPage: 2,
            totalElements: 15,
            items: []
          }
        })
      )
  )

  await auditLogsApi.list({
    page: 1,
    size: 10,
    sort: 'occurredAt,asc'
  })

  expect(fetchMock).toHaveBeenCalledWith(
    '/api/v1/admin/audit-logs?page=1&size=10&sort=occurredAt%2Casc',
    expect.objectContaining({
      headers: expect.any(Headers)
    })
  )
})

it('ném lỗi khi dữ liệu phân trang trả về không hợp lệ', async () => {
  vi.spyOn(globalThis, 'fetch').mockImplementation(
    async () =>
      new Response(
        JSON.stringify({
          data: {
            pageNo: -1,
            pageSize: 20,
            totalPage: 1,
            totalElements: null,
            items: []
          }
        })
      )
  )

  await expect(
    auditLogsApi.list({
      page: 0,
      size: 20
    })
  ).rejects.toThrow('Dữ liệu phân trang chưa đầy đủ hoặc không hợp lệ.')
})
