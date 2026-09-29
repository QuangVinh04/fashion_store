import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import {
  X,
  Minus,
  Plus,
  ShoppingBag,
  ArrowRight,
  Truck,
  Trash2,
  ShieldCheck,
  AlertCircle,
  RefreshCw,
} from 'lucide-react';
import { useCart } from '../context/CartContext';
import { money } from '../api/client';
import { Status } from '../components/StoreUI';

const SHIPPING_THRESHOLD = 500000;

export default function Cart() {
  const { cart, count, total, loading, error, refresh, updateQty, removeItem, clearCart } =
    useCart();
  const navigate = useNavigate();

  const [busyId, setBusyId] = useState<string>('');
  const [actionError, setActionError] = useState<string>('');

  async function handleQty(id: string, newQty: number) {
    if (newQty < 1) return;
    setBusyId(id);
    setActionError('');
    try {
      await updateQty(id, newQty);
    } catch (e) {
      setActionError((e as Error).message);
    } finally {
      setBusyId('');
    }
  }

  async function handleRemove(id: string) {
    setBusyId(id);
    setActionError('');
    try {
      await removeItem(id);
    } catch (e) {
      setActionError((e as Error).message);
    } finally {
      setBusyId('');
    }
  }

  async function handleClearAll() {
    if (!window.confirm('Bạn có chắc chắn muốn xóa toàn bộ sản phẩm trong giỏ hàng?')) return;
    setBusyId('ALL');
    setActionError('');
    try {
      await clearCart();
    } catch (e) {
      setActionError((e as Error).message);
    } finally {
      setBusyId('');
    }
  }

  const freeShippingProgress = Math.min(100, Math.round((total / SHIPPING_THRESHOLD) * 100));

  if (!loading && (!cart || cart.items.length === 0)) {
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
              <span className="text-[#111]">Giỏ Hàng</span>
            </nav>
          </div>
        </div>

        <div className="max-w-[1400px] mx-auto px-6 py-28 text-center">
          <div className="w-20 h-20 mx-auto mb-6 bg-[#f5f5f5] flex items-center justify-center rounded-full text-[#aaa]">
            <ShoppingBag size={36} />
          </div>
          <h2
            className="text-[#111] font-black uppercase text-3xl mb-3"
            style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
          >
            GIỎ HÀNG CỦA BẠN ĐANG TRỐNG
          </h2>
          <p className="text-sm text-[#777] max-w-sm mx-auto mb-8" style={{ fontWeight: 300 }}>
            Bạn chưa có sản phẩm nào trong giỏ hàng. Hãy khám phá các bộ sưu tập thời trang tối giản của LINO.
          </p>
          <Link
            to="/products"
            className="inline-flex items-center gap-3 bg-[#111] text-white px-8 py-4 text-xs tracking-widest uppercase font-bold hover:bg-[#E5001B] active:scale-[0.98] transition-all"
          >
            Khám Phá Sản Phẩm <ArrowRight size={14} />
          </Link>
        </div>
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
            <span className="text-[#111]">Giỏ Hàng</span>
          </nav>
        </div>
      </div>

      <div className="max-w-[1400px] mx-auto px-6 py-10">
        <div className="flex flex-col sm:flex-row sm:items-end justify-between mb-8 pb-4 border-b border-[rgba(0,0,0,0.08)] gap-4">
          <div>
            <p className="text-xs uppercase tracking-[0.25em] text-[#888] font-bold mb-1">
              Đơn Hàng Của Bạn
            </p>
            <h1
              className="text-[#111] font-black uppercase text-3xl md:text-4xl leading-tight"
              style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
            >
              GIỎ HÀNG ({count} SẢN PHẨM)
            </h1>
          </div>

          <button
            type="button"
            disabled={busyId === 'ALL'}
            onClick={() => void handleClearAll()}
            className="text-xs uppercase tracking-widest text-[#888] hover:text-[#E5001B] font-semibold underline underline-offset-4 self-start sm:self-auto"
          >
            Xóa Tất Cả
          </button>
        </div>

        {/* Free Shipping Progress */}
        <div className="mb-8 p-4 bg-[#fafafa] border border-[rgba(0,0,0,0.08)]">
          <div className="flex items-center gap-3 mb-2.5">
            <Truck size={18} className="text-[#111] shrink-0" />
            <p className="text-xs text-[#333]">
              {total >= SHIPPING_THRESHOLD ? (
                <span className="font-bold text-[#2D5A3D]">
                  Chúc mừng! Đơn hàng của bạn đủ điều kiện MIỄN PHÍ VẬN CHUYỂN toàn quốc.
                </span>
              ) : (
                <>
                  Mua thêm{' '}
                  <strong className="text-[#E5001B] font-bold">
                    {money(SHIPPING_THRESHOLD - total)}
                  </strong>{' '}
                  để được <strong className="text-[#111]">MIỄN PHÍ VẬN CHUYỂN</strong>
                </>
              )}
            </p>
          </div>
          <div className="w-full bg-[#e5e5e5] h-1.5 overflow-hidden">
            <div
              className={`h-full transition-all duration-500 ${
                total >= SHIPPING_THRESHOLD ? 'bg-[#2D5A3D]' : 'bg-[#111]'
              }`}
              style={{ width: `${freeShippingProgress}%` }}
            />
          </div>
        </div>

        <Status loading={loading} error={error} retry={() => void refresh()} />

        {actionError && (
          <div className="mb-6 p-4 bg-red-50 border border-red-200 text-xs text-[#B00018] flex items-center gap-2">
            <AlertCircle size={15} className="shrink-0 text-[#E5001B]" />
            <span>{actionError}</span>
          </div>
        )}

        {cart && (
          <div className="grid grid-cols-1 lg:grid-cols-[1fr_380px] gap-12 items-start">
            {/* Items List */}
            <div className="divide-y divide-[rgba(0,0,0,0.08)] border-y border-[rgba(0,0,0,0.08)]">
              {cart.items.map((item) => {
                const isBusy = busyId === item.id;
                return (
                  <div key={item.id} className="py-6 flex flex-col sm:flex-row gap-5 items-start">
                    <div className="flex-1 flex gap-4 min-w-0">
                      <div className="w-20 h-26 bg-[#f5f5f5] shrink-0 overflow-hidden flex items-center justify-center">
                        <ShoppingBag size={20} className="text-[#bbb]" />
                      </div>

                      <div className="min-w-0 flex-1">
                        <Link
                          to={`/products/${item.productId}`}
                          className="font-semibold text-sm text-[#111] hover:text-[#E5001B] transition-colors line-clamp-1"
                        >
                          {item.productName}
                        </Link>
                        <p className="text-xs text-[#777] mt-1">
                          Phân loại: {item.color || 'Màu tiêu chuẩn'} · {item.size || 'Size tiêu chuẩn'}
                        </p>
                        {item.sku && <p className="text-[11px] text-[#aaa] mt-0.5 font-mono">SKU: {item.sku}</p>}

                        <p className="text-sm font-bold text-[#111] mt-2">{money(item.unitPrice)}</p>

                        {/* Stock status indicator */}
                        {item.available === false && (
                          <p className="text-xs font-semibold text-[#E5001B] mt-2 flex items-center gap-1">
                            <AlertCircle size={13} /> Sản phẩm hiện không đủ số lượng tồn kho
                          </p>
                        )}
                        {item.available === null && (
                          <p className="text-[11px] text-[#888] mt-1">Đang kiểm tra tồn kho tại kho hàng</p>
                        )}
                      </div>
                    </div>

                    {/* Quantity & Actions */}
                    <div className="flex sm:flex-col items-center sm:items-end justify-between w-full sm:w-auto gap-4">
                      <div className="flex items-center border border-[#ddd] bg-white">
                        <button
                          type="button"
                          disabled={isBusy || item.quantity <= 1}
                          onClick={() => void handleQty(item.id, item.quantity - 1)}
                          className="w-8 h-8 flex items-center justify-center text-[#111] hover:bg-[#f5f5f5] disabled:opacity-30"
                        >
                          <Minus size={13} />
                        </button>
                        <span className="w-10 text-center text-xs font-bold text-[#111]">
                          {item.quantity}
                        </span>
                        <button
                          type="button"
                          disabled={isBusy || item.quantity >= 99}
                          onClick={() => void handleQty(item.id, item.quantity + 1)}
                          className="w-8 h-8 flex items-center justify-center text-[#111] hover:bg-[#f5f5f5] disabled:opacity-30"
                        >
                          <Plus size={13} />
                        </button>
                      </div>

                      <div className="text-right">
                        <p className="text-sm font-bold text-[#111]">{money(item.totalPrice)}</p>
                        <button
                          type="button"
                          disabled={isBusy}
                          onClick={() => void handleRemove(item.id)}
                          className="text-xs uppercase tracking-wider text-[#888] hover:text-[#E5001B] font-semibold mt-1 inline-flex items-center gap-1"
                        >
                          <Trash2 size={12} /> Xóa
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Order Summary Card */}
            <aside className="border border-[rgba(0,0,0,0.1)] p-6 md:p-8 bg-white space-y-6">
              <h2
                className="text-xl font-bold uppercase tracking-wide text-[#111] pb-4 border-b border-[rgba(0,0,0,0.08)]"
                style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
              >
                TÓM TẮT ĐƠN HÀNG
              </h2>

              <div className="space-y-3 text-sm">
                <div className="flex justify-between text-[#555]">
                  <span>Tổng tiền hàng</span>
                  <span className="font-semibold text-[#111]">{money(total)}</span>
                </div>
                <div className="flex justify-between text-[#555]">
                  <span>Vận chuyển</span>
                  <span className="text-xs text-[#888]">Tính ở bước thanh toán</span>
                </div>
                <p className="text-[11px] text-[#888] pt-1">
                  * Phí vận chuyển GHN và mã giảm giá sẽ được áp dụng chính xác ở trang Thanh toán.
                </p>
              </div>

              <div className="pt-4 border-t border-[rgba(0,0,0,0.08)] flex justify-between items-baseline">
                <span className="text-sm uppercase font-bold tracking-widest text-[#111]">
                  Tạm tính:
                </span>
                <span className="text-2xl font-black text-[#111]">{money(total)}</span>
              </div>

              <Link
                to="/checkout"
                className="w-full bg-[#111] text-white py-4 px-6 text-xs uppercase tracking-widest font-bold hover:bg-[#E5001B] active:scale-[0.98] transition-all block text-center"
              >
                Tiến Hành Thanh Toán →
              </Link>

              <Link
                to="/products"
                className="w-full border border-[#ddd] text-[#111] py-3 text-xs uppercase tracking-widest font-semibold hover:border-[#111] block text-center transition-colors"
              >
                Tiếp Tục Mua Sắm
              </Link>

              <div className="pt-4 border-t border-[rgba(0,0,0,0.08)] space-y-2 text-xs text-[#777]">
                <div className="flex items-center gap-2">
                  <ShieldCheck size={15} className="text-[#111] shrink-0" />
                  <span>Bảo mật giao dịch trực tuyến SSL 256-bit</span>
                </div>
                <div className="flex items-center gap-2">
                  <Truck size={15} className="text-[#111] shrink-0" />
                  <span>Giao hàng tận nơi toàn quốc qua GHN</span>
                </div>
              </div>
            </aside>
          </div>
        )}
      </div>
    </div>
  );
}
