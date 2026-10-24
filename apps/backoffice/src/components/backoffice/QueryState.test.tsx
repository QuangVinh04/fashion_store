import React, { act } from 'react'

import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import QueryState from './QueryState'
import ConfirmActionDialog from './ConfirmActionDialog'
import UnsavedChangesGuard from './UnsavedChangesGuard'

let container: HTMLDivElement
let root: Root

beforeEach(() => {
  ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
  container = document.createElement('div')
  document.body.append(container)
  root = createRoot(container)
})

afterEach(() => {
  act(() => root.unmount())
  container.remove()
  vi.restoreAllMocks()
})

describe('query states', () => {
  it('offers retry for an error and hides stale content', () => {
    const retry = vi.fn()

    act(() =>
      root.render(
        <QueryState error={new Error('failure')} onRetry={retry}>
          <p>stale row</p>
        </QueryState>
      )
    )
    expect(container.textContent).not.toContain('stale row')
    const button = container.querySelector('button')!

    expect(button.textContent).toContain('Thử lại')
    act(() => button.click())
    expect(retry).toHaveBeenCalledOnce()
  })

  it('distinguishes loading, empty and successful content', () => {
    act(() =>
      root.render(
        <QueryState loading>
          <p>data</p>
        </QueryState>
      )
    )
    expect(container.querySelector('[role="status"]')).not.toBeNull()
    expect(container.textContent).not.toContain('data')
    act(() =>
      root.render(
        <QueryState empty>
          <p>data</p>
        </QueryState>
      )
    )
    expect(container.textContent).toContain('Chưa có dữ liệu')
    expect(container.textContent).not.toContain('data')
    act(() =>
      root.render(
        <QueryState>
          <p>data</p>
        </QueryState>
      )
    )
    expect(container.textContent).toContain('data')
  })
})

describe('safe user actions', () => {
  it('disables confirmation and dismissal while a mutation is pending', () => {
    const confirm = vi.fn()
    const close = vi.fn()

    act(() =>
      root.render(
        <ConfirmActionDialog
          open
          title='Xóa sản phẩm'
          message='Sản phẩm Áo sơ mi sẽ bị xóa.'
          pending
          onConfirm={confirm}
          onClose={close}
        />
      )
    )
    const buttons = Array.from(document.querySelectorAll<HTMLButtonElement>('[role="dialog"] button'))

    expect(buttons.length).toBe(2)
    expect(buttons.every(button => button.disabled)).toBe(true)
    act(() => buttons.forEach(button => button.click()))
    expect(confirm).not.toHaveBeenCalled()
    expect(close).not.toHaveBeenCalled()
  })

  it('warns on unload and blocks a same-tab navigation when the user cancels', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    act(() => root.render(<UnsavedChangesGuard dirty />))
    const unload = new Event('beforeunload', { cancelable: true })

    window.dispatchEvent(unload)
    expect(unload.defaultPrevented).toBe(true)
    const link = document.createElement('a')

    link.href = '/products'
    document.body.append(link)
    const click = new MouseEvent('click', { bubbles: true, cancelable: true })

    link.dispatchEvent(click)
    expect(click.defaultPrevented).toBe(true)
    expect(window.confirm).toHaveBeenCalledOnce()
    link.remove()
    act(() => root.render(<UnsavedChangesGuard dirty={false} />))
    const clean = new Event('beforeunload', { cancelable: true })

    window.dispatchEvent(clean)
    expect(clean.defaultPrevented).toBe(false)
  })
})
