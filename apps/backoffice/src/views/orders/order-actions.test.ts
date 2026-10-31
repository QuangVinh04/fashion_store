import { expect, it } from 'vitest'

import { formatOrderMoney, ghnTrackingUrl, orderStatusLabels } from './order-actions'

it('has updated status labels according to the new workflow', () => {
  expect(orderStatusLabels.PENDING).toBe('Đang xử lý thanh toán')
  expect(orderStatusLabels.CONFIRMED).toBe('Chờ xác nhận')
  expect(orderStatusLabels.PROCESSING).toBe('Đang đóng gói')
  expect(orderStatusLabels.PACKED).toBe('Chờ lấy hàng')
  expect(orderStatusLabels.SHIPPING).toBe('Đang giao')
  expect(orderStatusLabels.DELIVERED).toBe('Đã giao')
  expect(orderStatusLabels.COMPLETED).toBe('Hoàn tất')
  expect(orderStatusLabels.CANCELLED).toBe('Đã hủy')
})

it('generates GHN tracking url correctly', () => {
  expect(ghnTrackingUrl('')).toBeNull()
  expect(ghnTrackingUrl('  ')).toBeNull()
  expect(ghnTrackingUrl('GHN12345')).toBe('https://donhang.ghn.vn/?order_code=GHN12345')
})

it('formats order money correctly', () => {
  const formatted = formatOrderMoney(100000, 'VND')

  expect(formatted).toContain('100.000')
})
