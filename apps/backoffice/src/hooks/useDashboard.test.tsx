import React, { act } from 'react'

import { createRoot, type Root } from 'react-dom/client'
// eslint-disable-next-line import/named
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useDashboard } from './useDashboard'

const mocks = vi.hoisted(() => ({
  hasPermission: vi.fn(),
  getStats: vi.fn(),
  getOperations: vi.fn()
}))

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({
    hasPermission: mocks.hasPermission
  })
}))

vi.mock('@/libs/api/dashboard', () => ({
  dashboardApi: {
    getStats: mocks.getStats,
    getOperations: mocks.getOperations
  }
}))

let container: HTMLDivElement
let root: Root

beforeEach(() => {
  ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
  container = document.createElement('div')
  document.body.append(container)
  root = createRoot(container)
  mocks.getStats.mockResolvedValue({ totalRevenue: 1000, totalOrders: 5, lowStockCount: 0, revenueByDay: [], orderByStatus: [], topProducts: [] })
  mocks.getOperations.mockResolvedValue({ totalOrders: 5, lowStockCount: 0, orderByStatus: [], topProducts: [] })
})

afterEach(() => {
  act(() => root.unmount())
  container.remove()
  vi.restoreAllMocks()
})

async function settle() {
  await act(async () => {
    await new Promise(resolve => setTimeout(resolve, 50))
  })
}

function Harness({ onHook }: { onHook: (hook: ReturnType<typeof useDashboard>) => void }) {
  const hook = useDashboard(7, 10)

  onHook(hook)

  return <div>dashboard</div>
}

describe('useDashboard', () => {
  it('calls getStats and returns hasRevenue=true when user has revenue:read permission', async () => {
    mocks.hasPermission.mockImplementation((perm: string) => perm === 'revenue:read')

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    let capturedHook: ReturnType<typeof useDashboard> | null = null

    await act(async () => {
      root.render(
        <QueryClientProvider client={client}>
          <Harness onHook={hook => { capturedHook = hook }} />
        </QueryClientProvider>
      )
    })
    await settle()

    expect(mocks.getStats).toHaveBeenCalled()
    expect(mocks.getOperations).not.toHaveBeenCalled()
    expect((capturedHook as ReturnType<typeof useDashboard> | null)?.hasRevenue).toBe(true)
  })

  it('calls getOperations and returns hasRevenue=false when user lacks revenue:read permission', async () => {
    mocks.hasPermission.mockReturnValue(false)

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    let capturedHook: ReturnType<typeof useDashboard> | null = null

    await act(async () => {
      root.render(
        <QueryClientProvider client={client}>
          <Harness onHook={hook => { capturedHook = hook }} />
        </QueryClientProvider>
      )
    })
    await settle()

    expect(mocks.getOperations).toHaveBeenCalled()
    expect(mocks.getStats).not.toHaveBeenCalled()
    expect((capturedHook as ReturnType<typeof useDashboard> | null)?.hasRevenue).toBe(false)
  })
})
