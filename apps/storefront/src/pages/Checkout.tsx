import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router';
import {
  Truck,
  CreditCard,
  QrCode,
  ShieldCheck,
  AlertCircle,
  Check,
  Lock,
  ArrowRight,
  ExternalLink,
  MapPin,
  Tag,
} from 'lucide-react';
import { store } from '../api/store';
import { money } from '../api/client';
import type { Address, Checkout } from '../api/types';
import { Status, useLoad } from '../components/StoreUI';
import { useCart } from '../context/CartContext';
import { useAuth } from '../context/AuthContext';

export default function CheckoutPage() {
  const { cart, refresh: refreshCart } = useCart();
  const { user } = useAuth();
  const navigate = useNavigate();

  const addressesLoad = useLoad(store.addresses, []);

  const [addressId, setAddressId] = useState<string>('');
  const [shippingMethod, setShippingMethod] = useState<'STANDARD' | 'EXPRESS'>('STANDARD');
  const [paymentProvider, setPaymentProvider] = useState<'COD' | 'VNPAY' | 'PAYOS'>('COD');
  const [couponCode, setCouponCode] = useState<string>('');
  const [checkout, setCheckout] = useState<Checkout | null>(null);

  const [busy, setBusy] = useState<boolean>(false);
  const [error, setError] = useState<string>('');

  // Resume active checkout if available in session
  useEffect(() => {
    const savedCheckoutId = sessionStorage.getItem('lino:checkoutId');
    if (!savedCheckoutId) return;

    let active = true;
    store
      .checkout(savedCheckoutId)
      .then((data) => {
        if (!active) return;
        if (data.orderId) {
          sessionStorage.removeItem('lino:checkoutId');
          navigate(`/profile/orders/${data.orderId}`, { replace: true });
        } else if (data.status === 'SUBMITTED' || data.status === 'PENDING') {
          setCheckout(data);
          if (data.addressId) setAddressId(data.addressId);
          setShippingMethod(data.shippingMethod);
          setPaymentProvider(data.paymentProvider);
          if (data.couponCode) setCouponCode(data.couponCode);
        } else {
          sessionStorage.removeItem('lino:checkoutId');
        }
      })
      .catch(() => {
        sessionStorage.removeItem('lino:checkoutId');
      });

    return () => {
      active = false;
    };
  }, [navigate]);

  // Set default address
  useEffect(() => {
    if (!addressId && addressesLoad.data && addressesLoad.data.length > 0) {
      const def = addressesLoad.data.find((a) => a.isDefault) || addressesLoad.data[0];
      setAddressId(def.id);
    }
  }, [addressesLoad.data, addressId]);

  const selectedAddress = addressesLoad.data?.find((a) => a.id === addressId);
  const paymentMethod = paymentProvider === 'COD' ? 'COD' : 'ONLINE';

  function invalidateCheckoutSnapshot() {
    setCheckout(null);
    sessionStorage.removeItem('lino:checkoutId');
  }

  // Create checkout preview snapshot with backend
  async function handlePreviewCheckout() {
    if (!addressId) {
      setError('Vui lòng chọn địa chỉ giao hàng.');
      return;
    }
    if (selectedAddress && (!selectedAddress.provinceId || !selectedAddress.wardId)) {
      setError('Địa chỉ cần chọn lại tỉnh và phường/xã theo danh mục GHN mới.');
      return;
    }

    setBusy(true);
    setError('');
    try {
      const result = await store.checkoutCreate({
        addressId,
        shippingMethod,
        paymentMethod,
        paymentProvider,
        couponCode: couponCode.trim() || undefined,
      });
      setCheckout(result);
      sessionStorage.setItem('lino:checkoutId', result.id);
      await refreshCart();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  // Submit order from snapshot
  async function handlePlaceOrder() {
    if (!checkout) return;
    setBusy(true);
    setError('');
    try {
      const order = await store.orderCreate(checkout.id, addressId);
      sessionStorage.setItem('lino:pendingOrder', order.id);
      sessionStorage.removeItem('lino:checkoutId');

      if (paymentProvider === 'COD') {
        navigate(`/profile/orders/${order.id}`);
        return;
      }

      // Online payment (VNPay or PayOS)
      let payUrl = order.paymentUrl;
      if (!payUrl && order.paymentId) {
        try {
          const initRes = await store.paymentInitiate(order.paymentId);
          payUrl = initRes.paymentUrl;
        } catch {
          // fallback to order detail
        }
      }

      if (payUrl) {
        window.location.assign(payUrl);
      } else {
        navigate(`/profile/orders/${order.id}`);
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  if (!cart?.items.length && !checkout) {
    return (
      <div className="max-w-[1400px] mx-auto px-6 py-28 text-center" style={{ fontFamily: "'Inter', sans-serif" }}>
        <h2
          className="text-[#111] font-black uppercase text-3xl mb-3"
          style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
        >
          GIỎ HÀNG ĐANG TRỐNG
        </h2>
        <p className="text-sm text-[#777] mb-8">Bạn không có sản phẩm nào để thanh toán.</p>
        <Link
          to="/products"
          className="inline-block bg-[#111] text-white px-8 py-4 text-xs uppercase tracking-widest font-bold hover:bg-[#E5001B]"
        >
          Xem Sản Phẩm
        </Link>
      </div>
    );
  }

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
            <Link to="/cart" className="hover:text-[#111]">
              Giỏ Hàng
            </Link>
            <span>/</span>
            <span className="text-[#111]">Thanh Toán</span>
          </nav>
        </div>
      </div>

      <div className="max-w-[1400px] mx-auto px-6 py-10">
        <h1
          className="text-[#111] font-black uppercase text-3xl md:text-4xl leading-tight mb-8"
          style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
        >
          TIẾN HÀNH ĐẶT HÀNG & THANH TOÁN
        </h1>

        <Status
          loading={addressesLoad.loading}
          error={addressesLoad.error}
          retry={() => void addressesLoad.refresh()}
        />

        <div className="grid grid-cols-1 lg:grid-cols-[1fr_400px] gap-12 items-start">
          {/* Left Form: Customer -> Address -> Shipping -> Payment -> Coupon */}
          <div className="space-y-8">
            {/* 1. Customer Info */}
            <section className="border border-[rgba(0,0,0,0.08)] p-6 bg-white">
              <h2 className="text-xs uppercase font-bold tracking-widest text-[#111] mb-4 flex items-center gap-2">
                <span className="w-5 h-5 bg-[#111] text-white text-[10px] font-bold rounded-full flex items-center justify-center">
                  1
                </span>
                Thông Tin Khách Hàng
              </h2>
              <div className="text-sm text-[#444] space-y-1">
                <p>
                  <strong>Họ và tên:</strong> {user?.fullName || 'Khách hàng LINO'}
                </p>
                <p>
                  <strong>Email:</strong> {user?.email || 'Chưa cập nhật'}
                </p>
                {user?.phone && (
                  <p>
                    <strong>Số điện thoại:</strong> {user.phone}
                  </p>
                )}
              </div>
            </section>

            {/* 2. Delivery Address */}
            <section className="border border-[rgba(0,0,0,0.08)] p-6 bg-white">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-xs uppercase font-bold tracking-widest text-[#111] flex items-center gap-2">
                  <span className="w-5 h-5 bg-[#111] text-white text-[10px] font-bold rounded-full flex items-center justify-center">
                    2
                  </span>
                  Địa Chỉ Nhận Hàng
                </h2>
                <Link
                  to="/profile/addresses"
                  className="text-xs uppercase tracking-wider text-[#111] hover:text-[#E5001B] font-semibold underline underline-offset-4"
                >
                  Quản lý địa chỉ
                </Link>
              </div>

              {addressesLoad.data && addressesLoad.data.length > 0 ? (
                <div className="space-y-3">
                  {addressesLoad.data.map((addr) => {
                    const isSelected = addressId === addr.id;
                    const isMissingGhn = !addr.provinceId || !addr.wardId;
                    return (
                      <label
                        key={addr.id}
                        className={`flex items-start gap-3.5 p-4 border cursor-pointer transition-all ${
                          isSelected
                            ? 'border-[#111] bg-[#fafafa]'
                            : 'border-[#ddd] hover:border-[#aaa]'
                        }`}
                      >
                        <input
                          type="radio"
                          name="addressSelection"
                          checked={isSelected}
                          onChange={() => {
                            setAddressId(addr.id);
                            invalidateCheckoutSnapshot();
                          }}
                          className="mt-1 accent-[#111]"
                        />
                        <div className="text-xs flex-1">
                          <div className="flex items-center gap-2 mb-1">
                            <strong className="text-sm text-[#111]">{addr.recipientName}</strong>
                            <span className="text-[#666]">· {addr.phone}</span>
                            {addr.isDefault && (
                              <span className="text-[10px] uppercase font-bold tracking-wider text-[#2D5A3D] bg-emerald-50 px-2 py-0.5 border border-emerald-200">
                                Mặc định
                              </span>
                            )}
                          </div>
                          <p className="text-[#555] leading-relaxed">
                            {addr.fullAddress ||
                              [addr.detailAddress, addr.ward, addr.district, addr.province].filter(Boolean).join(', ')}
                          </p>
                          {isMissingGhn && (
                            <p className="text-[#E5001B] font-medium mt-1">
                              * Vui lòng vào "Quản lý địa chỉ" để chọn lại tỉnh và phường/xã theo GHN.
                            </p>
                          )}
                        </div>
                      </label>
                    );
                  })}
                </div>
              ) : (
                <div className="p-4 bg-amber-50 border border-amber-200 text-xs text-amber-900">
                  <p className="mb-2">Bạn chưa có địa chỉ nhận hàng nào trong hệ thống.</p>
                  <Link
                    to="/profile/addresses"
                    className="inline-block bg-[#111] text-white px-4 py-2 uppercase font-bold tracking-wider hover:bg-[#E5001B]"
                  >
                    + Thêm Địa Chỉ Giao Hàng
                  </Link>
                </div>
              )}
            </section>

            {/* 3. Shipping Method */}
            <section className="border border-[rgba(0,0,0,0.08)] p-6 bg-white">
              <h2 className="text-xs uppercase font-bold tracking-widest text-[#111] mb-4 flex items-center gap-2">
                <span className="w-5 h-5 bg-[#111] text-white text-[10px] font-bold rounded-full flex items-center justify-center">
                  3
                </span>
                Phương Thức Vận Chuyển (GHN)
              </h2>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {[
                  {
                    id: 'STANDARD',
                    title: 'Giao Hàng Tiêu Chuẩn',
                    desc: '2–4 ngày làm việc trên toàn quốc',
                  },
                  {
                    id: 'EXPRESS',
                    title: 'Giao Hàng Hỏa Tốc',
                    desc: '1–2 ngày làm việc (khu vực trung tâm)',
                  },
                ].map((method) => {
                  const isSelected = shippingMethod === method.id;
                  return (
                    <label
                      key={method.id}
                      className={`p-4 border cursor-pointer transition-all flex items-start gap-3 ${
                        isSelected
                          ? 'border-[#111] bg-[#fafafa]'
                          : 'border-[#ddd] hover:border-[#aaa]'
                      }`}
                    >
                      <input
                        type="radio"
                        name="shippingMethodSelection"
                        checked={isSelected}
                        onChange={() => {
                          setShippingMethod(method.id as 'STANDARD' | 'EXPRESS');
                          invalidateCheckoutSnapshot();
                        }}
                        className="mt-0.5 accent-[#111]"
                      />
                      <div className="text-xs">
                        <strong className="block font-bold text-[#111] mb-0.5">
                          {method.title}
                        </strong>
                        <span className="text-[#777]">{method.desc}</span>
                      </div>
                    </label>
                  );
                })}
              </div>
            </section>

            {/* 4. Payment Provider */}
            <section className="border border-[rgba(0,0,0,0.08)] p-6 bg-white">
              <h2 className="text-xs uppercase font-bold tracking-widest text-[#111] mb-4 flex items-center gap-2">
                <span className="w-5 h-5 bg-[#111] text-white text-[10px] font-bold rounded-full flex items-center justify-center">
                  4
                </span>
                Phương Thức Thanh Toán
              </h2>

              <div className="space-y-3">
                {[
                  {
                    id: 'COD',
                    title: 'Thanh Toán Khi Nhận Hàng (COD)',
                    desc: 'Thanh toán tiền mặt cho nhân viên giao hàng khi nhận kiện hàng',
                    icon: Truck,
                  },
                  {
                    id: 'VNPAY',
                    title: 'Cổng Thanh Toán VNPay',
                    desc: 'Hỗ trợ thẻ ATM nội địa, thẻ quốc tế Visa/Mastercard và VNPAY-QR',
                    icon: CreditCard,
                  },
                  {
                    id: 'PAYOS',
                    title: 'Cổng Thanh Toán PayOS (VietQR)',
                    desc: 'Quét mã VietQR chuyển khoản tức thì 24/7 từ bất kỳ ứng dụng ngân hàng nào',
                    icon: QrCode,
                  },
                ].map((prov) => {
                  const isSelected = paymentProvider === prov.id;
                  const Icon = prov.icon;
                  return (
                    <label
                      key={prov.id}
                      className={`p-4 border cursor-pointer transition-all flex items-start gap-3 ${
                        isSelected
                          ? 'border-[#111] bg-[#fafafa]'
                          : 'border-[#ddd] hover:border-[#aaa]'
                      }`}
                    >
                      <input
                        type="radio"
                        name="paymentProviderSelection"
                        checked={isSelected}
                        onChange={() => {
                          setPaymentProvider(prov.id as 'COD' | 'VNPAY' | 'PAYOS');
                          invalidateCheckoutSnapshot();
                        }}
                        className="mt-1 accent-[#111]"
                      />
                      <Icon size={20} className="text-[#111] shrink-0 mt-0.5" />
                      <div className="text-xs flex-1">
                        <strong className="block font-bold text-[#111] mb-0.5">{prov.title}</strong>
                        <span className="text-[#666] leading-relaxed">{prov.desc}</span>
                      </div>
                    </label>
                  );
                })}
              </div>
            </section>

            {/* 5. Voucher Coupon */}
            <section className="border border-[rgba(0,0,0,0.08)] p-6 bg-white">
              <h2 className="text-xs uppercase font-bold tracking-widest text-[#111] mb-3 flex items-center gap-2">
                <Tag size={15} /> Mã Giảm Giá / Khuyến Mãi
              </h2>
              <div className="flex gap-2">
                <input
                  type="text"
                  placeholder="Nhập mã ưu đãi (ví dụ: LINOMOI)"
                  value={couponCode}
                  onChange={(e) => {
                    setCouponCode(e.target.value.toUpperCase());
                    invalidateCheckoutSnapshot();
                  }}
                  className="flex-1 border border-[#ddd] px-4 py-3 text-xs uppercase outline-none focus:border-[#111]"
                />
              </div>
            </section>
          </div>

          {/* Right Summary Sidebar */}
          <aside className="border border-[rgba(0,0,0,0.1)] p-6 md:p-8 bg-white space-y-6 sticky top-24">
            <h2
              className="text-xl font-bold uppercase tracking-wide text-[#111] pb-4 border-b border-[rgba(0,0,0,0.08)]"
              style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
            >
              ĐƠN HÀNG CỦA BẠN
            </h2>

            {/* Items review */}
            <div className="space-y-3 max-h-60 overflow-y-auto pr-1 divide-y divide-[rgba(0,0,0,0.05)]">
              {(checkout?.items || cart?.items || []).map((item, idx) => {
                const linePrice = 'lineTotal' in item ? item.lineTotal : item.totalPrice;
                return (
                  <div key={idx} className="pt-2.5 first:pt-0 flex justify-between gap-3 text-xs">
                    <div className="min-w-0 flex-1">
                      <p className="font-semibold text-[#111] truncate">{item.productName}</p>
                      <p className="text-[11px] text-[#777]">
                        {item.color || ''} {item.size ? `· Size ${item.size}` : ''} × {item.quantity}
                      </p>
                    </div>
                    <span className="font-bold text-[#111] shrink-0">{money(linePrice)}</span>
                  </div>
                );
              })}
            </div>

            {/* Financial breakdown */}
            <div className="pt-4 border-t border-[rgba(0,0,0,0.08)] space-y-2 text-xs">
              <div className="flex justify-between text-[#555]">
                <span>Tạm tính</span>
                <span className="font-semibold text-[#111]">
                  {money(checkout?.subtotalAmount ?? cart?.totalPrice)}
                </span>
              </div>

              {checkout ? (
                <>
                  <div className="flex justify-between text-[#555]">
                    <span>Giảm giá khuyến mãi</span>
                    <span className="font-semibold text-[#2D5A3D]">
                      {checkout.discountAmount > 0 ? `−${money(checkout.discountAmount)}` : '0 ₫'}
                    </span>
                  </div>
                  <div className="flex justify-between text-[#555]">
                    <span>Phí vận chuyển GHN</span>
                    <span className="font-semibold text-[#111]">
                      {checkout.shippingFee === 0 ? 'Miễn phí' : money(checkout.shippingFee)}
                    </span>
                  </div>
                </>
              ) : (
                <div className="flex justify-between text-[#888] italic">
                  <span>Phí ship & giảm giá</span>
                  <span>Nhấn nút bên dưới để tính</span>
                </div>
              )}

              <div className="pt-3 border-t border-[rgba(0,0,0,0.08)] flex justify-between items-baseline">
                <span className="text-xs uppercase font-bold tracking-widest text-[#111]">
                  Tổng Thanh Toán:
                </span>
                <span className="text-2xl font-black text-[#111]">
                  {money(checkout?.totalAmount ?? cart?.totalPrice)}
                </span>
              </div>
            </div>

            {error && (
              <div className="p-3 bg-red-50 border border-red-200 text-xs text-[#B00018] flex items-center gap-2">
                <AlertCircle size={15} className="shrink-0 text-[#E5001B]" />
                <span>{error}</span>
              </div>
            )}

            {checkout && (
              <p className="text-[11px] text-[#555] bg-[#fafafa] p-3 border border-[rgba(0,0,0,0.06)] leading-relaxed">
                ✓ Hệ thống đã tính đúng cước GHN và mức giảm giá. Đơn hàng sẽ được tạo và bảo mật với mã giao dịch duy nhất.
              </p>
            )}

            {/* Action Button */}
            {checkout ? (
              <button
                type="button"
                disabled={busy}
                onClick={() => void handlePlaceOrder()}
                className="w-full bg-[#111] text-white py-4 px-6 text-xs uppercase tracking-widest font-bold hover:bg-[#E5001B] active:scale-[0.98] transition-all disabled:opacity-50"
              >
                {busy
                  ? 'Đang Tạo Đơn Hàng…'
                  : paymentProvider === 'COD'
                    ? 'Xác Nhận Đặt Hàng'
                    : `Thanh Toán Qua ${paymentProvider}`}
              </button>
            ) : (
              <button
                type="button"
                disabled={busy || !addressId}
                onClick={() => void handlePreviewCheckout()}
                className="w-full bg-[#111] text-white py-4 px-6 text-xs uppercase tracking-widest font-bold hover:bg-[#E5001B] active:scale-[0.98] transition-all disabled:opacity-50"
              >
                {busy ? 'Đang Tính Tổng Tiền…' : 'Xem Tổng Tiền Chính Xác'}
              </button>
            )}

            <div className="pt-2 text-center">
              <span className="text-[11px] text-[#888] inline-flex items-center gap-1.5">
                <Lock size={12} /> Thông tin đơn hàng được bảo mật tuyệt đối
              </span>
            </div>
          </aside>
        </div>
      </div>
    </div>
  );
}
