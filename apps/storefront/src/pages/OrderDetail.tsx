import { customerError } from "../api/client";
import { useState, useEffect } from "react";
import { Link, useLocation, useParams } from "react-router";
import { AlertCircle, ArrowLeft, Check, Copy, Star } from "lucide-react";
import { store } from "../api/store";
import { walletCheckoutPath } from "../api/wallet-payment";
import { canPayOnline, paymentDeadline, paymentLinkMessage, requestPaymentUrl } from "../api/payment-link";
import { money } from "../api/client";
import { loadOrderPayment, loadOrderShipment } from "../api/order-resources";
import type { Order, Payment, ReturnRequest } from "../api/types";
import OrderActivityTimeline from "../components/OrderActivityTimeline";
import ProfileLayout from "../components/ProfileLayout";
import { Status, StatusBadge, useLoad } from "../components/StoreUI";
import ConfirmReceiptDialog from "../components/orders/ConfirmReceiptDialog";
import OrderCancellationActions from "../components/orders/OrderCancellationActions";
import { confirmReceipt,loadReturnRequest,RETURN_LABEL } from "../api/return-actions";

import OrderProgress from "../components/orders/OrderProgress";
import OrderProducts from "../components/orders/OrderProducts";
import { DetailRow } from "../components/orders/OrderFacts";
import { detailDate, returnPresentation } from "../api/order-presentation";
import { ORDER_LABEL } from "../api/types";
import "../styles/order-detail.css";

