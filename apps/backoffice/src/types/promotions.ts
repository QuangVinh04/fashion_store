export type PromotionType = 'PERCENT' | 'FIXED'
export type PromotionScopeType = 'ALL' | 'PRODUCT' | 'CATEGORY'

export interface Promotion {
  id: string
  code: string
  type: PromotionType
  value: number
  maxDiscount?: number | null
  minOrderValue?: number | null
  startAt: string
  endAt: string
  totalQuota?: number | null
  perUserQuota?: number | null
  scopeType: PromotionScopeType
  scopeIds?: string | null
  active: boolean
  createdAt?: string
  updatedAt?: string
}

export interface PromotionInput {
  code: string
  type: PromotionType
  value: number
  maxDiscount?: number | null
  minOrderValue?: number | null
  startAt: string
  endAt: string
  totalQuota?: number | null
  perUserQuota?: number | null
  scopeType: PromotionScopeType
  scopeIds?: string | null
  active?: boolean
}
