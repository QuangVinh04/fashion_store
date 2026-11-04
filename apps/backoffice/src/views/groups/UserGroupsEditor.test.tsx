import React, { act } from 'react'

import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { Group } from '@/types/group'
import { ApiError } from '@/libs/api/client'
import UserGroupsEditor from './UserGroupsEditor'

const mocks = vi.hoisted(() => ({
  hasPermission: vi.fn(),
  setUserGroups: vi.fn(),
  groups: [
    { id: 'g-admin', name: 'Admin', permissions: ['group:manage'], memberCount: 1, isSystem: true, isEditable: false },
    { id: 'g-customer', name: 'Customer', permissions: [], memberCount: null, isSystem: true, isEditable: false },
    { id: 'g-staff', name: 'Nhân viên bán hàng', permissions: ['order:read'], memberCount: 2, isSystem: false, isEditable: true }
  ] as unknown as Group[]
}))

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({
    hasPermission: mocks.hasPermission
  })
}))

vi.mock('@/hooks/useGroups', () => ({
  useGroups: () => ({
    groupsQuery: {
      data: mocks.groups,
      isLoading: false,
      error: null
    },
    setUserGroupsMutation: {
      mutateAsync: mocks.setUserGroups,
      isPending: false
    }
  })
}))

let container: HTMLDivElement
let root: Root

beforeEach(() => {
  ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
  container = document.createElement('div')
  document.body.append(container)
  root = createRoot(container)
  mocks.hasPermission.mockReturnValue(true)
})

afterEach(() => {
  act(() => root.unmount())
  container.remove()
  vi.restoreAllMocks()
})

describe('UserGroupsEditor', () => {
  it('does not include Customer in the selectable group options', () => {
    act(() => {
      root.render(
        <UserGroupsEditor
          userId='u1'
          initialGroups={[{ id: 'g-staff', name: 'Nhân viên bán hàng' }]}
        />
      )
    })

    const combobox = container.querySelector('[role="combobox"]') as HTMLElement

    act(() => {
      combobox?.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    })

    expect(document.body.textContent).toContain('Nhân viên bán hàng')
    expect(document.body.textContent).toContain('Admin')
    expect(document.body.textContent).not.toContain('Customer')
  })

  it('renders read-only chips when user lacks group:manage permission', () => {
    mocks.hasPermission.mockReturnValue(false)

    act(() => {
      root.render(
        <UserGroupsEditor
          userId='u1'
          initialGroups={[{ id: 'g-staff', name: 'Nhân viên bán hàng' }]}
        />
      )
    })

    expect(container.textContent).toContain('Nhân viên bán hàng')
    expect(container.querySelector('button[type="submit"]')).toBeNull()
  })

  it('preserves current selection and displays error when API returns 409 code 2035', async () => {
    mocks.setUserGroups.mockRejectedValue(
      new ApiError(409, 2035, 'Không thể tự rời nhóm Admin của chính mình')
    )

    act(() => {
      root.render(
        <UserGroupsEditor
          userId='u1'
          initialGroups={[{ id: 'g-admin', name: 'Admin' }]}
        />
      )
    })

    const button = container.querySelector('button[type="submit"]') as HTMLButtonElement

    await act(async () => {
      button?.click()
    })

    expect(container.textContent).toContain('Admin')
    expect(container.textContent).toContain('Không thể tự rời nhóm Admin của chính mình')
  })

  it('preserves current selection and displays error when API returns 409 code 2034', async () => {
    mocks.setUserGroups.mockRejectedValue(
      new ApiError(409, 2034, 'Phải còn ít nhất một quản trị viên')
    )

    act(() => {
      root.render(
        <UserGroupsEditor
          userId='u1'
          initialGroups={[{ id: 'g-admin', name: 'Admin' }]}
        />
      )
    })

    const button = container.querySelector('button[type="submit"]') as HTMLButtonElement

    await act(async () => {
      button?.click()
    })

    expect(container.textContent).toContain('Admin')
    expect(container.textContent).toContain('Phải còn ít nhất một quản trị viên')
  })
})
