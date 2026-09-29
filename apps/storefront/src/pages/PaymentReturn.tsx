import { useEffect, useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router';
import { CheckCircle2, XCircle, Clock, AlertTriangle, ArrowRight, RefreshCw } from 'lucide-react';
import { store } from '../api/store';
import { money } from '../api/client';
import type { Payment } from '../api/types';
import { Status, useLoad } from '../components/StoreUI';

export default function PaymentReturn() {
  const [searchParams] = useSearchParams();
  const location = useLocation();

  const isVnpay = location.pathname.includes('vnpay');
  const isPayosCancel = location.pathname.includes('payos/cancel') || searchParams.get('cancel') === 'true';
  const isPayosSuccess = location.pathname.includes('payos/success');

  const orderId =
    searchParams.get('orderId') ||
    sessionStorage.getItem('lino:pendingOrder') ||
    '';

  const [verifyStatus, setVerifyStatus] = useState<string>('');
  const [verifyError, setVerifyError] = useState<string>('');

  const paymentLoad = useLoad(
    () =>
      orderId
        ? store.payment(orderId)
        : Promise.reject(new Error('Không tìm thấy thông tin mã đơn hàng để tra cứu thanh toán.')),
    [orderId]
  );

  // VNPay checksum verification
  useEffect(() => {
    if (!isVnpay || !searchParams.toString()) return;

    let active = true;
    setVerifyStatus('Đang xác thực chữ ký giao dịch với cổng VNPay…');
    store
      .vnpayVerify(searchParams)
      .then(() => {
        if (active) {
          setVerifyStatus('Xác thực chữ ký hợp lệ từ VNPay.');
          void paymentLoad.refresh();
        }
      })
      .catch((err) => {
        if (active) {
          setVerifyError((err as Error).message || 'Chữ ký phản hồi không hợp lệ.');
        }
      });

    return () => {
      active = false;
    };
  }, [isVnpay]);

  // Polling for final status from backend
  useEffect(() => {
    if (!orderId) return;
    const status = paymentLoad.data?.status;
    if (['COMPLETED', 'FAILED', 'CANCELLED', 'REFUNDED'].includes(status || '')) return;

    let attempts = 0;
    const interval = window.setInterval(() => {
      if (!document.hidden && attempts++ < 15) {
        void paymentLoad.refresh();
      }
    }, 4000);

    return () => window.clearInterval(interval);
  }, [orderId, paymentLoad.data?.status]);

  const p = paymentLoad.data;
  const isCompleted = p?.status === 'COMPLETED' || (!p && isPayosSuccess);
  const isFailed = p?.status === 'FAILED' || p?.status === 'CANCELLED' || isPayosCancel;
  const isPending = !isCompleted && !isFailed;

  return (
    <div style={{ fontFamily: "'Inter', sans-serif" }} className="w-full">
      {/* Breadcrumb */}
      <div className="border-b border-[rgba(0,0,0,0.08)] bg-[#fafafa]">
        <div className="max-w-[1400px] mx-auto px-6 py-4">
          <nav className="flex items-center gap-2 text-xs text-[#888] uppercase tracking-widest font-medium">
            <Link to="/" className="hover:text-[#111]">
              Trang Chủ
            </Link>
            <span>/</span>
            <span className="text-[#111]">Kết Quả Thanh Toán</span>
          </nav>
        </div>
      </div>

      <div className="max-w-[800px] mx-auto px-6 py-16 text-center">
        {/* State Icon & Title */}
        {isCompleted ? (
          <div className="mb-6">
            <div className="w-20 h-20 bg-emerald-50 text-emerald-600 rounded-full flex items-center justify-center mx-auto mb-4 border border-emerald-200">
              <CheckCircle2 size={44} />
            </div>
            <h1
              className="text-[#111] font-black uppercase text-3xl md:text-4xl"
              style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
            >
              THANH TOÁN THÀNH CÔNG
            </h1>
            <p className="text-sm text-[#555] mt-2 max-w-md mx-auto" style={{ fontWeight: 300 }}>
              Đơn hàng của bạn đã được ghi nhận thanh toán thành công và chuyển sang bộ phận chuẩn bị hàng.
            </p>
          </div>
        ) : isFailed ? (
          <div className="mb-6">
            <div className="w-20 h-20 bg-rose-50 text-rose-600 rounded-full flex items-center justify-center mx-auto mb-4 border border-rose-200">
              <XCircle size={44} />
            </div>
            <h1
              className="text-[#111] font-black uppercase text-3xl md:text-4xl"
              style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
            >
              GIAO DỊCH KHÔNG THÀNH CÔNG
            </h1>
            <p className="text-sm text-[#555] mt-2 max-w-md mx-auto" style={{ fontWeight: 300 }}>
              Giao dịch đã bị hủy hoặc xảy ra lỗi trong quá trình xử lý từ ngân hàng.
            </p>
          </div>
        ) : (
          <div className="mb-6">
            <div className="w-20 h-20 bg-amber-50 text-amber-600 rounded-full flex items-center justify-center mx-auto mb-4 border border-amber-200">
              <Clock size={44} className="animate-pulse" />
            </div>
            <h1
              className="text-[#111] font-black uppercase text-3xl md:text-4xl"
              style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
            >
              ĐANG XÁC NHẬN THANH TOÁN
            </h1>
            <p className="text-sm text-[#555] mt-2 max-w-md mx-auto" style={{ fontWeight: 300 }}>
              Hệ thống đang đồng bộ dữ liệu giao dịch từ cổng thanh toán. Vui lòng giữ trang trong giây lát.
            </p>
          </div>
        )}

        {/* Verification messages */}
        {verifyStatus && (
          <p className="text-xs text-emerald-800 bg-emerald-50/80 p-2.5 mb-6 border border-emerald-200 inline-block">
            {verifyStatus}
          </p>
        )}
        {verifyError && (
          <p className="text-xs text-[#B00018] bg-red-50 p-2.5 mb-6 border border-red-200 inline-block">
            {verifyError}
          </p>
        )}

        {/* Payment entity details */}
        {p && (
          <div className="border border-[rgba(0,0,0,0.1)] p-6 bg-[#fafafa] max-w-md mx-auto mb-8 text-left text-xs space-y-2.5">
            <div className="flex justify-between border-b border-[rgba(0,0,0,0.06)] pb-2">
              <span className="text-[#777]">Cổng thanh toán:</span>
              <strong className="text-[#111] uppercase">{p.provider}</strong>
            </div>
            <div className="flex justify-between border-b border-[rgba(0,0,0,0.06)] pb-2">
              <span className="text-[#777]">Trạng thái hệ thống:</span>
              <strong
                className={
                  p.status === 'COMPLETED'
                    ? 'text-emerald-700 font-bold'
                    : p.status === 'FAILED'
                      ? 'text-rose-700 font-bold'
                      : 'text-amber-700 font-bold'
                }
              >
                {p.status}
              </strong>
            </div>
            <div className="flex justify-between border-b border-[rgba(0,0,0,0.06)] pb-2">
              <span className="text-[#777]">Số tiền giao dịch:</span>
              <strong className="text-[#111] text-sm">{money(p.amount)}</strong>
            </div>
            {p.failureReason && (
              <div className="flex justify-between pt-1 text-[#E5001B]">
                <span>Nguyên nhân:</span>
                <span>{p.failureReason}</span>
              </div>
            )}
          </div>
        )}

        {/* Navigation CTAs */}
        <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
          {orderId && (
            <Link
              to={`/profile/orders/${orderId}`}
              className="bg-[#111] text-white px-8 py-3.5 text-xs uppercase tracking-widest font-bold hover:bg-[#E5001B] active:scale-[0.98] transition-all inline-flex items-center gap-2"
            >
              Xem Chi Tiết Đơn Hàng <ArrowRight size={14} />
            </Link>
          )}
          <Link
            to="/products"
            className="border border-[#111] text-[#111] px-8 py-3.5 text-xs uppercase tracking-widest font-semibold hover:border-[#E5001B] hover:text-[#E5001B] transition-colors"
          >
            Tiếp Tục Mua Sắm
          </Link>
        </div>
      </div>
    </div>
  );
}
