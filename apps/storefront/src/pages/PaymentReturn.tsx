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
  const isPayosSuccess = location.pathname.includes("payos/success");
  const isPayos =
    location.pathname.includes("payos") || searchParams.has("orderCode");

  const orderId =
    searchParams.get("orderId") ||
    sessionStorage.getItem("lino:pendingOrder") ||
    "";

  const [verifyStatus, setVerifyStatus] = useState<string>("");
  const [verifyError, setVerifyError] = useState<string>("");

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
    setVerifyStatus("Đang xác thực chữ ký giao dịch với cổng VNPay…");
    store
      .vnpayVerify(searchParams)
      .then(() => {
        if (active) {
          if (vnpCode && vnpCode !== "00") {
            setVerifyError(
              vnpCode === "24"
                ? "Bạn đã hủy giao dịch trên cổng VNPay."
                : `Giao dịch không thành công (Mã phản hồi VNPay: ${vnpCode}).`,
            );
          } else {
            setVerifyStatus("Xác thực chữ ký hợp lệ từ VNPay.");
          }
          void paymentLoad.refresh();
        }
      })
      .catch((err) => {
        if (active) {
          setVerifyError(
            (err as Error).message || "Chữ ký phản hồi không hợp lệ.",
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
    setVerifyStatus("Đang xác thực thông tin giao dịch với cổng PayOS…");
    store
      .payosVerify(searchParams)
      .then(() => {
        if (active) {
          if (isPayosCancel) {
            setVerifyError("Đã ghi nhận yêu cầu hủy thanh toán từ cổng PayOS.");
          } else {
            setVerifyStatus("Xác thực giao dịch thành công từ PayOS.");
          }
          void paymentLoad.refresh();
        }
      })
      .catch((err) => {
        if (active) {
          setVerifyError(
            (err as Error).message || "Không thể xác thực giao dịch từ PayOS.",
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
  const isVnpaySuccess = isVnpay && vnpResponseCode === "00";

  const p = paymentLoad.data;
  const isCompleted =
    p?.status === "COMPLETED" ||
    (!p && isPayosSuccess && !isPayosCancel) ||
    (!p && isVnpaySuccess);
  const isFailed =
    p?.status === "FAILED" ||
    p?.status === "CANCELLED" ||
    isPayosCancel ||
    isVnpayFailed;
  const isPending = !isCompleted && !isFailed;

  // Polling for final status from backend
  useEffect(() => {
    if (!orderId || isFailed) return;
    const status = paymentLoad.data?.status;
    if (["COMPLETED", "FAILED", "CANCELLED", "REFUNDED"].includes(status || ""))
      return;

    let attempts = 0;
    const interval = window.setInterval(() => {
      if (!document.hidden && attempts++ < 15) {
        void paymentLoad.refresh();
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
            <div className="w-20 h-20 bg-emerald-50 text-emerald-600 rounded-full flex items-center justify-center mx-auto mb-4 border border-emerald-200">
              <CheckCircle2 size={44} />
            </div>
            <h1 className="text-foreground font-semibold text-xl text-balance">
              THANH TOÁN THÀNH CÔNG
            </h1>
            <p className="text-sm text-muted-foreground mt-2 max-w-md mx-auto">
              Đơn hàng của bạn đã được ghi nhận thanh toán thành công và chuyển
              sang bộ phận chuẩn bị hàng.
            </p>
          </div>
        ) : isFailed ? (
          <div className="mb-6">
            <div className="w-20 h-20 bg-rose-50 text-rose-600 rounded-full flex items-center justify-center mx-auto mb-4 border border-rose-200">
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
              Hệ thống đang đồng bộ dữ liệu giao dịch từ cổng thanh toán. Vui
              lòng giữ trang trong giây lát.
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
          <p className="text-xs text-destructive bg-red-50 p-2.5 mb-6 border border-red-200 inline-block">
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
                Trạng thái hệ thống:
              </span>
              <strong
                className={
                  p.status === "COMPLETED"
                    ? "text-emerald-700 font-bold"
                    : p.status === "FAILED"
                      ? "text-rose-700 font-bold"
                      : "text-amber-700 font-bold"
                }
              >
                {p.status}
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
