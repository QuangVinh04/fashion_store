import { act } from 'react'

import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useDebouncedSearch } from './useDebouncedSearch'

function TestHarness({
  externalValue,
  onCommit,
  delay = 300,
  onHookReady
}: {
  externalValue: string
  onCommit: (val: string) => void
  delay?: number
  onHookReady?: (hook: ReturnType<typeof useDebouncedSearch>) => void
}) {
  const hook = useDebouncedSearch(externalValue, onCommit, delay)

  onHookReady?.(hook)

  return (
    <div>
      <input
        data-testid='input'
        value={hook.value}
        onChange={e => hook.onChange(e.target.value)}
      />
      <button data-testid='clear' onClick={hook.onClear}>
        Clear
      </button>
      <span data-testid='val'>{hook.value}</span>
    </div>
  )
}

describe('useDebouncedSearch', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('gõ liên tiếp 5 ký tự chỉ phát sinh một lần cập nhật filter sau 300ms', () => {
    ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
    const onCommit = vi.fn()
    let currentHook: ReturnType<typeof useDebouncedSearch> | undefined
    const container = document.createElement('div')
    const root = createRoot(container)

    act(() => {
      root.render(
        <TestHarness
          externalValue=''
          onCommit={onCommit}
          delay={300}
          onHookReady={hook => {
            currentHook = hook
          }}
        />
      )
    })

    expect(currentHook?.value).toBe('')

    // Gõ liên tiếp 5 ký tự
    act(() => {
      currentHook?.onChange('a')
      currentHook?.onChange('áo')
      currentHook?.onChange('áo ')
      currentHook?.onChange('áo t')
      currentHook?.onChange('áo thun')
    })

    // Giá trị hiển thị cập nhật ngay lập tức
    expect(currentHook?.value).toBe('áo thun')

    // Nhưng onCommit chưa được gọi
    expect(onCommit).not.toHaveBeenCalled()

    // Tiến hành chờ 299ms - vẫn chưa gọi
    act(() => {
      vi.advanceTimersByTime(299)
    })
    expect(onCommit).not.toHaveBeenCalled()

    // Đạt 300ms - chỉ gọi đúng 1 lần duy nhất với giá trị cuối
    act(() => {
      vi.advanceTimersByTime(1)
    })
    expect(onCommit).toHaveBeenCalledTimes(1)
    expect(onCommit).toHaveBeenCalledWith('áo thun')

    act(() => {
      root.unmount()
      container.remove()
    })
  })

  it('nút xóa áp dụng ngay lập tức không cần đợi 300ms', () => {
    ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
    const onCommit = vi.fn()
    let currentHook: ReturnType<typeof useDebouncedSearch> | undefined
    const container = document.createElement('div')
    const root = createRoot(container)

    act(() => {
      root.render(
        <TestHarness
          externalValue='cũ'
          onCommit={onCommit}
          delay={300}
          onHookReady={hook => {
            currentHook = hook
          }}
        />
      )
    })

    expect(currentHook?.value).toBe('cũ')

    // Bắt đầu gõ
    act(() => {
      currentHook?.onChange('đang gõ dở')
    })
    expect(currentHook?.value).toBe('đang gõ dở')
    expect(onCommit).not.toHaveBeenCalled()

    // Bấm xóa (clear)
    act(() => {
      currentHook?.onClear()
    })

    // Áp dụng ngay lập tức
    expect(currentHook?.value).toBe('')
    expect(onCommit).toHaveBeenCalledTimes(1)
    expect(onCommit).toHaveBeenCalledWith('')

    // Tua quá 300ms thì không phát sinh thêm lệnh gọi nào với giá trị cũ
    act(() => {
      vi.advanceTimersByTime(300)
    })
    expect(onCommit).toHaveBeenCalledTimes(1)

    act(() => {
      root.unmount()
      container.remove()
    })
  })

  it('đồng bộ giá trị khi URL thay đổi từ bên ngoài (Back / Forward)', () => {
    ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
    const onCommit = vi.fn()
    let currentHook: ReturnType<typeof useDebouncedSearch> | undefined
    const container = document.createElement('div')
    const root = createRoot(container)

    act(() => {
      root.render(
        <TestHarness
          externalValue='áo khoác'
          onCommit={onCommit}
          delay={300}
          onHookReady={hook => {
            currentHook = hook
          }}
        />
      )
    })
    expect(currentHook?.value).toBe('áo khoác')

    // Giả lập Back/Forward trình duyệt đổi URL query param
    act(() => {
      root.render(
        <TestHarness
          externalValue='quần jeans'
          onCommit={onCommit}
          delay={300}
          onHookReady={hook => {
            currentHook = hook
          }}
        />
      )
    })
    expect(currentHook?.value).toBe('quần jeans')

    act(() => {
      root.unmount()
      container.remove()
    })
  })
})
