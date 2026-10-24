import { describe, expect, it } from 'vitest'

import { validatePromotion } from './promotion-validation'
import type { PromotionInput } from '@/types/promotions'

describe('promotion-validation', () => {
  const validPercentPromo: PromotionInput = {
    code: 'SUMMER2026',
    type: 'PERCENT',
    value: 20,
    maxDiscount: 50000,
    minOrderValue: 200000,
    startAt: '2026-06-01T00:00:00',
    endAt: '2026-06-30T23:59:59',
    totalQuota: 100,
    perUserQuota: 2,
    scopeType: 'ALL'
  }

  it('passes on valid input', () => {
    expect(validatePromotion(validPercentPromo)).toEqual({})
  })

  it('validates required and format of code', () => {
    expect(validatePromotion({ ...validPercentPromo, code: '' }).code).toBeDefined()
    expect(validatePromotion({ ...validPercentPromo, code: '   ' }).code).toBeDefined()
    expect(validatePromotion({ ...validPercentPromo, code: 'INVALID CODE!' }).code).toBeDefined()
    expect(validatePromotion({ ...validPercentPromo, code: 'A'.repeat(51) }).code).toBeDefined()
    expect(validatePromotion({ ...validPercentPromo, code: 'VALID_CODE-123' }).code).toBeUndefined()
  })

  it('validates value constraints for percent and fixed types', () => {
    expect(validatePromotion({ ...validPercentPromo, value: 0 }).value).toBeDefined()
    expect(validatePromotion({ ...validPercentPromo, value: -5 }).value).toBeDefined()
    expect(validatePromotion({ ...validPercentPromo, type: 'PERCENT', value: 101 }).value).toBeDefined()
    expect(validatePromotion({ ...validPercentPromo, type: 'PERCENT', value: 100 }).value).toBeUndefined()

    expect(validatePromotion({ ...validPercentPromo, type: 'FIXED', value: 150000 }).value).toBeUndefined()
  })

  it('validates startAt and endAt required and startAt < endAt', () => {
    expect(validatePromotion({ ...validPercentPromo, startAt: '' }).startAt).toBeDefined()
    expect(validatePromotion({ ...validPercentPromo, endAt: '' }).endAt).toBeDefined()
    expect(
      validatePromotion({
        ...validPercentPromo,
        startAt: '2026-06-30T00:00:00',
        endAt: '2026-06-01T00:00:00'
      }).endAt
    ).toBeDefined()
    expect(
      validatePromotion({
        ...validPercentPromo,
        startAt: '2026-06-01T00:00:00',
        endAt: '2026-06-01T00:00:00'
      }).endAt
    ).toBeDefined()
  })

  it('validates optional numerical limits when provided', () => {
    expect(validatePromotion({ ...validPercentPromo, maxDiscount: 0 }).maxDiscount).toBeDefined()
    expect(validatePromotion({ ...validPercentPromo, minOrderValue: -1 }).minOrderValue).toBeDefined()
    expect(validatePromotion({ ...validPercentPromo, totalQuota: 0 }).totalQuota).toBeDefined()
    expect(validatePromotion({ ...validPercentPromo, totalQuota: 1.5 }).totalQuota).toBeDefined()
    expect(validatePromotion({ ...validPercentPromo, perUserQuota: 0 }).perUserQuota).toBeDefined()
    expect(validatePromotion({ ...validPercentPromo, perUserQuota: 2.2 }).perUserQuota).toBeDefined()

    // When empty/null/undefined, they are valid
    expect(
      validatePromotion({
        ...validPercentPromo,
        maxDiscount: null,
        minOrderValue: undefined,
        totalQuota: null,
        perUserQuota: null
      })
    ).toEqual({})
  })

  it('validates scopeIds when scopeType is PRODUCT or CATEGORY', () => {
    expect(
      validatePromotion({
        ...validPercentPromo,
        scopeType: 'PRODUCT',
        scopeIds: ''
      }).scopeIds
    ).toBeDefined()

    expect(
      validatePromotion({
        ...validPercentPromo,
        scopeType: 'CATEGORY',
        scopeIds: '   , , '
      }).scopeIds
    ).toBeDefined()

    expect(
      validatePromotion({
        ...validPercentPromo,
        scopeType: 'PRODUCT',
        scopeIds: 'prod-1, prod-2'
      }).scopeIds
    ).toBeUndefined()
  })
})
