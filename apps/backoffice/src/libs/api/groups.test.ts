import { afterEach, expect, it, vi } from 'vitest'

import { Permission } from '@/configs/permissions'
import { groupsApi } from './groups'
import { staffApi } from './staff'

afterEach(() => vi.restoreAllMocks())

it('calls GET /api/v1/admin/permissions for listPermissions', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(JSON.stringify({ data: [{ authority: Permission.ORDER_READ, area: 'Orders', label: 'Xem đơn hàng', isAssignable: true }] }))
  )

  const result = await groupsApi.listPermissions()

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/permissions')
  expect(result[0].authority).toBe(Permission.ORDER_READ)
})

it('calls GET /api/v1/admin/groups for list', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(JSON.stringify({ data: [{ id: 'g1', name: 'Kho', permissions: [Permission.INVENTORY_READ], memberCount: 2, isSystem: false, isEditable: true }] }))
  )

  const result = await groupsApi.list()

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/groups')
  expect(result[0].name).toBe('Kho')
})

it('calls POST /api/v1/admin/groups for create', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(JSON.stringify({ data: { group: { id: 'g1', name: 'Kho', permissions: [Permission.INVENTORY_READ], memberCount: 0, isSystem: false, isEditable: true }, notLoggedOutUserIds: [] } }))
  )

  const payload = { name: 'Kho', permissions: [Permission.INVENTORY_READ] }
  const result = await groupsApi.create(payload)

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/groups')
  expect(fetch.mock.calls[0][1]?.method).toBe('POST')
  expect(JSON.parse(fetch.mock.calls[0][1]?.body as string)).toEqual(payload)
  expect(result.group.name).toBe('Kho')
})

it('calls PUT /api/v1/admin/groups/{id} for update', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(JSON.stringify({ data: { group: { id: 'g1', name: 'Kho Mới', permissions: [Permission.INVENTORY_READ], memberCount: 1, isSystem: false, isEditable: true }, notLoggedOutUserIds: ['u1'] } }))
  )

  const payload = { name: 'Kho Mới', permissions: [Permission.INVENTORY_READ] }
  const result = await groupsApi.update('g1', payload)

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/groups/g1')
  expect(fetch.mock.calls[0][1]?.method).toBe('PUT')
  expect(JSON.parse(fetch.mock.calls[0][1]?.body as string)).toEqual(payload)
  expect(result.notLoggedOutUserIds).toEqual(['u1'])
})

it('calls DELETE /api/v1/admin/groups/{id} for remove', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(JSON.stringify({ data: ['u1', 'u2'] }))
  )

  const result = await groupsApi.remove('g1')

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/groups/g1')
  expect(fetch.mock.calls[0][1]?.method).toBe('DELETE')
  expect(result).toEqual(['u1', 'u2'])
})

it('calls GET /api/v1/admin/groups/{id}/members for members', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(JSON.stringify({ data: { items: [{ userId: 'u1', email: 'test@mail.com', fullName: 'Tester' }], totalItems: 1, totalPages: 1, page: 0, size: 20 } }))
  )

  const result = await groupsApi.members('g1', 0, 20)

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/groups/g1/members?page=0&size=20')
  expect(result.items).toHaveLength(1)
})

it('calls GET /api/v1/admin/groups/{id}/members with keyword', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(JSON.stringify({ data: { items: [], totalElements: 0, totalPage: 0, pageNo: 0, pageSize: 20 } }))
  )

  await groupsApi.members('g1', 0, 20, 'nguyen')

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/groups/g1/members?page=0&size=20&keyword=nguyen')
})

it('calls GET /api/v1/admin/groups/{id}/candidates for search candidates', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(
      JSON.stringify({
        data: [{ userId: 'u1', email: 'u1@example.com', fullName: 'Nguyen Van A', isActive: true }]
      })
    )
  )

  const result = await groupsApi.candidates('g1', 'nguyen')

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/groups/g1/candidates?keyword=nguyen')
  expect(result[0].userId).toBe('u1')
})

it('calls PUT /api/v1/admin/users/{userId}/groups for setUserGroups', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(JSON.stringify({ data: { groups: [{ id: 'g1', name: 'Kho' }], notLoggedOutUserIds: [] } }))
  )

  const result = await groupsApi.setUserGroups('u1', ['g1'])

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/users/u1/groups')
  expect(fetch.mock.calls[0][1]?.method).toBe('PUT')
  expect(JSON.parse(fetch.mock.calls[0][1]?.body as string)).toEqual({ groupIds: ['g1'] })
  expect(result.groups).toHaveLength(1)
})

it('calls POST /api/v1/admin/groups/{id}/members for addMembers', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(
      JSON.stringify({
        data: { succeededUserIds: ['u1', 'u2'], failures: [], notLoggedOutUserIds: [] }
      })
    )
  )

  const result = await groupsApi.addMembers('g1', ['u1', 'u2'])

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/groups/g1/members')
  expect(fetch.mock.calls[0][1]?.method).toBe('POST')
  expect(JSON.parse(fetch.mock.calls[0][1]?.body as string)).toEqual({ userIds: ['u1', 'u2'] })
  expect(result.succeededUserIds).toEqual(['u1', 'u2'])
})

it('calls DELETE /api/v1/admin/groups/{id}/members for removeMembers', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(
      JSON.stringify({
        data: {
          succeededUserIds: ['u1'],
          failures: [{ userId: 'u2', code: 2035, message: 'Không thể tự rời nhóm Admin của chính mình' }],
          notLoggedOutUserIds: ['u1']
        }
      })
    )
  )

  const result = await groupsApi.removeMembers('g1', ['u1', 'u2'])

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/groups/g1/members')
  expect(fetch.mock.calls[0][1]?.method).toBe('DELETE')
  expect(JSON.parse(fetch.mock.calls[0][1]?.body as string)).toEqual({ userIds: ['u1', 'u2'] })
  expect(result.succeededUserIds).toEqual(['u1'])
  expect(result.failures).toHaveLength(1)
})

it('calls POST /api/v1/admin/staff for staffApi.create', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(JSON.stringify({ data: { id: 's1', email: 'staff@example.com', fullName: 'Staff One', groups: [] } }))
  )

  const payload = {
    email: 'staff@example.com',
    firstName: 'One',
    lastName: 'Staff',
    temporaryPassword: 'TempPassword123!',
    groupIds: ['g1']
  }

  const result = await staffApi.create(payload)

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/staff')
  expect(fetch.mock.calls[0][1]?.method).toBe('POST')
  expect(JSON.parse(fetch.mock.calls[0][1]?.body as string)).toEqual(payload)
  expect(result.email).toBe('staff@example.com')
})
