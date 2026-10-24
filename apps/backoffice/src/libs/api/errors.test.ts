import { describe, expect, it } from 'vitest'

import { ApiError } from './client'
import { mapFieldErrors, toVietnameseError } from './errors'

describe('Vietnamese API errors', () => {
  it('keeps indexed variant field paths and translates validation text', () => {
    const error = new ApiError(400, 9001, 'Validation failed', {
      'variants[0].sku': 'must not be blank',
      'variants[1].price': 'must be greater than or equal to 0'
    })

    expect(mapFieldErrors(error)).toEqual({
      'variants[0].sku': 'Vui lòng nhập giá trị.',
      'variants[1].price': 'Giá trị phải lớn hơn hoặc bằng 0.'
    })
    expect(toVietnameseError(error)).toBe('Dữ liệu chưa hợp lệ. Vui lòng kiểm tra các trường được đánh dấu.')
  })

  it('distinguishes expired session, access denial and network failures', () => {
    expect(toVietnameseError(new ApiError(401, 2005, 'Unauthenticated'))).toContain('đăng nhập')
    expect(toVietnameseError(new ApiError(403, 2006, 'Unauthorized'))).toContain('quyền')
    expect(toVietnameseError(new TypeError('Failed to fetch'))).toContain('kết nối')
  })

  it('preserves readable Vietnamese business messages', () => {
    expect(toVietnameseError(new ApiError(409, 3100, 'SKU đã tồn tại'))).toBe('SKU đã tồn tại')
    expect(mapFieldErrors(new Error('unknown'))).toEqual({})
  })
})
