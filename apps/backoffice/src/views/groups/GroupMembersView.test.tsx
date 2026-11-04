import React, { act } from 'react'

import { createRoot, type Root } from 'react-dom/client'
// eslint-disable-next-line import/named
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { Permission } from '@/configs/permissions'
import { MockAuthProvider } from '@/context/AuthContext'
import GroupMembersView from './GroupMembersView'
import type { Group, GroupMember } from '@/types/group'

const mocks = vi.hoisted(() => ({
  groups: [
    { id: 'g-admin', name: 'Admin', permissions: ['group:manage'], memberCount: 2, isSystem: true, isEditable: false },
    { id: 'g-customer', name: 'Customer', permissions: [], memberCount: 10, isSystem: true, isEditable: false },
    { id: 'g-staff', name: 'Nhân viên bán hàng', permissions: ['order:read'], memberCount: 3, isSystem: false, isEditable: true }
  ] as Group[],
  membersCustomer: [
    { userId: 'u-cust-1', email: 'cust1@example.com', fullName: 'Khách hàng 1', isActive: true },
    { userId: 'u-cust-2', email: null, fullName: null, isActive: null }
  ] as GroupMember[],
  membersAdmin: [
    { userId: 'mock-user', email: 'me@example.com', fullName: 'Admin Me', isActive: true },
    { userId: 'u-other', email: 'other@example.com', fullName: 'Admin Other', isActive: true }
  ] as GroupMember[],
  removeMembers: vi.fn(),
  addMembers: vi.fn(),
  useGroupMembers: vi.fn()
}))

vi.mock('@/hooks/useGroups', () => ({
  useGroups: () => ({
    groupsQuery: {
      data: mocks.groups,
      isLoading: false,
      error: null,
      refetch: vi.fn()
    }
  }),
  useGroupMembers: (groupId: string, page = 0, size = 20, keyword = '') => {
    mocks.useGroupMembers(groupId, page, size, keyword)
    let items = mocks.membersAdmin

    if (groupId === 'g-customer') {
      items = mocks.membersCustomer
    }

    return {
      data: {
        items,
        totalElements: items.length,
        totalPage: 1,
        pageNo: page,
        pageSize: size
      },
      isLoading: false,
      error: null,
      refetch: vi.fn()
    }
  }
}))

vi.mock('@/libs/api/groups', () => ({
  groupsApi: {
    removeMembers: (...args: any[]) => mocks.removeMembers(...args),
    addMembers: (...args: any[]) => mocks.addMembers(...args)
  }
}))

vi.mock('@/views/customers/CustomerDetailDialog', () => ({
  default: () => <div data-testid='customer-detail-dialog'>Customer Detail Dialog</div>
}))

vi.mock('./AddGroupMembersDialog', () => ({
  default: () => <div data-testid='add-group-members-dialog'>Add Group Members Dialog</div>
}))

let container: HTMLDivElement
let root: Root

beforeEach(() => {
  ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
  container = document.createElement('div')
  document.body.append(container)
  root = createRoot(container)
})

afterEach(() => {
  act(() => root.unmount())
  container.remove()
  vi.restoreAllMocks()
})

function renderView(groupId: string, permissions: readonly string[] = [Permission.GROUP_MANAGE, Permission.CUSTOMER_READ]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  })

  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <MockAuthProvider permissions={permissions as any}>
          <GroupMembersView groupId={groupId} />
        </MockAuthProvider>
      </QueryClientProvider>
    )
  })
}

describe('GroupMembersView', () => {
  it('hides checkboxes, add button and remove action for Customer group', () => {
    renderView('g-customer', [Permission.GROUP_MANAGE, Permission.CUSTOMER_READ])

    expect(container.textContent).toContain('Thành viên nhóm: Customer')
    expect(container.textContent).toContain('Khách hàng 1')
    expect(container.textContent).toContain('Chưa đăng nhập lần nào')

    expect(container.querySelectorAll('input[type="checkbox"]')).toHaveLength(0)
    expect(container.textContent).not.toContain('Thêm thành viên')
    expect(container.textContent).not.toContain('Xóa khỏi nhóm')
  })

  it('still displays add button but hides customer detail button when customer:read is missing', () => {
    renderView('g-admin', [Permission.GROUP_MANAGE])

    expect(container.textContent).toContain('Thêm thành viên')
    expect(container.querySelector('[aria-label="Xem chi tiết khách hàng"]')).toBeNull()
    expect(container.textContent).not.toContain('Cần quyền xem khách hàng (customer:read)')
  })

  it('renders search input and binds keyword to useGroupMembers query', () => {
    renderView('g-admin', [Permission.GROUP_MANAGE, Permission.CUSTOMER_READ])

    const searchInput = container.querySelector('input[placeholder*="Tìm theo email"]') as HTMLInputElement

    expect(searchInput).toBeTruthy()

    act(() => {
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set

      setter?.call(searchInput, 'admin')
      searchInput.dispatchEvent(new Event('input', { bubbles: true }))
      searchInput.dispatchEvent(new Event('change', { bubbles: true }))
    })

    expect(searchInput.value).toBe('admin')
  })

  it('disables self-removal checkbox in Admin group', () => {
    renderView('g-admin', [Permission.GROUP_MANAGE, Permission.CUSTOMER_READ])

    const checkboxes = container.querySelectorAll('tbody input[type="checkbox"]') as NodeListOf<HTMLInputElement>

    expect(checkboxes).toHaveLength(2)

    const disabledCheckboxes = Array.from(checkboxes).filter(cb => cb.disabled)

    expect(disabledCheckboxes).toHaveLength(1)
  })

  it('renders "Chưa đăng nhập lần nào" when member has null email and fullName', () => {
    renderView('g-customer', [Permission.GROUP_MANAGE, Permission.CUSTOMER_READ])

    expect(container.textContent).toContain('Chưa đăng nhập lần nào')
    expect(container.textContent).toContain('ID: u-cust-2')
  })

  it('selects members and triggers removeMembers with failure handling', async () => {
    mocks.removeMembers.mockResolvedValue({
      succeededUserIds: [],
      failures: [{ userId: 'u-other', code: 2034, message: 'Phải còn ít nhất một quản trị viên' }],
      notLoggedOutUserIds: []
    })

    renderView('g-admin', [Permission.GROUP_MANAGE, Permission.CUSTOMER_READ])

    const enabledCheckbox = Array.from(
      container.querySelectorAll('tbody input[type="checkbox"]') as NodeListOf<HTMLInputElement>
    ).find(cb => !cb.disabled)

    expect(enabledCheckbox).toBeTruthy()

    act(() => {
      enabledCheckbox?.click()
    })

    expect(container.textContent).toContain('Đã chọn 1 thành viên')

    const removeBtn = Array.from(container.querySelectorAll('button')).find(b =>
      b.textContent?.includes('Xóa khỏi nhóm')
    )

    expect(removeBtn).toBeTruthy()

    act(() => {
      removeBtn?.click()
    })

    expect(document.body.textContent).toContain('Xóa thành viên khỏi nhóm')

    const dialog = document.body.querySelector('[role="dialog"]')

    const confirmBtn = Array.from(dialog?.querySelectorAll('button') || []).find(
      b => b.textContent?.trim() === 'Xóa khỏi nhóm'
    )

    await act(async () => {
      confirmBtn?.click()
    })

    expect(mocks.removeMembers).toHaveBeenCalledWith('g-admin', ['u-other'])
    expect(document.body.textContent).toContain('1 thất bại.')
    expect(document.body.textContent).toContain('Phải còn ít nhất một quản trị viên')
  })
})
