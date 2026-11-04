import React, { act } from 'react'

// eslint-disable-next-line import/named
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import AddGroupMembersDialog from './AddGroupMembersDialog'

const mocks = vi.hoisted(() => ({
  candidates: vi.fn(),
  addMembers: vi.fn(),
  onSuccess: vi.fn(),
  onClose: vi.fn()
}))

vi.mock('@/libs/api/groups', () => ({
  groupsApi: {
    candidates: (...args: any[]) => mocks.candidates(...args),
    addMembers: (...args: any[]) => mocks.addMembers(...args)
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

function renderDialog(props?: Partial<React.ComponentProps<typeof AddGroupMembersDialog>>) {
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <AddGroupMembersDialog
          open={true}
          groupId='g-staff'
          groupName='Nhân viên'
          onClose={mocks.onClose}
          onSuccess={mocks.onSuccess}
          {...props}
        />
      </QueryClientProvider>
    )
  })
}

beforeEach(() => {
  ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
  vi.useFakeTimers()
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
  queryClient.clear()
  vi.clearAllTimers()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('AddGroupMembersDialog', () => {
  it('does not search when keyword is shorter than 2 characters', async () => {
    renderDialog()

    const searchInput = document.body.querySelector('input') as HTMLInputElement

    act(() => {
      changeInput(searchInput, 'a')
    })

    await act(async () => {
      await vi.advanceTimersByTimeAsync(350)
    })

    await act(async () => {
      await vi.advanceTimersByTimeAsync(50)
    })

    expect(mocks.candidates).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('Nhập tối thiểu 2 ký tự')
  })

  it('searches candidates on keyword >= 2, preserves selection across keyword changes, and submits', async () => {
    mocks.candidates.mockResolvedValueOnce([
      { userId: 'u1', email: 'u1@example.com', fullName: 'User One', isActive: true },
      { userId: 'u2', email: 'u2@example.com', fullName: 'User Two', isActive: true }
    ])
    mocks.addMembers.mockResolvedValue({
      succeededUserIds: ['u1', 'u3'],
      failures: [],
      notLoggedOutUserIds: []
    })

    renderDialog()

    const searchInput = document.body.querySelector('input') as HTMLInputElement

    act(() => {
      changeInput(searchInput, 'user')
    })

    await act(async () => {
      await vi.advanceTimersByTimeAsync(350)
    })

    await act(async () => {
      await vi.advanceTimersByTimeAsync(50)
    })

    expect(mocks.candidates).toHaveBeenCalledWith('g-staff', 'user')
    expect(document.body.textContent).toContain('User One')
    expect(document.body.textContent).toContain('User Two')

    // Tick chọn u1
    const checkboxes = document.body.querySelectorAll(
      'tbody input[type="checkbox"]'
    ) as NodeListOf<HTMLInputElement>

    act(() => {
      checkboxes[0].click()
    })

    expect(document.body.textContent).toContain('Đã chọn 1 người dùng')

    // Đổi từ khóa sang 'other'
    mocks.candidates.mockResolvedValueOnce([
      { userId: 'u3', email: 'u3@example.com', fullName: 'User Three', isActive: true }
    ])

    act(() => {
      changeInput(searchInput, 'other')
    })

    await act(async () => {
      await vi.advanceTimersByTimeAsync(350)
    })

    await act(async () => {
      await vi.advanceTimersByTimeAsync(50)
    })

    expect(mocks.candidates).toHaveBeenCalledWith('g-staff', 'other')
    expect(document.body.textContent).toContain('User Three')

    // u1 vẫn được giữ trong selection!
    expect(document.body.textContent).toContain('Đã chọn 1 người dùng')

    // Tick chọn tiếp u3
    const newCheckboxes = document.body.querySelectorAll(
      'tbody input[type="checkbox"]'
    ) as NodeListOf<HTMLInputElement>

    act(() => {
      newCheckboxes[0].click()
    })

    expect(document.body.textContent).toContain('Đã chọn 2 người dùng')

    const submitBtn = Array.from(document.body.querySelectorAll('button')).find(b =>
      b.textContent?.includes('Thêm (2 người)')
    )

    expect(submitBtn).toBeTruthy()

    await act(async () => {
      submitBtn?.click()
    })

    expect(mocks.addMembers).toHaveBeenCalledWith('g-staff', ['u1', 'u3'])
    expect(mocks.onSuccess).toHaveBeenCalled()
  })

  it('keeps the latest search results when earlier response resolves later', async () => {
    let resolveFirst!: (value: any) => void
    let resolveSecond!: (value: any) => void

    const firstPromise = new Promise(resolve => {
      resolveFirst = resolve
    })

    const secondPromise = new Promise(resolve => {
      resolveSecond = resolve
    })

    mocks.candidates.mockImplementation((_groupId: string, keyword: string) => {
      if (keyword === 'alpha') return firstPromise
      if (keyword === 'beta') return secondPromise

      return Promise.resolve([])
    })

    renderDialog()

    const searchInput = document.body.querySelector('input') as HTMLInputElement

    // Gõ từ khóa đầu tiên: 'alpha'
    act(() => {
      changeInput(searchInput, 'alpha')
    })

    await act(async () => {
      await vi.advanceTimersByTimeAsync(350)
    })

    await act(async () => {
      await vi.advanceTimersByTimeAsync(50)
    })

    expect(mocks.candidates).toHaveBeenCalledWith('g-staff', 'alpha')

    // Gõ từ khóa thứ hai: 'beta' trước khi 'alpha' trả về kết quả
    act(() => {
      changeInput(searchInput, 'beta')
    })

    await act(async () => {
      await vi.advanceTimersByTimeAsync(350)
    })

    await act(async () => {
      await vi.advanceTimersByTimeAsync(50)
    })

    expect(mocks.candidates).toHaveBeenCalledWith('g-staff', 'beta')

    // Response của từ khóa sau ('beta') về trước
    await act(async () => {
      resolveSecond([
        { userId: 'u-beta', email: 'beta@example.com', fullName: 'User Beta', isActive: true }
      ])
      await vi.advanceTimersByTimeAsync(50)
    })

    expect(document.body.textContent).toContain('User Beta')
    expect(document.body.textContent).not.toContain('User Alpha')

    // Response của từ khóa đầu ('alpha') về sau
    await act(async () => {
      resolveFirst([
        { userId: 'u-alpha', email: 'alpha@example.com', fullName: 'User Alpha', isActive: true }
      ])
      await vi.advanceTimersByTimeAsync(50)
    })

    // Danh sách vẫn phải hiển thị kết quả của từ khóa sau ('User Beta'), không bị 'User Alpha' đè
    expect(document.body.textContent).toContain('User Beta')
    expect(document.body.textContent).not.toContain('User Alpha')
  })
})
