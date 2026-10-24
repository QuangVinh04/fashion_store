import { act } from 'react'

import { createRoot } from 'react-dom/client'

// eslint-disable-next-line import/named
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, expect, it, vi } from 'vitest'

import ProductForm from './ProductForm'

const publishedProduct = {
  id: 'p1',
  name: 'Published shirt',
  slug: 'published-shirt',
  status: 'PUBLISHED',
  basePrice: 100,
  price: 100,
  categoryId: 'c1',
  categoryIds: ['c1'],
  categories: [{ id: 'c1', name: 'Shirts' }],
  images: [],
  attributes: [],
  stock: {
    totalQuantity: 15,
    totalReserved: 3,
    totalAvailable: 12,
    activeVariantCount: 1,
    outOfStockVariantCount: 0,
    lowStockVariantCount: 0,
    state: 'IN_STOCK'
  },
  variants: [
    {
      id: 'v1',
      colorOptionId: 'c1',
      sizeOptionId: 's1',
      sku: 'SKU-1',
      price: 100,
      active: true,
      stock: { quantity: 15, reservedQuantity: 3, availableQuantity: 12, minThreshold: 3 }
    }
  ]
}

const mocks = vi.hoisted(() => ({
  update: vi.fn(),
  publish: vi.fn()
}))

vi.mock('next/link', () => ({
  default: ({ children, href, ...props }: any) => (
    <a href={href} {...props}>
      {children}
    </a>
  )
}))

vi.mock('@/components/backoffice/MediaPicker', () => ({ default: () => null }))
vi.mock('@/components/backoffice/UnsavedChangesGuard', () => ({ default: () => null }))

vi.mock('@/libs/api/client', () => ({
  api: { get: vi.fn(async () => []) }
}))

vi.mock('@/hooks/useCatalog', () => ({
  useBrandsQuery: () => ({ data: [], error: null, isLoading: false, refetch: vi.fn() }),
  useCategoriesQuery: () => ({
    data: [{ id: 'c1', name: 'Shirts', children: [] }],
    error: null,
    isLoading: false,
    refetch: vi.fn()
  }),
  useColorsQuery: () => ({
    data: [{ id: 'c1', name: 'Red', active: true }],
    error: null,
    isLoading: false,
    refetch: vi.fn()
  }),
  useSizesQuery: () => ({
    data: [{ id: 's1', name: 'M', active: true }],
    error: null,
    isLoading: false,
    refetch: vi.fn()
  }),
  useProductDetailQuery: () => ({ data: publishedProduct, error: null, isLoading: false, refetch: vi.fn() }),
  useCreateProductMutation: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdateProductMutation: () => ({ mutateAsync: mocks.update, isPending: false }),
  usePublishProductMutation: () => ({ mutateAsync: mocks.publish, isPending: false })
}))

afterEach(() => {
  mocks.update.mockReset()
  mocks.publish.mockReset()
})

async function renderPublishedProduct() {
  ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
  const container = document.createElement('div')
  const root = createRoot(container)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  document.body.append(container)
  await act(async () =>
    root.render(
      <QueryClientProvider client={client}>
        <ProductForm productId='p1' />
      </QueryClientProvider>
    )
  )
  await act(async () => {
    await new Promise(resolve => setTimeout(resolve, 70))
  })

  return {
    container,
    cleanup: () => {
      act(() => root.unmount())
      client.clear()
      container.remove()
    }
  }
}

it('does not offer save-and-publish for an already published product', async () => {
  const view = await renderPublishedProduct()

  try {
    const publishButton = Array.from(view.container.querySelectorAll('button')).find(
      button => button.textContent === 'Lưu và xuất bản'
    )

    expect(publishButton).toBeUndefined()
  } finally {
    view.cleanup()
  }
})

it('shows a read-only stock summary for an existing product', async () => {
  const view = await renderPublishedProduct()

  try {
    expect(view.container.textContent).toContain('Có thể bán')
    expect(view.container.textContent).toContain('12')
    expect(view.container.querySelector('input[name="stock"]')).toBeNull()
  } finally {
    view.cleanup()
  }
})

it('keeps the slug read-only while the product is published', async () => {
  const view = await renderPublishedProduct()

  try {
    const slug = view.container.querySelector<HTMLInputElement>('input[name="slug"]')

    expect(slug).not.toBeNull()
    expect(slug!.disabled).toBe(true)
  } finally {
    view.cleanup()
  }
})
