import { customerError } from "../api/client";
import { useState } from "react";
import { Link, useNavigate } from "react-router";
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
} from "lucide-react";
import { useCart } from "../context/CartContext";
import { money } from "../api/client";
import { Status } from "../components/StoreUI";

const SHIPPING_THRESHOLD = 500000;

export default function Cart() {
  const {
    cart,
    count,
    total,
    loading,
    error,
    refresh,
    updateQty,
    removeItem,
    clearCart,
  } = useCart();
  const navigate = useNavigate();

  const [busyId, setBusyId] = useState<string>("");
  const [actionError, setActionError] = useState<string>("");

  async function handleQty(id: string, newQty: number) {
    if (newQty < 1) return;
    setBusyId(id);
    setActionError("");
    try {
      await updateQty(id, newQty);
    } catch (e) {
      setActionError(customerError(e));
    } finally {
      setBusyId("");
    }
  }

  async function handleRemove(id: string) {
    setBusyId(id);
    setActionError("");
    try {
      await removeItem(id);
    } catch (e) {
      setActionError(customerError(e));
    } finally {
      setBusyId("");
    }
  }

  async function handleClearAll() {
    if (
      !window.confirm(
        "Bạn có chắc chắn muốn xóa toàn bộ sản phẩm trong giỏ hàng?",
      )
    )
      return;
    setBusyId("ALL");
    setActionError("");
    try {
      await clearCart();
    } catch (e) {
      setActionError(customerError(e));
    } finally {
      setBusyId("");
    }
  }

  const freeShippingProgress = Math.min(
    100,
    Math.round((total / SHIPPING_THRESHOLD) * 100),
  );

  if (!loading && !error && cart && cart.items.length === 0) {
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
              <span className="text-foreground">Giỏ Hàng</span>
            </nav>
          </div>
        </div>

        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-28 text-center">
          <div className="w-20 h-20 mx-auto mb-6 bg-background flex items-center justify-center rounded-full text-muted-foreground">
            <ShoppingBag size={36} />
          </div>
          <h2 className="text-foreground font-semibold text-lg mb-3 text-balance">
            GIỎ HÀNG CỦA BẠN ĐANG TRỐNG
          </h2>
          <p className="text-sm text-muted-foreground max-w-sm mx-auto mb-8">
            Bạn chưa có sản phẩm nào trong giỏ hàng. Hãy khám phá các bộ sưu tập
            thời trang tối giản của LINO.
          </p>
          <Link
            to="/products"
            className="inline-flex items-center gap-3 bg-primary text-white px-8 py-4 text-xs font-semibold hover:bg-primary-hover active:scale-[0.98] transition-all store-action"
          >
            Khám Phá Sản Phẩm <ArrowRight size={14} />
          </Link>
        </div>
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
            <span className="text-foreground">Giỏ Hàng</span>
          </nav>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-10">
        <div className="flex flex-col sm:flex-row sm:items-end justify-between mb-8 pb-4 border-b border-border gap-4">
          <div>
            <p className="text-xs text-muted-foreground font-semibold mb-1">
              Đơn Hàng Của Bạn
            </p>
            <h1 className="text-foreground font-semibold text-xl leading-tight text-balance">
              GIỎ HÀNG ({count} SẢN PHẨM)
            </h1>
          </div>

          <button
            type="button"
            disabled={busyId === "ALL"}
            onClick={() => void handleClearAll()}
            className="text-xs text-muted-foreground hover:text-primary font-semibold underline underline-offset-4 self-start sm:self-auto store-button"
          >
            Xóa Tất Cả
          </button>
        </div>

        {/* Free Shipping Progress */}
        <div className="mb-8 p-4 bg-background border border-border">
          <div className="flex items-center gap-3 mb-2.5">
            <Truck size={18} className="text-foreground shrink-0" />
            <p className="text-xs text-foreground">
              {total >= SHIPPING_THRESHOLD ? (
                <span className="font-semibold text-success">
                  Chúc mừng! Đơn hàng của bạn đủ điều kiện MIỄN PHÍ VẬN CHUYỂN
                  toàn quốc.
                </span>
              ) : (
                <>
                  Mua thêm{" "}
                  <strong className="text-primary font-semibold">
                    {money(SHIPPING_THRESHOLD - total)}
                  </strong>{" "}
                  để được{" "}
                  <strong className="text-foreground">
                    MIỄN PHÍ VẬN CHUYỂN
                  </strong>
                </>
              )}
            </p>
          </div>
          <div className="w-full bg-secondary h-1.5 overflow-hidden">
            <div
              className={`h-full transition-all duration-500 ${
                total >= SHIPPING_THRESHOLD ? "bg-success" : "bg-foreground"
              }`}
              style={{ width: `${freeShippingProgress}%` }}
            />
          </div>
        </div>

        <Status loading={loading} error={error} retry={() => void refresh()} />

        {actionError && (
          <div className="mb-6 p-4 bg-primary-light border border-destructive/20 text-xs text-destructive flex items-center gap-2">
            <AlertCircle size={15} className="shrink-0 text-primary" />
            <span>{actionError}</span>
          </div>
        )}

        {cart && (
          <div className="grid grid-cols-1 lg:grid-cols-[1fr_380px] gap-6 items-start">
            {/* Items List */}
            <div className="divide-y divide-border border-y border-border">
              {cart.items.map((item) => {
                const isBusy = busyId === item.id;
                return (
                  <div
                    key={item.id}
                    className="py-6 flex flex-col sm:flex-row gap-5 items-start"
                  >
                    <div className="flex-1 flex gap-4 min-w-0">
                      <div className="w-20 h-26 bg-background shrink-0 overflow-hidden flex items-center justify-center">
                        <ShoppingBag
                          size={20}
                          className="text-muted-foreground"
                        />
                      </div>

                      <div className="min-w-0 flex-1">
                        <Link
                          to={`/products/${item.productId}`}
                          className="font-semibold text-sm text-foreground hover:text-primary transition-colors line-clamp-2 store-card-title"
                        >
                          {item.productName}
                        </Link>
                        <p className="text-xs text-muted-foreground mt-1">
                          Phân loại: {item.color || "Màu tiêu chuẩn"} ·{" "}
                          {item.size || "Size tiêu chuẩn"}
                        </p>
                        {item.sku && (
                          <p className="text-xs text-muted-foreground mt-0.5 font-mono">
                            SKU: {item.sku}
                          </p>
                        )}

                        <p className="text-sm font-semibold text-foreground mt-2">
                          {money(item.unitPrice)}
                        </p>

                        {/* Stock status indicator */}
                        {item.available === false && (
                          <p className="text-xs font-semibold text-primary mt-2 flex items-center gap-1">
                            <AlertCircle size={13} /> Sản phẩm hiện không đủ số
                            lượng tồn kho
                          </p>
                        )}
                        {item.available === null && (
                          <p className="text-xs text-muted-foreground mt-1">
                            Đang kiểm tra tồn kho tại kho hàng
                          </p>
                        )}
                      </div>
                    </div>

                    {/* Quantity & Actions */}
                    <div className="flex sm:flex-col items-center sm:items-end justify-between w-full sm:w-auto gap-4">
                      <div className="flex items-center border border-border-strong bg-white rounded-2xl">
                        <button
                          type="button"
                          aria-label={`Giảm số lượng ${item.productName}`}
                          disabled={isBusy || item.quantity <= 1}
                          onClick={() =>
                            void handleQty(item.id, item.quantity - 1)
                          }
                          className="w-8 h-8 flex items-center justify-center text-foreground hover:bg-background disabled:opacity-30 store-button"
                        >
                          <Minus size={13} />
                        </button>
                        <span className="w-10 text-center text-xs font-semibold text-foreground">
                          {item.quantity}
                        </span>
                        <button
                          type="button"
                          aria-label={`Tăng số lượng ${item.productName}`}
                          disabled={isBusy || item.quantity >= 99}
                          onClick={() =>
                            void handleQty(item.id, item.quantity + 1)
                          }
                          className="w-8 h-8 flex items-center justify-center text-foreground hover:bg-background disabled:opacity-30 store-button"
                        >
                          <Plus size={13} />
                        </button>
                      </div>

                      <div className="text-right">
                        <p className="text-sm font-semibold text-foreground">
                          {money(item.totalPrice)}
                        </p>
                        <button
                          type="button"
                          disabled={isBusy}
                          onClick={() => void handleRemove(item.id)}
                          className="text-xs text-muted-foreground hover:text-primary font-semibold mt-1 inline-flex items-center gap-1 store-button"
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
            <aside className="border border-border p-4 sm:p-5 bg-white space-y-6 rounded-2xl">
              <h2 className="text-lg font-semibold text-foreground pb-4 border-b border-border text-balance">
                TÓM TẮT ĐƠN HÀNG
              </h2>

              <div className="space-y-3 text-sm">
                <div className="flex justify-between text-muted-foreground">
                  <span>Tổng tiền hàng</span>
                  <span className="font-semibold text-foreground">
                    {money(total)}
                  </span>
                </div>
                <div className="flex justify-between text-muted-foreground">
                  <span>Vận chuyển</span>
                  <span className="text-xs text-muted-foreground">
                    Tính ở bước thanh toán
                  </span>
                </div>
                <p className="text-xs text-muted-foreground pt-1">
                  * Phí vận chuyển GHN và mã giảm giá sẽ được áp dụng chính xác
                  ở trang Thanh toán.
                </p>
              </div>

              <div className="pt-4 border-t border-border flex justify-between items-baseline">
                <span className="text-sm font-semibold text-foreground">
                  Tạm tính:
                </span>
                <span className="text-2xl font-semibold text-foreground">
                  {money(total)}
                </span>
              </div>

              <Link
                to="/checkout"
                className="w-full bg-primary text-white py-4 px-4 sm:px-6 text-xs font-semibold hover:bg-primary-hover active:scale-[0.98] transition-all block text-center store-action"
              >
                Tiến Hành Thanh Toán →
              </Link>

              <Link
                to="/products"
                className="w-full border border-border-strong text-foreground py-3 text-xs font-semibold hover:border-border-strong block text-center transition-colors store-action"
              >
                Tiếp Tục Mua Sắm
              </Link>

              <div className="pt-4 border-t border-border space-y-2 text-xs text-muted-foreground">
                <div className="flex items-center gap-2">
                  <ShieldCheck size={15} className="text-foreground shrink-0" />
                  <span>Bảo mật giao dịch trực tuyến SSL 256-bit</span>
                </div>
                <div className="flex items-center gap-2">
                  <Truck size={15} className="text-foreground shrink-0" />
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
