import React, { act } from 'react'

import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { Permission } from '@/configs/permissions'
import type { PermissionItem } from '@/types/group'
import PermissionChecklist from './PermissionChecklist'

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

describe('PermissionChecklist', () => {
  const items: PermissionItem[] = [
    {
      authority: Permission.ORDER_READ,
      area: 'Bán hàng',
      label: 'Xem đơn hàng',
      isAssignable: true
    },
    {
      authority: Permission.GROUP_MANAGE,
      area: 'Hệ thống',
      label: 'Quản trị nhóm nội bộ',
      isAssignable: false
    }
  ]

  it('renders assignable permissions and excludes non-assignable permissions', () => {
    act(() => {
      root.render(
        <PermissionChecklist
          items={items}
          selected={new Set([Permission.ORDER_READ])}
          onChange={vi.fn()}
        />
      )
    })

    expect(container.textContent).toContain('Xem đơn hàng')
    expect(container.textContent).not.toContain('Quản trị nhóm nội bộ')
  })
})
