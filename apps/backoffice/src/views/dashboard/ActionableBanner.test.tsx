import { act } from 'react'
import { createRoot } from 'react-dom/client'
import { describe, expect, it } from 'vitest'
import React from 'react'

import ActionableBanner from './ActionableBanner'

describe('ActionableBanner', () => {
  it('ẩn toàn bộ banner khi không có việc nào cần xử lý và không đang tải', () => {
    const container = document.createElement('div')
    const root = createRoot(container)

    act(() => {
      root.render(
        <ActionableBanner
          orderByStatus={[
            { status: 'DELIVERED', count: 10 },
            { status: 'CANCELLED', count: 2 }
          ]}
          returnRequestsCount={0}
          isLoading={false}
        />
      )
    })

    expect(container.innerHTML).toBe('')
  })

  it('chỉ hiển thị các card con có số lượng > 0 và ẩn card có số lượng = 0', () => {
    const container = document.createElement('div')
    const root = createRoot(container)

    act(() => {
      root.render(
        <ActionableBanner
          orderByStatus={[
            { status: 'CONFIRMED', count: 5 },
            { status: 'PROCESSING', count: 0 },
            { status: 'PACKED', count: 0 }
          ]}
          returnRequestsCount={2}
          isLoading={false}
        />
      )
    })

    // Khối banner xuất hiện
    expect(container.textContent).toContain('Việc cần xử lý')
    expect(container.textContent).toContain('7 việc cần làm')

    // 2 Card có số lượng > 0 xuất hiện
    expect(container.textContent).toContain('5 Đơn tiếp nhận')
    expect(container.textContent).toContain('2 Đổi trả hàng')

    // 2 Card có số lượng = 0 không xuất hiện
    expect(container.textContent).not.toContain('Đơn đóng gói')
    expect(container.textContent).not.toContain('Đơn xuất kho')
  })
})
