import React, { act } from 'react'

import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { Permission } from '@/configs/permissions'
import { MockAuthProvider } from '@/context/AuthContext'
import VerticalMenu from './VerticalMenu'

vi.mock('@menu/hooks/useVerticalNav', () => ({
  default: () => ({ isBreakpointReached: false, transitionDuration: 300 })
}))

vi.mock('react-perfect-scrollbar', () => ({
  default: ({ children }: { children: React.ReactNode }) => <div>{children}</div>
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

describe('VerticalMenu', () => {
  it('displays "Nhóm & phân quyền" for admin with group:manage permission', () => {
    act(() => {
      root.render(
        <MockAuthProvider permissions={[Permission.GROUP_MANAGE, Permission.DASHBOARD_READ]}>
          <VerticalMenu scrollMenu={vi.fn()} />
        </MockAuthProvider>
      )
    })

    expect(document.body.textContent).toContain('Nhóm & phân quyền')
    expect(document.body.textContent).toContain('Quản trị')
    expect(document.body.textContent).toContain('Tổng quan')
  })

  it('hides "Nhóm & phân quyền" when user lacks group:manage permission', () => {
    act(() => {
      root.render(
        <MockAuthProvider permissions={[Permission.ORDER_READ]}>
          <VerticalMenu scrollMenu={vi.fn()} />
        </MockAuthProvider>
      )
    })

    expect(document.body.textContent).not.toContain('Nhóm & phân quyền')
    expect(document.body.textContent).not.toContain('Quản trị')
    expect(document.body.textContent).toContain('Bán hàng')
    expect(document.body.textContent).toContain('Danh sách đơn hàng')
    expect(document.body.textContent).not.toContain('Yêu cầu đổi trả')
  })
})
