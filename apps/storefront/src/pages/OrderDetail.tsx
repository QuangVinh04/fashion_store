import { customerError } from "../api/client";
import { useState, useEffect } from "react";
import { Link, useParams } from "react-router";
import {
  Package,
  Truck,
  CheckCircle2,
  Clock,
  AlertCircle,
  Copy,
  Check,
  CreditCard,
  MapPin,
  RotateCcw,
  Star,
  ExternalLink,
} from "lucide-react";
import { store } from "../api/store";
import { money } from "../api/client";
import { loadOrderPayment, loadOrderShipment } from "../api/order-resources";
import type { Order, Payment, ReturnRequest } from "../api/types";
import OrderActivityTimeline from "../components/OrderActivityTimeline";
import ProfileLayout from "../components/ProfileLayout";
import { PageTitle, Status, StatusBadge, useLoad } from "../components/StoreUI";

export default function OrderDetail() {
  const { id = "" } = useParams();

  const orderLoad = useLoad(() => store.order(id), [id]);
  const historyLoad = useLoad(() => store.orderHistory(id), [id]);

  const [payment, setPayment] = useState<Payment | null>(null);
  const [paymentLoading, setPaymentLoading] = useState(false);
  const [paymentError, setPaymentError] = useState<string | null>(null);
  const [shipmentError, setShipmentError] = useState<string | null>(null);
  const [shipmentLoading, setShipmentLoading] = useState(false);
  const [resourceRetry, setResourceRetry] = useState(0);
  const [shipment, setShipment] = useState<{
    trackingCode: string;
    status: string;
    provider: string;
  } | null>(null);
  const [returnRequest, setReturnRequest] = useState<ReturnRequest | null>(
    null,
  );
  const [productIds, setProductIds] = useState<Record<string, string>>({});

  const [copiedTracking, setCopiedTracking] = useState(false);
  const [busyAction, setBusyAction] = useState(false);
  const [actionNotice, setActionNotice] = useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);

  const order = orderLoad.data;

  // Clear data when navigating between orders.
  useEffect(() => {
    setPayment(null);
    setShipment(null);
    setReturnRequest(null);
    setPaymentError(null);
    setShipmentError(null);
  }, [id]);

  useEffect(() => {
    if (!order || order.id !== id) return;
    const controller = new AbortController();
    setPayment(null);
    setPaymentError(null);
    if (order.status === "CANCELLED" && !order.paymentId && !order.paymentUrl) {
      setPaymentLoading(false);
      return () => controller.abort();
    }
    // Before authorization there is no payment yet. Order polling discovers it.
    if (order.status === "PENDING" && !order.paymentId && !order.paymentUrl) {
      setPaymentLoading(true);
      return () => controller.abort();
    }
    setPaymentLoading(true);
    void loadOrderPayment(() => store.payment(id), controller.signal)
      .then((p) => {
        if (!controller.signal.aborted) {
          setPayment(p);
          if (!p) setPaymentError("Thông tin thanh toán chưa sẵn sàng. Vui lòng thử lại.");
        }
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setPaymentError("Không thể tải thông tin thanh toán. Vui lòng thử lại.");
      })
      .finally(() => {
        if (!controller.signal.aborted) setPaymentLoading(false);
      });
    return () => controller.abort();
  }, [id, order?.id, order?.status, order?.paymentId, order?.paymentUrl, resourceRetry]);

  useEffect(() => {
    if (!order || order.id !== id) return;
    let active = true;
    setShipment(null);
    setShipmentError(null);
    setShipmentLoading(Boolean(order.trackingCode));
    void loadOrderShipment(order.trackingCode, () => store.orderShipment(id))
      .then((s) => { if (active) setShipment(s); })
      .catch(() => {
        if (active) setShipmentError("Không thể tải thông tin vận chuyển. Vui lòng thử lại.");
      })
      .finally(() => { if (active) setShipmentLoading(false); });
    return () => { active = false; };
  }, [id, order?.id, order?.status, order?.trackingCode, resourceRetry]);

  // Return requests are available after delivery.
  useEffect(() => {
    if (!order || order.id !== id) return;
    let active = true;

    if (["DELIVERED", "RETURNED", "REFUNDED"].includes(order.status)) {
      store
        .returnRequest(id)
        .then((r) => {
          if (active) setReturnRequest(r);
        })
        .catch(() => {});
    }

    return () => {
      active = false;
    };
  }, [id, order?.id, order?.status]);

  // Resolve variantIds to productIds for reviews
  useEffect(() => {
    const ids = order?.items.map((i) => i.variantId).filter(Boolean) || [];
    if (!ids.length) return;
    let active = true;
    store
      .variantSnapshots(ids)
      .then((rows) => {
        if (active) {
          const map: Record<string, string> = {};
          rows.forEach((r) => {
            map[r.variantId] = r.productId;
          });
          setProductIds(map);
        }
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [order?.id]);

  // Auto-polling for PENDING orders
  useEffect(() => {
    if (order?.status !== "PENDING" || order.id !== id) return;
    let attempts = 0;
    const interval = window.setInterval(() => {
      if (!document.hidden && attempts++ < 30) {
        void orderLoad.refresh();
      }
      if (attempts >= 30) {
        window.clearInterval(interval);
        setPaymentLoading(false);
        if (!order?.paymentId && !order?.paymentUrl)
          setPaymentError("Đơn hàng vẫn đang xử lý. Vui lòng thử cập nhật lại.");
      }
    }, 5000);
    return () => window.clearInterval(interval);
  }, [order?.id, order?.status, order?.paymentId, order?.paymentUrl, id, resourceRetry]);

  const retryResources = () => {
    void orderLoad.refresh();
    setResourceRetry((value) => value + 1);
  };

  const copyTracking = () => {
    const trackingCode = shipment?.trackingCode || order?.trackingCode;
    if (!trackingCode) return;
    navigator.clipboard.writeText(trackingCode);
    setCopiedTracking(true);
    setTimeout(() => setCopiedTracking(false), 2000);
  };

  async function handleCancelOrder() {
    if (
      !window.confirm(
        "Bạn có chắc chắn muốn gửi yêu cầu hủy đơn hàng này không?",
      )
    )
      return;
    setBusyAction(true);
    setActionNotice(null);
    try {
      await store.orderCancel(
        id,
        "Khách hàng yêu cầu hủy đơn từ trang tài khoản",
      );
      setActionNotice({
        type: "success",
        text: "Đã gửi yêu cầu hủy đơn. Shop sẽ cập nhật kết quả xử lý tại đây.",
      });
      await orderLoad.refresh();
    } catch (e) {
      setActionNotice({
        type: "error",
        text: customerError(e) || "Không thể hủy đơn hàng vào lúc này.",
      });
    } finally {
      setBusyAction(false);
    }
  }

  async function handleOnlinePayment() {
    setBusyAction(true);
    setActionNotice(null);
    try {
      let payUrl = order?.paymentUrl;
      let p = payment;

      // 1. Thử lấy payment nếu state chưa có
      if (!p) {
        try {
          p = await store.payment(id);
          if (p) setPayment(p);
        } catch {
          // Chưa có bản ghi payment
        }
      }

      // 2. Nếu có payment mà chưa có payUrl -> gọi initiate
      if (!payUrl && p) {
        try {
          const res = await store.paymentInitiate(p.id);
          payUrl = res.paymentUrl;
        } catch {
          // Bỏ qua nếu lỗi initiate
        }
      }

      // 3. Nếu vẫn chưa có, đợi và thử lại ngầm trong 4 giây (tránh hiển thị lỗi sớm)
      if (!payUrl) {
        for (let i = 0; i < 6; i++) {
          await new Promise((resolve) => setTimeout(resolve, 600));
          try {
            const freshOrder = await store.order(id);
            if (freshOrder.paymentUrl) {
              payUrl = freshOrder.paymentUrl;
              break;
            }
          } catch {}

          if (!p) {
            try {
              p = await store.payment(id);
              if (p) setPayment(p);
            } catch {}
          }

          if (p) {
            try {
              const res = await store.paymentInitiate(p.id);
              if (res.paymentUrl) {
                payUrl = res.paymentUrl;
                break;
              }
            } catch {}
          }
        }
      }

      if (payUrl) {
        sessionStorage.setItem("lino:pendingOrder", id);
        window.location.assign(payUrl);
      } else {
        setActionNotice({
          type: "error",
          text: "Cổng thanh toán chưa sẵn sàng cấp URL giao dịch. Vui lòng thử lại sau ít phút.",
        });
      }
    } catch (e) {
      setActionNotice({ type: "error", text: customerError(e) });
    } finally {
      setBusyAction(false);
    }
  }

  return (
    <ProfileLayout>
      <div className="w-full">
        {/* Navigation & Header */}
        <div className="mb-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-border">
          <div>
            <Link
              to="/profile/orders"
              className="text-xs text-muted-foreground hover:text-foreground font-semibold mb-2 inline-block store-text-link"
            >
              ← Quay Lại Danh Sách Đơn Hàng
            </Link>
            <h1 className="text-foreground font-semibold text-xl leading-none text-balance">
              ĐƠN HÀNG: {order?.orderCode || id}
            </h1>
            {order && (
              <p className="text-xs text-muted-foreground mt-1.5">
                Ngày đặt: {new Date(order.createdAt).toLocaleString("vi-VN")}
              </p>
            )}
          </div>

          {order && <StatusBadge status={order.status} />}
        </div>

        <Status
          loading={orderLoad.loading}
          error={orderLoad.error}
          retry={() => void orderLoad.refresh()}
        />

        {actionNotice && (
          <div
            className={`mb-6 p-4 text-xs font-medium flex items-center gap-2.5 border ${
              actionNotice.type === "success"
                ? "bg-success-light text-success border-success/20"
                : "bg-primary-light text-destructive border-destructive/20"
            }`}
          >
            {actionNotice.type === "success" ? (
              <Check size={16} className="text-success" />
            ) : (
              <AlertCircle size={16} className="text-destructive" />
            )}
            <span>{actionNotice.text}</span>
          </div>
        )}

        {order && (
          <div className="grid grid-cols-1 lg:grid-cols-[1fr_360px] gap-6 items-start">
            {/* Left Column: Timeline, Shipment, Items */}
            <div className="space-y-8">
              {/* 1. Timeline & Shipment */}
              <section className="border border-border p-4 sm:p-5 bg-white space-y-6 rounded-2xl">
                <div className="flex items-center justify-between pb-3 border-b border-border">
                  <h2 className="text-xs font-semibold text-foreground flex items-center gap-2 text-balance">
                    <Truck size={16} /> Hoạt Động Đơn Hàng
                  </h2>
                  {shipment && (
                    <span className="text-xs font-semibold text-success">
                      {shipment.provider}: {({ PENDING: "Chờ lấy hàng", PICKED: "Đã lấy hàng", SHIPPING: "Đang vận chuyển", DELIVERED: "Đã giao hàng", RETURNED: "Đang hoàn hàng", CANCELLED: "Đã hủy vận đơn" } as Record<string, string>)[shipment.status] || "Đang cập nhật"}
                    </span>
                  )}
                </div>

                {(shipment?.trackingCode || order.trackingCode) && (
                  <div className="p-3 bg-background border border-border flex items-center justify-between gap-3 text-xs">
                    <div>
                      <span className="text-muted-foreground mr-2">
                        Mã vận đơn GHN:
                      </span>
                      <strong className="text-foreground font-mono text-sm">
                        {shipment?.trackingCode || order.trackingCode}
                      </strong>
                    </div>
                    <button
                      type="button"
                      onClick={copyTracking}
                      className="text-xs font-semibold text-foreground hover:text-primary inline-flex items-center gap-1 store-button"
                    >
                      {copiedTracking ? (
                        <>
                          <Check size={13} className="text-success" /> Đã
                          chép
                        </>
                      ) : (
                        <>
                          <Copy size={13} /> Sao chép
                        </>
                      )}
                    </button>
                  </div>
                )}

                <div aria-live="polite" className="text-xs text-muted-foreground">
                  {shipmentError ? (
                    <div role="alert" className="space-y-2 text-destructive">
                      <p>{shipmentError}</p>
                      <button type="button" onClick={retryResources} className="underline store-button">Thử lại</button>
                    </div>
                  ) : shipmentLoading ? (
                    <p>Đang tải thông tin vận chuyển…</p>
                  ) : !shipment && !order.trackingCode ? (
                    <p>{order.status === "CANCELLED"
                      ? "Đơn hàng đã hủy. Chưa có vận đơn."
                      : order.status === "PENDING"
                        ? "Chưa có mã vận đơn. Đơn hàng đang chờ xác nhận."
                        : ["CONFIRMED", "PROCESSING", "PACKED"].includes(order.status)
                          ? "Chưa có mã vận đơn. Shop đang chuẩn bị giao hàng."
                          : "Chưa có thông tin vận đơn."}</p>
                  ) : (shipment?.status === "PENDING" || !shipment) ? (
                    <p>Đã có mã vận đơn. Chờ cập nhật từ đơn vị vận chuyển.</p>
                  ) : null}
                </div>

                {/* History Timeline */}
                <OrderActivityTimeline
                  key={id}
                  history={historyLoad.data}
                  loading={historyLoad.loading}
                  error={historyLoad.error}
                  retry={() => void historyLoad.refresh()}
                />

                {order.cancelReason && (
                  <div className="p-3 bg-primary-light text-xs text-destructive border border-destructive/20">
                    <strong>Đơn hàng đã hủy.</strong> Vui lòng liên hệ shop nếu bạn cần hỗ trợ.
                  </div>
                )}
              </section>

              {/* 2. Order Products */}
              <section className="border border-border p-4 sm:p-5 bg-white rounded-2xl">
                <h2 className="text-xs font-semibold text-foreground mb-6 flex items-center gap-2 pb-3 border-b border-border text-balance">
                  <Package size={16} /> Danh Sách Sản Phẩm ({order.items.length}
                  )
                </h2>

                <div className="divide-y divide-border">
                  {order.items.map((item, idx) => {
                    const productId = productIds[item.variantId];
                    const canReview =
                      ["DELIVERED", "COMPLETED"].includes(order.status) &&
                      productId;

                    return (
                      <div key={idx} className="py-4 space-y-3">
                        <div className="flex items-start justify-between gap-4 text-xs">
                          <div className="min-w-0 flex-1">
                            {productId ? (
                              <Link
                                to={`/products/${productId}`}
                                className="font-semibold text-sm text-foreground hover:text-primary transition-colors block store-text-link"
                              >
                                {item.productName}
                              </Link>
                            ) : (
                              <span className="font-semibold text-sm text-foreground">
                                {item.productName}
                              </span>
                            )}
                            <p className="text-muted-foreground mt-1">
                              Phân loại: {item.color || "Màu tiêu chuẩn"}{" "}
                              {item.size ? `· Size ${item.size}` : ""} ×{" "}
                              {item.quantity}
                            </p>
                            <p className="text-foreground font-semibold mt-1">
                              Đơn giá: {money(item.unitPrice)}
                            </p>
                          </div>
                          <span className="font-semibold text-sm text-foreground shrink-0">
                            {money(item.lineTotal)}
                          </span>
                        </div>

                        {/* Inline Review Component */}
                        {canReview && (
                          <div className="pt-2">
                            <OrderItemReview
                              productId={productId}
                              orderId={id}
                              productName={item.productName}
                            />
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </section>
            </div>

            {/* Right Column: Actions & Financials */}
            <aside className="space-y-6">
              {/* Payment & Status Actions */}
              <section className="border border-border p-4 sm:p-5 bg-white space-y-4 rounded-2xl">
                <h2 className="text-xs font-semibold text-foreground pb-3 border-b border-border text-balance">
                  Thao Tác Đơn Hàng
                </h2>

                {/* Cancel Action */}
                {order.status === "PENDING" && (
                  <div>
                    <button
                      type="button"
                      disabled={busyAction}
                      onClick={() => void handleCancelOrder()}
                      className="w-full border border-destructive/20 bg-primary-light text-destructive py-3 text-xs font-semibold hover:bg-primary-light disabled:opacity-50 store-button"
                    >
                      {busyAction ? "Đang Xử Lý…" : "Yêu Cầu Hủy Đơn Hàng"}
                    </button>
                    <p className="text-xs text-muted-foreground mt-1 text-center">
                      * Chỉ áp dụng khi đơn hàng ở trạng thái Chờ xử lý.
                    </p>
                  </div>
                )}

                {/* Resume Online Payment */}
                {order.paymentMethod === "ONLINE" &&
                  (payment?.status === "PENDING" ||
                    order.status === "PENDING") && (
                    <div>
                      <button
                        type="button"
                        disabled={busyAction}
                        onClick={() => void handleOnlinePayment()}
                        className="w-full bg-primary text-white py-3.5 text-xs font-semibold hover:bg-primary-hover disabled:opacity-50 store-button"
                      >
                        Thanh Toán Trực Tuyến Ngay
                      </button>
                      <p className="text-xs text-muted-foreground mt-1 text-center">
                        Mở cổng VNPay/PayOS để hoàn tất giao dịch.
                      </p>
                    </div>
                  )}

                {/* Return Request Button */}
                {order.status === "DELIVERED" && (
                  <div>
                    <Link
                      to={`/profile/orders/${id}/return`}
                      className="w-full border border-border-strong text-foreground py-3 text-xs font-semibold hover:bg-primary-hover hover:text-white block text-center transition-colors store-action"
                    >
                      {returnRequest
                        ? "Xem Yêu Cầu Trả Hàng"
                        : "Gửi Yêu Cầu Trả Hàng (7 Ngày)"}
                    </Link>
                  </div>
                )}

                {returnRequest && (
                  <div className="p-3 bg-background border border-border text-xs space-y-1">
                    <p>
                      <strong>Trạng thái trả hàng:</strong>{" "}
                      <span className="font-semibold text-primary">
                        {({ PENDING: "Đang xem xét", APPROVED: "Đã chấp nhận", REJECTED: "Chưa được chấp nhận" } as Record<string, string>)[returnRequest.status] || "Đang cập nhật"}
                      </span>
                    </p>
                    <p className="text-muted-foreground">
                      Lý do: {returnRequest.reason}
                    </p>
                    {returnRequest.rejectReason && (
                      <p className="text-destructive">
                        Từ chối: {returnRequest.rejectReason}
                      </p>
                    )}
                  </div>
                )}
              </section>

              {/* Delivery info */}
              <section className="border border-border p-4 sm:p-5 bg-white text-xs space-y-2 rounded-2xl">
                <h2 className="text-xs font-semibold text-foreground pb-3 border-b border-border flex items-center gap-1.5 text-balance">
                  <MapPin size={14} /> Thông Tin Nhận Hàng
                </h2>
                <p>
                  <strong>Người nhận:</strong> {order.recipientName}
                </p>
                <p>
                  <strong>Số điện thoại:</strong> {order.recipientPhone}
                </p>
                <p className="text-muted-foreground leading-relaxed pt-1">
                  <strong>Địa chỉ:</strong> {order.shippingAddress}
                </p>
              </section>

              {/* Payment Summary */}
              <section className="border border-border p-4 sm:p-5 bg-white space-y-3 text-xs rounded-2xl">
                <h2 className="text-xs font-semibold text-foreground pb-3 border-b border-border flex items-center gap-1.5 text-balance">
                  <CreditCard size={14} /> Thanh Toán & Chi Phí
                </h2>

                <div className="flex justify-between text-muted-foreground">
                  <span>Phương thức:</span>
                  <strong className="text-foreground">
                    {order.paymentMethod === "COD" ? "Thanh toán khi nhận hàng" : `Thanh toán trực tuyến (${({ VNPAY: "VNPay", PAYOS: "PayOS" } as Record<string, string>)[order.paymentProvider] || "Cổng thanh toán"})`}
                  </strong>
                </div>

                <div className="flex justify-between text-muted-foreground">
                  <span>Trạng thái thanh toán:</span>
                  <strong
                    className={
                      payment?.status === "COMPLETED"
                        ? "text-success uppercase font-bold"
                        : payment?.status === "FAILED"
                          ? "text-destructive uppercase font-bold"
                          : "text-amber-700 uppercase font-bold"
                    }
                  >
                    {payment?.status
                      ? ({ COD_PENDING: "Thanh toán khi nhận hàng", PENDING: "Chờ thanh toán", COMPLETED: "Đã thanh toán", FAILED: "Thanh toán thất bại", CANCELLED: "Đã hủy", REFUND_PENDING: "Đang hoàn tiền", REFUNDED: "Đã hoàn tiền", REFUND_FAILED: "Hoàn tiền thất bại" } as Record<string, string>)[payment.status] || "Đang cập nhật"
                      : paymentError ? "Chưa tải được thông tin"
                        : order.status === "CANCELLED" ? "Đơn hàng đã hủy"
                          : paymentLoading ? "Đang cập nhật thanh toán…" : "Chờ cập nhật"}
                  </strong>
                </div>

                {paymentError && (
                  <div role="alert" className="text-destructive space-y-2">
                    <p>{paymentError}</p>
                    <button type="button" onClick={retryResources} className="underline store-button">Thử lại</button>
                  </div>
                )}

                <div className="pt-2 border-t border-border space-y-1.5">
                  <div className="flex justify-between text-muted-foreground">
                    <span>Tạm tính tiền hàng:</span>
                    <span>{money(order.subtotalAmount)}</span>
                  </div>
                  <div className="flex justify-between text-muted-foreground">
                    <span>Giảm giá:</span>
                    <span className="text-success">
                      −{money(order.discountAmount)}
                    </span>
                  </div>
                  <div className="flex justify-between text-muted-foreground">
                    <span>Phí giao hàng GHN:</span>
                    <span>{money(order.shippingFee)}</span>
                  </div>
                </div>

                <div className="pt-3 border-t border-border flex justify-between items-baseline text-sm">
                  <span className="font-semibold text-foreground">
                    Tổng cộng:
                  </span>
                  <span className="text-xl font-semibold text-foreground">
                    {money(order.totalAmount)}
                  </span>
                </div>
              </section>
            </aside>
          </div>
        )}
      </div>
    </ProfileLayout>
  );
}

function OrderItemReview({
  productId,
  orderId,
  productName,
}: {
  productId: string;
  orderId: string;
  productName: string;
}) {
  const [open, setOpen] = useState(false);
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState("");
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<string>("");

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!comment.trim()) return;
    setBusy(true);
    setFeedback("");
    try {
      await store.addReview(productId, orderId, rating, comment.trim());
      setFeedback("Cảm ơn bạn! Đánh giá đã được gửi thành công.");
      setTimeout(() => setOpen(false), 2500);
    } catch (err) {
      setFeedback(customerError(err) || "Gửi đánh giá không thành công.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="border border-border p-3 bg-background">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="text-xs font-semibold text-foreground hover:text-primary flex items-center gap-1.5 store-button"
      >
        <Star size={13} /> {open ? "Đóng đánh giá" : "Đánh giá sản phẩm này"}
      </button>

      {open && (
        <form onSubmit={handleSubmit} className="mt-3 space-y-3">
          <div className="flex items-center gap-2">
            <span className="text-xs text-muted-foreground">
              Mức độ hài lòng:
            </span>
            <div className="flex items-center gap-1">
              {[1, 2, 3, 4, 5].map((star) => (
                <button
                  key={star}
                  type="button"
                  aria-label={`Đánh giá ${star} sao`}
                  aria-pressed={rating === star}
                  onClick={() => setRating(star)}
                  className="p-1 hover:scale-110 transition-transform store-button"
                >
                  <Star
                    size={16}
                    className={
                      star <= rating
                        ? "fill-amber-400 stroke-amber-400"
                        : "fill-transparent stroke-[#bbb]"
                    }
                  />
                </button>
              ))}
            </div>
            <span className="text-xs font-semibold text-foreground ml-1">
              {rating} sao
            </span>
          </div>

          <textarea
            aria-label="Nội dung đánh giá sản phẩm"
            required
            rows={3}
            maxLength={1000}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder="Chia sẻ cảm nhận chân thực về phom dáng, chất vải và sự vừa vặn…"
            className="w-full border border-border-strong bg-white p-2.5 text-xs outline-none focus:border-border-strong store-textarea"
          />

          <div className="flex items-center justify-between">
            <span className="text-xs text-muted-foreground">
              {comment.length}/1000 ký tự
            </span>
            <button
              type="submit"
              disabled={busy || !comment.trim()}
              className="bg-primary text-white px-4 py-2 text-xs font-semibold hover:bg-primary-hover disabled:opacity-50 store-button"
            >
              {busy ? "Đang gửi…" : "Gửi Đánh Giá"}
            </button>
          </div>

          {feedback && (
            <p className="text-xs text-success font-medium pt-1">
              {feedback}
            </p>
          )}
        </form>
      )}
    </div>
  );
}
