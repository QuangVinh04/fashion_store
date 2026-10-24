import { act, useState } from 'react'

import { createRoot } from 'react-dom/client'

// eslint-disable-next-line import/named
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { expect, it, vi } from 'vitest'

import type { ProductVariant } from '@/types/catalog'
import VariantMatrix from './VariantMatrix'

it('shows read-only stock and links an existing variant to the inventory page', async () => {
  ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
  const container = document.createElement('div')
  const root = createRoot(container)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  function Harness() {
    const [rows] = useState<ProductVariant[]>([
      {
        id: 'v1',
        colorOptionId: 'c1',
        sizeOptionId: 's1',
        sku: 'A',
        price: 100,
        stock: { quantity: 10, reservedQuantity: 2, availableQuantity: 8, minThreshold: 3 }
      }
    ])

    return (
      <QueryClientProvider client={client}>
        <VariantMatrix
          colors={[{ id: 'c1', name: 'Đỏ', active: true }]}
          sizes={[{ id: 's1', name: 'S', active: true }]}
          variants={rows}
          prefix='SHIRT'
          price={100}
          errors={{}}
          onChange={vi.fn()}
        />
      </QueryClientProvider>
    )
  }

  document.body.append(container)

  try {
    await act(async () => root.render(<Harness />))
    expect(container.textContent).toContain('8 có thể bán')
    expect(container.textContent).toContain('Tồn 10')
    expect(container.querySelector('a[href="/inventory/v1"]')).not.toBeNull()
  } finally {
    act(() => root.unmount())
    client.clear()
    container.remove()
  }
})

it('selects a library thumbnail for one variant and preserves it when regenerating', async () => {
  ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
  const container = document.createElement('div')
  const root = createRoot(container)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })

  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(
      JSON.stringify({
        data: {
          pageNo: 0,
          pageSize: 20,
          totalPage: 1,
          items: [
            {
              id: 'm1',
              originalFilename: 'thumb.png',
              displayName: 'Ảnh áo',
              url: 'https://example.test/thumb.png',
              mediaType: 'IMAGE',
              status: 'ACTIVE'
            }
          ]
        }
      })
    )
  )

  let current: ProductVariant[] = []

  function Harness() {
    const [rows, setRows] = useState<ProductVariant[]>([
      { colorOptionId: 'c1', sizeOptionId: 's1', sku: 'A', price: 100, initialQuantity: 0 },
      { colorOptionId: 'c1', sizeOptionId: 's2', sku: 'B', price: 100, initialQuantity: 0 }
    ])

    current = rows

    return (
      <QueryClientProvider client={client}>
        <VariantMatrix
          colors={[{ id: 'c1', name: 'Đỏ', active: true }]}
          sizes={[
            { id: 's1', name: 'S', active: true },
            { id: 's2', name: 'M', active: true }
          ]}
          variants={rows}
          prefix='SHIRT'
          price={100}
          errors={{}}
          onChange={setRows}
        />
      </QueryClientProvider>
    )
  }

  document.body.append(container)

  try {
    await act(async () => root.render(<Harness />))
    await act(async () => container.querySelector<HTMLButtonElement>('[aria-label="Chọn thumbnail Đỏ / S"]')!.click())
    await act(async () => {
      await new Promise(resolve => setTimeout(resolve, 40))
    })

    const choose = Array.from(document.querySelectorAll<HTMLButtonElement>('[role="dialog"] button')).find(
      button => button.textContent === 'Ảnh áo'
    )!

    expect(choose).toBeTruthy()
    await act(async () => choose.click())
    expect(current[0].thumbnailMediaId).toBe('m1')
    expect(current[1].thumbnailMediaId).toBeUndefined()

    const generate = Array.from(container.querySelectorAll('button')).find(
      button => button.textContent === 'Sinh ma trận'
    )!

    await act(async () => generate.click())
    expect(current[0]).toMatchObject({ sku: 'A', thumbnailMediaId: 'm1' })
    expect(current).toHaveLength(2)
    expect(fetch).toHaveBeenCalledOnce()
  } finally {
    act(() => root.unmount())
    client.clear()
    container.remove()
    fetch.mockRestore()
  }
})
