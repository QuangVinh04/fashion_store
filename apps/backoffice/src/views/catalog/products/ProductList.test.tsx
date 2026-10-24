import { act } from 'react'

import { createRoot } from 'react-dom/client'

// eslint-disable-next-line import/named
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, expect, it, vi } from 'vitest'

import ProductList from './ProductList'

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
vi.mock('@/hooks/useCatalog', () => ({
  useBrandsQuery: () => ({ data: [] }),
  useCategoriesQuery: () => ({ data: [] })
}))
vi.mock('next/link', () => ({
  default: ({ children, href, ...props }: any) => (
    <a href={href} {...props}>
      {children}
    </a>
  )
}))

afterEach(() => {
  vi.restoreAllMocks()
  state.page = 0
  state.filters = {}
  state.setPage.mockClear()
  state.setFilters.mockClear()
})

async function renderList(response: object) {
  ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ data: response })))
  const container = document.createElement('div')
  const root = createRoot(container)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  document.body.append(container)
  await act(async () =>
    root.render(
      <QueryClientProvider client={client}>
        <ProductList />
      </QueryClientProvider>
    )
  )
  await act(async () => {
    await new Promise(resolve => setTimeout(resolve, 70))
  })

  return {
    container,
    fetch,
    cleanup: () => {
      act(() => root.unmount())
      client.clear()
      container.remove()
    }
  }
}

it('passes every filter to the server and displays its exact count', async () => {
  state.filters = {
    keyword: 'shirt',
    status: 'DRAFT',
    categoryId: 'c1',
    brandId: 'b1',
    gender: 'MEN',
    productType: 'SHIRT',
    minPrice: '100',
    maxPrice: '500'
  }

  const view = await renderList({
    pageNo: 0,
    pageSize: 20,
    totalPage: 3,
    totalElements: 41,
    items: [{ id: 'p1', name: 'Shirt', slug: 'shirt', status: 'DRAFT', basePrice: 200 }]
  })

  try {
    const url = new URL(String(view.fetch.mock.calls[0][0]), 'http://localhost')

    Object.entries(state.filters).forEach(([key, value]) => expect(url.searchParams.get(key)).toBe(value))
    expect(view.container.textContent).toContain('41')
  } finally {
    view.cleanup()
  }
})

it('rejects missing totals instead of reporting an invented count', async () => {
  const view = await renderList({ pageNo: 0, pageSize: 20, totalPage: 2, items: [] })

  try {
    expect(view.container.querySelector('[role="alert"]')).not.toBeNull()
  } finally {
    view.cleanup()
  }
})

it('clamps pagination after the last row disappears', async () => {
  state.page = 2
  const view = await renderList({ pageNo: 2, pageSize: 20, totalPage: 2, totalElements: 40, items: [] })

  try {
    expect(state.setPage).toHaveBeenCalledWith(1)
  } finally {
    view.cleanup()
  }
})

it('does not send an inverted price range', async () => {
  state.filters = { minPrice: '500', maxPrice: '100' }
  const view = await renderList({ pageNo: 0, pageSize: 20, totalPage: 0, totalElements: 0, items: [] })

  try {
    expect(view.fetch).not.toHaveBeenCalled()
    expect(view.container.textContent).toContain('Khoảng giá')
  } finally {
    view.cleanup()
  }
})

it('keeps detailed filters in a drawer and cancels edits without changing applied filters', async () => {
  state.filters = { gender: 'MEN', minPrice: '100' }
  const view = await renderList({ pageNo: 0, pageSize: 20, totalPage: 0, totalElements: 0, items: [] })

  try {
    expect(view.container.textContent).not.toContain('Giá từ (đ)')

    const open = Array.from(view.container.querySelectorAll('button')).find(button =>
      button.textContent?.includes('Bộ lọc nâng cao')
    )

    expect(open).toBeDefined()
    expect(open!.textContent).toContain('2')
    await act(async () => open!.click())
    const dialog = document.querySelector('[role="dialog"]')!

    expect(dialog.textContent).toContain('Khoảng giá')
    const input = dialog.querySelector('input[type="number"]') as HTMLInputElement

    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, '200')
      input.dispatchEvent(new Event('input', { bubbles: true }))
    })
    expect(state.setFilters).not.toHaveBeenCalled()
    await act(async () =>
      Array.from(dialog.querySelectorAll('button'))
        .find(button => button.textContent === 'Hủy')!
        .click()
    )
    expect(state.setFilters).not.toHaveBeenCalled()
    await act(async () => open!.click())
    expect((document.querySelector('[role="dialog"] input[type="number"]') as HTMLInputElement).value).toBe('100')
  } finally {
    view.cleanup()
  }
})

