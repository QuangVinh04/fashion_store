import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import {
  ShoppingBag,
  ArrowRight,
  Eye,
  Calendar,
  CreditCard,
} from "lucide-react";
import { store } from "../api/store";
import { money } from "../api/client";
import type { OrderStatus, OrderSummary } from "../api/types";
import { ORDER_LABEL } from "../api/types";
import ProfileLayout from "../components/ProfileLayout";
import { PageTitle, Status, StatusBadge, useLoad } from "../components/StoreUI";

const ORDER_TABS: { label: string; value: string }[] = [
  { label: "Tất Cả", value: "" },
  { label: "Chờ Xử Lý", value: "PENDING" },
  { label: "Đã Xác Nhận", value: "CONFIRMED" },
  { label: "Đang Giao", value: "SHIPPING" },
  { label: "Đã Giao", value: "DELIVERED" },
  { label: "Đã Hủy", value: "CANCELLED" },
];

export default function Orders() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get("page") || 0));
  const status = params.get("status") || "";

  const ordersLoad = useLoad(
    () => store.orders(page, status || undefined),
    [page, status],
  );

  const setTab = (val: string) => {
    const next = new URLSearchParams(params);
    if (val) next.set("status", val);
    else next.delete("status");
    next.delete("page");
    setParams(next);
  };

  const setPage = (p: number) => {
    const next = new URLSearchParams(params);
    if (p > 0) next.set("page", String(p));
    else next.delete("page");
    setParams(next);
  };

  return (
    <ProfileLayout>
      <div className="w-full">
        <PageTitle eyebrow="Tài Khoản Của Bạn">ĐƠN HÀNG CỦA TÔI</PageTitle>

        {/* Status Filter Tabs */}
        <div className="flex gap-1 mb-8 border-b border-border overflow-x-auto pb-px">
          {ORDER_TABS.map((tab) => {
            const isSelected = status === tab.value;
            return (
              <button
                key={tab.value}
                type="button"
                aria-pressed={isSelected}
                onClick={() => setTab(tab.value)}
                className={`px-4 py-3 text-xs font-semibold whitespace-nowrap transition-all relative ${
                  isSelected
                    ? "text-foreground"
                    : "text-muted-foreground hover:text-foreground"
                } store-button`}
              >
                {tab.label}
                {isSelected && (
                  <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-foreground" />
                )}
              </button>
            );
          })}
        </div>

        <Status
          loading={ordersLoad.loading}
          error={ordersLoad.error}
          retry={() => void ordersLoad.refresh()}
        />

        {ordersLoad.data && ordersLoad.data.items.length > 0 ? (
          <div className="space-y-4">
            {ordersLoad.data.items.map((order: OrderSummary) => (
              <div
                key={order.id}
                className="border border-border bg-white hover:border-border-strong transition-all overflow-hidden rounded-2xl"
              >
                <div className="p-5 md:p-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-background border-b border-border">
                  <div className="flex flex-wrap items-center gap-4">
                    <div>
                      <span className="text-xs font-semibold text-muted-foreground block">
                        Mã Đơn Hàng
                      </span>
                      <strong className="text-sm text-foreground font-mono">
                        {order.orderCode}
                      </strong>
                    </div>

                    <div className="hidden md:block w-px h-8 bg-border-strong" />

                    <div>
                      <span className="text-xs font-semibold text-muted-foreground block">
                        Ngày Đặt
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {new Date(order.createdAt).toLocaleDateString("vi-VN")}
                      </span>
                    </div>

                    <div className="hidden md:block w-px h-8 bg-border-strong" />

                    <div>
                      <span className="text-xs font-semibold text-muted-foreground block">
                        Tổng Tiền
                      </span>
                      <strong className="text-sm text-foreground">
                        {money(order.totalAmount)}
                      </strong>
                    </div>
                  </div>

                  <div className="flex items-center gap-3">
                    <StatusBadge status={order.status} />

                    <Link
                      to={`/profile/orders/${order.id}`}
                      className="bg-primary text-white px-4 py-2 text-xs font-semibold hover:bg-primary-hover inline-flex items-center gap-1.5 transition-colors shrink-0 store-action"
                    >
                      <Eye size={13} /> Chi Tiết
                    </Link>
                  </div>
                </div>

                <div className="p-5 flex flex-wrap items-center justify-between gap-3 text-xs text-muted-foreground">
                  <div className="flex items-center gap-4">
                    <span>
                      Thanh toán:{" "}
                      <strong className="text-foreground">
                        {({ COD: "Thanh toán khi nhận hàng", VNPAY: "VNPay", PAYOS: "PayOS" } as Record<string, string>)[order.paymentProvider] || "Đang cập nhật"}
                      </strong>
                    </span>
                    <span>·</span>
                    <span>
                      Phương thức:{" "}
                      <strong className="text-foreground">
                        {order.paymentMethod === "COD" ? "Khi nhận hàng" : "Trực tuyến"}
                      </strong>
                    </span>
                  </div>

                  <Link
                    to={`/profile/orders/${order.id}`}
                    className="text-xs text-foreground font-semibold hover:text-primary inline-flex items-center gap-1 underline underline-offset-4 store-text-link"
                  >
                    Xem lịch trình vận đơn & sản phẩm <ArrowRight size={12} />
                  </Link>
                </div>
              </div>
            ))}

            {/* Pagination */}
            {ordersLoad.data.totalPage > 1 && (
              <div className="mt-8 pt-6 border-t border-border flex items-center justify-between">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage(page - 1)}
                  className="bg-primary text-white px-5 py-2.5 text-xs font-semibold hover:bg-primary-hover disabled:opacity-30 disabled:hover:bg-primary-hover store-button"
                >
                  ← Trước
                </button>
                <span className="text-xs text-muted-foreground font-medium">
                  Trang {page + 1} / {ordersLoad.data.totalPage}
                </span>
                <button
                  type="button"
                  disabled={page + 1 >= ordersLoad.data.totalPage}
                  onClick={() => setPage(page + 1)}
                  className="bg-primary text-white px-5 py-2.5 text-xs font-semibold hover:bg-primary-hover disabled:opacity-30 disabled:hover:bg-primary-hover store-button"
                >
                  Sau →
                </button>
              </div>
            )}
          </div>
        ) : (
          !ordersLoad.loading &&
          !ordersLoad.error && (
            <div className="py-20 text-center border border-dashed border-border-strong p-8">
              <ShoppingBag
                size={36}
                className="text-border-strong mx-auto mb-4"
              />
              <p className="text-base font-semibold text-foreground mb-1">
                Không có đơn hàng nào
              </p>
              <p className="text-xs text-muted-foreground mb-6">
                {status
                  ? "Không tìm thấy đơn hàng nào ở trạng thái này."
                  : "Bạn chưa thực hiện đơn đặt hàng nào tại LINO."}
              </p>
              <Link
                to="/products"
                className="inline-block bg-primary text-white px-4 sm:px-6 py-3 text-xs font-semibold hover:bg-primary-hover store-action"
              >
                Khám Phá Sản Phẩm Ngay
              </Link>
            </div>
          )
        )}
      </div>
    </ProfileLayout>
  );
}
