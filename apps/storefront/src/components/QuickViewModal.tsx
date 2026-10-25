"use client";

import { useEffect, useState, useMemo } from "react";
import { Link } from "react-router";
import { X, Check, ShoppingBag, ArrowRight, RefreshCw, Star, AlertCircle } from "lucide-react";
import { motion } from "motion/react";
import { store } from "../api/store";
import { media, money } from "../api/client";
import type { Product, Variant } from "../api/types";
import { useCart } from "../context/CartContext";
import { useAuth } from "../context/AuthContext";
import {
  Dialog,
  DialogContent,
  DialogTitle,
  DialogDescription,
} from "../app/components/ui/dialog";

interface QuickViewModalProps {
  productId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function QuickViewModal({
  productId,
  open,
  onOpenChange,
}: QuickViewModalProps) {
  const [product, setProduct] = useState<Product | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [selectedColor, setSelectedColor] = useState<string>("");
  const [selectedSize, setSelectedSize] = useState<string>("");
  const [activePhoto, setActivePhoto] = useState<string>("");
  const [busy, setBusy] = useState(false);
  const [justAdded, setJustAdded] = useState(false);
  const [feedback, setFeedback] = useState<string>("");

  const { addItem } = useCart();
  const { isLoggedIn, login } = useAuth();

  useEffect(() => {
    if (!open || !productId) return;
    setLoading(true);
    setError("");
    setSelectedColor("");
    setSelectedSize("");
    setFeedback("");
    setJustAdded(false);

    let active = true;
    store
      .product(productId)
      .then((data) => {
        if (active) {
          setProduct(data);
          const initialPhoto =
            data.images?.[0]?.url ||
            data.thumbnailUrl ||
            media(data.thumbnailMediaId);
          setActivePhoto(initialPhoto || "");

          // Preselect first available color
          if (data.variants?.length) {
            const firstColor =
              data.variants[0].colorOptionId || data.variants[0].color || "";
            setSelectedColor(firstColor);
          }
        }
      })
      .catch(() => {
        if (active) setError("Không thể tải thông tin sản phẩm.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [open, productId]);

  const activeVariants = useMemo(
    () => product?.variants?.filter((v) => v.active !== false) || [],
    [product?.variants],
  );

  // Available colors
  const colorOptions = useMemo(() => {
    const map = new Map<string, Variant>();
    activeVariants.forEach((v) => {
      const key = v.colorOptionId || v.color;
      if (key && !map.has(key)) map.set(key, v);
    });
    return Array.from(map.values());
  }, [activeVariants]);

  // Available sizes for chosen color
  const sizeOptions = useMemo(() => {
    const map = new Map<string, Variant>();
    const filtered = selectedColor
      ? activeVariants.filter(
          (v) => (v.colorOptionId || v.color) === selectedColor,
        )
      : activeVariants;
    filtered.forEach((v) => {
      const key = v.sizeOptionId || v.size;
      if (key && !map.has(key)) map.set(key, v);
    });
    return Array.from(map.values());
  }, [activeVariants, selectedColor]);

  // Matching variant
  const selectedVariant = useMemo(() => {
    if (!selectedColor || !selectedSize) return null;
    return (
      activeVariants.find(
        (v) =>
          (v.colorOptionId || v.color) === selectedColor &&
          (v.sizeOptionId || v.size) === selectedSize,
      ) || null
    );
  }, [activeVariants, selectedColor, selectedSize]);

  // Dynamic photo based on color
  useEffect(() => {
    if (!product || !selectedColor) return;
    const colorImg = product.images?.find(
      (img) => img.colorOptionId === selectedColor,
    );
    if (colorImg?.url) {
      setActivePhoto(colorImg.url);
    }
  }, [selectedColor, product]);

  const effectivePrice =
    product?.salePrice ?? product?.price ?? product?.basePrice ?? 0;
  const isSale =
    product?.salePrice != null && product.salePrice < product.basePrice;

  async function handleAddToCart() {
    if (!isLoggedIn) {
      onOpenChange(false);
      login(`/products/${productId}`);
      return;
    }
    if (!selectedColor || !selectedSize || !selectedVariant) {
      setFeedback("Vui lòng chọn màu sắc và kích cỡ.");
      return;
    }
    if (selectedVariant.available === false) {
      setFeedback("Biến thể này hiện đã hết hàng.");
      return;
    }

    setBusy(true);
    setFeedback("");
    try {
      await addItem(selectedVariant.id, 1);
      setJustAdded(true);
      setTimeout(() => setJustAdded(false), 2000);
    } catch {
      setFeedback("Không thể thêm vào giỏ hàng. Vui lòng thử lại.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl p-0 gap-0 overflow-hidden rounded-3xl bg-card border border-border shadow-2xl">
        <DialogTitle className="sr-only">
          {product?.name || "Xem nhanh sản phẩm"}
        </DialogTitle>
        <DialogDescription className="sr-only">
          Xem nhanh thông tin chi tiết và chọn size mua hàng
        </DialogDescription>

        {loading ? (
          <div className="flex items-center justify-center py-24 gap-3 text-sm text-muted-foreground">
            <RefreshCw size={20} className="animate-spin text-primary" />
            <span>Đang tải thông tin sản phẩm…</span>
          </div>
        ) : error ? (
          <div className="p-8 text-center text-sm text-destructive">
            {error}
          </div>
        ) : product ? (
          <div className="grid sm:grid-cols-2 gap-0">
            {/* Left Image Section */}
            <div className="relative aspect-[3/4] bg-background border-b sm:border-b-0 sm:border-r border-border overflow-hidden">
              {activePhoto ? (
                <img
                  src={activePhoto}
                  alt={product.name}
                  className="size-full object-cover"
                />
              ) : (
                <div className="size-full flex items-center justify-center text-sm text-muted-foreground">
                  LINO Collection
                </div>
              )}
              {isSale && (
                <span className="absolute top-3 left-3 rounded-full bg-destructive px-2.5 py-1 text-[11px] font-semibold text-white shadow-xs">
                  Sale -{Math.round((1 - effectivePrice / product.basePrice) * 100)}%
                </span>
              )}
            </div>

            {/* Right Information Section */}
            <div className="p-6 flex flex-col justify-between gap-5 overflow-y-auto max-h-[85vh]">
              <div>
                <p className="text-xs text-muted-foreground font-semibold uppercase tracking-wider">
                  {product.categoryName || product.brandName || "LINO"}
                </p>
                <h3 className="mt-1 text-lg font-bold text-foreground leading-snug">
                  {product.name}
                </h3>

                <div className="mt-2.5 flex items-baseline gap-2 tabular-nums">
                  <span className="text-xl font-bold text-foreground">
                    {money(effectivePrice)}
                  </span>
                  {isSale && (
                    <span className="text-xs text-muted-foreground line-through">
                      {money(product.basePrice)}
                    </span>
                  )}
                </div>

                <div className="mt-2 flex items-center gap-1.5 text-xs text-amber-500">
                  <div className="flex items-center">
                    {Array.from({ length: 5 }).map((_, i) => (
                      <Star
                        key={i}
                        size={13}
                        className="fill-amber-400 stroke-amber-400"
                      />
                    ))}
                  </div>
                  <span className="font-semibold text-foreground">5.0</span>
                  <span className="text-muted-foreground">(Đánh giá cao)</span>
                </div>

                {/* Color Selection */}
                {colorOptions.length > 0 && (
                  <div className="mt-5">
                    <p className="text-xs font-semibold text-foreground mb-2 flex items-center justify-between">
                      <span>Màu sắc:</span>
                      <span className="font-normal text-muted-foreground">
                        {colorOptions.find(
                          (c) => (c.colorOptionId || c.color) === selectedColor,
                        )?.color || "Chưa chọn"}
                      </span>
                    </p>
                    <div className="flex flex-wrap gap-2">
                      {colorOptions.map((v) => {
                        const key = v.colorOptionId || v.color || "";
                        const isChosen = selectedColor === key;
                        return (
                          <button
                            key={key}
                            type="button"
                            onClick={() => {
                              setSelectedColor(key);
                              setSelectedSize("");
                              setFeedback("");
                            }}
                            className={`size-8 rounded-full border-2 p-0.5 transition-all ${
                              isChosen
                                ? "border-primary scale-110 shadow-xs"
                                : "border-border hover:border-foreground/40"
                            }`}
                            title={v.color}
                          >
                            <span
                              className="block size-full rounded-full border border-black/10"
                              style={{ background: v.colorHex || "#ccc" }}
                            />
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* Size Selection */}
                {sizeOptions.length > 0 && (
                  <div className="mt-4">
                    <p className="text-xs font-semibold text-foreground mb-2 flex items-center justify-between">
                      <span>Kích cỡ:</span>
                      <span className="font-normal text-muted-foreground">
                        {selectedSize || "Chưa chọn"}
                      </span>
                    </p>
                    <div className="flex flex-wrap gap-2">
                      {sizeOptions.map((v) => {
                        const key = v.sizeOptionId || v.size || "";
                        const isChosen = selectedSize === key;
                        const isSoldOut = v.available === false;
                        return (
                          <button
                            key={key}
                            type="button"
                            disabled={isSoldOut}
                            onClick={() => {
                              setSelectedSize(key);
                              setFeedback("");
                            }}
                            className={`min-w-10 h-9 rounded-xl border px-3 text-xs font-semibold transition-all ${
                              isChosen
                                ? "border-primary bg-primary text-white shadow-xs"
                                : isSoldOut
                                  ? "border-border/60 bg-muted/40 text-muted-foreground line-through opacity-50 cursor-not-allowed"
                                  : "border-border bg-card text-foreground hover:border-foreground/40 active:scale-95"
                            }`}
                          >
                            {v.size || "Size"}
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}

                {feedback && (
                  <p className="mt-3 text-xs font-medium text-destructive flex items-center gap-1.5">
                    <AlertCircle size={13} /> {feedback}
                  </p>
                )}
              </div>

              {/* Actions */}
              <div className="space-y-2 pt-4 border-t border-border">
                <button
                  type="button"
                  disabled={busy}
                  onClick={handleAddToCart}
                  className={`w-full py-3.5 rounded-full text-xs font-semibold flex items-center justify-center gap-2 transition-all shadow-md ${
                    justAdded
                      ? "bg-emerald-600 text-white border-emerald-600"
                      : "bg-primary text-white hover:bg-primary-hover active:scale-[0.98]"
                  }`}
                >
                  {busy ? (
                    <>
                      <RefreshCw size={15} className="animate-spin" />
                      <span>Đang xử lý…</span>
                    </>
                  ) : justAdded ? (
                    <motion.span
                      initial={{ scale: 0.8 }}
                      animate={{ scale: 1 }}
                      className="flex items-center gap-2"
                    >
                      <Check size={16} /> Đã thêm vào giỏ hàng!
                    </motion.span>
                  ) : (
                    <>
                      <ShoppingBag size={16} /> Thêm Vào Giỏ Hàng
                    </>
                  )}
                </button>

                <Link
                  to={`/products/${productId}`}
                  onClick={() => onOpenChange(false)}
                  className="w-full py-2.5 rounded-full text-xs font-semibold text-muted-foreground hover:text-foreground text-center block transition-colors"
                >
                  Xem trang chi tiết đầy đủ <ArrowRight size={12} className="inline ml-1" />
                </Link>
              </div>
            </div>
          </div>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
