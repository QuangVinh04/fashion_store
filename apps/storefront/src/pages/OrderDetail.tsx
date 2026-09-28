import { Link, useParams } from "react-router";
import { Check, Package, Truck, MapPin, CreditCard, Copy } from "lucide-react";
import { DEMO_ORDERS, STATUS_LABEL, STATUS_COLOR, type OrderStatus } from "../data/orders";
import { formatVND } from "../data/products";
import ProfileLayout from "../components/ProfileLayout";

export default function OrderDetail() {
  const { id } = useParams();
  const order = DEMO_ORDERS.find((o) => o.id === id);

  if (!order) {
    return (
      <ProfileLayout>
        <div className="text-center py-16">
          <p className="text-[#888] text-sm mb-4">Không tìm thấy đơn hàng.</p>
          <Link to="/profile/orders" className="text-xs uppercase tracking-widest text-[#111] underline">Xem tất cả đơn hàng</Link>
        </div>
      </ProfileLayout>
    );
  }

  return (
    <ProfileLayout>
      <div style={{ fontFamily: "'Inter', sans-serif" }}>
        {/* Header */}
        <div className="flex flex-wrap items-start justify-between gap-4 mb-8">
          <div>
            <Link to="/profile/orders" className="text-xs uppercase tracking-widest text-[#888] hover:text-[#111] transition-colors mb-3 inline-block">
              ← Đơn Hàng Của Tôi
            </Link>
            <h1
              className="text-[#111]"
              style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "clamp(1.75rem, 3vw, 2.5rem)", letterSpacing: "-0.01em", lineHeight: 1 }}
            >
              ĐƠN HÀNG {order.id}
            </h1>
            <p className="text-sm text-[#888] mt-2">Ngày đặt: {new Date(order.date).toLocaleDateString("vi-VN")}</p>
          </div>
          <span className={`text-xs uppercase tracking-widest px-4 py-2 border font-medium ${STATUS_COLOR[order.status as OrderStatus]}`}>
            {STATUS_LABEL[order.status as OrderStatus]}
          </span>
        </div>

        <div className="grid lg:grid-cols-[1fr_340px] gap-8">
          {/* Left */}
          <div className="space-y-8">
            {/* Tracking Timeline */}
            <div className="border border-[rgba(0,0,0,0.12)] p-6">
              <div className="flex items-center gap-2 mb-6">
                <Package size={16} className="text-[#888]" />
                <p className="text-xs font-semibold uppercase tracking-widest text-[#111]">Theo Dõi Đơn Hàng</p>
              </div>
              {order.trackingCode && (
                <div className="flex items-center gap-3 mb-6 p-3 bg-[#f5f5f5]">
                  <p className="text-xs text-[#555] flex-1">Mã vận đơn: <strong className="text-[#111]">{order.trackingCode}</strong></p>
                  <button className="text-[#888] hover:text-[#111] transition-colors"><Copy size={13} /></button>
                </div>
              )}
              <div className="space-y-0">
                {order.timeline.map((step, i) => (
                  <div key={i} className="flex gap-4">
                    <div className="flex flex-col items-center">
                      <div className={`w-7 h-7 flex items-center justify-center flex-shrink-0 ${step.done ? "bg-[#111]" : "bg-[#e0e0e0]"}`}>
                        {step.done ? <Check size={14} className="text-white" /> : <span className="w-2 h-2 rounded-full bg-[#aaa]" />}
                      </div>
                      {i < order.timeline.length - 1 && (
                        <div className={`w-px flex-1 my-1 ${step.done ? "bg-[#111]" : "bg-[#e0e0e0]"}`} style={{ minHeight: "24px" }} />
                      )}
                    </div>
                    <div className="pb-6">
                      <p className={`text-sm font-medium ${step.done ? "text-[#111]" : "text-[#aaa]"}`}>{step.status}</p>
                      {step.date && <p className="text-xs text-[#888] mt-0.5">{step.date}</p>}
                    </div>
                  </div>
                ))}
              </div>
              {order.status === "cancelled" && (
                <div className="mt-4 pt-4 border-t border-[rgba(0,0,0,0.08)]">
                  <p className="text-xs text-[#888]">Lý do hủy: Khách hàng yêu cầu hủy đơn.</p>
                </div>
              )}
            </div>

            {/* Products */}
            <div className="border border-[rgba(0,0,0,0.12)]">
              <div className="px-6 py-4 border-b border-[rgba(0,0,0,0.08)] flex items-center gap-2">
                <Package size={16} className="text-[#888]" />
                <p className="text-xs font-semibold uppercase tracking-widest text-[#111]">Sản Phẩm</p>
              </div>
              <div className="divide-y divide-[rgba(0,0,0,0.06)]">
                {order.items.map((item, i) => (
                  <div key={i} className="px-6 py-5 flex items-center gap-4">
                    <Link to={`/products/${item.productId}`} className="w-16 h-20 flex-shrink-0 bg-[#f5f5f5] overflow-hidden">
                      <img src={item.img} alt={item.name} className="w-full h-full object-cover" />
                    </Link>
                    <div className="flex-1 min-w-0">
                      <Link to={`/products/${item.productId}`} className="text-sm font-medium text-[#111] hover:text-[#E5001B] transition-colors">
                        {item.name}
                      </Link>
                      <div className="flex items-center gap-3 mt-1.5 flex-wrap">
                        <span className="text-xs text-[#888]">Size: {item.size}</span>
                        <span className="text-[#ccc]">·</span>
                        <span className="text-xs text-[#888]">Màu: {item.color}</span>
                        <span className="text-[#ccc]">·</span>
                        <span className="text-xs text-[#888]">SL: {item.quantity}</span>
                      </div>
                    </div>
                    <div className="text-right flex-shrink-0">
                      <p className="text-xs text-[#888]">{formatVND(item.price)} × {item.quantity}</p>
                      <p className="text-sm font-semibold text-[#111] mt-0.5">{formatVND(item.price * item.quantity)}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Right */}
          <div className="space-y-5">
            {/* Delivery Address */}
            <div className="border border-[rgba(0,0,0,0.12)] p-5">
              <div className="flex items-center gap-2 mb-4">
                <MapPin size={15} className="text-[#888]" />
                <p className="text-xs font-semibold uppercase tracking-widest text-[#111]">Địa Chỉ Giao Hàng</p>
              </div>
              <p className="text-sm font-medium text-[#111]">{order.address.fullName}</p>
              <p className="text-sm text-[#555] mt-1">{order.address.phone}</p>
              <p className="text-sm text-[#555] mt-1">{order.address.address}</p>
              <p className="text-sm text-[#555]">{order.address.ward}, {order.address.district}</p>
              <p className="text-sm text-[#555]">{order.address.province}</p>
            </div>

            {/* Payment */}
            <div className="border border-[rgba(0,0,0,0.12)] p-5">
              <div className="flex items-center gap-2 mb-4">
                <CreditCard size={15} className="text-[#888]" />
                <p className="text-xs font-semibold uppercase tracking-widest text-[#111]">Thanh Toán</p>
              </div>
              <p className="text-sm text-[#555]">{order.paymentMethod}</p>
            </div>

            {/* Order Summary */}
            <div className="border border-[rgba(0,0,0,0.12)] p-5">
              <div className="flex items-center gap-2 mb-4">
                <Truck size={15} className="text-[#888]" />
                <p className="text-xs font-semibold uppercase tracking-widest text-[#111]">Tóm Tắt Đơn Hàng</p>
              </div>
              <div className="space-y-2.5">
                <div className="flex justify-between text-sm">
                  <span className="text-[#555]">Tạm tính</span>
                  <span className="text-[#111]">{formatVND(order.subtotal)}</span>
                </div>
                {order.discount > 0 && (
                  <div className="flex justify-between text-sm">
                    <span className="text-[#2D5A3D]">Giảm giá</span>
                    <span className="text-[#2D5A3D]">−{formatVND(order.discount)}</span>
                  </div>
                )}
                <div className="flex justify-between text-sm">
                  <span className="text-[#555]">Vận chuyển</span>
                  <span className={order.shipping === 0 ? "text-[#2D5A3D]" : "text-[#111]"}>
                    {order.shipping === 0 ? "Miễn phí" : formatVND(order.shipping)}
                  </span>
                </div>
                <div className="flex justify-between text-base font-bold text-[#111] border-t border-[rgba(0,0,0,0.1)] pt-3 mt-3">
                  <span>Tổng Cộng</span>
                  <span className="text-[#E5001B]">{formatVND(order.total)}</span>
                </div>
              </div>
            </div>

            {order.status === "delivered" && (
              <Link
                to="/products"
                className="w-full flex items-center justify-center gap-3 bg-[#111] text-white py-4 text-xs tracking-widest uppercase font-medium hover:bg-[#E5001B] transition-colors"
              >
                Mua Lại Sản Phẩm
              </Link>
            )}
            {(order.status === "pending" || order.status === "confirmed") && (
              <button className="w-full flex items-center justify-center gap-3 border-2 border-[#E5001B] text-[#E5001B] py-4 text-xs tracking-widest uppercase font-medium hover:bg-[#E5001B] hover:text-white transition-colors">
                Hủy Đơn Hàng
              </button>
            )}
          </div>
        </div>
      </div>
    </ProfileLayout>
  );
}