export default function OrderDetail() {
  const { id = "" } = useParams();
  // Checkout chuyển sang đây kèm lý do khi không mở được cổng thanh toán.
  const checkoutPaymentError = (useLocation().state as { paymentError?: string } | null)?.paymentError;

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

  const [copiedTracking, setCopiedTracking] = useState(false);
  const [busyAction, setBusyAction] = useState(false);
  const [cancelRequested, setCancelRequested] = useState(false);
  const [actionNotice, setActionNotice] = useState<{
    type: "success" | "error";
    text: string;
  } | null>(checkoutPaymentError ? { type: "error", text: checkoutPaymentError } : null);

  const order = orderLoad.data;
  const [receiptOpen,setReceiptOpen]=useState(false);
  const [receiptBusy,setReceiptBusy]=useState(false);
  const [receiptError,setReceiptError]=useState('');
  const [returnError,setReturnError]=useState('');
  useEffect(()=>{if(!order?.returnEligibleUntil)return;const remaining=Date.parse(order.returnEligibleUntil)-Date.now();
    if(remaining<=0)return;const timer=setTimeout(()=>void orderLoad.refresh(),Math.min(remaining+250,2147483647));return()=>clearTimeout(timer);
  },[order?.returnEligibleUntil]);
  async function handleConfirmReceipt() {
    if(!order?.canConfirmReceipt || order.activeReturnId || receiptBusy)return;
    setReceiptBusy(true);setReceiptError('');
    try {const updated=await confirmReceipt(id);orderLoad.setData(updated);setReceiptOpen(false);void historyLoad.refresh();}
    catch(error){setReceiptError(customerError(error));void orderLoad.refresh();}
    finally{setReceiptBusy(false);}
  }

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
    // Đơn PENDING chưa có paymentId/paymentUrl cho tới khi khách trả tiền hoặc mở link, nên luôn
    // tải payment; loadOrderPayment tự chờ khi payment chưa được tạo.
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
  }, [id, order?.id, order?.status, resourceRetry]);

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

    if (order.activeReturnId || ["DELIVERED", "COMPLETED", "RETURNED", "REFUNDED"].includes(order.status)) {
      loadReturnRequest(() => store.returnRequest(id))
        .then((r) => {
          if (active) setReturnRequest(r);
        })
        .catch(error => {if(active)setReturnError(customerError(error));});
    }

    return () => {
      active = false;
    };
  }, [id, order?.id, order?.status, order?.activeReturnId]);

  // Auto-polling for PENDING orders
  useEffect(() => {
    if (order?.status !== "PENDING" || order.id !== id) return;
    let attempts = 0;
    const interval = window.setInterval(() => {
      if (!document.hidden && attempts++ < 30) {
        void orderLoad.refresh();
      }
      if (attempts >= 30) window.clearInterval(interval);
    }, 5000);
    return () => window.clearInterval(interval);
  }, [order?.id, order?.status, id, resourceRetry]);

  // Đồng hồ hạn thanh toán cho đơn online chưa trả tiền.
  const [now, setNow] = useState(() => Date.now());
  const awaitingPayment = !!order && canPayOnline(order, payment) && !cancelRequested;
  const deadline = awaitingPayment ? paymentDeadline(payment?.authorizationExpiresAt, now) : null;
  useEffect(() => {
    if (!awaitingPayment || !payment?.authorizationExpiresAt) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [awaitingPayment, payment?.authorizationExpiresAt]);
  // Quá hạn: saga tự huỷ đơn sau vài chục giây; làm mới cho tới khi đơn đổi trạng thái.
  useEffect(() => {
    if (!deadline?.expired) return;
    const timer = window.setInterval(() => void orderLoad.refresh(), 10000);
    return () => window.clearInterval(timer);
  }, [deadline?.expired]);

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

  async function handleOnlinePayment() {
    setBusyAction(true);
    setActionNotice(null);
    try {
      const payUrl = order?.paymentUrl || await requestPaymentUrl(id, store);
      sessionStorage.setItem("lino:pendingOrder", id);
      window.location.assign(payUrl);
    } catch (e) {
      setActionNotice({ type: "error", text: paymentLinkMessage(e) || customerError(e) });
      void orderLoad.refresh();
    } finally {
      setBusyAction(false);
    }
  }

  const cancelled = order?.status === "CANCELLED";
  const delivered = order && ["DELIVERED", "COMPLETED"].includes(order.status);
  const paymentLabel = order?.paymentMethod === "COD" ? "Thanh toán khi nhận hàng (COD)"
    : order?.paymentMethod === "WALLET" ? "Ví Fashion Store" : order?.paymentProvider === "PAYOS" ? "PayOS" : "VNPay";
  const paymentStatus = payment?.status ? ({ COD_PENDING: "Thanh toán khi nhận hàng", PENDING: "Chờ thanh toán", COMPLETED: "Đã thanh toán", FAILED: "Chưa thanh toán thành công", CANCELLED: "Đã hủy", REFUNDED: "Đã hoàn tiền", REFUND_PENDING: "Đang hoàn tiền", REFUND_FAILED: "Đang kiểm tra hoàn tiền" } as Record<string, string>)[payment.status] || "Đang cập nhật" : paymentLoading ? "Đang cập nhật…" : "Chưa có thông tin";
  return <ProfileLayout flush>
    <ConfirmReceiptDialog open={receiptOpen} busy={receiptBusy} blocked={!order?.canConfirmReceipt || !!order?.activeReturnId || orderLoad.loading} error={receiptError} onOpenChange={setReceiptOpen} onConfirm={()=>void handleConfirmReceipt()}/>
    <article className="order-detail">
      <section className="order-panel">
        <header className="order-topline">
          <Link to="/profile/orders"><ArrowLeft size={14}/> Quay lại</Link>
          <div className="order-top-meta"><span>Mã đơn hàng: <strong>{order?.orderCode || id}</strong></span>{order && <StatusBadge status={order.status}/>}</div>
        </header>
        <Status loading={orderLoad.loading && !order} error={orderLoad.error} retry={()=>void orderLoad.refresh()}/>
        {order && <>
          <OrderProgress status={order.status}/>
          <div className="order-banner" data-tone={cancelled ? 'cancelled' : delivered ? 'success' : 'neutral'}>
            <h1>{cancelled ? 'Đã hủy đơn hàng' : awaitingPayment ? 'Chờ thanh toán' : ORDER_LABEL[order.status]}</h1>
            <p>{cancelled ? 'Đơn hàng này đã được hủy.' : awaitingPayment ? 'Hoàn tất thanh toán để shop xác nhận và chuẩn bị đơn hàng của bạn.' : delivered ? 'Cảm ơn bạn đã mua sắm tại LINO. Thông tin đơn hàng và các hỗ trợ sau mua được cập nhật bên dưới.' : 'Theo dõi tiến trình xử lý và thông tin vận chuyển của đơn hàng tại đây.'}</p>
            <p>Đặt lúc {detailDate(order.createdAt)}{cancelled && order.cancellation?.closedAt ? ` · Hủy lúc ${detailDate(order.cancellation.closedAt)}` : ''}</p>
          </div>
          <div className="order-actions">
            <OrderCancellationActions key={order.id} order={order} onRefresh={orderLoad.refresh} onCancelRequested={()=>{setCancelRequested(true);setActionNotice(null);}}/>
            {awaitingPayment && !deadline?.expired && <button disabled={busyAction} className="order-action order-action-primary" onClick={()=>void handleOnlinePayment()}>{busyAction ? 'Đang mở thanh toán…' : 'Thanh toán ngay'}</button>}
            {order.paymentMethod === 'WALLET' && order.status === 'PENDING' && walletCheckoutPath(order) && <Link className="order-action order-action-primary" to={walletCheckoutPath(order)!}>Thanh toán bằng ví</Link>}
            {order.canConfirmReceipt && !order.activeReturnId && <button className="order-action order-action-primary" disabled={receiptBusy || orderLoad.loading} onClick={()=>{setReceiptError('');setReceiptOpen(true);}}>Đã nhận được hàng</button>}
            {(order.canRequestReturn || order.activeReturnId || returnRequest) && <Link className="order-action" to={`/profile/orders/${id}/return`}>{order.activeReturnId || returnRequest ? 'Xem yêu cầu trả hàng / hoàn tiền' : 'Yêu cầu trả hàng / hoàn tiền'}</Link>}
            <Link className="order-action" to="/contact">Liên hệ hỗ trợ</Link>
            {(cancelled || delivered) && <Link className="order-action" to="/products">Tiếp tục mua sắm</Link>}
          </div>
          {order.canRequestReturn && order.returnEligibleUntil && <p className="px-6 pb-4 text-xs text-muted-foreground">Hạn yêu cầu trả hàng: {detailDate(order.returnEligibleUntil)}</p>}
          {deadline && <p role="timer" className="px-6 pb-4 text-sm text-muted-foreground">{deadline.expired
            ? 'Đã quá hạn thanh toán. Đơn hàng đang được hủy tự động.'
            : <>Vui lòng thanh toán trong <strong className="text-foreground tabular-nums">{deadline.remaining}</strong> (trước {detailDate(payment!.authorizationExpiresAt!)}). Quá thời hạn, đơn hàng sẽ tự hủy.</>}</p>}
          {actionNotice && <p role="status" className="px-6 pb-4 text-sm text-destructive">{actionNotice.text}</p>}
          {returnError && <p role="alert" className="px-6 pb-4 text-sm text-destructive">{returnError}</p>}
          {returnRequest && <Link to={`/profile/orders/${id}/return`} className="block border-t border-border px-6 py-4 text-sm">Yêu cầu trả hàng: <strong>{RETURN_LABEL[returnRequest.status] || 'Đang cập nhật'}</strong> →</Link>}
        </>}
      </section>
      {order && <>
        {!cancelled && <section className="order-panel order-delivery">
          <div className="order-address"><h2>Địa chỉ nhận hàng</h2><strong>{order.recipientName}</strong><p>{order.recipientPhone}</p><p>{order.shippingAddress}</p></div>
          <div className="order-delivery-history">
            <div className="order-tracking"><strong>{shipment?.provider || order.shippingProvider || 'Vận chuyển'}</strong>{(shipment?.trackingCode || order.trackingCode) && <button onClick={copyTracking} aria-label="Sao chép mã vận đơn">{shipment?.trackingCode || order.trackingCode}{copiedTracking ? <Check size={14}/> : <Copy size={14}/>}</button>}</div>
            {shipmentError ? <p role="alert" className="mb-3 text-xs text-destructive">{shipmentError} <button onClick={retryResources} className="underline">Thử lại</button></p> : shipmentLoading ? <p className="mb-3 text-xs text-muted-foreground">Đang tải vận chuyển…</p> : !shipment && !order.trackingCode ? <p className="mb-3 text-xs text-muted-foreground">Chưa có thông tin vận đơn.</p> : null}
            <OrderActivityTimeline key={id} history={historyLoad.data} loading={historyLoad.loading} error={historyLoad.error} retry={()=>void historyLoad.refresh()}/>
          </div>
        </section>}
        <section className="order-panel">
          <OrderProducts order={order} returnLabel={returnRequest ? returnPresentation(returnRequest).title : undefined} refunded={returnRequest?.refundStatus === 'COMPLETED'} review={delivered ? (productId, name)=><OrderItemReview productId={productId} orderId={id} productName={name}/> : undefined}/>
          <dl className="order-facts">
            {!cancelled && <><DetailRow label="Tổng tiền hàng">{money(order.subtotalAmount)}</DetailRow><DetailRow label="Phí vận chuyển">{money(order.shippingFee)}</DetailRow><DetailRow label="Giảm giá">−{money(order.discountAmount)}</DetailRow><DetailRow label="Thành tiền" total>{money(order.totalAmount)}</DetailRow></>}
            <DetailRow label="Phương thức thanh toán">{paymentLabel}</DetailRow>
            {!cancelled && <DetailRow label="Trạng thái thanh toán">{paymentStatus}</DetailRow>}
            <DetailRow label="Mã đơn hàng">{order.orderCode}</DetailRow>
          </dl>
          {paymentError && !cancelled && <p role="alert" className="px-6 py-4 text-xs text-destructive">{paymentError} <button onClick={retryResources} className="underline">Thử lại</button></p>}
        </section>
        {cancelled && <section className="order-panel order-reason"><h2>Lý do hủy đơn</h2><p>{order.cancelReason || order.cancellation?.reason || 'Chưa có thông tin lý do hủy.'}</p></section>}
      </>}
    </article>
  </ProfileLayout>;
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
