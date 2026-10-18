import { api, query, type Page } from './client';
import type { OrderSummary } from './types';
import { returnPresentation } from './order-presentation';

export const PURCHASE_TABS = [
  { value: '', label: 'Tất cả' },
  { value: 'PENDING', label: 'Đang xử lý' },
  { value: 'PREPARING', label: 'Chuẩn bị hàng' },
  { value: 'SHIPPING', label: 'Đang giao' },
  { value: 'COMPLETED', label: 'Hoàn thành' },
  { value: 'CANCELLED', label: 'Đã hủy' },
  { value: 'RETURNS', label: 'Trả hàng/Hoàn tiền' },
];

export function getPurchases(page: number, tab: string, q: string, signal?: AbortSignal, status?: string) {
  return api<Page<OrderSummary>>(`/api/v1/orders?${query({ page, tab: tab || undefined, q: q || undefined, status })}`, { signal });
}

export function purchaseAction(order: Pick<OrderSummary, 'id' | 'status' | 'returnRequestId'>) {
  const href = `/profile/orders/${encodeURIComponent(order.id)}`;
  if (order.returnRequestId) return { href: `${href}/return`, label: 'Xem trả hàng/Hoàn tiền' };
  if (order.status === 'CANCELLED') return { href, label: 'Xem chi tiết hủy đơn' };
  if (order.status === 'PENDING') return { href, label: 'Xem đơn / Thanh toán' };
  if (['DELIVERED', 'COMPLETED'].includes(order.status)) return { href, label: 'Xem đơn & đánh giá' };
  return { href, label: 'Xem chi tiết đơn hàng' };
}

export function purchasePresentation(order: OrderSummary, tab: string) {
  const isReturn = tab === 'RETURNS';
  const returned = order.returnRequestId ? returnPresentation({
    status: order.returnStatus || '', refundStatus: order.returnRefundStatus || undefined, kind: order.returnKind,
  }) : null;
  const closed = ['REJECTED', 'CANCELLED', 'RETURN_CANCELLED'].includes(order.returnStatus || '')
    && (!order.returnRefundStatus || order.returnRefundStatus === 'NOT_STARTED');
  return {
    isReturn, status: order.status === 'REFUNDED' && order.returnKind === 'CUSTOMER_RETURN' ? 'COMPLETED' as const : order.status,
    statusLabel: returned?.title || 'Đang cập nhật yêu cầu',
    returnLabel: returned?.title || '',
    description: returned?.description || 'Xem chi tiết yêu cầu để theo dõi tiến trình xử lý.',
    totalLabel: !isReturn ? 'Thành tiền' : closed ? 'Yêu cầu đã đóng'
      : returned?.completed ? 'Tổng tiền đã hoàn' : 'Tiền hoàn dự kiến',
    amount: !isReturn ? order.totalAmount : closed ? null
      : returned?.completed ? order.actualRefundAmount ?? null : order.expectedRefundAmount ?? null,
  };
}
