import React, { act } from 'react'

import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { Permission } from '@/configs/permissions'
import RequirePermission from './RequirePermission'

const mocks = vi.hoisted(() => ({
  hasAnyPermission: vi.fn(),
  hasPermission: vi.fn()
}))

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({
    hasAnyPermission: mocks.hasAnyPermission,
    hasPermission: mocks.hasPermission
  })
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

describe('RequirePermission', () => {
  it('renders children when user has required permission', () => {
    mocks.hasAnyPermission.mockReturnValue(true)

    act(() => {
      root.render(
        <RequirePermission anyOf={[Permission.ORDER_READ]}>
          <div data-testid='protected-content'>Orders Content</div>
        </RequirePermission>
      )
    })

    expect(container.textContent).toContain('Orders Content')
    expect(container.textContent).not.toContain('Bạn không có quyền xem trang này')
  })

  it('renders PageHeader and permission denied message when user lacks permission', () => {
    mocks.hasAnyPermission.mockReturnValue(false)

    act(() => {
      root.render(
        <RequirePermission anyOf={[Permission.WALLET_READ]}>
          <div data-testid='protected-content'>Wallets Content</div>
        </RequirePermission>
      )
    })

    expect(container.textContent).not.toContain('Wallets Content')
    expect(container.textContent).toContain('không có quyền')
    expect(container.textContent).toContain('Bạn không có quyền xem trang này')
  })
})
