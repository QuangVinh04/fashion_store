import type { Promotion, PromotionScopeType, PromotionType } from '@/types/promotions'
import { formatOrderMoney } from '@/views/orders/order-actions'

export const promotionTypeLabels: Record<PromotionType, string> = {
  PERCENT: 'Giảm theo %',
  FIXED: 'Giảm số tiền cố định'
}

export const promotionScopeLabels: Record<PromotionScopeType, string> = {
  ALL: 'Toàn bộ đơn hàng',
  PRODUCT: 'Theo sản phẩm',
  CATEGORY: 'Theo danh mục'
}

export type PromotionStatus = 'Đang chạy' | 'Sắp diễn ra' | 'Đã hết hạn' | 'Đang tắt'

export function promotionStatusLabel(
  p: Pick<Promotion, 'active' | 'startAt' | 'endAt'>,
  now: Date = new Date()
): PromotionStatus {
  if (!p.active) {
    return 'Đang tắt'
  }

  const start = new Date(p.startAt).getTime()
  const end = new Date(p.endAt).getTime()
  const currentTime = now.getTime()

  if (currentTime < start) {
    return 'Sắp diễn ra'
  }

  if (currentTime > end) {
    return 'Đã hết hạn'
  }

  return 'Đang chạy'
}

export function promotionStatusColor(
  status: PromotionStatus
): 'success' | 'info' | 'secondary' {
  switch (status) {
    case 'Đang chạy':
      return 'success'
    case 'Sắp diễn ra':
      return 'info'
    case 'Đã hết hạn':
    case 'Đang tắt':
    default:
      return 'secondary'
  }
}

export function formatPromotionValue(p: Pick<Promotion, 'type' | 'value'>): string {
  if (p.type === 'PERCENT') {
    return `${p.value}%`
  }

  return formatOrderMoney(p.value, 'VND')
}
