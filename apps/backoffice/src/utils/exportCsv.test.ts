import { describe, expect, it, vi } from 'vitest'

import { exportToCsv } from './exportCsv'

describe('exportToCsv', () => {
  it('generates a CSV blob and triggers download', () => {
    const createObjectURLMock = vi.fn().mockReturnValue('blob:mock-url')
    const revokeObjectURLMock = vi.fn()

    window.URL.createObjectURL = createObjectURLMock
    window.URL.revokeObjectURL = revokeObjectURLMock

    const clickMock = vi.fn()

    vi.spyOn(document.body, 'appendChild')
    vi.spyOn(document.body, 'removeChild')

    // Mock HTMLAnchorElement click
    vi.spyOn(document, 'createElement').mockImplementation((tagName: string) => {
      const el = document.createElementNS('http://www.w3.org/1999/xhtml', tagName)

      if (tagName === 'a') {
        el.click = clickMock
      }

      
return el
    })

    exportToCsv(
      'test-orders',
      ['Mã đơn', 'Tổng tiền'],
      [['ORD-001', 150000], ['ORD-002', 250000]]
    )

    expect(createObjectURLMock).toHaveBeenCalled()
    expect(clickMock).toHaveBeenCalled()
    expect(revokeObjectURLMock).toHaveBeenCalledWith('blob:mock-url')
  })
})
