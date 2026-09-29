import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ShoppingBag, ArrowRight, Eye, Calendar, CreditCard } from 'lucide-react';
import { store } from '../api/store';
import { money } from '../api/client';
import type { OrderStatus, OrderSummary } from '../api/types';
import { ORDER_LABEL } from '../api/types';
import ProfileLayout from '../components/ProfileLayout';
import { PageTitle, Status, StatusBadge, useLoad } from '../components/StoreUI';

const ORDER_TABS: { label: string; value: string }[] = [
  { label: 'Tất Cả', value: '' },
  { label: 'Chờ Xử Lý', value: 'PENDING' },
  { label: 'Đã Xác Nhận', value: 'CONFIRMED' },
  { label: 'Đang Giao', value: 'SHIPPING' },
  { label: 'Đã Giao', value: 'DELIVERED' },
  { label: 'Đã Hủy', value: 'CANCELLED' },
];

export default function Orders() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get('page') || 0));
  const status = params.get('status') || '';

  const ordersLoad = useLoad(
    () => store.orders(page, status || undefined),
    [page, status]
  );

  const setTab = (val: string) => {
    const next = new URLSearchParams(params);
    if (val) next.set('status', val);
    else next.delete('status');
    next.delete('page');
    setParams(next);
  };

  const setPage = (p: number) => {
    const next = new URLSearchParams(params);
    if (p > 0) next.set('page', String(p));
    else next.delete('page');
    setParams(next);
  };

  return (
    <ProfileLayout>
      <div style={{ fontFamily: "'Inter', sans-serif" }} className="w-full">
        <PageTitle eyebrow="Tài Khoản Của Bạn">ĐƠN HÀNG CỦA TÔI</PageTitle>

        {/* Status Filter Tabs */}
        <div className="flex gap-1 mb-8 border-b border-[rgba(0,0,0,0.1)] overflow-x-auto pb-px">
          {ORDER_TABS.map((tab) => {
            const isSelected = status === tab.value;
            return (
              <button
                key={tab.value}
                type="button"
                onClick={() => setTab(tab.value)}
                className={`px-4 py-3 text-xs uppercase tracking-widest font-semibold whitespace-nowrap transition-all relative ${
                  isSelected ? 'text-[#111]' : 'text-[#888] hover:text-[#111]'
                }`}
              >
                {tab.label}
                {isSelected && (
                  <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-[#111]" />
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
                className="border border-[rgba(0,0,0,0.08)] bg-white hover:border-[#111] transition-all overflow-hidden"
              >
                <div className="p-5 md:p-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-[#fafafa] border-b border-[rgba(0,0,0,0.06)]">
                  <div className="flex flex-wrap items-center gap-4">
                    <div>
                      <span className="text-[10px] uppercase font-bold tracking-widest text-[#888] block">
                        Mã Đơn Hàng
                      </span>
                      <strong className="text-sm text-[#111] font-mono">
                        {order.orderCode}
                      </strong>
                    </div>

                    <div className="hidden md:block w-px h-8 bg-[#ddd]" />

                    <div>
                      <span className="text-[10px] uppercase font-bold tracking-widest text-[#888] block">
                        Ngày Đặt
                      </span>
                      <span className="text-xs text-[#555]">
                        {new Date(order.createdAt).toLocaleDateString('vi-VN')}
                      </span>
                    </div>

                    <div className="hidden md:block w-px h-8 bg-[#ddd]" />

                    <div>
                      <span className="text-[10px] uppercase font-bold tracking-widest text-[#888] block">
                        Tổng Tiền
                      </span>
                      <strong className="text-sm text-[#111]">
                        {money(order.totalAmount)}
                      </strong>
                    </div>
                  </div>

                  <div className="flex items-center gap-3">
                    <StatusBadge status={order.status} />

                    <Link
                      to={`/profile/orders/${order.id}`}
                      className="bg-[#111] text-white px-4 py-2 text-xs uppercase tracking-widest font-semibold hover:bg-[#E5001B] inline-flex items-center gap-1.5 transition-colors shrink-0"
                    >
                      <Eye size={13} /> Chi Tiết
                    </Link>
                  </div>
                </div>

                <div className="p-5 flex flex-wrap items-center justify-between gap-3 text-xs text-[#666]">
                  <div className="flex items-center gap-4">
                    <span>
                      Thanh toán: <strong className="uppercase text-[#111]">{order.paymentProvider}</strong>
                    </span>
                    <span>·</span>
                    <span>Phương thức: <strong className="uppercase text-[#111]">{order.paymentMethod}</strong></span>
                  </div>

                  <Link
                    to={`/profile/orders/${order.id}`}
                    className="text-xs uppercase tracking-wider text-[#111] font-semibold hover:text-[#E5001B] inline-flex items-center gap-1 underline underline-offset-4"
                  >
                    Xem lịch trình vận đơn & sản phẩm <ArrowRight size={12} />
                  </Link>
                </div>
              </div>
            ))}

            {/* Pagination */}
            {ordersLoad.data.totalPage > 1 && (
              <div className="mt-8 pt-6 border-t border-[rgba(0,0,0,0.08)] flex items-center justify-between">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage(page - 1)}
                  className="bg-[#111] text-white px-5 py-2.5 text-xs uppercase tracking-widest font-semibold hover:bg-[#E5001B] disabled:opacity-30 disabled:hover:bg-[#111]"
                >
                  ← Trước
                </button>
                <span className="text-xs text-[#888] font-medium">
                  Trang {page + 1} / {ordersLoad.data.totalPage}
                </span>
                <button
                  type="button"
                  disabled={page + 1 >= ordersLoad.data.totalPage}
                  onClick={() => setPage(page + 1)}
                  className="bg-[#111] text-white px-5 py-2.5 text-xs uppercase tracking-widest font-semibold hover:bg-[#E5001B] disabled:opacity-30 disabled:hover:bg-[#111]"
                >
                  Sau →
                </button>
              </div>
            )}
          </div>
        ) : (
          !ordersLoad.loading &&
          !ordersLoad.error && (
            <div className="py-20 text-center border border-dashed border-[#ddd] p-8">
              <ShoppingBag size={36} className="text-[#ccc] mx-auto mb-4" />
              <p className="text-base font-bold text-[#111] mb-1">
                Không có đơn hàng nào
              </p>
              <p className="text-xs text-[#888] mb-6">
                {status
                  ? 'Không tìm thấy đơn hàng nào ở trạng thái này.'
                  : 'Bạn chưa thực hiện đơn đặt hàng nào tại LINO.'}
              </p>
              <Link
                to="/products"
                className="inline-block bg-[#111] text-white px-6 py-3 text-xs uppercase tracking-widest font-bold hover:bg-[#E5001B]"
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
