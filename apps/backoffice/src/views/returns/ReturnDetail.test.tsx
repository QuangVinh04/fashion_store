import { act } from 'react'

import { createRoot } from 'react-dom/client'
// eslint-disable-next-line import/named
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, it, expect, vi } from 'vitest'

import ReturnDetail from './ReturnDetail'
import type { ReturnCase } from '@/types/returns'

afterEach(() => {
  sessionStorage.clear()
  vi.restoreAllMocks()
})

const row: ReturnCase = {
  id: 'case',
  orderId: 'order',
  userId: 'buyer',
  orderCode: 'ORD-1',
  reason: 'Goods returned',
  kind: 'CUSTOMER_RETURN',
  status: 'RECEIVED',
  qcPassed: true,
  images: [],
  qcEvidenceIds: [],
  createdAt: '2026-10-14T01:00:00Z'
}

async function mount(options: { caseData?: ReturnCase; method?: string; writeStatus?: number } = {}) {
  ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
  const container = document.createElement('div')

  document.body.append(container)
  const root = createRoot(container)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  let current = options.caseData || row
  const writes: { url: string; body: unknown }[] = []
  let reads = 0

  vi.spyOn(globalThis, 'fetch').mockImplementation(async (path, init) => {
    const url = String(path)

    if (init?.method === 'POST') {
      writes.push({ url, body: init.body ? JSON.parse(String(init.body)) : undefined })
      if (!options.writeStatus) throw new TypeError('network unavailable')

      if (options.writeStatus === 202) {
        current = { ...current, refundStartedAt: '2026-10-14T02:00:00Z', approvedRefundAmount: '80000' }

        return new Response(JSON.stringify({ data: current }), { status: 202 })
      }

      return new Response(JSON.stringify({ code: 4025, message: 'State changed' }), { status: options.writeStatus })
    }

    if (url === '/api/v1/admin/orders/returns/case') {
      reads++

      return new Response(JSON.stringify({ data: current }))
    }

    if (url.endsWith('/history')) return new Response(JSON.stringify({ data: [] }))
    if (url.includes('/refunds'))
      return new Response(JSON.stringify({ data: { content: [], totalElements: 0, totalPages: 0, number: 0 } }))
    if (url.includes('/api/v1/admin/payments/order/'))
      return new Response(
        JSON.stringify({
          data: {
            id: 'pay',
            orderId: 'order',
            userId: 'buyer',
            method: options.method || 'WALLET',
            provider: 'INTERNAL',
            status: options.method === 'COD' ? 'COD_PENDING' : 'COMPLETED',
            amount: '80000',
            currency: 'VND'
          }
        })
      )

    return new Response(JSON.stringify({ message: 'Unexpected endpoint' }), { status: 404 })
  })
  await act(async () =>
    root.render(
      <QueryClientProvider client={client}>
        <ReturnDetail id='case' />
      </QueryClientProvider>
    )
  )
  for (let attempt = 0; attempt < 20 && !container.textContent?.includes('Duyệt hoàn vào ví'); attempt++)
    await act(async () => {
      await new Promise(resolve => setTimeout(resolve, 15))
    })

  return {
    container,
    writes,
    get reads() {
      return reads
    },
    cleanup: () => {
      act(() => root.unmount())
      client.clear()
      container.remove()
    }
  }
}

async function confirmRefund(page: Awaited<ReturnType<typeof mount>>) {
  const open = Array.from(page.container.querySelectorAll('button')).find(
    button => button.textContent === 'Duyệt hoàn vào ví'
  )!

  expect(open.disabled).toBe(false)
  await act(async () => open.click())

  const confirm = Array.from(document.querySelectorAll('.MuiDialog-root button')).find(
    button => button.textContent === 'Xác nhận'
  ) as HTMLButtonElement

  await act(async () => confirm.click())
  await act(async () => {
    await new Promise(resolve => setTimeout(resolve, 30))
  })
}

it('uses actual backend DTO without actions and blocks unpaid COD', async () => {
  const page = await mount({ method: 'COD' })

  try {
    const button = Array.from(page.container.querySelectorAll('button')).find(
      button => button.textContent === 'Duyệt hoàn vào ví'
    )

    expect(button?.disabled).toBe(true)
    expect(page.writes).toHaveLength(0)
  } finally {
    page.cleanup()
  }
})
it('202 shows pending credit, locks further refund writes and uses canonical endpoint', async () => {
  const page = await mount({ writeStatus: 202 })

  try {
    await confirmRefund(page)
    expect(page.writes).toEqual([{ url: '/api/v1/admin/orders/returns/case/refund', body: {} }])
    expect(page.container.textContent).toContain('Đang hoàn vào ví')
    expect(page.container.textContent).not.toContain('Đã hoàn ví')
    expect(
      Array.from(page.container.querySelectorAll('button')).some(button => button.textContent === 'Duyệt hoàn vào ví')
    ).toBe(false)
  } finally {
    page.cleanup()
  }
})
it('unknown money result survives reload without automatic write replay', async () => {
  const page = await mount()
  let reloaded: Awaited<ReturnType<typeof mount>> | undefined

  try {
    await confirmRefund(page)
    expect(page.writes).toHaveLength(1)
    const stored = sessionStorage.getItem('backoffice:return-operation:case')

    expect(JSON.parse(stored!).action).toBe('refund')
    page.cleanup()
    reloaded = await mount()
    expect(reloaded.writes).toHaveLength(0)
    expect(sessionStorage.getItem('backoffice:return-operation:case')).toBe(stored)
    expect(reloaded.container.textContent).toContain('chưa xác định kết quả')
  } finally {
    if (reloaded) reloaded.cleanup()
    else page.cleanup()
  }
})
it('confirmed receipt shows ledger deep link and no money mutation', async () => {
  const page = await mount({
    caseData: {
      ...row,
      status: 'REFUNDED',
      closedAt: '2026-10-14T03:00:00Z',
      refundLedgerEntryId: 'ledger',
      approvedRefundAmount: '80000'
    }
  })

  try {
    expect(page.container.querySelector('a[href="/wallets/buyer?ledgerId=ledger"]')).not.toBeNull()
    expect(page.writes).toHaveLength(0)
  } finally {
    page.cleanup()
  }
})
