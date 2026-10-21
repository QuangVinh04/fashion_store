import { useEffect, useState } from "react";
import { Link, useLocation, useSearchParams } from "react-router";
import {
  CheckCircle2,
  XCircle,
  Clock,
  AlertTriangle,
  ArrowRight,
  RefreshCw,
} from "lucide-react";
import { store } from "../api/store";
import { money } from "../api/client";
import type { Payment } from "../api/types";
import { Status, useLoad } from "../components/StoreUI";
import {resolvePaymentReturn,paymentReturnState} from "../api/payment-return";

export default function PaymentReturn() {
  const [params]=useSearchParams();const location=useLocation();
  return <PaymentReturnContent key={`${location.pathname}:${params.toString()}`}/>;
}
export function PaymentReturnContent() {
  const [searchParams] = useSearchParams();
  const location = useLocation();

  const isVnpay = location.pathname.includes("vnpay");
  const isPayosCancel =
    location.pathname.includes("payos/cancel") ||
    searchParams.get("cancel") === "true";
  const isPayos =
    location.pathname.includes("payos") || searchParams.has("orderCode");

  const provider=isVnpay?'VNPAY':'PAYOS';
  const reference=searchParams.get(isVnpay?'vnp_TxnRef':'orderCode')||'';
  const resolutionLoad=useLoad(()=>resolvePaymentReturn(provider,searchParams),[provider,reference]);
  const orderId=resolutionLoad.data?.orderId||'';
  const orderLoad=useLoad(()=>orderId?store.order(orderId):Promise.resolve(null),[orderId]);

  const [verifyStatus, setVerifyStatus] = useState<string>("");
  const [verifyError, setVerifyError] = useState<string>("");
  const [pollExpired, setPollExpired] = useState(false);

  const paymentLoad = useLoad(
    () =>
      orderId
        ? store.payment(orderId)
        : Promise.reject(
            new Error(
              "Không tìm thấy thông tin mã đơn hàng để tra cứu thanh toán.",
            ),
          ),
    [orderId],
  );

  // VNPay checksum verification & return processing
  useEffect(() => {
    if (!isVnpay || !orderId || !reference) return;

    let active = true;
    const vnpCode = searchParams.get("vnp_ResponseCode");
    setVerifyStatus("Đang kiểm tra kết quả thanh toán…");
    store
      .vnpayVerify(searchParams)
      .then(() => {
        if (active) {
          if (vnpCode && vnpCode !== "00") {
            setVerifyError(
              vnpCode === "24"
                ? "Bạn đã hủy giao dịch trên cổng VNPay."
                : "Chưa xác nhận được thanh toán. Vui lòng kiểm tra lại đơn hàng.",
            );
          } else {
            setVerifyStatus("Đã nhận phản hồi thanh toán. Đang kiểm tra đơn hàng.");
          }
          void paymentLoad.refresh();void resolutionLoad.refresh();void orderLoad.refresh();
        }
      })
      .catch(() => {
        if (active) {
          setVerifyError(
            "Chưa kiểm tra được kết quả thanh toán. Vui lòng thử lại.",
          );
          void paymentLoad.refresh();void resolutionLoad.refresh();void orderLoad.refresh();
        }
      });

    return () => {
      active = false;
    };
  }, [isVnpay,orderId,reference]);

  // PayOS verification & return processing
  useEffect(() => {
    if (!isPayos || !orderId || !reference) return;

    let active = true;
    setVerifyStatus("Đang kiểm tra kết quả thanh toán…");
    store
      .payosVerify(searchParams)
      .then(() => {
        if (active) {
          if (isPayosCancel) {
            setVerifyStatus("Đang tra cứu kết quả thực tế từ payOS.");
          } else {
            setVerifyStatus("Đã nhận phản hồi thanh toán. Đang kiểm tra đơn hàng.");
          }
          void paymentLoad.refresh();void resolutionLoad.refresh();void orderLoad.refresh();
        }
      })
      .catch(() => {
        if (active) {
          setVerifyError(
            "Chưa kiểm tra được kết quả thanh toán. Vui lòng thử lại.",
          );
          void paymentLoad.refresh();void resolutionLoad.refresh();void orderLoad.refresh();
        }
      });

    return () => {
      active = false;
    };
  }, [isPayos,orderId,reference]);

  const p = paymentLoad.data;
  const state=paymentReturnState(p?{...p,captureStatus:resolutionLoad.data?.captureStatus}:null,orderLoad.data);
  const isCompleted=state==='COMPLETED';
  const isFailed=state==='FAILED';
  const isPending=!['COMPLETED','FAILED','REFUNDED'].includes(state);
  // Thanh toán lỗi thì saga huỷ đơn và nhả kho; giỏ hàng chỉ bị xoá khi đơn được xác nhận nên vẫn còn hàng.
  const orderCancelled=orderLoad.data?.status==='CANCELLED';

  // Chờ saga đóng đơn để báo đúng "đã hủy" thay vì để khách đoán.
  useEffect(() => {
    if (!isFailed || orderCancelled) return;
    let attempts = 0;
    const interval = window.setInterval(() => {
      if (!document.hidden && attempts++ < 15) void orderLoad.refresh();
      else if (attempts >= 15) window.clearInterval(interval);
    }, 4000);
    return () => window.clearInterval(interval);
  }, [isFailed, orderCancelled]);

  // Polling for final status from backend
  useEffect(() => {
    if (!orderId || isFailed) return;
    if(['COMPLETED','FAILED','REFUNDED'].includes(state))return;

    let attempts = 0;
    setPollExpired(false);
    const interval = window.setInterval(() => {
      if (!document.hidden && attempts++ < 15) {
        void paymentLoad.refresh();void resolutionLoad.refresh();void orderLoad.refresh();
      }
      if (attempts >= 15) {
        window.clearInterval(interval);
        setPollExpired(true);
      }
    }, 4000);

    return () => window.clearInterval(interval);
  }, [orderId,state]);

  return (
    <div className="w-full">
      {/* Breadcrumb */}
      <div className="border-b border-border bg-background">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-4">
          <nav className="flex items-center gap-2 text-xs text-muted-foreground font-medium">
            <Link to="/" className="hover:text-foreground store-text-link">
              Trang Chủ
            </Link>
            <span>/</span>
            <span className="text-foreground">Kết Quả Thanh Toán</span>
          </nav>
        </div>
      </div>

      <div className="max-w-[800px] mx-auto px-4 sm:px-6 py-16 text-center">
        {resolutionLoad.error&&<Status loading={false} error={resolutionLoad.error} retry={()=>void resolutionLoad.refresh()}/>}
        {state==='ORDER_PENDING'&&<p className='store-alert mb-6'>Đã ghi nhận tiền thanh toán. Đơn hàng đang được xác nhận; vui lòng chờ cập nhật tại chi tiết đơn.</p>}
        {state==='REVIEW_REQUIRED'&&<p className='store-alert mb-6'>Đã nhận thông tin tiền thanh toán cần đối soát. Cửa hàng đang kiểm tra khoản thu và kết quả xử lý đơn.</p>}
        {state==='REFUNDED'&&<div className='mb-6'><h1 className='text-foreground font-semibold text-xl'>ĐÃ HOÀN TIỀN VÀO VÍ</h1><p className='text-sm text-muted-foreground mt-2'>Khoản hoàn đã được ghi nhận. Bạn có thể kiểm tra số dư và lịch sử giao dịch trong ví.</p><Link to='/profile/wallet' className='store-text-link'>Xem ví cửa hàng</Link></div>}
        {/* State Icon & Title */}
        {isCompleted ? (
          <div className="mb-6">
            <div className="w-20 h-20 bg-success-light text-success rounded-full flex items-center justify-center mx-auto mb-4 border border-success/20">
              <CheckCircle2 size={44} />
            </div>
            <h1 className="text-foreground font-semibold text-xl text-balance">
              THANH TOÁN THÀNH CÔNG
            </h1>
            <p className="text-sm text-muted-foreground mt-2 max-w-md mx-auto">
              Thanh toán của bạn đã được ghi nhận. Bạn có thể xem tiến độ xử lý
              tại trang chi tiết đơn hàng.
            </p>
          </div>
        ) : isFailed ? (
          <div className="mb-6">
            <div className="w-20 h-20 bg-primary-light text-destructive rounded-full flex items-center justify-center mx-auto mb-4 border border-destructive/20">
              <XCircle size={44} />
            </div>
            <h1 className="text-foreground font-semibold text-xl text-balance">
              GIAO DỊCH KHÔNG THÀNH CÔNG
            </h1>
            <p className="text-sm text-muted-foreground mt-2 max-w-md mx-auto">
              {orderCancelled
                ? "Đơn hàng đã được hủy vì thanh toán chưa thành công. Sản phẩm vẫn còn trong giỏ hàng, bạn có thể đặt lại."
                : "Giao dịch đã bị hủy hoặc ngân hàng từ chối. Đơn hàng đang được hủy và hàng giữ chỗ sẽ được hoàn lại."}
            </p>
          </div>
        ) : state!=='REFUNDED' ? (
          <div className="mb-6">
            <div className="w-20 h-20 bg-amber-50 text-amber-600 rounded-full flex items-center justify-center mx-auto mb-4 border border-amber-200">
              <Clock size={44} className="animate-pulse" />
            </div>
            <h1 className="text-foreground font-semibold text-xl text-balance">
              ĐANG XÁC NHẬN THANH TOÁN
            </h1>
            <p className="text-sm text-muted-foreground mt-2 max-w-md mx-auto">
              {pollExpired || paymentLoad.error
                ? "Chưa xác nhận được kết quả thanh toán. Vui lòng kiểm tra lại hoặc xem chi tiết đơn hàng."
                : "Đang kiểm tra kết quả thanh toán của bạn. Vui lòng chờ trong giây lát."}
            </p>
          </div>
        ) : null}

        {/* Verification messages */}
        {isPending && (pollExpired || paymentLoad.error) && (
          <button type="button" onClick={() => void paymentLoad.refresh()} className="store-button mb-4 underline">Kiểm tra lại thanh toán</button>
        )}
        {verifyStatus && isPending && !verifyError && !pollExpired && !paymentLoad.error && (
          <p className="text-xs text-success bg-success-light/80 p-2.5 mb-6 border border-success/20 inline-block">
            {verifyStatus}
          </p>
        )}
        {verifyError && !isCompleted && (
          <p className="text-xs text-destructive bg-primary-light p-2.5 mb-6 border border-destructive/20 inline-block">
            {verifyError}
          </p>
        )}

        {/* Payment entity details */}
        {p && (
          <div className="border border-border p-6 bg-background max-w-md mx-auto mb-8 text-left text-xs space-y-2.5">
            <div className="flex justify-between border-b border-border pb-2">
              <span className="text-muted-foreground">Cổng thanh toán:</span>
              <strong className="text-foreground">{p.provider}</strong>
            </div>
            <div className="flex justify-between border-b border-border pb-2">
              <span className="text-muted-foreground">
                Trạng thái thanh toán:
              </span>
              <strong
                className={
                  p.status === "COMPLETED"
                    ? "text-success font-bold"
                    : p.status === "FAILED"
                      ? "text-destructive font-bold"
                      : "text-amber-700 font-bold"
                }
              >
                {({ COMPLETED: "Đã thanh toán", PENDING: "Chờ thanh toán", COD_PENDING: "Thanh toán khi nhận hàng", FAILED: "Thanh toán chưa thành công", CANCELLED: "Đã hủy thanh toán", REFUNDED: "Đã hoàn tiền", REFUND_PENDING: "Đang hoàn tiền", REFUND_FAILED: "Chưa hoàn tiền thành công" } as Record<string, string>)[p.status] || "Đang cập nhật"}
              </strong>
            </div>
            <div className="flex justify-between border-b border-border pb-2">
              <span className="text-muted-foreground">Số tiền giao dịch:</span>
              <strong className="text-foreground text-sm">
                {money(p.amount)}
              </strong>
            </div>
            {p.failureReason && (
              <div className="flex justify-between pt-1 text-primary">
                <span>Nguyên nhân:</span>
                <span>Thanh toán chưa hoàn tất. Vui lòng liên hệ shop nếu bạn đã bị trừ tiền.</span>
              </div>
            )}
          </div>
        )}

        {/* Navigation CTAs */}
        <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
          {isFailed && (
            <Link
              to="/cart"
              className="bg-primary text-white px-8 py-3.5 text-xs font-semibold hover:bg-primary-hover active:scale-[0.98] transition-all inline-flex items-center gap-2 store-action"
            >
              Quay Lại Giỏ Hàng Để Đặt Lại <ArrowRight size={14} />
            </Link>
          )}
          {orderId && (
            <Link
              to={`/profile/orders/${orderId}`}
              className={isFailed
                ? "border border-border-strong text-foreground px-8 py-3.5 text-xs font-semibold hover:border-primary hover:text-primary transition-colors inline-flex items-center gap-2 store-action"
                : "bg-primary text-white px-8 py-3.5 text-xs font-semibold hover:bg-primary-hover active:scale-[0.98] transition-all inline-flex items-center gap-2 store-action"}
            >
              Xem Chi Tiết Đơn Hàng <ArrowRight size={14} />
            </Link>
          )}
          {!isFailed && (
            <Link
              to="/products"
              className="border border-border-strong text-foreground px-8 py-3.5 text-xs font-semibold hover:border-primary hover:text-primary transition-colors store-action"
            >
              Tiếp Tục Mua Sắm
            </Link>
          )}
        </div>
      </div>
    </div>
  );
}
