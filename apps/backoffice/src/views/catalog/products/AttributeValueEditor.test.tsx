import { act } from 'react'

import { createRoot } from 'react-dom/client'
import { Simulate } from 'react-dom/test-utils'

// eslint-disable-next-line import/named
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { expect, it, vi } from 'vitest'

import AttributeValueEditor from './AttributeValueEditor'

it('keeps saved hidden options visible and loads selectable values from the option API', async () => {
  ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true

  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(
      JSON.stringify({
        data: [
          { id: 'o1', value: 'Cotton', published: true },
          { id: 'o2', value: 'Hidden', published: false }
        ]
      })
    )
  )

  const container = document.createElement('div')
  const root = createRoot(container)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  document.body.append(container)

  try {
    await act(async () =>
      root.render(
        <QueryClientProvider client={client}>
          <AttributeValueEditor
            attribute={{ id: 'a1', name: 'Material', type: 'MULTI_SELECT', published: true }}
            label='Material'
            values={['Archived']}
            onChange={vi.fn()}
          />
        </QueryClientProvider>
      )
    )
    await act(async () => {
      await new Promise(resolve => setTimeout(resolve, 70))
    })
    expect(fetch.mock.calls[0][0]).toBe('/admin/product-attributes/a1/options')
    expect(container.textContent).toContain('Archived')
    expect(container.querySelector('input')?.disabled).toBe(false)
  } finally {
    act(() => root.unmount())
    client.clear()
    container.remove()
    fetch.mockRestore()
  }
})

it('preserves text without requesting irrelevant option data', async () => {
  ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
  const fetch = vi.spyOn(globalThis, 'fetch')
  const container = document.createElement('div')
  const root = createRoot(container)
  const client = new QueryClient()

  try {
    await act(async () =>
      root.render(
        <QueryClientProvider client={client}>
          <AttributeValueEditor
            attribute={{ id: 'a1', name: 'Material', type: 'TEXT', published: true }}
            label='Material'
            values={['Cotton 100%']}
            onChange={vi.fn()}
          />
        </QueryClientProvider>
      )
    )
    expect(container.querySelector('input')?.value).toBe('Cotton 100%')
    expect(fetch).not.toHaveBeenCalled()
  } finally {
    act(() => root.unmount())
    client.clear()
    fetch.mockRestore()
  }
})

it('keeps every value of a saved hidden attribute when one is edited', async () => {
  ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
  const container = document.createElement('div')
  const root = createRoot(container)
  const client = new QueryClient()
  const change = vi.fn()

  try {
    await act(async () => root.render(<QueryClientProvider client={client}><AttributeValueEditor label='Saved material' values={['Cotton', 'Linen']} onChange={change} /></QueryClientProvider>))
    const inputs = container.querySelectorAll('input')

    expect(Array.from(inputs).map(input => input.value)).toEqual(['Cotton', 'Linen'])
    act(() => Simulate.change(inputs[0], { target: { value: 'Wool' } } as any))
    expect(change).toHaveBeenCalledWith(['Wool', 'Linen'])
  } finally { act(() => root.unmount()); client.clear() }
})
