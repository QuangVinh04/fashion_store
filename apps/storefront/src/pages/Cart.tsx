import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { X, Minus, Plus, ShoppingBag, ArrowRight, Tag, Truck } from "lucide-react";
import { useCart } from "../context/CartContext";
import { formatVND } from "../data/products";

const SHIPPING_THRESHOLD = 500000;
const SHIPPING_FEE = 30000;

export default function Cart() {
  const { items, removeItem, updateQty, total, count, clearCart } = useCart();
  const navigate = useNavigate();
  const [coupon, setCoupon] = useState("");
  const [couponApplied, setCouponApplied] = useState(false);
  const [couponError, setCouponError] = useState("");

  const discount = couponApplied ? Math.floor(total * 0.1) : 0;
  const shipping = total - discount >= SHIPPING_THRESHOLD ? 0 : SHIPPING_FEE;
  const finalTotal = total - discount + shipping;

  const applyCoupon = () => {
    if (coupon.toUpperCase() === "LINOMOI") {
      setCouponApplied(true);
      setCouponError("");
    } else {
      setCouponError("Mã giảm giá không hợp lệ hoặc đã hết hạn.");
      setCouponApplied(false);
    }
  };

  if (items.length === 0) {
    return (
      <div style={{ fontFamily: "'Inter', sans-serif" }}>
        <div className="max-w-[1400px] mx-auto px-6 py-5 border-b border-[rgba(0,0,0,0.08)]">
          <nav className="flex items-center gap-2 text-xs text-[#888] uppercase tracking-widest">
            <Link to="/" className="hover:text-[#111] transition-colors">Trang Chủ</Link>
            <span>/</span>
            <span className="text-[#111]">Giỏ Hàng</span>
          </nav>
        </div>
        <div className="max-w-[1400px] mx-auto px-6 py-32 text-center">
          <ShoppingBag size={48} className="text-[#ddd] mx-auto mb-6" />
          <h2 style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 800, fontSize: "2rem", letterSpacing: "-0.01em", color: "#111" }} className="mb-3">
            GIỎ HÀNG TRỐNG
          </h2>
          <p className="text-sm text-[#888] mb-8">Hãy thêm sản phẩm vào giỏ để tiếp tục mua sắm.</p>
          <Link to="/products" className="inline-flex items-center gap-3 bg-[#111] text-white px-8 py-4 text-xs tracking-widest uppercase font-medium hover:bg-[#E5001B] transition-colors duration-300">
            Khám Phá Sản Phẩm <ArrowRight size={14} />
          </Link>
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
          <span className="text-[#111]">Giỏ Hàng</span>
        </nav>
      </div>

      <div className="max-w-[1400px] mx-auto px-6 py-10">
        <div className="flex items-center justify-between mb-8">
          <h1 style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "clamp(2rem, 4vw, 3rem)", letterSpacing: "-0.02em", lineHeight: 1, color: "#111" }}>
            GIỎ HÀNG ({count} sản phẩm)
          </h1>
          <button
            onClick={() => clearCart()}
            className="text-xs uppercase tracking-widest text-[#888] hover:text-[#E5001B] transition-colors"
          >
            Xóa Tất Cả
          </button>
        </div>

        {/* Shipping Progress */}
        {total < SHIPPING_THRESHOLD && (
          <div className="bg-[#f5f5f5] p-4 mb-8 flex items-center gap-3">
            <Truck size={16} className="text-[#888] flex-shrink-0" />
            <div className="flex-1">
              <p className="text-xs text-[#555]">
                Thêm <strong className="text-[#111]">{formatVND(SHIPPING_THRESHOLD - total)}</strong> để được miễn phí vận chuyển
              </p>
              <div className="mt-2 h-1 bg-[#e0e0e0]">
                <div
                  className="h-full bg-[#111] transition-all duration-500"
                  style={{ width: `${Math.min((total / SHIPPING_THRESHOLD) * 100, 100)}%` }}
                />
              </div>
            </div>
          </div>
        )}
        {total >= SHIPPING_THRESHOLD && (
          <div className="bg-[#f0f8f0] border border-[#2D5A3D]/20 p-4 mb-8 flex items-center gap-3">
            <Truck size={16} className="text-[#2D5A3D] flex-shrink-0" />
            <p className="text-xs text-[#2D5A3D] font-medium">Chúc mừng! Đơn hàng của bạn được miễn phí vận chuyển.</p>
          </div>
        )}

        <div className="grid lg:grid-cols-[1fr_380px] xl:grid-cols-[1fr_420px] gap-12">
          {/* Cart Items */}
          <div>
            {/* Header row */}
            <div className="hidden md:grid grid-cols-[1fr_100px_120px_40px] gap-4 pb-4 border-b border-[rgba(0,0,0,0.1)] text-xs uppercase tracking-widest text-[#888]">
              <span>Sản Phẩm</span>
              <span className="text-center">Số Lượng</span>
              <span className="text-right">Thành Tiền</span>
              <span />
            </div>

            <div className="divide-y divide-[rgba(0,0,0,0.08)]">
              {items.map((item) => (
                <div key={`${item.product.id}-${item.size}-${item.color}`} className="py-6 grid md:grid-cols-[1fr_100px_120px_40px] gap-4 items-center">
                  {/* Product */}
                  <div className="flex gap-4">
                    <Link to={`/products/${item.product.id}`} className="w-20 h-24 flex-shrink-0 overflow-hidden bg-[#f5f5f5]">
                      <img src={item.product.img} alt={item.product.name} className="w-full h-full object-cover" />
                    </Link>
                    <div className="flex-1 min-w-0">
                      <p className="text-xs text-[#888] uppercase tracking-widest mb-1">{item.product.subcategory}</p>
                      <Link to={`/products/${item.product.id}`} className="text-sm font-medium text-[#111] hover:text-[#E5001B] transition-colors block leading-snug">
                        {item.product.name}
                      </Link>
                      <div className="flex items-center gap-3 mt-2">
                        <span className="text-xs text-[#888]">Size: {item.size}</span>
                        <span className="text-[#ddd]">|</span>
                        <div className="flex items-center gap-1.5">
                          <span
                            className="w-3 h-3 rounded-full border border-[#ddd] inline-block"
                            style={{ backgroundColor: item.product.colors.find(c => c.name === item.color)?.hex ?? "#111" }}
                          />
                          <span className="text-xs text-[#888]">{item.color}</span>
                        </div>
                      </div>
                      <p className="text-sm font-semibold text-[#111] mt-2 md:hidden">{formatVND(item.product.price * item.quantity)}</p>
                    </div>
                  </div>

                  {/* Qty */}
                  <div className="flex items-center border border-[rgba(0,0,0,0.2)] w-fit md:mx-auto">
                    <button
                      onClick={() => updateQty(item.product.id, item.size, item.color, item.quantity - 1)}
                      className="w-8 h-8 flex items-center justify-center text-[#111] hover:bg-[#f5f5f5] transition-colors"
                      disabled={item.quantity <= 1}
                    >
                      <Minus size={12} />
                    </button>
                    <span className="w-9 text-center text-sm font-medium text-[#111]">{item.quantity}</span>
                    <button
                      onClick={() => updateQty(item.product.id, item.size, item.color, item.quantity + 1)}
                      className="w-8 h-8 flex items-center justify-center text-[#111] hover:bg-[#f5f5f5] transition-colors"
                    >
                      <Plus size={12} />
                    </button>
                  </div>

                  {/* Total */}
                  <p className="hidden md:block text-right text-sm font-semibold text-[#111]">
                    {formatVND(item.product.price * item.quantity)}
                  </p>

                  {/* Remove */}
                  <button
                    onClick={() => removeItem(item.product.id, item.size, item.color)}
                    className="w-8 h-8 flex items-center justify-center text-[#aaa] hover:text-[#E5001B] transition-colors md:mx-auto"
                    aria-label="Xóa sản phẩm"
                  >
                    <X size={16} />
                  </button>
                </div>
              ))}
            </div>

            <div className="mt-6 flex flex-wrap gap-4 justify-between items-center">
              <Link to="/products" className="flex items-center gap-2 text-xs uppercase tracking-widest text-[#888] hover:text-[#111] transition-colors border-b border-transparent hover:border-[#111] pb-0.5">
                ← Tiếp Tục Mua Sắm
              </Link>
            </div>
          </div>

          {/* Order Summary */}
          <div>
            <div className="border border-[rgba(0,0,0,0.12)] p-6">
              <h2 className="text-sm font-semibold uppercase tracking-widest text-[#111] mb-6">Tóm Tắt Đơn Hàng</h2>

              {/* Coupon */}
              <div className="mb-6">
                <p className="text-xs uppercase tracking-widest text-[#888] mb-3">Mã Giảm Giá</p>
                <div className="flex gap-0">
                  <div className="relative flex-1">
                    <Tag size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-[#aaa]" />
                    <input
                      type="text"
                      value={coupon}
                      onChange={(e) => { setCoupon(e.target.value); setCouponError(""); }}
                      placeholder="Nhập mã giảm giá"
                      className="w-full border border-[rgba(0,0,0,0.15)] border-r-0 pl-9 pr-3 py-3 text-sm text-[#111] placeholder:text-[#aaa] focus:outline-none focus:border-[#111] transition-colors"
                      onKeyDown={(e) => e.key === "Enter" && applyCoupon()}
                    />
                  </div>
                  <button
                    onClick={applyCoupon}
                    className="bg-[#111] text-white px-4 py-3 text-xs uppercase tracking-widest font-medium hover:bg-[#E5001B] transition-colors flex-shrink-0"
                  >
                    Áp Dụng
                  </button>
                </div>
                {couponError && <p className="text-xs text-[#E5001B] mt-2">{couponError}</p>}
                {couponApplied && <p className="text-xs text-[#2D5A3D] mt-2">✓ Mã LINOMOI đã được áp dụng — giảm 10%</p>}
              </div>

              {/* Price Breakdown */}
              <div className="space-y-3 border-t border-[rgba(0,0,0,0.08)] pt-5">
                <div className="flex justify-between text-sm">
                  <span className="text-[#555]">Tạm tính ({count} sản phẩm)</span>
                  <span className="text-[#111] font-medium">{formatVND(total)}</span>
                </div>
                {couponApplied && (
                  <div className="flex justify-between text-sm">
                    <span className="text-[#2D5A3D]">Mã giảm giá (10%)</span>
                    <span className="text-[#2D5A3D] font-medium">−{formatVND(discount)}</span>
                  </div>
                )}
                <div className="flex justify-between text-sm">
                  <span className="text-[#555]">Phí vận chuyển</span>
                  <span className={shipping === 0 ? "text-[#2D5A3D] font-medium" : "text-[#111] font-medium"}>
                    {shipping === 0 ? "Miễn phí" : formatVND(shipping)}
                  </span>
                </div>
                <div className="flex justify-between text-base font-bold text-[#111] border-t border-[rgba(0,0,0,0.1)] pt-4 mt-4">
                  <span>Tổng Cộng</span>
                  <span>{formatVND(finalTotal)}</span>
                </div>
              </div>

              <button
                onClick={() => navigate("/checkout")}
                className="w-full mt-6 bg-[#E5001B] text-white py-4 text-xs tracking-widest uppercase font-medium hover:bg-[#c00018] transition-colors flex items-center justify-center gap-3"
              >
                Tiến Hành Thanh Toán <ArrowRight size={14} />
              </button>

              <div className="mt-4 flex items-center justify-center gap-4">
                {["visa", "mastercard", "cod", "momo"].map((method) => (
                  <span key={method} className="text-[10px] uppercase tracking-widest text-[#aaa] border border-[#e0e0e0] px-2 py-1">
                    {method}
                  </span>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
