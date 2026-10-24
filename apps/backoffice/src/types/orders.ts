export type OrderStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'PROCESSING'
  | 'PACKED'
  | 'SHIPPING'
  | 'DELIVERED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'RETURNED'
  | 'REFUNDED'

export interface OrderSummary {
  id: string
  orderCode: string
  status: OrderStatus
  paymentMethod: string
  paymentProvider: string
  currency: string
  totalAmount: number
  cancelReason?: string
  createdAt: string
  updatedAt?: string
}

export interface OrderDetail extends OrderSummary {
  canCancel?:boolean
  shipmentCreationPending?:boolean
  cancellation?:{id:string;operationId:string;status:string;reason:string;requestedAt?:string;fulfillmentStoppedAt?:string;inventoryCancelledAt?:string;
    refundId?:string;refundLedgerEntryId?:string;closedAt?:string;failureReason?:string;reviewOwner?:string;reviewDueAt?:string;carrierVerifiedAt?:string}
  checkoutId?: string
  paymentId?: string
  recipientName?: string
  recipientPhone?: string
  shippingAddress?: string
  shippingProvider?: string
  trackingCode?: string
  subtotalAmount: number
  discountAmount: number
  shippingFee: number
  items: {
    variantId: string
    productName: string
    size?: string
    color?: string
    unitPrice: number
    quantity: number
    lineTotal: number
  }[]
}

export interface CarrierVerificationInput {
  operationId:string
  reason:string
  carrierCancelled:boolean
  noHandoff:boolean
  noShipmentExists:boolean
  trackingCode?:string
  mediaIds:string[]
}
export interface CarrierVerification {
  operationId:string
  verifiedBy:string
  verifiedAt:string
  reason:string
  trackingCode?:string
  noShipmentExists:boolean
  mediaIds:string[]
}
export interface Shipment {
  id: string
  orderId: string
  provider: string
  trackingCode?: string
  ghnOrderCode?: string
  fee?: number
  status: string
  createdAt?: string
}

export interface OrderHistory {
  id: string
  fromStatus?: OrderStatus
  toStatus: OrderStatus
  action: string
  changedBy?: string
  reason?: string
  createdAt: string
}
export interface OrderSaga {
  sagaId: string
  status: string
  currentStep: string
  retryCount: number
  failureCode?: string
  failureReason?: string
  stepDeadline?: string
}
