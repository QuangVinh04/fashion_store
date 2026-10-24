import { describe, expect, it } from 'vitest'

import {
  formatPromotionValue,
  promotionScopeLabels,
  promotionStatusLabel,
  promotionTypeLabels
} from './promotion-actions'
import type { Promotion } from '@/types/promotions'

describe('promotion-actions', () => {
  it('provides correct labels for types and scopes', () => {
    expect(promotionTypeLabels.PERCENT).toBe('Giảm theo %')
    expect(promotionTypeLabels.FIXED).toBe('Giảm số tiền cố định')
    expect(promotionScopeLabels.ALL).toBe('Toàn bộ đơn hàng')
    expect(promotionScopeLabels.PRODUCT).toBe('Theo sản phẩm')
    expect(promotionScopeLabels.CATEGORY).toBe('Theo danh mục')
  })

  it('formats promotion value correctly', () => {
    expect(formatPromotionValue({ type: 'PERCENT', value: 15 })).toBe('15%')

    const formattedFixed = formatPromotionValue({ type: 'FIXED', value: 50000 })

    expect(formattedFixed).toContain('50.000')
  })

  it('derives promotion status correctly', () => {
    const now = new Date('2026-10-17T12:00:00Z')

    // Inactive wins regardless of dates
    const inactive: Pick<Promotion, 'active' | 'startAt' | 'endAt'> = {
      active: false,
      startAt: '2026-10-01T00:00:00Z',
      endAt: '2026-10-30T00:00:00Z'
    }

    expect(promotionStatusLabel(inactive, now)).toBe('Đang tắt')

    // Future start
    const upcoming: Pick<Promotion, 'active' | 'startAt' | 'endAt'> = {
      active: true,
      startAt: '2026-10-20T00:00:00Z',
      endAt: '2026-10-30T00:00:00Z'
    }

    expect(promotionStatusLabel(upcoming, now)).toBe('Sắp diễn ra')

    // Expired end
    const expired: Pick<Promotion, 'active' | 'startAt' | 'endAt'> = {
      active: true,
      startAt: '2026-10-01T00:00:00Z',
      endAt: '2026-10-10T00:00:00Z'
    }

    expect(promotionStatusLabel(expired, now)).toBe('Đã hết hạn')

    // Active & currently running
    const running: Pick<Promotion, 'active' | 'startAt' | 'endAt'> = {
      active: true,
      startAt: '2026-10-10T00:00:00Z',
      endAt: '2026-10-20T00:00:00Z'
    }

    expect(promotionStatusLabel(running, now)).toBe('Đang chạy')
  })
})
