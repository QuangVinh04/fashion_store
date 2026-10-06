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

export default function PaymentReturn() {
  const [searchParams] = useSearchParams();
  const location = useLocation();

  const isVnpay = location.pathname.includes("vnpay");
  const isPayosCancel =
    location.pathname.includes("payos/cancel") ||
    searchParams.get("cancel") === "true";
  const isPayos =
    location.pathname.includes("payos") || searchParams.has("orderCode");

  const orderId =
    searchParams.get("orderId") ||
    sessionStorage.getItem("lino:pendingOrder") ||
    "";

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
    if (!isVnpay || !searchParams.toString()) return;

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
          void paymentLoad.refresh();
        }
      })
      .catch(() => {
        if (active) {
          setVerifyError(
            "Chưa kiểm tra được kết quả thanh toán. Vui lòng thử lại.",
          );
          void paymentLoad.refresh();
        }
      });

    return () => {
      active = false;
    };
  }, [isVnpay]);

  // PayOS verification & return processing
  useEffect(() => {
    if (!isPayos || !searchParams.toString()) return;

    let active = true;
    setVerifyStatus("Đang kiểm tra kết quả thanh toán…");
    store
      .payosVerify(searchParams)
      .then(() => {
        if (active) {
          if (isPayosCancel) {
            setVerifyError("Đã ghi nhận yêu cầu hủy thanh toán từ cổng PayOS.");
          } else {
            setVerifyStatus("Đã nhận phản hồi thanh toán. Đang kiểm tra đơn hàng.");
          }
          void paymentLoad.refresh();
        }
      })
      .catch(() => {
        if (active) {
          setVerifyError(
            "Chưa kiểm tra được kết quả thanh toán. Vui lòng thử lại.",
          );
          void paymentLoad.refresh();
        }
      });

    return () => {
      active = false;
    };
  }, [isPayos, isPayosCancel]);

  const vnpResponseCode = searchParams.get("vnp_ResponseCode");
  const isVnpayFailed =
    isVnpay && vnpResponseCode != null && vnpResponseCode !== "00";

  const p = paymentLoad.data;
  const isCompleted = p?.status === "COMPLETED";
  const isFailed =
    p?.status === "FAILED" ||
    p?.status === "CANCELLED" ||
    (!p && (isPayosCancel || isVnpayFailed));
  const isPending = !isCompleted && !isFailed;

  // Polling for final status from backend
  useEffect(() => {
    if (!orderId || isFailed) return;
    const status = paymentLoad.data?.status;
    if (["COMPLETED", "FAILED", "CANCELLED", "REFUNDED"].includes(status || ""))
      return;

    let attempts = 0;
    setPollExpired(false);
    const interval = window.setInterval(() => {
      if (!document.hidden && attempts++ < 15) {
        void paymentLoad.refresh();
      }
      if (attempts >= 15) {
        window.clearInterval(interval);
        setPollExpired(true);
      }
    }, 4000);

    return () => window.clearInterval(interval);
  }, [orderId, paymentLoad.data?.status, isFailed]);

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
              Giao dịch đã bị hủy hoặc xảy ra lỗi trong quá trình xử lý từ ngân
              hàng.
            </p>
          </div>
        ) : (
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
        )}

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
          {orderId && (
            <Link
              to={`/profile/orders/${orderId}`}
              className="bg-primary text-white px-8 py-3.5 text-xs font-semibold hover:bg-primary-hover active:scale-[0.98] transition-all inline-flex items-center gap-2 store-action"
            >
              Xem Chi Tiết Đơn Hàng <ArrowRight size={14} />
            </Link>
          )}
          <Link
            to="/products"
            className="border border-border-strong text-foreground px-8 py-3.5 text-xs font-semibold hover:border-primary hover:text-primary transition-colors store-action"
          >
            Tiếp Tục Mua Sắm
          </Link>
        </div>
      </div>
    </div>
  );
}
