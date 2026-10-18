import { api, json, ApiError } from './client';
import type { Order, ReturnRequest } from './types';

export async function loadReturnRequest(read: () => Promise<ReturnRequest>) {
  try { return await read(); }
  catch (error) { if (error instanceof ApiError && error.status === 404 && error.code === 4019) return null; throw error; }
}

export function receiptCapabilities(order: Pick<Order, 'canRequestReturn' | 'canConfirmReceipt' | 'activeReturnId'>, request: ReturnRequest | null) {
  const track = !!order.activeReturnId || !!request;
  return { track, canRequestReturn: !!order.canRequestReturn && !track, canConfirmReceipt: !!order.canConfirmReceipt && !order.activeReturnId };
}

export const confirmReceipt = (id: string) => api<Order>(`/api/v1/orders/${encodeURIComponent(id)}/confirm-receipt`, json('POST'));

export const returnEvidenceUrl = (orderId: string, mediaId: string, signal?: AbortSignal) =>
  api<string>(`/api/v1/orders/${encodeURIComponent(orderId)}/return-request/evidence/${encodeURIComponent(mediaId)}`, { signal });

export async function uploadReturnEvidence(file: File) {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size <= 0 || file.size > 5242880) {
    throw new Error('Use a JPEG, PNG or WebP image up to 5 MiB');
  }
  const signed = await api<{ mediaId: string; uploadUrl: string; uploadHeaders: Record<string, string> }>(
    '/api/v1/files/evidence/returns/presign',
    json('POST', { filename: file.name, contentType: file.type, sizeBytes: file.size, visibility: 'PRIVATE' })
  );
  const put = await fetch(signed.uploadUrl, { method: 'PUT', body: file, headers: signed.uploadHeaders });
  if (!put.ok) throw new Error('Upload failed');
  const completed = await api<{ id: string; purpose: string; status: string; visibility: string }>(
    `/api/v1/files/${encodeURIComponent(signed.mediaId)}/complete`,
    json('POST', {})
  );
  if (completed.id !== signed.mediaId || completed.purpose !== 'RETURN_EVIDENCE' || completed.status !== 'ACTIVE' || completed.visibility !== 'PRIVATE') {
    throw new Error('Evidence verification failed');
  }
  return completed.id;
}

export const RETURN_LABEL: Record<string, string> = {
  REQUESTED: 'Chờ shop xem xét',
  APPROVED: 'Đã duyệt yêu cầu',
  IN_TRANSIT: 'Hàng trả đang vận chuyển',
  RECEIVED: 'Shop đã nhận hàng',
  REFUNDED: 'Đã hoàn tiền vào ví',
  REJECTED: 'Yêu cầu bị từ chối',
  CANCELLED: 'Đã hủy yêu cầu',
};
