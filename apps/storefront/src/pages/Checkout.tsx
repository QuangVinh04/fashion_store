import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { ChevronDown, Check, CreditCard, Truck, Smartphone, ArrowRight, Lock } from "lucide-react";
import { useCart } from "../context/CartContext";
import { formatVND } from "../data/products";

const PROVINCES = ["Hà Nội", "Hồ Chí Minh", "Đà Nẵng", "Hải Phòng", "Cần Thơ", "An Giang", "Bình Dương", "Đồng Nai", "Khánh Hòa", "Lâm Đồng"];

const PAYMENT_METHODS = [
  { id: "cod", label: "Thanh Toán Khi Nhận Hàng (COD)", icon: Truck, desc: "Trả tiền mặt khi nhận được hàng" },
  { id: "card", label: "Thẻ Tín Dụng / Ghi Nợ", icon: CreditCard, desc: "Visa, Mastercard, JCB, Napas" },
  { id: "momo", label: "Ví MoMo", icon: Smartphone, desc: "Thanh toán qua ứng dụng MoMo" },
];

const STEPS = ["Thông Tin", "Vận Chuyển", "Thanh Toán"];

function FieldInput({
  label, value, onChange, placeholder, type = "text", required = false, error,
}: {
  label: string; value: string; onChange: (v: string) => void; placeholder?: string; type?: string; required?: boolean; error?: string;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-xs font-semibold uppercase tracking-widest text-[#111]">
        {label}{required && <span className="text-[#E5001B] ml-0.5">*</span>}
      </label>
      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className={`border px-4 py-3 text-sm text-[#111] placeholder:text-[#aaa] focus:outline-none transition-colors ${error ? "border-[#E5001B]" : "border-[rgba(0,0,0,0.15)] focus:border-[#111]"}`}
      />
      {error && <p className="text-xs text-[#E5001B]">{error}</p>}
    </div>
  );
}

export default function Checkout() {
  const { items, total, count, clearCart } = useCart();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [form, setForm] = useState({
    fullName: "", email: "", phone: "",
    address: "", ward: "", district: "", province: "",
    note: "",
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [paymentMethod, setPaymentMethod] = useState("cod");
  const [cardNumber, setCardNumber] = useState("");
  const [cardName, setCardName] = useState("");
  const [cardExpiry, setCardExpiry] = useState("");
  const [cardCVV, setCardCVV] = useState("");
  const [ordered, setOrdered] = useState(false);

  const discount = 0;
  const shipping = total >= 500000 ? 0 : 30000;
  const finalTotal = total - discount + shipping;

  const set = (key: string) => (v: string) => {
    setForm((f) => ({ ...f, [key]: v }));
    setErrors((e) => ({ ...e, [key]: "" }));
  };

  const validateStep0 = () => {
    const e: Record<string, string> = {};
    if (!form.fullName.trim()) e.fullName = "Vui lòng nhập họ tên";
    if (!form.email.match(/^[^\s@]+@[^\s@]+\.[^\s@]+$/)) e.email = "Email không hợp lệ";
    if (!form.phone.match(/^[0-9]{9,11}$/)) e.phone = "Số điện thoại không hợp lệ";
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const validateStep1 = () => {
    const e: Record<string, string> = {};
    if (!form.address.trim()) e.address = "Vui lòng nhập địa chỉ";
    if (!form.ward.trim()) e.ward = "Vui lòng nhập phường/xã";
    if (!form.district.trim()) e.district = "Vui lòng nhập quận/huyện";
    if (!form.province) e.province = "Vui lòng chọn tỉnh/thành";
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleNext = () => {
    if (step === 0 && !validateStep0()) return;
    if (step === 1 && !validateStep1()) return;
    setStep((s) => s + 1);
  };

  const handlePlaceOrder = () => {
    setOrdered(true);
    clearCart();
    setTimeout(() => navigate("/"), 3000);
  };

  if (items.length === 0 && !ordered) {
    return (
      <div className="max-w-[1400px] mx-auto px-6 py-24 text-center" style={{ fontFamily: "'Inter', sans-serif" }}>
        <p className="text-[#888] text-sm mb-4">Giỏ hàng của bạn đang trống.</p>
        <Link to="/products" className="text-xs uppercase tracking-widest text-[#111] underline">Mua sắm ngay</Link>
      </div>
    );
  }

  if (ordered) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center" style={{ fontFamily: "'Inter', sans-serif" }}>
        <div className="text-center px-6 max-w-md">
          <div className="w-16 h-16 bg-[#2D5A3D] flex items-center justify-center mx-auto mb-6">
            <Check size={28} className="text-white" />
          </div>
          <h2 style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "2.5rem", letterSpacing: "-0.02em", color: "#111" }} className="mb-3">
            ĐẶT HÀNG THÀNH CÔNG!
          </h2>
          <p className="text-sm text-[#555] mb-2" style={{ fontWeight: 300 }}>
            Cảm ơn bạn đã tin tưởng LINO. Đơn hàng của bạn đã được xác nhận.
          </p>
          <p className="text-xs text-[#888] mb-8">Mã đơn hàng: <strong className="text-[#111]">LINO-{Date.now().toString().slice(-6)}</strong></p>
          <p className="text-xs text-[#aaa]">Đang chuyển về trang chủ...</p>
        </div>
      </div>
    );
  }

  return (
    <div style={{ fontFamily: "'Inter', sans-serif" }}>
      {/* Breadcrumb */}
      <div className="max-w-[1400px] mx-auto px-6 py-5 border-b border-[rgba(0,0,0,0.08)]">
        <nav className="flex items-center gap-2 text-xs text-[#888] uppercase tracking-widest">
          <Link to="/" className="hover:text-[#111] transition-colors">Trang Chủ</Link>
          <span>/</span>
          <Link to="/cart" className="hover:text-[#111] transition-colors">Giỏ Hàng</Link>
          <span>/</span>
          <span className="text-[#111]">Thanh Toán</span>
        </nav>
      </div>

      <div className="max-w-[1400px] mx-auto px-6 py-10">
        <h1 style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "clamp(2rem, 4vw, 3rem)", letterSpacing: "-0.02em", lineHeight: 1, color: "#111" }} className="mb-10">
          THANH TOÁN
        </h1>

        {/* Step Indicator */}
        <div className="flex items-center gap-0 mb-10 max-w-lg">
          {STEPS.map((s, i) => (
            <div key={s} className="flex items-center flex-1 last:flex-none">
              <div className="flex items-center gap-2">
                <div className={`w-7 h-7 flex items-center justify-center text-xs font-bold transition-colors ${i < step ? "bg-[#2D5A3D] text-white" : i === step ? "bg-[#111] text-white" : "bg-[#f5f5f5] text-[#aaa]"}`}>
                  {i < step ? <Check size={14} /> : i + 1}
                </div>
                <span className={`text-xs uppercase tracking-widest font-medium ${i <= step ? "text-[#111]" : "text-[#aaa]"}`}>{s}</span>
              </div>
              {i < STEPS.length - 1 && <div className={`flex-1 h-px mx-4 ${i < step ? "bg-[#2D5A3D]" : "bg-[#e0e0e0]"}`} />}
            </div>
          ))}
        </div>

        <div className="grid lg:grid-cols-[1fr_380px] xl:grid-cols-[1fr_420px] gap-12">
          {/* Form */}
          <div>
            {/* Step 0: Customer Info */}
            {step === 0 && (
              <div className="space-y-5">
                <h2 className="text-sm font-semibold uppercase tracking-widest text-[#111] mb-6">Thông Tin Khách Hàng</h2>
                <FieldInput label="Họ Và Tên" value={form.fullName} onChange={set("fullName")} placeholder="Nguyễn Văn An" required error={errors.fullName} />
                <div className="grid sm:grid-cols-2 gap-5">
                  <FieldInput label="Email" value={form.email} onChange={set("email")} placeholder="email@example.com" type="email" required error={errors.email} />
                  <FieldInput label="Số Điện Thoại" value={form.phone} onChange={set("phone")} placeholder="0901 234 567" type="tel" required error={errors.phone} />
                </div>
                <div className="pt-4">
                  <button onClick={handleNext} className="w-full sm:w-auto flex items-center justify-center gap-3 bg-[#111] text-white px-10 py-4 text-xs tracking-widest uppercase font-medium hover:bg-[#E5001B] transition-colors duration-300">
                    Tiếp Theo <ArrowRight size={14} />
                  </button>
                </div>
              </div>
            )}

            {/* Step 1: Shipping Address */}
            {step === 1 && (
              <div className="space-y-5">
                <h2 className="text-sm font-semibold uppercase tracking-widest text-[#111] mb-6">Địa Chỉ Giao Hàng</h2>
                <FieldInput label="Địa Chỉ (Số nhà, tên đường)" value={form.address} onChange={set("address")} placeholder="123 Đường Nguyễn Huệ" required error={errors.address} />
                <div className="grid sm:grid-cols-2 gap-5">
                  <FieldInput label="Phường/Xã" value={form.ward} onChange={set("ward")} placeholder="Phường Bến Nghé" required error={errors.ward} />
                  <FieldInput label="Quận/Huyện" value={form.district} onChange={set("district")} placeholder="Quận 1" required error={errors.district} />
                </div>
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-semibold uppercase tracking-widest text-[#111]">
                    Tỉnh/Thành Phố<span className="text-[#E5001B] ml-0.5">*</span>
                  </label>
                  <div className="relative">
                    <select
                      value={form.province}
                      onChange={(e) => { setForm(f => ({ ...f, province: e.target.value })); setErrors(er => ({ ...er, province: "" })); }}
                      className={`w-full border px-4 py-3 text-sm text-[#111] bg-white appearance-none focus:outline-none transition-colors ${errors.province ? "border-[#E5001B]" : "border-[rgba(0,0,0,0.15)] focus:border-[#111]"}`}
                    >
                      <option value="">Chọn tỉnh/thành phố</option>
                      {PROVINCES.map((p) => <option key={p} value={p}>{p}</option>)}
                    </select>
                    <ChevronDown size={14} className="absolute right-4 top-1/2 -translate-y-1/2 text-[#888] pointer-events-none" />
                  </div>
                  {errors.province && <p className="text-xs text-[#E5001B]">{errors.province}</p>}
                </div>
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-semibold uppercase tracking-widest text-[#111]">Ghi Chú Đơn Hàng</label>
                  <textarea
                    value={form.note}
                    onChange={(e) => setForm(f => ({ ...f, note: e.target.value }))}
                    placeholder="Giao giờ hành chính, gọi điện trước khi giao..."
                    rows={3}
                    className="border border-[rgba(0,0,0,0.15)] px-4 py-3 text-sm text-[#111] placeholder:text-[#aaa] focus:outline-none focus:border-[#111] resize-none transition-colors"
                  />
                </div>
                <div className="flex gap-4 pt-4">
                  <button onClick={() => setStep(0)} className="flex items-center gap-2 text-xs uppercase tracking-widest text-[#888] hover:text-[#111] transition-colors border-b border-transparent hover:border-[#111] pb-0.5">
                    ← Quay Lại
                  </button>
                  <button onClick={handleNext} className="flex items-center gap-3 bg-[#111] text-white px-10 py-4 text-xs tracking-widest uppercase font-medium hover:bg-[#E5001B] transition-colors">
                    Tiếp Theo <ArrowRight size={14} />
                  </button>
                </div>
              </div>
            )}

            {/* Step 2: Payment */}
            {step === 2 && (
              <div className="space-y-5">
                <h2 className="text-sm font-semibold uppercase tracking-widest text-[#111] mb-6">Phương Thức Thanh Toán</h2>

                <div className="space-y-3">
                  {PAYMENT_METHODS.map((method) => {
                    const Icon = method.icon;
                    return (
                      <button
                        key={method.id}
                        onClick={() => setPaymentMethod(method.id)}
                        className={`w-full text-left border p-4 flex items-center gap-4 transition-all ${paymentMethod === method.id ? "border-[#111] bg-[#fafafa]" : "border-[rgba(0,0,0,0.12)] hover:border-[#888]"}`}
                      >
                        <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center flex-shrink-0 ${paymentMethod === method.id ? "border-[#111]" : "border-[#ccc]"}`}>
                          {paymentMethod === method.id && <div className="w-2.5 h-2.5 rounded-full bg-[#111]" />}
                        </div>
                        <Icon size={18} className="text-[#555] flex-shrink-0" />
                        <div>
                          <p className="text-sm font-medium text-[#111]">{method.label}</p>
                          <p className="text-xs text-[#888] mt-0.5">{method.desc}</p>
                        </div>
                      </button>
                    );
                  })}
                </div>

                {/* Card Fields */}
                {paymentMethod === "card" && (
                  <div className="border border-[rgba(0,0,0,0.1)] p-5 space-y-4 mt-2">
                    <div className="flex flex-col gap-1.5">
                      <label className="text-xs font-semibold uppercase tracking-widest text-[#111]">Số Thẻ</label>
                      <input
                        type="text"
                        value={cardNumber}
                        onChange={(e) => setCardNumber(e.target.value.replace(/\D/g, "").slice(0, 16).replace(/(.{4})/g, "$1 ").trim())}
                        placeholder="0000 0000 0000 0000"
                        className="border border-[rgba(0,0,0,0.15)] px-4 py-3 text-sm text-[#111] placeholder:text-[#aaa] focus:outline-none focus:border-[#111] transition-colors"
                      />
                    </div>
                    <FieldInput label="Tên Chủ Thẻ" value={cardName} onChange={setCardName} placeholder="NGUYEN VAN AN" />
                    <div className="grid grid-cols-2 gap-4">
                      <div className="flex flex-col gap-1.5">
                        <label className="text-xs font-semibold uppercase tracking-widest text-[#111]">Ngày Hết Hạn</label>
                        <input
                          type="text"
                          value={cardExpiry}
                          onChange={(e) => {
                            const val = e.target.value.replace(/\D/g, "").slice(0, 4);
                            setCardExpiry(val.length > 2 ? `${val.slice(0, 2)}/${val.slice(2)}` : val);
                          }}
                          placeholder="MM/YY"
                          className="border border-[rgba(0,0,0,0.15)] px-4 py-3 text-sm text-[#111] placeholder:text-[#aaa] focus:outline-none focus:border-[#111] transition-colors"
                        />
                      </div>
                      <div className="flex flex-col gap-1.5">
                        <label className="text-xs font-semibold uppercase tracking-widest text-[#111]">CVV</label>
                        <input
                          type="password"
                          value={cardCVV}
                          onChange={(e) => setCardCVV(e.target.value.replace(/\D/g, "").slice(0, 3))}
                          placeholder="•••"
                          className="border border-[rgba(0,0,0,0.15)] px-4 py-3 text-sm text-[#111] placeholder:text-[#aaa] focus:outline-none focus:border-[#111] transition-colors"
                        />
                      </div>
                    </div>
                  </div>
                )}

                {/* Delivery Summary */}
                <div className="border border-[rgba(0,0,0,0.1)] p-5 bg-[#fafafa]">
                  <p className="text-xs font-semibold uppercase tracking-widest text-[#111] mb-3">Giao Tới</p>
                  <p className="text-sm text-[#555]">{form.fullName} — {form.phone}</p>
                  <p className="text-sm text-[#555]">{form.address}, {form.ward}, {form.district}, {form.province}</p>
                  <button onClick={() => setStep(1)} className="text-xs text-[#E5001B] mt-2 hover:underline">Chỉnh sửa</button>
                </div>

                <div className="flex gap-4 pt-4">
                  <button onClick={() => setStep(1)} className="flex items-center gap-2 text-xs uppercase tracking-widest text-[#888] hover:text-[#111] transition-colors border-b border-transparent hover:border-[#111] pb-0.5">
                    ← Quay Lại
                  </button>
                  <button
                    onClick={handlePlaceOrder}
                    className="flex items-center gap-3 bg-[#E5001B] text-white px-10 py-4 text-xs tracking-widest uppercase font-medium hover:bg-[#c00018] transition-colors"
                  >
                    <Lock size={14} /> Đặt Hàng — {formatVND(finalTotal)}
                  </button>
                </div>

                <p className="text-xs text-[#aaa] flex items-center gap-2 mt-2">
                  <Lock size={11} />
                  Thông tin thanh toán được mã hóa SSL 256-bit an toàn.
                </p>
              </div>
            )}
          </div>

          {/* Order Summary Sidebar */}
          <div>
            <div className="border border-[rgba(0,0,0,0.12)] p-6 sticky top-24">
              <h2 className="text-sm font-semibold uppercase tracking-widest text-[#111] mb-5">Đơn Hàng Của Bạn</h2>

              <div className="space-y-4 max-h-64 overflow-y-auto pr-1 mb-5">
                {items.map((item) => (
                  <div key={`${item.product.id}-${item.size}-${item.color}`} className="flex gap-3">
                    <div className="relative w-16 h-20 flex-shrink-0 bg-[#f5f5f5]">
                      <img src={item.product.img} alt={item.product.name} className="w-full h-full object-cover" />
                      <span className="absolute -top-1.5 -right-1.5 w-5 h-5 bg-[#111] text-white text-[9px] font-bold flex items-center justify-center rounded-full">
                        {item.quantity}
                      </span>
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-medium text-[#111] leading-snug">{item.product.name}</p>
                      <p className="text-xs text-[#888] mt-1">Size: {item.size} · {item.color}</p>
                      <p className="text-xs font-semibold text-[#111] mt-1">{formatVND(item.product.price * item.quantity)}</p>
                    </div>
                  </div>
                ))}
              </div>

              <div className="border-t border-[rgba(0,0,0,0.08)] pt-4 space-y-2.5">
                <div className="flex justify-between text-sm">
                  <span className="text-[#555]">Tạm tính ({count} SP)</span>
                  <span className="text-[#111] font-medium">{formatVND(total)}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-[#555]">Vận chuyển</span>
                  <span className={shipping === 0 ? "text-[#2D5A3D] font-medium" : "text-[#111] font-medium"}>
                    {shipping === 0 ? "Miễn phí" : formatVND(shipping)}
                  </span>
                </div>
                <div className="flex justify-between text-base font-bold text-[#111] border-t border-[rgba(0,0,0,0.1)] pt-3 mt-3">
                  <span>Tổng Cộng</span>
                  <span className="text-[#E5001B]">{formatVND(finalTotal)}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
