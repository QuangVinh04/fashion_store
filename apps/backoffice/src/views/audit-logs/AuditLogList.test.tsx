import React, { act } from 'react'

import { createRoot, type Root } from 'react-dom/client'
// eslint-disable-next-line import/named
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/libs/api/client'
import { auditLogsApi } from '@/libs/api/audit-logs'
import AuditLogList from './AuditLogList'

let currentSearch = ''
const searchListeners = new Set<() => void>()

const mockRouter = {
  push: vi.fn((url: string) => {
    currentSearch = url.includes('?') ? url.split('?')[1] : ''
    searchListeners.forEach(listener => listener())
  })
}

vi.mock('next/navigation', () => ({
  useRouter: () => mockRouter,
  usePathname: () => '/audit-logs',
  useSearchParams: () => {
    const [, setTick] = React.useState(0)

    React.useEffect(() => {
      const listener = () => setTick(n => n + 1)

      searchListeners.add(listener)

      return () => {
        searchListeners.delete(listener)
      }
    }, [])

    return new URLSearchParams(currentSearch)
  }
}))

vi.mock('@/libs/api/audit-logs', () => ({
  auditLogsApi: {
    list: vi.fn()
  }
}))

let container: HTMLDivElement
let root: Root
let queryClient: QueryClient

function changeInput(input: HTMLInputElement, value: string) {
  const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set

  setter?.call(input, value)
  input.dispatchEvent(new Event('input', { bubbles: true }))
  input.dispatchEvent(new Event('change', { bubbles: true }))
}

async function waitForCondition(check: () => void | boolean, maxMs = 2000): Promise<void> {
  const start = Date.now()

  while (Date.now() - start < maxMs) {
    try {
      if (check() !== false) return
    } catch {
      // Tiếp tục chờ đến khi điều kiện thoả mãn
    }

    await act(async () => {
      await new Promise(resolve => setTimeout(resolve, 10))
    })
  }

  check()
}

function renderComponent() {
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <AuditLogList />
      </QueryClientProvider>
    )
  })
}

beforeEach(() => {
  ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
  currentSearch = ''
  searchListeners.clear()
  mockRouter.push.mockClear()

  queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: Infinity
      }
    }
  })

  container = document.createElement('div')
  document.body.append(container)
  root = createRoot(container)
})

afterEach(() => {
  act(() => root.unmount())
  container.remove()
  vi.restoreAllMocks()
})

describe('AuditLogList', () => {
  it('hiển thị danh sách nhật ký với các cột và định dạng thời gian chính xác', async () => {
    vi.mocked(auditLogsApi.list).mockResolvedValue({
      pageNo: 0,
      pageSize: 20,
      totalPage: 1,
      totalElements: 1,
      items: [
        {
          id: 'log-1',
          sourceService: 'identity-service',
          actorId: '00000000-0000-0000-0000-000000000001',
          action: 'USER_LOCK',
          entityType: 'USER',
          entityId: '00000000-0000-0000-0000-000000000002',
          summary: 'Khóa tài khoản vi phạm chính sách',
          occurredAt: '2026-11-07T14:30:00'
        }
      ]
    })

    renderComponent()

    await waitForCondition(() => {
      expect(container.textContent).toContain('07/11/2026 14:30:00')
      expect(container.textContent).toContain('identity-service')
      expect(container.textContent).toContain('USER_LOCK')
      expect(container.textContent).toContain('USER')
      expect(container.textContent).toContain('Khóa tài khoản vi phạm chính sách')
      expect(container.textContent).toContain('00000000...')
    })
  })

  it('bộ lọc cập nhật URL query qua useListQueryState và gửi đúng tham số API', async () => {
    vi.mocked(auditLogsApi.list).mockResolvedValue({
      pageNo: 0,
      pageSize: 20,
      totalPage: 1,
      totalElements: 0,
      items: []
    })

    renderComponent()

    await waitForCondition(() => {
      expect(container.textContent).toContain('Chưa có thao tác nào')
    })

    const inputs = container.querySelectorAll<HTMLInputElement>('input')

    expect(inputs.length).toBeGreaterThanOrEqual(4)

    act(() => {
      changeInput(inputs[0], 'actor-test')
      changeInput(inputs[1], 'ORDER')
      changeInput(inputs[2], 'ord-123')
      changeInput(inputs[3], 'RETURN_REFUND')
    })

    const filterButton = Array.from(container.querySelectorAll('button')).find(
      btn => btn.textContent?.includes('Lọc') && !btn.textContent.includes('Xoá')
    )

    expect(filterButton).toBeDefined()

    await act(async () => {
      filterButton?.click()
    })

    await waitForCondition(() => {
      expect(mockRouter.push).toHaveBeenCalledWith(expect.stringContaining('actorId=actor-test'), expect.anything())
      expect(auditLogsApi.list).toHaveBeenCalledWith(
        expect.objectContaining({
          page: 0,
          size: 20,
          actorId: 'actor-test',
          entityType: 'ORDER',
          entityId: 'ord-123',
          action: 'RETURN_REFUND'
        })
      )
    })

    const resetButton = Array.from(container.querySelectorAll('button')).find(btn =>
      btn.textContent?.includes('Xoá lọc')
    )

    await act(async () => {
      resetButton?.click()
    })

    await waitForCondition(() => {
      expect(auditLogsApi.list).toHaveBeenLastCalledWith(
        expect.objectContaining({
          page: 0,
          size: 20,
          actorId: undefined,
          entityType: undefined,
          entityId: undefined,
          action: undefined
        })
      )
    })
  })

  it('hiển thị thông báo rỗng khi chưa lọc và khi đang lọc', async () => {
    vi.mocked(auditLogsApi.list).mockResolvedValue({
      pageNo: 0,
      pageSize: 20,
      totalPage: 0,
      totalElements: 0,
      items: []
    })

    renderComponent()

    await waitForCondition(() => {
      expect(container.textContent).toContain('Chưa có thao tác nào')
    })

    const inputs = container.querySelectorAll<HTMLInputElement>('input')

    act(() => {
      changeInput(inputs[0], 'some-actor')
    })

    const filterButton = Array.from(container.querySelectorAll('button')).find(
      btn => btn.textContent?.includes('Lọc') && !btn.textContent.includes('Xoá')
    )

    await act(async () => {
      filterButton?.click()
    })

    await waitForCondition(() => {
      expect(container.textContent).toContain('Không có kết quả phù hợp')
    })
  })

  it('hiển thị thông báo 403 khi người dùng không có quyền xem nhật ký', async () => {
    vi.mocked(auditLogsApi.list).mockRejectedValue(new ApiError(403, 2007, 'Forbidden'))

    renderComponent()

    await waitForCondition(() => {
      expect(container.textContent).toContain('Bạn không có quyền xem nhật ký')
      expect(container.textContent).toContain('Thử lại')
    })
  })
})
