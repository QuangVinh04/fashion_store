import { motion, AnimatePresence } from "motion/react";
import { ShoppingBag, Check, RefreshCw, ArrowUp } from "lucide-react";
import { media, money } from "../api/client";
import { ease, spring } from "../app/components/ui/animate-tokens";

interface StickyQuickBuyBarProps {
  product: {
    id: string;
    name: string;
    thumbnailUrl?: string;
    thumbnailMediaId?: string | null;
    basePrice: number;
  };
  displayPrice: number;
  isSale: boolean;
  selectedColorName?: string;
  selectedSizeName?: string;
  busy: boolean;
  justAdded: boolean;
  disabled: boolean;
  visible: boolean;
  onAddToCart: () => void;
  onBuyNow: () => void;
  onScrollToOptions: () => void;
}

export function StickyQuickBuyBar({
  product,
  displayPrice,
  isSale,
  selectedColorName,
  selectedSizeName,
  busy,
  justAdded,
  disabled,
  visible,
  onAddToCart,
  onBuyNow,
  onScrollToOptions,
}: StickyQuickBuyBarProps) {
  const photoUrl = product.thumbnailUrl || media(product.thumbnailMediaId);
  const hasOptionsSelected = Boolean(selectedColorName && selectedSizeName);

  return (
    <AnimatePresence>
      {visible && (
        <motion.aside
          aria-label="Thanh mua hàng nhanh"
          initial={{ y: "100%", opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          exit={{ y: "100%", opacity: 0 }}
          transition={{ duration: 0.28, ease: ease.soft }}
          className="fixed bottom-0 inset-x-0 z-40 border-t border-border/80 bg-card/90 backdrop-blur-md shadow-lg py-2.5 px-4 sm:px-6"
        >
          <div className="max-w-7xl mx-auto flex items-center justify-between gap-4">
            {/* Product summary snippet */}
            <div className="flex items-center gap-3 min-w-0">
              <div className="size-11 shrink-0 overflow-hidden rounded-lg border border-border bg-background">
                {photoUrl ? (
                  <img
                    src={photoUrl}
                    alt={product.name}
                    className="size-full object-cover"
                  />
                ) : (
                  <div className="size-full flex items-center justify-center bg-secondary text-xs text-muted-foreground">
                    LINO
                  </div>
                )}
              </div>
              <div className="min-w-0">
                <p className="text-xs font-semibold text-foreground truncate max-w-[180px] sm:max-w-xs md:max-w-sm">
                  {product.name}
                </p>
                <div className="flex items-center gap-2 text-xs">
                  <span className="font-bold text-foreground">
                    {money(displayPrice)}
                  </span>
                  {isSale && (
                    <span className="text-muted-foreground line-through text-[11px]">
                      {money(product.basePrice)}
                    </span>
                  )}
                  {hasOptionsSelected ? (
                    <span className="hidden sm:inline-block text-muted-foreground">
                      • {selectedColorName} / {selectedSizeName}
                    </span>
                  ) : (
                    <button
                      type="button"
                      onClick={onScrollToOptions}
                      className="hidden sm:inline-flex items-center gap-1 text-primary hover:underline font-medium text-[11px]"
                    >
                      <span>Chọn phân loại</span>
                      <ArrowUp size={11} />
                    </button>
                  )}
                </div>
              </div>
            </div>

            {/* Actions */}
            <div className="flex items-center gap-2 shrink-0">
              {!hasOptionsSelected && (
                <button
                  type="button"
                  onClick={onScrollToOptions}
                  className="sm:hidden text-xs text-primary font-semibold underline px-2 py-1"
                >
                  Chọn Size
                </button>
              )}
              <button
                type="button"
                disabled={disabled || busy}
                onClick={onAddToCart}
                className={`relative flex items-center justify-center gap-1.5 h-10 px-4 rounded-xl text-xs font-semibold transition-all duration-200 active:scale-[0.97] ${
                  justAdded
                    ? "bg-emerald-600 text-white"
                    : "bg-secondary text-foreground hover:bg-secondary/80 border border-border"
                } disabled:opacity-40 disabled:cursor-not-allowed`}
              >
                {busy ? (
                  <RefreshCw size={14} className="animate-spin" />
                ) : justAdded ? (
                  <motion.span
                    initial={{ scale: 0.8 }}
                    animate={{ scale: 1 }}
                    transition={spring.pop}
                    className="flex items-center gap-1"
                  >
                    <Check size={14} />
                    <span>Đã thêm!</span>
                  </motion.span>
                ) : (
                  <>
                    <ShoppingBag size={14} />
                    <span className="hidden sm:inline">Thêm vào giỏ</span>
                  </>
                )}
              </button>

              <button
                type="button"
                disabled={disabled}
                onClick={onBuyNow}
                className="flex items-center justify-center h-10 px-5 rounded-xl bg-primary text-primary-foreground text-xs font-semibold hover:bg-primary-hover active:scale-[0.97] transition-all disabled:opacity-40 disabled:cursor-not-allowed shadow-xs"
              >
                Mua ngay
              </button>
            </div>
          </div>
        </motion.aside>
      )}
    </AnimatePresence>
  );
}
