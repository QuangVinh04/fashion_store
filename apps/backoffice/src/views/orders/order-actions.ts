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
  CONFIRMED: 'info',
  PROCESSING: 'warning',
  PACKED: 'info',
  SHIPPING: 'primary',
  DELIVERED: 'success',
  COMPLETED: 'success',
  CANCELLED: 'error',
  RETURNED: 'error',
  REFUNDED: 'secondary'
}

export const ORDER_LIFECYCLE_STEPS: OrderStatus[] = [
  'PENDING',
  'CONFIRMED',
  'PROCESSING',
  'PACKED',
  'SHIPPING',
  'DELIVERED',
  'COMPLETED'
]

export const paymentMethodLabels: Record<string, string> = {
  COD: 'Thu tiền khi nhận (COD)',
  WALLET: 'Ví khách hàng',
  ONLINE: 'Trực tuyến'
}

export const paymentProviderLabels: Record<string, string> = {
  INTERNAL: 'Nội bộ',
  PAYOS: 'PayOS'
}

export const paymentStatusLabels: Record<string, string> = {
  COMPLETED: 'Đã thanh toán',
  COD_PENDING: 'Chờ xác nhận thu COD',
  PENDING: 'Chờ thanh toán',
  REFUNDED: 'Đã hoàn tiền',
  CANCELLED: 'Đã hủy',
  FAILED: 'Thanh toán lỗi'
}

export const paymentStatusColors: Record<string, 'success' | 'warning' | 'error' | 'info' | 'secondary'> = {
  COMPLETED: 'success',
  COD_PENDING: 'warning',
  PENDING: 'warning',
  REFUNDED: 'info',
  CANCELLED: 'secondary',
  FAILED: 'error'
}

export const shipmentStatusLabels: Record<string, string> = {
  READY_TO_PICK: 'Chờ lấy hàng',
  PICKING: 'Đang lấy hàng',
  CANCELLED: 'Đã hủy',
  DELIVERING: 'Đang giao hàng',
  DELIVERED: 'Đã giao hàng',
  RETURN: 'Chuyển hoàn',
  RETURNED: 'Đã chuyển hoàn',
  WAITING_TO_RETURN: 'Chờ chuyển hoàn'
}

export const shipmentStatusColors: Record<string, 'info' | 'warning' | 'success' | 'error' | 'secondary'> = {
  READY_TO_PICK: 'warning',
  PICKING: 'info',
  DELIVERING: 'info',
  DELIVERED: 'success',
  CANCELLED: 'error',
  RETURN: 'warning',
  RETURNED: 'error',
  WAITING_TO_RETURN: 'warning'
}

export function ghnTrackingUrl(trackingCode?: string): string | null {
  const code = trackingCode?.trim()

  if (!code) return null

  return `https://donhang.ghn.vn/?order_code=${encodeURIComponent(code)}`
}

export function allowedOrderActions(
  order: { status: OrderStatus; trackingCode?: string; cancellation?: { status: string } },
  shipment?: { id: string } | null
): OrderStatus[] {
  if (order.cancellation) return []

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
