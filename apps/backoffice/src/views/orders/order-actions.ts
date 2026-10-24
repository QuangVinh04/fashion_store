import type { OrderStatus } from '@/types/orders'

export const orderStatusLabels: Record<OrderStatus, string> = {
  PENDING: 'Đang xác nhận',
  CONFIRMED: 'Đã xác nhận',
  PROCESSING: 'Đang xử lý',
  PACKED: 'Đã đóng gói',
  SHIPPING: 'Đang giao',
  DELIVERED: 'Đã giao',
  COMPLETED: 'Hoàn tất',
  CANCELLED: 'Đã hủy',
  RETURNED: 'Đã trả hàng',
  REFUNDED: 'Đã hoàn tiền'
}

export type OrderStatusColor = 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning'

export const orderStatusColors: Record<OrderStatus, OrderStatusColor> = {
  PENDING: 'warning',
  CONFIRMED: 'primary',
  PROCESSING: 'primary',
  PACKED: 'info',
  SHIPPING: 'info',
  DELIVERED: 'success',
  COMPLETED: 'success',
  CANCELLED: 'error',
  RETURNED: 'error',
  REFUNDED: 'secondary'
}

export const paymentMethodLabels: Record<string, string> = {
  COD: 'Thu tiền khi nhận (COD)',
  WALLET: 'Ví khách hàng',
  ONLINE: 'Trực tuyến'
}

export const paymentProviderLabels: Record<string, string> = {
  INTERNAL: 'Nội bộ',
  PAYOS: 'PayOS'
}

export function allowedOrderActions(
  order: { status: OrderStatus; trackingCode?: string; cancellation?:{status:string} },
  shipment?: { id: string } | null
): OrderStatus[] {
  if(order.cancellation) return []

  switch (order.status) {
    case 'CONFIRMED':
      return ['PROCESSING']
    case 'PROCESSING':
      return ['PACKED']
    case 'PACKED':
      return shipment || order.trackingCode?.trim() ? ['SHIPPING'] : []
    case 'SHIPPING':
      return ['DELIVERED']
    default:
      return []
  }
}

export function formatOrderMoney(value: number, currency: string) {
  try {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency }).format(value)
  } catch {
    return new Intl.NumberFormat('vi-VN').format(value) + ' ' + (currency || '')
  }
}
