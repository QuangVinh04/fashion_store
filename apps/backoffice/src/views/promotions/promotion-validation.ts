import type { PromotionInput } from '@/types/promotions'

export function validatePromotion(input: Partial<PromotionInput>): Record<string, string> {
  const errors: Record<string, string> = {}

  // code
  const trimmedCode = input.code?.trim() || ''

  if (!trimmedCode) {
    errors.code = 'Mã khuyến mãi không được để trống.'
  } else if (trimmedCode.length > 50) {
    errors.code = 'Mã không được vượt quá 50 ký tự.'
  } else if (!/^[A-Z0-9_-]+$/i.test(trimmedCode)) {
    errors.code = 'Mã chỉ được chứa chữ cái, chữ số, gạch ngang và gạch dưới.'
  }

  // value
  if (input.value === undefined || input.value === null || input.value === ('' as unknown as number)) {
    errors.value = 'Vui lòng nhập giá trị khuyến mãi.'
  } else {
    const numValue = Number(input.value)

    if (isNaN(numValue) || numValue <= 0) {
      errors.value = 'Giá trị khuyến mãi phải lớn hơn 0.'
    } else if (input.type === 'PERCENT' && numValue > 100) {
      errors.value = 'Giá trị giảm theo % không được vượt quá 100.'
    }
  }

  // startAt & endAt
  if (!input.startAt) {
    errors.startAt = 'Vui lòng chọn thời gian bắt đầu.'
  }

  if (!input.endAt) {
    errors.endAt = 'Vui lòng chọn thời gian kết thúc.'
  }

  if (input.startAt && input.endAt) {
    const startTime = new Date(input.startAt).getTime()
    const endTime = new Date(input.endAt).getTime()

    if (startTime >= endTime) {
      errors.endAt = 'Thời gian kết thúc phải sau thời gian bắt đầu.'
    }
  }

  // maxDiscount
  if (input.maxDiscount !== undefined && input.maxDiscount !== null && input.maxDiscount !== ('' as unknown as number)) {
    const maxVal = Number(input.maxDiscount)

    if (isNaN(maxVal) || maxVal <= 0) {
      errors.maxDiscount = 'Giảm tối đa phải lớn hơn 0.'
    }
  }

  // minOrderValue
  if (
    input.minOrderValue !== undefined &&
    input.minOrderValue !== null &&
    input.minOrderValue !== ('' as unknown as number)
  ) {
    const minVal = Number(input.minOrderValue)

    if (isNaN(minVal) || minVal <= 0) {
      errors.minOrderValue = 'Đơn tối thiểu phải lớn hơn 0.'
    }
  }

  // totalQuota
  if (input.totalQuota !== undefined && input.totalQuota !== null && input.totalQuota !== ('' as unknown as number)) {
    const quotaVal = Number(input.totalQuota)

    if (!Number.isInteger(quotaVal) || quotaVal < 1) {
      errors.totalQuota = 'Tổng lượt dùng phải là số nguyên lớn hơn hoặc bằng 1.'
    }
  }

  // perUserQuota
  if (
    input.perUserQuota !== undefined &&
    input.perUserQuota !== null &&
    input.perUserQuota !== ('' as unknown as number)
  ) {
    const userQuotaVal = Number(input.perUserQuota)

    if (!Number.isInteger(userQuotaVal) || userQuotaVal < 1) {
      errors.perUserQuota = 'Lượt dùng/khách phải là số nguyên lớn hơn hoặc bằng 1.'
    }
  }

  // scopeIds when scopeType !== 'ALL'
  if (input.scopeType && input.scopeType !== 'ALL') {
    const ids = (input.scopeIds || '')
      .split(',')
      .map(id => id.trim())
      .filter(Boolean)

    if (ids.length === 0) {
      errors.scopeIds = 'Vui lòng nhập ít nhất một ID áp dụng (cách nhau bởi dấu phẩy).'
    }
  }

  return errors
}
