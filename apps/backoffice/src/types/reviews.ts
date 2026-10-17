export type ReviewStatus = 'VISIBLE' | 'HIDDEN'
export type FitRating = 'TRUE_TO_SIZE' | 'RUNS_SMALL' | 'RUNS_LARGE'

export interface Review {
  id: string
  productId: string
  productName?: string | null
  userId?: string | null
  customerName?: string | null
  customerEmail?: string | null
  customerPhone?: string | null
  orderId?: string | null
  rating: number
  comment?: string | null
  verifiedPurchase?: boolean
  status?: ReviewStatus
  replyComment?: string | null
  repliedAt?: string | null
  repliedBy?: string | null
  fitRating?: FitRating | null
  customerHeight?: number | null
  customerWeight?: number | null
  mediaUrls?: string[]
  createdAt?: string
}
