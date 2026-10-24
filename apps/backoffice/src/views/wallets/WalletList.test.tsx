import { act } from 'react'

import { createRoot } from 'react-dom/client'

// eslint-disable-next-line import/named
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, expect, it, vi } from 'vitest'

import WalletList from './WalletList'
import { walletsApi } from '@/libs/api/wallets'

const state = vi.hoisted(() => ({
  page: 0,
  size: 20,
  filters: {} as Record<string, string>,
  setPage: vi.fn(),
  setSize: vi.fn(),
  setFilters: vi.fn()
}))

vi.mock('@/hooks/useListQueryState', async importOriginal => ({
  ...(await importOriginal<object>()),
  default: () => state
}))

vi.mock('next/link', () => ({
  default: ({ children, href, ...props }: any) => (
    <a href={href} {...props}>
      {children}
    </a>
  )
}))

vi.mock('@/libs/api/wallets', () => ({
  walletsApi: {
    list: vi.fn()
  }
}))

afterEach(() => {
  vi.restoreAllMocks()
  state.page = 0
  state.filters = {}
  state.setPage.mockClear()
  state.setFilters.mockClear()
})

it('renders table rows and money formatting for wallets', async () => {
  ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true

  vi.mocked(walletsApi.list).mockResolvedValue({
    content: [
      {
        userId: 'buyer-uuid-123',
        currency: 'VND',
        balance: '500000'
      }
    ],
    number: 0,
    totalPages: 1,
    totalElements: 1,
    size: 20
  })

  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false }
    }
  })

  const container = document.createElement('div')
  const root = createRoot(container)

  document.body.append(container)

  try {
    await act(async () => {
      root.render(
        <QueryClientProvider client={client}>
          <WalletList />
        </QueryClientProvider>
      )
    })

    await act(async () => {
      await new Promise(resolve => setTimeout(resolve, 50))
    })

    expect(container.textContent).toContain('buyer-uuid-123')
    expect(container.textContent).toContain('VND')
    expect(container.textContent).toMatch(/500\.000/)
    expect(container.querySelector('a[href="/wallets/buyer-uuid-123"]')).not.toBeNull()
  } finally {
    act(() => root.unmount())
    container.remove()
  }
})
