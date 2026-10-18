import { walletMoney } from '../../api/wallet';
import { walletPaymentState, type WalletSession } from '../../api/wallet-payment';
export default function WalletPaymentState({ session, uncertain }: { session: WalletSession; uncertain: boolean }) {
  const state = walletPaymentState(session);
  const message = session.status==='COMPLETED' ? 'Đã thanh toán bằng ví. Đơn hàng đang được xác nhận.'
    : session.status==='REFUNDED' ? 'Thanh toán đã được hoàn lại vào ví.'
    : uncertain ? 'Chưa xác định được kết quả lần thanh toán vừa gửi. Hệ thống đang kiểm tra cùng giao dịch.'
    : state.expired ? 'Phiên thanh toán đã hết hạn. Vui lòng kiểm tra trạng thái đơn hàng.'
    : state.missing!=='0' ? `Số dư chưa đủ. Bạn còn thiếu ${walletMoney(state.missing)}. Vui lòng đổi phương thức thanh toán.`
    : 'Kiểm tra số tiền và bấm xác nhận để thanh toán toàn bộ đơn hàng.';
  return <p role="status" className="rounded-xl bg-background p-4 text-sm">{message}</p>;
}
