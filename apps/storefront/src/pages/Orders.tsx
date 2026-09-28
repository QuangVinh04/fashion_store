import { useState } from "react";
import { Link } from "react-router";
import { ShoppingBag, ArrowRight } from "lucide-react";
import { DEMO_ORDERS, STATUS_LABEL, STATUS_COLOR, type OrderStatus } from "../data/orders";
import { formatVND } from "../data/products";
import ProfileLayout from "../components/ProfileLayout";

const TABS: { label: string; value: string }[] = [
  { label: "Tất Cả", value: "all" },
  { label: "Chờ Xác Nhận", value: "pending" },
  { label: "Đang Vận Chuyển", value: "shipping" },
  { label: "Đã Giao", value: "delivered" },
  { label: "Đã Hủy", value: "cancelled" },
];

export default function Orders() {
  const [activeTab, setActiveTab] = useState("all");

  const filtered = activeTab === "all"
    ? DEMO_ORDERS
    : DEMO_ORDERS.filter((o) => o.status === activeTab);

  return (
    <ProfileLayout>
      <div>
        <h1
          className="text-[#111] mb-8"
          style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "clamp(1.75rem, 3vw, 2.5rem)", letterSpacing: "-0.01em", lineHeight: 1 }}
        >
          ĐƠN HÀNG CỦA TÔI
        </h1>

        {/* Tabs */}
        <div className="flex flex-wrap gap-0 mb-6 border-b border-[rgba(0,0,0,0.1)] overflow-x-auto">
          {TABS.map((tab) => (
            <button
              key={tab.value}
              onClick={() => setActiveTab(tab.value)}
              className="px-4 py-3 text-xs tracking-widest uppercase font-medium transition-all relative whitespace-nowrap"
              style={{ color: activeTab === tab.value ? "#111" : "#888" }}
            >
              {tab.label}
              {activeTab === tab.value && <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-[#111]" />}
            </button>
          ))}
        </div>

        {filtered.length === 0 && (
          <div className="text-center py-16 border border-dashed border-[rgba(0,0,0,0.15)]">
            <ShoppingBag size={36} className="text-[#ddd] mx-auto mb-4" />
            <p className="text-[#888] text-sm mb-4">Không có đơn hàng nào.</p>
            <Link to="/products" className="text-xs uppercase tracking-widest text-[#111] underline hover:text-[#E5001B] transition-colors">
              Mua sắm ngay
            </Link>
          </div>
        )}

        <div className="space-y-4">
          {filtered.map((order) => (
            <div key={order.id} className="border border-[rgba(0,0,0,0.12)] overflow-hidden">
              {/* Order Header */}
              <div className="bg-[#f5f5f5] px-5 py-4 flex flex-wrap items-center justify-between gap-3">
                <div className="flex flex-wrap items-center gap-4">
                  <div>
                    <p className="text-[10px] uppercase tracking-widest text-[#888]">Mã Đơn Hàng</p>
                    <p className="text-sm font-semibold text-[#111] mt-0.5">{order.id}</p>
                  </div>
                  <div className="hidden sm:block w-px h-8 bg-[rgba(0,0,0,0.1)]" />
                  <div>
                    <p className="text-[10px] uppercase tracking-widest text-[#888]">Ngày Đặt</p>
                    <p className="text-sm text-[#555] mt-0.5">{new Date(order.date).toLocaleDateString("vi-VN")}</p>
                  </div>
                  <div className="hidden sm:block w-px h-8 bg-[rgba(0,0,0,0.1)]" />
                  <div>
                    <p className="text-[10px] uppercase tracking-widest text-[#888]">Tổng Tiền</p>
                    <p className="text-sm font-semibold text-[#111] mt-0.5">{formatVND(order.total)}</p>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <span className={`text-[10px] uppercase tracking-widest px-3 py-1.5 border font-medium ${STATUS_COLOR[order.status as OrderStatus]}`}>
                    {STATUS_LABEL[order.status as OrderStatus]}
                  </span>
                  <Link
                    to={`/profile/orders/${order.id}`}
                    className="flex items-center gap-1.5 text-xs uppercase tracking-widest text-[#111] font-medium hover:text-[#E5001B] transition-colors"
                  >
                    Chi Tiết <ArrowRight size={12} />
                  </Link>
                </div>
              </div>

              {/* Order Items */}
              <div className="px-5 divide-y divide-[rgba(0,0,0,0.06)]">
                {order.items.map((item, i) => (
                  <div key={i} className="py-4 flex items-center gap-4">
                    <Link to={`/products/${item.productId}`} className="w-16 h-20 flex-shrink-0 bg-[#f5f5f5] overflow-hidden">
                      <img src={item.img} alt={item.name} className="w-full h-full object-cover" />
                    </Link>
                    <div className="flex-1 min-w-0">
                      <Link to={`/products/${item.productId}`} className="text-sm font-medium text-[#111] hover:text-[#E5001B] transition-colors block truncate">
                        {item.name}
                      </Link>
                      <p className="text-xs text-[#888] mt-1">Size: {item.size} · Màu: {item.color} · SL: {item.quantity}</p>
                      <p className="text-sm font-semibold text-[#111] mt-1">{formatVND(item.price * item.quantity)}</p>
                    </div>
                    {order.status === "delivered" && (
                      <Link
                        to={`/products/${item.productId}`}
                        className="hidden sm:flex text-xs uppercase tracking-widest text-[#888] hover:text-[#111] transition-colors border-b border-transparent hover:border-[#888] pb-0.5 flex-shrink-0"
                      >
                        Mua Lại
                      </Link>
                    )}
                  </div>
                ))}
              </div>

              {/* Footer */}
              <div className="bg-[#fafafa] px-5 py-3 flex flex-wrap items-center justify-between gap-2 text-xs text-[#888]">
                <span className="uppercase tracking-widest">Thanh toán: {order.paymentMethod}</span>
                <span className="font-medium text-[#111]">Tổng: {formatVND(order.total)}</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </ProfileLayout>
  );
}
