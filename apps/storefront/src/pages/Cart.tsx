import { motion, AnimatePresence } from "motion/react";
import { customerError } from "../api/client";
import { useState, useEffect } from "react";
import { store } from "../api/store";
import { media } from "../api/client";
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
  Check,
  Sparkles,
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
  const [thumbnails, setThumbnails] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!cart?.items.length) return;
    const pids = Array.from(new Set(cart.items.map((i) => i.productId)));
    pids.forEach((pid) => {
      if (!thumbnails[pid]) {
        store.product(pid).then((p) => {
          const img = p.thumbnailUrl || media(p.thumbnailMediaId) || p.images?.[0]?.url || media(p.images?.[0]?.mediaId);
          if (img) {
            setThumbnails((prev) => ({ ...prev, [pid]: img }));
          }
        }).catch(() => {});
      }
    });
  }, [cart?.items]);

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

  if (!cart) {
    if (loading) {
      return (
        <div className="w-full">
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
          <div className="max-w-7xl mx-auto px-4 sm:px-6 py-28">
            <Status loading={true} />
          </div>
        </div>
      );
    }
    if (error) {
      return (
        <div className="w-full">
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
          <div className="max-w-7xl mx-auto px-4 sm:px-6 py-28">
            <Status loading={false} error={error} retry={() => void refresh()} />
          </div>
        </div>
      );
    }
  }

  if (cart && cart.items.length === 0) {
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
        <div className="mb-8 p-5 bg-card border border-border rounded-2xl shadow-2xs">
          <div className="flex items-center justify-between gap-3 mb-3">
            <div className="flex items-center gap-2.5">
              <div className={`size-8 rounded-full flex items-center justify-center shrink-0 ${
                total >= SHIPPING_THRESHOLD ? "bg-emerald-100 text-emerald-700" : "bg-primary/10 text-primary"
              }`}>
                {total >= SHIPPING_THRESHOLD ? <Check size={16} /> : <Truck size={16} />}
              </div>
              <p className="text-xs text-foreground">
                {total >= SHIPPING_THRESHOLD ? (
                  <span className="font-semibold text-emerald-700">
                    Tuyệt vời! Đơn hàng của bạn đã đạt điều kiện MIỄN PHÍ VẬN CHUYỂN toàn quốc!
                  </span>
                ) : (
                  <>
                    Mua thêm{" "}
                    <strong className="text-primary font-bold">
                      {money(SHIPPING_THRESHOLD - total)}
                    </strong>{" "}
                    để nhận ngay{" "}
                    <strong className="text-foreground font-semibold">
                      MIỄN PHÍ VẬN CHUYỂN
                    </strong>
                  </>
                )}
              </p>
            </div>
            <span className="text-xs font-semibold text-muted-foreground hidden sm:inline tabular-nums">
              {Math.min(100, Math.round((total / SHIPPING_THRESHOLD) * 100))}%
            </span>
          </div>

          <div className="w-full bg-secondary h-2 rounded-full overflow-hidden relative">
            <motion.div
              initial={{ width: 0 }}
              animate={{ width: `${Math.min(100, (total / SHIPPING_THRESHOLD) * 100)}%` }}
              transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
              className={`h-full rounded-full transition-colors ${
                total >= SHIPPING_THRESHOLD ? "bg-emerald-600" : "bg-primary"
              }`}
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
            <div className="border-y border-border">
              <AnimatePresence mode="popLayout" initial={false}>
                {cart.items.map((item) => {
                  const isBusy = busyId === item.id;
                  return (
                    <motion.div
                      key={item.id}
                      layout
                      initial={{ opacity: 0, height: 0 }}
                      animate={{ opacity: 1, height: "auto" }}
                      exit={{
                        opacity: 0,
                        x: -30,
                        height: 0,
                        paddingTop: 0,
                        paddingBottom: 0,
                        transition: {
                          opacity: { duration: 0.2 },
                          height: { duration: 0.3, ease: [0.33, 0.17, 0.67, 0.5] },
                          x: { duration: 0.25 },
                        },
                      }}
                      transition={{ type: "spring", stiffness: 350, damping: 30 }}
                      className="py-6 flex flex-col sm:flex-row gap-5 items-start border-b border-border last:border-b-0 overflow-hidden"
                    >
                    <div className="flex-1 flex gap-4 min-w-0">
                      <div className="w-20 h-26 bg-background rounded-xl border border-border/70 shrink-0 overflow-hidden flex items-center justify-center">
                        {thumbnails[item.productId] ? (
                          <img
                            src={thumbnails[item.productId]}
                            alt={item.productName}
                            className="w-full h-full object-cover transition-transform duration-300 hover:scale-105"
                          />
                        ) : (
                          <div className="flex flex-col items-center justify-center gap-1 text-muted-foreground">
                            <ShoppingBag size={18} />
                            <span className="text-[10px] font-mono">LINO</span>
                          </div>
                        )}
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
                    </motion.div>
                  );
                })}
              </AnimatePresence>
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
