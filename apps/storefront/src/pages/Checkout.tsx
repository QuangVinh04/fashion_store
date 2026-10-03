import { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router";
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
} from "lucide-react";
import { store } from "../api/store";
import { money } from "../api/client";
import type { Address, Checkout } from "../api/types";
import { Status, useLoad } from "../components/StoreUI";
import { useCart } from "../context/CartContext";
import { useAuth } from "../context/AuthContext";

export default function CheckoutPage() {
  const { cart, refresh: refreshCart } = useCart();
  const { user } = useAuth();
  const navigate = useNavigate();

  const addressesLoad = useLoad(store.addresses, []);

  const [addressId, setAddressId] = useState<string>("");
  const [shippingMethod, setShippingMethod] = useState<"STANDARD" | "EXPRESS">(
    "STANDARD",
  );
  const [paymentProvider, setPaymentProvider] = useState<
    "COD" | "VNPAY" | "PAYOS"
  >("COD");
  const [couponCode, setCouponCode] = useState<string>("");
  const [checkout, setCheckout] = useState<Checkout | null>(null);

  const [busy, setBusy] = useState<boolean>(false);
  const [processingText, setProcessingText] = useState<string>("");
  const [error, setError] = useState<string>("");

  // Resume active checkout if available in session
  useEffect(() => {
    const savedCheckoutId = sessionStorage.getItem("lino:checkoutId");
    if (!savedCheckoutId) return;

    let active = true;
    store
      .checkout(savedCheckoutId)
      .then((data) => {
        if (!active) return;
        if (data.orderId) {
          sessionStorage.removeItem("lino:checkoutId");
          navigate(`/profile/orders/${data.orderId}`, { replace: true });
        } else if (data.status === "SUBMITTED" || data.status === "PENDING") {
          setCheckout(data);
          if (data.addressId) setAddressId(data.addressId);
          setShippingMethod(data.shippingMethod);
          setPaymentProvider(data.paymentProvider);
          if (data.couponCode) setCouponCode(data.couponCode);
        } else {
          sessionStorage.removeItem("lino:checkoutId");
        }
      })
      .catch(() => {
        sessionStorage.removeItem("lino:checkoutId");
      });

    return () => {
      active = false;
    };
  }, [navigate]);

  // Set default address
  useEffect(() => {
    if (!addressId && addressesLoad.data && addressesLoad.data.length > 0) {
      const def =
        addressesLoad.data.find((a) => a.isDefault) || addressesLoad.data[0];
      setAddressId(def.id);
    }
  }, [addressesLoad.data, addressId]);

  const selectedAddress = addressesLoad.data?.find((a) => a.id === addressId);
  const paymentMethod = paymentProvider === "COD" ? "COD" : "ONLINE";

  function invalidateCheckoutSnapshot() {
    setCheckout(null);
    sessionStorage.removeItem("lino:checkoutId");
  }

  // Create checkout preview snapshot with backend
  async function handlePreviewCheckout() {
    if (!addressId) {
      setError("Vui lòng chọn địa chỉ giao hàng.");
      return;
    }
    if (
      selectedAddress &&
      (!selectedAddress.provinceId || !selectedAddress.wardId)
    ) {
      setError("Địa chỉ cần chọn lại tỉnh và phường/xã theo danh mục GHN mới.");
      return;
    }

    setBusy(true);
    setError("");
    try {
      const result = await store.checkoutCreate({
        addressId,
        shippingMethod,
        paymentMethod,
        paymentProvider,
        couponCode: couponCode.trim() || undefined,
      });
      setCheckout(result);
      sessionStorage.setItem("lino:checkoutId", result.id);
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
    setError("");
    setProcessingText("");
    try {
      const order = await store.orderCreate(checkout.id, addressId);
      sessionStorage.setItem("lino:pendingOrder", order.id);
      sessionStorage.removeItem("lino:checkoutId");

      if (paymentProvider === "COD") {
        navigate(`/profile/orders/${order.id}`);
        return;
      }

      // Online payment (VNPay or PayOS) -> Chờ URL thanh toán để tự động chuyển hướng chuẩn TMĐT
      setProcessingText(`Đang kết nối cổng thanh toán ${paymentProvider}...`);

      let payUrl: string | undefined = order.paymentUrl;
      const startTime = Date.now();
      const maxWaitMs = 8000;

      while (!payUrl && Date.now() - startTime < maxWaitMs) {
        await new Promise((resolve) => setTimeout(resolve, 600));

        // 1. Thử lấy order mới nhất từ order-service
        try {
          const freshOrder = await store.order(order.id);
          if (freshOrder.paymentUrl) {
            payUrl = freshOrder.paymentUrl;
            break;
          }
          if (freshOrder.paymentId) {
            try {
              const initRes = await store.paymentInitiate(freshOrder.paymentId);
              if (initRes.paymentUrl) {
                payUrl = initRes.paymentUrl;
                break;
              }
            } catch {
              // Bỏ qua nếu chưa initiate được
            }
          }
        } catch {
          // Bỏ qua lỗi mạng tạm thời trong lúc chờ
        }

        // 2. Thử tra cứu payment từ payment-service theo orderId
        if (!payUrl) {
          try {
            const p = await store.payment(order.id);
            if (p && p.id) {
              const initRes = await store.paymentInitiate(p.id);
              if (initRes.paymentUrl) {
                payUrl = initRes.paymentUrl;
                break;
              }
            }
          } catch {
            // payment có thể chưa được tạo xong trong 1 vài ms đầu
          }
        }
      }

      if (payUrl) {
        window.location.assign(payUrl);
      } else {
        // Fallback về trang chi tiết đơn hàng nếu cổng thanh toán phản hồi quá lâu
        navigate(`/profile/orders/${order.id}`);
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
      setProcessingText("");
    }
  }

  if (!cart?.items.length && !checkout) {
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-28 text-center">
        <h2 className="text-foreground font-semibold text-lg mb-3 text-balance">
          GIỎ HÀNG ĐANG TRỐNG
        </h2>
        <p className="text-sm text-muted-foreground mb-8">
          Bạn không có sản phẩm nào để thanh toán.
        </p>
        <Link
          to="/products"
          className="inline-block bg-primary text-white px-8 py-4 text-xs font-semibold hover:bg-primary-hover store-action"
        >
          Xem Sản Phẩm
        </Link>
      </div>
    );
  }

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
            <Link to="/cart" className="hover:text-foreground store-text-link">
              Giỏ Hàng
            </Link>
            <span>/</span>
            <span className="text-foreground">Thanh Toán</span>
          </nav>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-10">
        <h1 className="text-foreground font-semibold text-xl leading-tight mb-8 text-balance">
          TIẾN HÀNH ĐẶT HÀNG & THANH TOÁN
        </h1>

        <Status
          loading={addressesLoad.loading}
          error={addressesLoad.error}
          retry={() => void addressesLoad.refresh()}
        />

        <div className="grid grid-cols-1 lg:grid-cols-[1fr_400px] gap-6 items-start">
          {/* Left Form: Customer -> Address -> Shipping -> Payment -> Coupon */}
          <div className="space-y-8">
            {/* 1. Customer Info */}
            <section className="border border-border p-4 sm:p-5 bg-white rounded-2xl">
              <h2 className="text-xs font-semibold text-foreground mb-4 flex items-center gap-2 text-balance">
                <span className="w-5 h-5 bg-secondary text-foreground text-xs font-semibold rounded-full flex items-center justify-center">
                  1
                </span>
                Thông Tin Khách Hàng
              </h2>
              <div className="text-sm text-foreground space-y-1">
                <p>
                  <strong>Họ và tên:</strong>{" "}
                  {user?.fullName || "Khách hàng LINO"}
                </p>
                <p>
                  <strong>Email:</strong> {user?.email || "Chưa cập nhật"}
                </p>
                {user?.phone && (
                  <p>
                    <strong>Số điện thoại:</strong> {user.phone}
                  </p>
                )}
              </div>
            </section>

            {/* 2. Delivery Address */}
            <section className="border border-border p-4 sm:p-5 bg-white rounded-2xl">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-xs font-semibold text-foreground flex items-center gap-2 text-balance">
                  <span className="w-5 h-5 bg-secondary text-foreground text-xs font-semibold rounded-full flex items-center justify-center">
                    2
                  </span>
                  Địa Chỉ Nhận Hàng
                </h2>
                <Link
                  to="/profile/addresses"
                  className="text-xs text-foreground hover:text-primary font-semibold underline underline-offset-4 store-text-link"
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
                            ? "border-border-strong bg-background"
                            : "border-border-strong hover:border-border-strong"
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
                          className="mt-1 choice-control"
                        />
                        <div className="text-xs flex-1">
                          <div className="flex items-center gap-2 mb-1">
                            <strong className="text-sm text-foreground">
                              {addr.recipientName}
                            </strong>
                            <span className="text-muted-foreground">
                              · {addr.phone}
                            </span>
                            {addr.isDefault && (
                              <span className="text-xs font-semibold text-success bg-emerald-50 px-2 py-0.5 border border-emerald-200">
                                Mặc định
                              </span>
                            )}
                          </div>
                          <p className="text-muted-foreground leading-relaxed">
                            {addr.fullAddress ||
                              [
                                addr.detailAddress,
                                addr.ward,
                                addr.district,
                                addr.province,
                              ]
                                .filter(Boolean)
                                .join(", ")}
                          </p>
                          {isMissingGhn && (
                            <p className="text-primary font-medium mt-1">
                              * Vui lòng vào "Quản lý địa chỉ" để chọn lại tỉnh
                              và phường/xã theo GHN.
                            </p>
                          )}
                        </div>
                      </label>
                    );
                  })}
                </div>
              ) : (
                <div className="p-4 bg-amber-50 border border-amber-200 text-xs text-amber-900">
                  <p className="mb-2">
                    Bạn chưa có địa chỉ nhận hàng nào trong hệ thống.
                  </p>
                  <Link
                    to="/profile/addresses"
                    className="inline-block bg-primary text-white px-4 py-2 font-semibold hover:bg-primary-hover store-action"
                  >
                    + Thêm Địa Chỉ Giao Hàng
                  </Link>
                </div>
              )}
            </section>

            {/* 3. Shipping Method */}
            <section className="border border-border p-4 sm:p-5 bg-white rounded-2xl">
              <h2 className="text-xs font-semibold text-foreground mb-4 flex items-center gap-2 text-balance">
                <span className="w-5 h-5 bg-secondary text-foreground text-xs font-semibold rounded-full flex items-center justify-center">
                  3
                </span>
                Phương Thức Vận Chuyển (GHN)
              </h2>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {[
                  {
                    id: "STANDARD",
                    title: "Giao Hàng Tiêu Chuẩn",
                    desc: "2–4 ngày làm việc trên toàn quốc",
                  },
                  {
                    id: "EXPRESS",
                    title: "Giao Hàng Hỏa Tốc",
                    desc: "1–2 ngày làm việc (khu vực trung tâm)",
                  },
                ].map((method) => {
                  const isSelected = shippingMethod === method.id;
                  return (
                    <label
                      key={method.id}
                      className={`p-4 border cursor-pointer transition-all flex items-start gap-3 ${
                        isSelected
                          ? "border-border-strong bg-background"
                          : "border-border-strong hover:border-border-strong"
                      }`}
                    >
                      <input
                        type="radio"
                        name="shippingMethodSelection"
                        checked={isSelected}
                        onChange={() => {
                          setShippingMethod(
                            method.id as "STANDARD" | "EXPRESS",
                          );
                          invalidateCheckoutSnapshot();
                        }}
                        className="mt-0.5 choice-control"
                      />
                      <div className="text-xs">
                        <strong className="block font-semibold text-foreground mb-0.5">
                          {method.title}
                        </strong>
                        <span className="text-muted-foreground">
                          {method.desc}
                        </span>
                      </div>
                    </label>
                  );
                })}
              </div>
            </section>

            {/* 4. Payment Provider */}
            <section className="border border-border p-4 sm:p-5 bg-white rounded-2xl">
              <h2 className="text-xs font-semibold text-foreground mb-4 flex items-center gap-2 text-balance">
                <span className="w-5 h-5 bg-secondary text-foreground text-xs font-semibold rounded-full flex items-center justify-center">
                  4
                </span>
                Phương Thức Thanh Toán
              </h2>

              <div className="space-y-3">
                {[
                  {
                    id: "COD",
                    title: "Thanh Toán Khi Nhận Hàng (COD)",
                    desc: "Thanh toán tiền mặt cho nhân viên giao hàng khi nhận kiện hàng",
                    icon: Truck,
                  },
                  {
                    id: "VNPAY",
                    title: "Cổng Thanh Toán VNPay",
                    desc: "Hỗ trợ thẻ ATM nội địa, thẻ quốc tế Visa/Mastercard và VNPAY-QR",
                    icon: CreditCard,
                  },
                  {
                    id: "PAYOS",
                    title: "Cổng Thanh Toán PayOS (VietQR)",
                    desc: "Quét mã VietQR chuyển khoản tức thì 24/7 từ bất kỳ ứng dụng ngân hàng nào",
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
                          ? "border-border-strong bg-background"
                          : "border-border-strong hover:border-border-strong"
                      }`}
                    >
                      <input
                        type="radio"
                        name="paymentProviderSelection"
                        checked={isSelected}
                        onChange={() => {
                          setPaymentProvider(
                            prov.id as "COD" | "VNPAY" | "PAYOS",
                          );
                          invalidateCheckoutSnapshot();
                        }}
                        className="mt-1 choice-control"
                      />
                      <Icon
                        size={20}
                        className="text-foreground shrink-0 mt-0.5"
                      />
                      <div className="text-xs flex-1">
                        <strong className="block font-semibold text-foreground mb-0.5">
                          {prov.title}
                        </strong>
                        <span className="text-muted-foreground leading-relaxed">
                          {prov.desc}
                        </span>
                      </div>
                    </label>
                  );
                })}
              </div>
            </section>

            {/* 5. Voucher Coupon */}
            <section className="border border-border p-4 sm:p-5 bg-white rounded-2xl">
              <h2 className="text-xs font-semibold text-foreground mb-3 flex items-center gap-2 text-balance">
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
                  className="flex-1 border border-border-strong px-4 py-3 text-xs outline-none focus:border-border-strong store-input"
                />
              </div>
            </section>
          </div>

          {/* Right Summary Sidebar */}
          <aside className="border border-border p-4 sm:p-5 bg-white space-y-6 sticky top-24 rounded-2xl">
            <h2 className="text-lg font-semibold text-foreground pb-4 border-b border-border text-balance">
              ĐƠN HÀNG CỦA BẠN
            </h2>

            {/* Items review */}
            <div className="space-y-3 max-h-60 overflow-y-auto pr-1 divide-y divide-border">
              {(checkout?.items || cart?.items || []).map((item, idx) => {
                const linePrice =
                  "lineTotal" in item ? item.lineTotal : item.totalPrice;
                return (
                  <div
                    key={idx}
                    className="pt-2.5 first:pt-0 flex justify-between gap-3 text-xs"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="font-semibold text-foreground truncate">
                        {item.productName}
                      </p>
                      <p className="text-xs text-muted-foreground">
                        {item.color || ""}{" "}
                        {item.size ? `· Size ${item.size}` : ""} ×{" "}
                        {item.quantity}
                      </p>
                    </div>
                    <span className="font-semibold text-foreground shrink-0">
                      {money(linePrice)}
                    </span>
                  </div>
                );
              })}
            </div>

            {/* Financial breakdown */}
            <div className="pt-4 border-t border-border space-y-2 text-xs">
              <div className="flex justify-between text-muted-foreground">
                <span>Tạm tính</span>
                <span className="font-semibold text-foreground">
                  {money(checkout?.subtotalAmount ?? cart?.totalPrice)}
                </span>
              </div>

              {checkout ? (
                <>
                  <div className="flex justify-between text-muted-foreground">
                    <span>Giảm giá khuyến mãi</span>
                    <span className="font-semibold text-success">
                      {checkout.discountAmount > 0
                        ? `−${money(checkout.discountAmount)}`
                        : "0 ₫"}
                    </span>
                  </div>
                  <div className="flex justify-between text-muted-foreground">
                    <span>Phí vận chuyển GHN</span>
                    <span className="font-semibold text-foreground">
                      {checkout.shippingFee === 0
                        ? "Miễn phí"
                        : money(checkout.shippingFee)}
                    </span>
                  </div>
                </>
              ) : (
                <div className="flex justify-between text-muted-foreground italic">
                  <span>Phí ship & giảm giá</span>
                  <span>Nhấn nút bên dưới để tính</span>
                </div>
              )}

              <div className="pt-3 border-t border-border flex justify-between items-baseline">
                <span className="text-xs font-semibold text-foreground">
                  Tổng Thanh Toán:
                </span>
                <span className="text-lg font-semibold text-foreground">
                  {money(checkout?.totalAmount ?? cart?.totalPrice)}
                </span>
              </div>
            </div>

            {error && (
              <div className="p-3 bg-red-50 border border-red-200 text-xs text-destructive flex items-center gap-2">
                <AlertCircle size={15} className="shrink-0 text-primary" />
                <span>{error}</span>
              </div>
            )}

            {checkout && (
              <p className="text-xs text-muted-foreground bg-background p-3 border border-border leading-relaxed">
                ✓ Hệ thống đã tính đúng cước GHN và mức giảm giá. Đơn hàng sẽ
                được tạo và bảo mật với mã giao dịch duy nhất.
              </p>
            )}

            {/* Action Button */}
            {checkout ? (
              <button
                type="button"
                disabled={busy}
                onClick={() => void handlePlaceOrder()}
                className="w-full bg-primary text-white py-4 px-4 sm:px-6 text-xs font-semibold hover:bg-primary-hover active:scale-[0.98] transition-all disabled:opacity-50 store-button"
              >
                {busy
                  ? "Đang Tạo Đơn Hàng…"
                  : paymentProvider === "COD"
                    ? "Xác Nhận Đặt Hàng"
                    : `Thanh Toán Qua ${paymentProvider}`}
              </button>
            ) : (
              <button
                type="button"
                disabled={busy || !addressId}
                onClick={() => void handlePreviewCheckout()}
                className="w-full bg-primary text-white py-4 px-4 sm:px-6 text-xs font-semibold hover:bg-primary-hover active:scale-[0.98] transition-all disabled:opacity-50 store-button"
              >
                {busy ? "Đang Tính Tổng Tiền…" : "Xem Tổng Tiền Chính Xác"}
              </button>
            )}

            <div className="pt-2 text-center">
              <span className="text-xs text-muted-foreground inline-flex items-center gap-1.5">
                <Lock size={12} /> Thông tin đơn hàng được bảo mật tuyệt đối
              </span>
            </div>
          </aside>
        </div>
      </div>

      {busy && processingText && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white p-8 max-w-sm w-full text-center border border-black/10 rounded-2xl">
            <div className="w-12 h-12 border-4 border-border-strong border-t-transparent rounded-full animate-spin mx-auto mb-4" />
            <h3 className="font-semibold text-base mb-2 text-foreground text-balance">
              ĐANG KẾT NỐI CỔNG THANH TOÁN
            </h3>
            <p className="text-xs text-muted-foreground leading-relaxed">
              {processingText}
            </p>
            <p className="text-xs text-muted-foreground mt-3">
              Vui lòng không đóng trình duyệt hoặc tải lại trang...
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
