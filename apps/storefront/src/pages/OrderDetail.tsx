import { useState, useEffect } from 'react';
import { Link, useParams } from 'react-router';
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
} from 'lucide-react';
import { store } from '../api/store';
import { money } from '../api/client';
import type { Order, OrderStatus, Payment, ReturnRequest } from '../api/types';
import { ORDER_LABEL } from '../api/types';
import ProfileLayout from '../components/ProfileLayout';
import { PageTitle, Status, StatusBadge, useLoad } from '../components/StoreUI';

export default function OrderDetail() {
  const { id = '' } = useParams();

  const orderLoad = useLoad(() => store.order(id), [id]);
  const historyLoad = useLoad(() => store.orderHistory(id), [id]);

  const [payment, setPayment] = useState<Payment | null>(null);
  const [shipment, setShipment] = useState<{ trackingCode: string; status: string; provider: string } | null>(null);
  const [returnRequest, setReturnRequest] = useState<ReturnRequest | null>(null);
  const [productIds, setProductIds] = useState<Record<string, string>>({});

  const [copiedTracking, setCopiedTracking] = useState(false);
  const [busyAction, setBusyAction] = useState(false);
  const [actionNotice, setActionNotice] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const order = orderLoad.data;

  // Load payment, shipment, returnRequest
  useEffect(() => {
    if (!order) return;
    let active = true;

    store
      .payment(id)
      .then((p) => {
        if (active) setPayment(p);
      })
      .catch(() => {});

    store
      .orderShipment(id)
      .then((s) => {
        if (active) setShipment(s);
      })
      .catch(() => {});

    if (['DELIVERED', 'RETURNED', 'REFUNDED'].includes(order.status)) {
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
  }, [id, order?.status]);

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
    if (order?.status !== 'PENDING') return;
    let attempts = 0;
    const interval = window.setInterval(() => {
      if (!document.hidden && attempts++ < 30) {
        void orderLoad.refresh();
        if (order?.paymentMethod === 'ONLINE') {
          void store.payment(id).then(setPayment).catch(() => {});
        }
      }
    }, 5000);
    return () => window.clearInterval(interval);
  }, [order?.status, id]);

  const copyTracking = () => {
    if (!shipment?.trackingCode) return;
    navigator.clipboard.writeText(shipment.trackingCode);
    setCopiedTracking(true);
    setTimeout(() => setCopiedTracking(false), 2000);
  };

  async function handleCancelOrder() {
    if (!window.confirm('Bạn có chắc chắn muốn gửi yêu cầu hủy đơn hàng này không?')) return;
    setBusyAction(true);
    setActionNotice(null);
    try {
      await store.orderCancel(id, 'Khách hàng yêu cầu hủy đơn từ trang tài khoản');
      setActionNotice({
        type: 'success',
        text: 'Đã gửi yêu cầu hủy đơn. Hệ thống đang tiến hành xử lý bù trừ.',
      });
      await orderLoad.refresh();
    } catch (e) {
      setActionNotice({
        type: 'error',
        text: (e as Error).message || 'Không thể hủy đơn hàng vào lúc này.',
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
      if (!payUrl && payment) {
        const res = await store.paymentInitiate(payment.id);
        payUrl = res.paymentUrl;
      }
      if (payUrl) {
        sessionStorage.setItem('lino:pendingOrder', id);
        window.location.assign(payUrl);
      } else {
        setActionNotice({
          type: 'error',
          text: 'Cổng thanh toán chưa sẵn sàng cấp URL giao dịch. Vui lòng thử lại sau ít phút.',
        });
      }
    } catch (e) {
      setActionNotice({ type: 'error', text: (e as Error).message });
    } finally {
      setBusyAction(false);
    }
  }

  return (
    <ProfileLayout>
      <div style={{ fontFamily: "'Inter', sans-serif" }} className="w-full">
        {/* Navigation & Header */}
        <div className="mb-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-[rgba(0,0,0,0.08)]">
          <div>
            <Link
              to="/profile/orders"
              className="text-xs uppercase tracking-widest text-[#888] hover:text-[#111] font-semibold mb-2 inline-block"
            >
              ← Quay Lại Danh Sách Đơn Hàng
            </Link>
            <h1
              className="text-[#111] font-black uppercase text-2xl md:text-3xl leading-none"
              style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
            >
              ĐƠN HÀNG: {order?.orderCode || id}
            </h1>
            {order && (
              <p className="text-xs text-[#777] mt-1.5">
                Ngày đặt: {new Date(order.createdAt).toLocaleString('vi-VN')}
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
              actionNotice.type === 'success'
                ? 'bg-emerald-50 text-emerald-900 border-emerald-200'
                : 'bg-red-50 text-red-900 border-red-200'
            }`}
          >
            {actionNotice.type === 'success' ? (
              <Check size={16} className="text-emerald-700" />
            ) : (
              <AlertCircle size={16} className="text-red-700" />
            )}
            <span>{actionNotice.text}</span>
          </div>
        )}

        {order && (
          <div className="grid grid-cols-1 lg:grid-cols-[1fr_360px] gap-10 items-start">
            {/* Left Column: Timeline, Shipment, Items */}
            <div className="space-y-8">
              {/* 1. Timeline & Shipment */}
              <section className="border border-[rgba(0,0,0,0.08)] p-6 bg-white space-y-6">
                <div className="flex items-center justify-between pb-3 border-b border-[rgba(0,0,0,0.06)]">
                  <h2 className="text-xs uppercase font-bold tracking-widest text-[#111] flex items-center gap-2">
                    <Truck size={16} /> Lịch Trình & Vận Chuyển
                  </h2>
                  {shipment && (
                    <span className="text-xs font-semibold text-[#2D5A3D]">
                      {shipment.provider}: {shipment.status}
                    </span>
                  )}
                </div>

                {shipment?.trackingCode && (
                  <div className="p-3 bg-[#fafafa] border border-[rgba(0,0,0,0.06)] flex items-center justify-between gap-3 text-xs">
                    <div>
                      <span className="text-[#888] mr-2">Mã vận đơn GHN:</span>
                      <strong className="text-[#111] font-mono text-sm">
                        {shipment.trackingCode}
                      </strong>
                    </div>
                    <button
                      type="button"
                      onClick={copyTracking}
                      className="text-xs font-semibold text-[#111] hover:text-[#E5001B] inline-flex items-center gap-1 uppercase tracking-wider"
                    >
                      {copiedTracking ? (
                        <>
                          <Check size={13} className="text-emerald-600" /> Đã chép
                        </>
                      ) : (
                        <>
                          <Copy size={13} /> Sao chép
                        </>
                      )}
                    </button>
                  </div>
                )}

                {/* History Timeline */}
                <div className="space-y-4 pt-2">
                  {historyLoad.data && historyLoad.data.length > 0 ? (
                    <div className="relative pl-6 space-y-6 before:absolute before:left-2 before:top-2 before:bottom-2 before:w-0.5 before:bg-[#e5e5e5]">
                      {historyLoad.data.map((h, i) => (
                        <div key={h.id} className="relative text-xs">
                          <span className="absolute -left-6 top-0.5 w-3 h-3 rounded-full bg-[#111] border-2 border-white" />
                          <div className="flex flex-col sm:flex-row sm:items-baseline justify-between gap-1">
                            <strong className="text-[#111] uppercase font-bold">
                              {ORDER_LABEL[h.toStatus as OrderStatus] || h.toStatus}
                            </strong>
                            <span className="text-[11px] text-[#888]">
                              {new Date(h.createdAt).toLocaleString('vi-VN')}
                            </span>
                          </div>
                          {h.reason && <p className="text-[#666] mt-0.5">{h.reason}</p>}
                        </div>
                      ))}
                    </div>
                  ) : (
                    <p className="text-xs text-[#888]">Đang cập nhật lịch trình đơn hàng…</p>
                  )}
                </div>

                {order.cancelReason && (
                  <div className="p-3 bg-red-50 text-xs text-[#B00018] border border-red-200">
                    <strong>Lý do hủy đơn:</strong> {order.cancelReason}
                  </div>
                )}
              </section>

              {/* 2. Order Products */}
              <section className="border border-[rgba(0,0,0,0.08)] p-6 bg-white">
                <h2 className="text-xs uppercase font-bold tracking-widest text-[#111] mb-6 flex items-center gap-2 pb-3 border-b border-[rgba(0,0,0,0.06)]">
                  <Package size={16} /> Danh Sách Sản Phẩm ({order.items.length})
                </h2>

                <div className="divide-y divide-[rgba(0,0,0,0.06)]">
                  {order.items.map((item, idx) => {
                    const productId = productIds[item.variantId];
                    const canReview = ['DELIVERED', 'COMPLETED'].includes(order.status) && productId;

                    return (
                      <div key={idx} className="py-4 space-y-3">
                        <div className="flex items-start justify-between gap-4 text-xs">
                          <div className="min-w-0 flex-1">
                            {productId ? (
                              <Link
                                to={`/products/${productId}`}
                                className="font-semibold text-sm text-[#111] hover:text-[#E5001B] transition-colors block"
                              >
                                {item.productName}
                              </Link>
                            ) : (
                              <span className="font-semibold text-sm text-[#111]">
                                {item.productName}
                              </span>
                            )}
                            <p className="text-[#777] mt-1">
                              Phân loại: {item.color || 'Màu tiêu chuẩn'}{' '}
                              {item.size ? `· Size ${item.size}` : ''} × {item.quantity}
                            </p>
                            <p className="text-[#111] font-semibold mt-1">
                              Đơn giá: {money(item.unitPrice)}
                            </p>
                          </div>
                          <span className="font-bold text-sm text-[#111] shrink-0">
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
              <section className="border border-[rgba(0,0,0,0.08)] p-6 bg-white space-y-4">
                <h2 className="text-xs uppercase font-bold tracking-widest text-[#111] pb-3 border-b border-[rgba(0,0,0,0.06)]">
                  Thao Tác Đơn Hàng
                </h2>

                {/* Cancel Action */}
                {order.status === 'PENDING' && (
                  <div>
                    <button
                      type="button"
                      disabled={busyAction}
                      onClick={() => void handleCancelOrder()}
                      className="w-full border border-rose-300 bg-rose-50 text-rose-800 py-3 text-xs uppercase tracking-widest font-semibold hover:bg-rose-100 disabled:opacity-50"
                    >
                      {busyAction ? 'Đang Xử Lý…' : 'Yêu Cầu Hủy Đơn Hàng'}
                    </button>
                    <p className="text-[11px] text-[#888] mt-1 text-center">
                      * Chỉ áp dụng khi đơn hàng ở trạng thái Chờ xử lý.
                    </p>
                  </div>
                )}

                {/* Resume Online Payment */}
                {order.paymentMethod === 'ONLINE' &&
                  (payment?.status === 'PENDING' || order.status === 'PENDING') && (
                    <div>
                      <button
                        type="button"
                        disabled={busyAction}
                        onClick={() => void handleOnlinePayment()}
                        className="w-full bg-[#111] text-white py-3.5 text-xs uppercase tracking-widest font-bold hover:bg-[#E5001B] disabled:opacity-50"
                      >
                        Thanh Toán Trực Tuyến Ngay
                      </button>
                      <p className="text-[11px] text-[#888] mt-1 text-center">
                        Mở cổng VNPay/PayOS để hoàn tất giao dịch.
                      </p>
                    </div>
                  )}

                {/* Return Request Button */}
                {order.status === 'DELIVERED' && (
                  <div>
                    <Link
                      to={`/profile/orders/${id}/return`}
                      className="w-full border border-[#111] text-[#111] py-3 text-xs uppercase tracking-widest font-bold hover:bg-[#111] hover:text-white block text-center transition-colors"
                    >
                      {returnRequest ? 'Xem Yêu Cầu Trả Hàng' : 'Gửi Yêu Cầu Trả Hàng (7 Ngày)'}
                    </Link>
                  </div>
                )}

                {returnRequest && (
                  <div className="p-3 bg-[#fafafa] border border-[rgba(0,0,0,0.06)] text-xs space-y-1">
                    <p>
                      <strong>Trạng thái trả hàng:</strong>{' '}
                      <span className="uppercase font-bold text-[#E5001B]">
                        {returnRequest.status}
                      </span>
                    </p>
                    <p className="text-[#666]">Lý do: {returnRequest.reason}</p>
                    {returnRequest.rejectReason && (
                      <p className="text-[#B00018]">
                        Từ chối: {returnRequest.rejectReason}
                      </p>
                    )}
                  </div>
                )}
              </section>

              {/* Delivery info */}
              <section className="border border-[rgba(0,0,0,0.08)] p-6 bg-white text-xs space-y-2">
                <h2 className="text-xs uppercase font-bold tracking-widest text-[#111] pb-3 border-b border-[rgba(0,0,0,0.06)] flex items-center gap-1.5">
                  <MapPin size={14} /> Thông Tin Nhận Hàng
                </h2>
                <p>
                  <strong>Người nhận:</strong> {order.recipientName}
                </p>
                <p>
                  <strong>Số điện thoại:</strong> {order.recipientPhone}
                </p>
                <p className="text-[#555] leading-relaxed pt-1">
                  <strong>Địa chỉ:</strong> {order.shippingAddress}
                </p>
              </section>

              {/* Payment Summary */}
              <section className="border border-[rgba(0,0,0,0.08)] p-6 bg-white space-y-3 text-xs">
                <h2 className="text-xs uppercase font-bold tracking-widest text-[#111] pb-3 border-b border-[rgba(0,0,0,0.06)] flex items-center gap-1.5">
                  <CreditCard size={14} /> Thanh Toán & Chi Phí
                </h2>

                <div className="flex justify-between text-[#666]">
                  <span>Phương thức:</span>
                  <strong className="text-[#111] uppercase">
                    {order.paymentMethod} ({order.paymentProvider})
                  </strong>
                </div>

                <div className="flex justify-between text-[#666]">
                  <span>Trạng thái thanh toán:</span>
                  <strong
                    className={
                      payment?.status === 'COMPLETED'
                        ? 'text-emerald-700 uppercase font-bold'
                        : payment?.status === 'FAILED'
                          ? 'text-rose-700 uppercase font-bold'
                          : 'text-amber-700 uppercase font-bold'
                    }
                  >
                    {payment?.status || 'Chờ cập nhật'}
                  </strong>
                </div>

                <div className="pt-2 border-t border-[rgba(0,0,0,0.06)] space-y-1.5">
                  <div className="flex justify-between text-[#555]">
                    <span>Tạm tính tiền hàng:</span>
                    <span>{money(order.subtotalAmount)}</span>
                  </div>
                  <div className="flex justify-between text-[#555]">
                    <span>Giảm giá:</span>
                    <span className="text-[#2D5A3D]">−{money(order.discountAmount)}</span>
                  </div>
                  <div className="flex justify-between text-[#555]">
                    <span>Phí giao hàng GHN:</span>
                    <span>{money(order.shippingFee)}</span>
                  </div>
                </div>

                <div className="pt-3 border-t border-[rgba(0,0,0,0.08)] flex justify-between items-baseline text-sm">
                  <span className="font-bold uppercase tracking-wider text-[#111]">Tổng cộng:</span>
                  <span className="text-xl font-black text-[#111]">{money(order.totalAmount)}</span>
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
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<string>('');

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!comment.trim()) return;
    setBusy(true);
    setFeedback('');
    try {
      await store.addReview(productId, orderId, rating, comment.trim());
      setFeedback('Cảm ơn bạn! Đánh giá đã được gửi thành công.');
      setTimeout(() => setOpen(false), 2500);
    } catch (err) {
      setFeedback((err as Error).message || 'Gửi đánh giá không thành công.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="border border-[rgba(0,0,0,0.06)] p-3 bg-[#fafafa]">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="text-xs uppercase tracking-wider font-semibold text-[#111] hover:text-[#E5001B] flex items-center gap-1.5"
      >
        <Star size={13} /> {open ? 'Đóng đánh giá' : 'Đánh giá sản phẩm này'}
      </button>

      {open && (
        <form onSubmit={handleSubmit} className="mt-3 space-y-3">
          <div className="flex items-center gap-2">
            <span className="text-xs text-[#555]">Mức độ hài lòng:</span>
            <div className="flex items-center gap-1">
              {[1, 2, 3, 4, 5].map((star) => (
                <button
                  key={star}
                  type="button"
                  onClick={() => setRating(star)}
                  className="p-1 hover:scale-110 transition-transform"
                >
                  <Star
                    size={16}
                    className={
                      star <= rating
                        ? 'fill-amber-400 stroke-amber-400'
                        : 'fill-transparent stroke-[#bbb]'
                    }
                  />
                </button>
              ))}
            </div>
            <span className="text-xs font-bold text-[#111] ml-1">{rating} sao</span>
          </div>

          <textarea
            required
            rows={3}
            maxLength={1000}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder="Chia sẻ cảm nhận chân thực về phom dáng, chất vải và sự vừa vặn…"
            className="w-full border border-[#ddd] bg-white p-2.5 text-xs outline-none focus:border-[#111]"
          />

          <div className="flex items-center justify-between">
            <span className="text-[10px] text-[#888]">{comment.length}/1000 ký tự</span>
            <button
              type="submit"
              disabled={busy || !comment.trim()}
              className="bg-[#111] text-white px-4 py-2 text-xs uppercase tracking-wider font-semibold hover:bg-[#E5001B] disabled:opacity-50"
            >
              {busy ? 'Đang gửi…' : 'Gửi Đánh Giá'}
            </button>
          </div>

          {feedback && <p className="text-xs text-emerald-700 font-medium pt-1">{feedback}</p>}
        </form>
      )}
    </div>
  );
}
