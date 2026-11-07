import { describe, expect, it } from 'vitest'

import { formatOccurredAt, truncateText, VISIBLE_ID_CHARS } from './format'

describe('audit log format helpers', () => {
  it('định dạng occurredAt sang dd/MM/yyyy HH:mm:ss không đổi múi giờ', () => {
    expect(formatOccurredAt('2026-11-07T14:30:00')).toBe('07/11/2026 14:30:00')
    expect(formatOccurredAt('2026-11-07T14:30:00.123456')).toBe('07/11/2026 14:30:00')
    expect(formatOccurredAt('')).toBe('')
    expect(formatOccurredAt('invalid-date')).toBe('invalid-date')
  })

  it('rút gọn văn bản vượt quá VISIBLE_ID_CHARS kèm dấu ba chấm', () => {
    expect(VISIBLE_ID_CHARS).toBe(8)
    expect(truncateText('00000000-0000-0000-0000-000000000001')).toBe('00000000...')
    expect(truncateText('12345678')).toBe('12345678')
    expect(truncateText('123456789')).toBe('12345678...')
    expect(truncateText('short')).toBe('short')
    expect(truncateText('')).toBe('')
  })
})