it('applies advanced filters together and can clear them without clearing the primary filters', async () => {
  state.filters = { keyword: 'shirt', status: 'DRAFT', gender: 'MEN', minPrice: '100', maxPrice: '500' }
  const view = await renderList({ pageNo: 0, pageSize: 20, totalPage: 0, totalElements: 0, items: [] })

  try {
    await act(async () =>
      Array.from(view.container.querySelectorAll('button'))
        .find(button => button.textContent?.includes('Bộ lọc nâng cao'))!
        .click()
    )
    const dialog = document.querySelector('[role="dialog"]')!

    await act(async () =>
      Array.from(dialog.querySelectorAll('button'))
        .find(button => button.textContent === 'Xóa lọc nâng cao')!
        .click()
    )
    expect(state.setFilters).not.toHaveBeenCalled()
    await act(async () =>
      Array.from(dialog.querySelectorAll('button'))
        .find(button => button.textContent === 'Áp dụng')!
        .click()
    )
    expect(state.setFilters).toHaveBeenCalledWith({
      brandId: undefined,
      gender: undefined,
      productType: undefined,
      minPrice: undefined,
      maxPrice: undefined
    })
  } finally {
    view.cleanup()
  }
})

it('offers restore instead of delete or archive for an archived product', async () => {
  const view = await renderList({
    pageNo: 0,
    pageSize: 20,
    totalPage: 1,
    totalElements: 1,
    items: [{ id: 'p1', name: 'Archived shirt', slug: 'archived-shirt', status: 'ARCHIVED', basePrice: 200 }]
  })

  try {
    const restore = view.container.querySelector<HTMLButtonElement>('[aria-label="Khôi phục Archived shirt"]')

    expect(restore).not.toBeNull()
    expect(restore!.disabled).toBe(false)
    expect(view.container.querySelector('[aria-label="Xóa Archived shirt"]')).toBeNull()
    expect(view.container.querySelector('[aria-label="Lưu trữ Archived shirt"]')).toBeNull()
  } finally {
    view.cleanup()
  }
})

it('restores archived products in bulk through the restore endpoint', async () => {
  const view = await renderList({
    pageNo: 0,
    pageSize: 20,
    totalPage: 1,
    totalElements: 1,
    items: [{ id: 'p1', name: 'Archived shirt', slug: 'archived-shirt', status: 'ARCHIVED', basePrice: 200 }]
  })

  try {
    const checkbox = view.container.querySelector<HTMLInputElement>('[aria-label="Chọn Archived shirt"]')

    await act(async () => checkbox!.click())

    const restore = Array.from(view.container.querySelectorAll('button')).find(
      button => button.textContent === 'Khôi phục'
    )

    expect(restore).toBeDefined()
    await act(async () => restore!.click())

    const confirm = Array.from(document.querySelectorAll<HTMLButtonElement>('[role="dialog"] button')).find(
      button => button.textContent === 'Xác nhận'
    )

    await act(async () => confirm!.click())
    await act(async () => {
      await new Promise(resolve => setTimeout(resolve, 70))
    })

    const calls = view.fetch.mock.calls.map(([url, init]) => [String(url), (init as RequestInit | undefined)?.method])

    expect(calls).toContainEqual(['/admin/products/p1/restore', 'POST'])
  } finally {
    view.cleanup()
  }
})

it('shows the read-only stock summary returned by the admin API', async () => {
  const view = await renderList({
    pageNo: 0,
    pageSize: 20,
    totalPage: 1,
    totalElements: 1,
    items: [
      {
        id: 'p1',
        name: 'Low stock shirt',
        slug: 'low-stock-shirt',
        status: 'PUBLISHED',
        basePrice: 200,
        stock: {
          totalQuantity: 15,
          totalReserved: 3,
          totalAvailable: 12,
          activeVariantCount: 4,
          outOfStockVariantCount: 0,
          lowStockVariantCount: 2,
          state: 'LOW_STOCK'
        }
      }
    ]
  })

  try {
    expect(view.container.textContent).toContain('12')
    expect(view.container.textContent).toContain('Sắp hết')
  } finally {
    view.cleanup()
  }
})

it('validates draft price bounds before applying and sends the complete valid draft once', async () => {
  state.filters = { minPrice: '500', maxPrice: '100', gender: 'MEN' }
  const view = await renderList({ pageNo: 0, pageSize: 20, totalPage: 0, totalElements: 0, items: [] })

  try {
    await act(async () =>
      Array.from(view.container.querySelectorAll('button'))
        .find(button => button.textContent?.includes('Bộ lọc nâng cao'))!
        .click()
    )
    const dialog = document.querySelector('[role="dialog"]')!

    await act(async () => dialog.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })))
    expect(state.setFilters).not.toHaveBeenCalled()
    expect(dialog.querySelector('[role="alert"]')).not.toBeNull()
    const maxInput = dialog.querySelectorAll('input[type="number"]')[1] as HTMLInputElement

    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(maxInput, '700')
      maxInput.dispatchEvent(new Event('input', { bubbles: true }))
    })
    await act(async () => dialog.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })))
    expect(state.setFilters).toHaveBeenCalledTimes(1)
    expect(state.setFilters).toHaveBeenCalledWith({
      brandId: undefined,
      gender: 'MEN',
      productType: undefined,
      minPrice: '500',
      maxPrice: '700'
    })
  } finally {
    view.cleanup()
  }
})
