import type { Order, Payment } from './types';

/**
 * Link thanh toán online do khách tạo đồng bộ qua /initiate. Chỉ chờ khi payment chưa được tạo
 * (saga đang giữ kho); lỗi cổng thanh toán được báo ngay để khách thử lại hoặc huỷ đơn.
 */
export class PaymentLinkError extends Error {
  constructor(public reason: 'gateway' | 'closed' | 'paid' | 'not-ready') {
    super(reason);
  }
}

type PaymentLinkApi = {
  payment: (orderId: string) => Promise<Payment>;
  paymentInitiate: (paymentId: string) => Promise<{ paymentUrl?: string }>;
};

const status = (error: unknown) => (error as { status?: number } | null)?.status;

export async function requestPaymentUrl(
  orderId: string,
  api: PaymentLinkApi,
  { attempts = 10, delayMs = 600, sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms)) } = {},
): Promise<string> {
  for (let attempt = 0; attempt < attempts; attempt++) {
    if (attempt > 0) await sleep(delayMs);
    let payment: Payment;
    try {
      payment = await api.payment(orderId);
    } catch (error) {
      if (status(error) === 404) continue; // Payment chưa được tạo: chờ saga.
      throw error;
    }
    if (payment.status === 'COMPLETED') throw new PaymentLinkError('paid');
    if (payment.status !== 'PENDING') throw new PaymentLinkError('closed');
    try {
      const { paymentUrl } = await api.paymentInitiate(payment.id);
      if (paymentUrl) return paymentUrl;
    } catch (error) {
      if (status(error) === 502) throw new PaymentLinkError('gateway');
      if (status(error) === 400) throw new PaymentLinkError('closed');
      throw error;
    }
  }
  throw new PaymentLinkError('not-ready');
}

export function paymentLinkMessage(error: unknown): string | undefined {
  if (!(error instanceof PaymentLinkError)) return undefined;
  return {
    gateway: 'Cổng thanh toán đang gặp sự cố. Vui lòng thử lại hoặc hủy đơn hàng.',
    closed: 'Phiên thanh toán đã hết hạn hoặc đơn hàng đang được hủy.',
    paid: 'Đơn hàng đã được thanh toán. Shop đang xác nhận đơn hàng.',
    'not-ready': 'Đơn hàng vẫn đang được xử lý. Vui lòng thử lại sau giây lát.',
  }[error.reason];
}

/** Payment chưa tải được (đang tạo) vẫn cho bấm: requestPaymentUrl tự chờ. */
export function canPayOnline(order: Pick<Order, 'paymentMethod' | 'status'>, payment: Pick<Payment, 'status'> | null) {
  return order.paymentMethod === 'ONLINE' && order.status === 'PENDING' && (!payment || payment.status === 'PENDING');
}

export function paymentDeadline(expiresAt: string | undefined, now: number) {
  if (!expiresAt) return null;
  const left = Math.max(0, Math.ceil((Date.parse(expiresAt) - now) / 1000));
  const pad = (value: number) => String(value).padStart(2, '0');
  return { expired: left === 0, remaining: `${pad(Math.floor(left / 60))}:${pad(left % 60)}` };
}
