import { act } from 'react'

import { createRoot } from 'react-dom/client'

// eslint-disable-next-line import/named
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { expect, it, vi } from 'vitest'

import MediaLibrary from './MediaLibrary'

const { setPage } = vi.hoisted(() => ({ setPage: vi.fn() }))

vi.mock('@/hooks/useListQueryState', async importOriginal => ({
  ...await importOriginal<object>(),
  default: () => ({ page: 2, size: 20, filters: {}, setPage, setFilters: vi.fn() })
}))

it('returns to an existing page after the final item on the last page disappears', async () => {
  ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
  const container = document.createElement('div')
  const root = createRoot(container)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ data: { pageNo: 2, pageSize: 20, totalPage: 2, items: [] } })))

  document.body.append(container)

  try {
    await act(async () => root.render(<QueryClientProvider client={client}><MediaLibrary /></QueryClientProvider>))
    await act(async () => { await new Promise(resolve => setTimeout(resolve, 50)) })
    expect(setPage).toHaveBeenCalledWith(1)
  } finally {
    act(() => root.unmount())
    client.clear()
    container.remove()
    fetch.mockRestore()
  }
})
