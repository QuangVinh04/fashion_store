import type { ReturnRequest } from './types';

export function orderStep(status: string) {
  return ({ PENDING: 0, CONFIRMED: 1, PROCESSING: 1, PACKED: 1, SHIPPING: 2, DELIVERED: 3, COMPLETED: 4 } as Record<string, number>)[status] ?? -1;
}

export function detailDate(value?: string | null) {
  if (!value || Number.isNaN(Date.parse(value))) return 'Chưa cập nhật';
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date(value));
}

export function returnPresentation(request: Pick<ReturnRequest, 'status' | 'refundStatus' | 'kind'>) {
  const completed = request.refundStatus === 'COMPLETED';
  if (completed) return { title: 'Đã hoàn tiền', description: 'Khoản hoàn đã được xác nhận. Bạn có thể kiểm tra giao dịch trong ví Fashion Store.', step: 2, completed, tone: 'success' };
  if (request.refundStatus === 'REVIEW_REQUIRED' || request.status === 'REFUNDED') return { title: 'Đang kiểm tra kết quả hoàn tiền', description: 'Shop đang kiểm tra giao dịch. Kết quả sẽ được cập nhật sau khi có xác nhận.', step: 2, completed: false, tone: 'neutral' };
  if (request.refundStatus === 'PROCESSING') return { title: 'Đang hoàn tiền', description: 'Yêu cầu hoàn tiền đang được xử lý. Vui lòng chờ xác nhận ghi có.', step: 2, completed: false, tone: 'neutral' };
  if (request.kind === 'CARRIER_RETURN' || request.kind === 'VERIFIED_LOSS') {
    if (request.status === 'REJECTED' || request.status === 'CANCELLED') {
      return { title: 'Yêu cầu hoàn tiền đã đóng', description: 'Xem thông tin và lý do xử lý bên dưới.', step: -1, completed: false, tone: 'neutral' };
    }
    return {
      title: request.kind === 'VERIFIED_LOSS' ? 'Hoàn tiền do thất lạc' : 'Hoàn tiền do giao hàng không thành công',
      description: 'Shop đang xử lý khoản hoàn cho đơn hàng này. Bạn không cần gửi yêu cầu trả hàng.',
      step: 2, completed: false, tone: 'neutral',
    };
  }
  const states: Record<string, [string, string, number]> = {
    REQUESTED: ['Chờ xác nhận yêu cầu', 'Shop đã nhận yêu cầu và sẽ xem xét thông tin bạn đã gửi.', 0],
    APPROVED: ['Đã chấp nhận trả hàng', 'Vui lòng chuẩn bị và đóng gói sản phẩm để bàn giao theo hướng dẫn của shop.', 0],
    IN_TRANSIT: ['Hàng trả đang vận chuyển', 'Đơn vị vận chuyển đã lấy hàng. Shop sẽ kiểm tra khi nhận được kiện hàng.', 0],
    RECEIVED: ['Shop đã nhận hàng', 'Shop đang kiểm tra sản phẩm và thông tin hoàn tiền.', 1],
    REJECTED: ['Yêu cầu trả hàng bị từ chối', 'Xem lý do từ chối trong thông tin yêu cầu bên dưới.', -1],
    CANCELLED: ['Đã hủy yêu cầu trả hàng', 'Yêu cầu trả hàng này đã được đóng.', -1],
  };
  const [title, description, step] = states[request.status] ?? ['Đang cập nhật yêu cầu', 'Shop đang kiểm tra và cập nhật tiến trình yêu cầu của bạn.', 0];
  return { title, description, step, completed: false, tone: 'neutral' };
}
