import { customerError } from "../api/client";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router";
import { Heart, Star, AlertCircle, RefreshCw, ShoppingBag } from "lucide-react";
import { media, money } from "../api/client";
import type { OrderStatus, ProductSummary } from "../api/types";
import { ORDER_LABEL } from "../api/types";
import { store } from "../api/store";
import { useAuth } from "../context/AuthContext";

export function useLoad<T>(load: () => Promise<T>, keys: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setData(await load());
      setError("");
    } catch (e) {
      setError(customerError(e));
    } finally {
      setLoading(false);
    }
  }, keys); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    void refresh();
  }, [refresh]);
  return { data, loading, error, refresh, setData };
}

export function Status({
  loading,
  error,
  retry,
}: {
  loading: boolean;
  error?: string;
  retry?: () => void;
}) {
  if (loading) {
    return (
      <div
        role="status"
        className="flex items-center justify-center py-16 gap-3 text-sm text-muted-foreground"
      >
        <RefreshCw size={18} className="animate-spin text-foreground" />
        <span>Đang tải dữ liệu…</span>
      </div>
    );
  }
  if (error) {
    return (
      <div
        role="alert"
        className="my-4 rounded-xl bg-primary-light p-4 text-sm text-destructive flex flex-col sm:flex-row sm:items-center justify-between gap-3"
      >
        <div className="flex min-w-0 items-center gap-3">
          <AlertCircle size={18} className="shrink-0 text-primary" />
          <span className="min-w-0 text-pretty">{error}</span>
        </div>
        {retry && (
          <button
            onClick={retry}
            className="min-h-9 self-start shrink-0 rounded-lg px-3 text-sm font-medium text-foreground hover:bg-white/60 store-button"
          >
            Thử lại
          </button>
        )}
      </div>
    );
  }
  return null;
}

export function PageTitle({
  eyebrow,
  children,
}: {
  eyebrow?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="mb-6">
      {eyebrow && (
        <p className="mb-2 text-xs text-muted-foreground font-medium">
          {eyebrow}
        </p>
      )}
      <h1
        className="text-xl text-foreground font-semibold leading-snug text-balance"
        style={{
          fontFamily: "var(--app-font)",
        }}
      >
        {children}
      </h1>
    </div>
  );
}

export function ProductPhoto({ src, name }: { src?: string; name: string }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [src]);
  if (!src || failed) {
    return (
      <div
        className="flex h-full w-full flex-col items-center justify-center bg-background text-xs text-muted-foreground gap-2 select-none"
        aria-label={`${name}: chưa có ảnh`}
      >
        <ShoppingBag size={24} className="text-border-strong" />
        <span>LINO Collection</span>
      </div>
    );
  }
  return (
    <img
      loading="lazy"
      src={src}
      alt={name}
      className="h-full w-full object-cover transition-transform duration-700 ease-out group-hover:scale-105"
      onError={() => setFailed(true)}
    />
  );
}

export function ProductCard({
  product,
  onWishlistChange,
}: {
  product: ProductSummary;
  onWishlistChange?: (productId: string, inWishlist: boolean) => void;
}) {
  const { isLoggedIn, login } = useAuth();
  const [liked, setLiked] = useState(false);
  const [busy, setBusy] = useState(false);
  const [wishError, setWishError] = useState("");

  useEffect(() => {
    if (!isLoggedIn) {
      setLiked(false);
      return;
    }
    let active = true;
    void store
      .wishCheck(product.id)
      .then((result) => {
        if (active) setLiked(result.inWishlist);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [isLoggedIn, product.id]);

  async function toggleWish(e: React.MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    if (!isLoggedIn) {
      login(`/products/${product.id}`);
      return;
    }
    setBusy(true);
    setWishError("");
    try {
      if (liked) {
        await store.wishRemove(product.id);
        setLiked(false);
        onWishlistChange?.(product.id, false);
      } else {
        await store.wishAdd(product.id);
        setLiked(true);
        onWishlistChange?.(product.id, true);
      }
    } catch {
      setWishError("Không cập nhật được yêu thích. Vui lòng thử lại.");
    } finally {
      setBusy(false);
    }
  }

  const effectivePrice =
    product.salePrice ?? product.price ?? product.basePrice;
  const isSale =
    product.salePrice != null && product.salePrice < product.basePrice;
  const photoUrl = product.thumbnailUrl || media(product.thumbnailMediaId);

  return (
    <article className="group min-w-0 flex flex-col overflow-hidden rounded-2xl border border-border bg-card">
      <div className="relative aspect-[3/4] bg-background overflow-hidden">
        <Link
          to={`/products/${product.id}`}
          aria-label={product.name}
          className="block h-full w-full"
        >
          <ProductPhoto src={photoUrl} name={product.name} />
        </Link>

        {/* Sale / Status Tag */}
        {isSale && (
          <span className="absolute top-3 left-3 rounded-md bg-primary-light text-destructive text-xs font-medium px-2 py-1 pointer-events-none">
            Sale
          </span>
        )}

        {/* Wishlist Button */}
        <button
          type="button"
          disabled={busy}
          onClick={toggleWish}
          aria-label={liked ? "Bỏ yêu thích" : "Yêu thích"}
          aria-pressed={liked}
          className="absolute right-3 top-3 flex size-9 items-center justify-center rounded-lg bg-white text-foreground transition-colors hover:bg-primary-light disabled:opacity-45"
        >
          <Heart
            size={16}
            className={
              liked ? "fill-primary stroke-primary" : "stroke-foreground"
            }
          />
        </button>
      </div>

      <div className="flex flex-1 flex-col justify-between gap-3 p-4">
        {wishError && (
          <div role="alert" className="rounded-lg bg-primary-light p-3 text-sm text-destructive">
            <p>{wishError}</p>
            <button type="button" disabled={busy} onClick={toggleWish} className="store-button underline">Thử lại</button>
          </div>
        )}
        <div>
          <p className="text-xs text-muted-foreground font-medium truncate">
            {product.categoryName || product.brandName || "LINO"}
          </p>
          <Link
            to={`/products/${product.id}`}
            className="store-card-title mt-1 block text-base font-medium text-foreground leading-snug hover:text-primary transition-colors line-clamp-2"
          >
            {product.name}
          </Link>
        </div>

        <div className="flex flex-wrap items-baseline gap-2 tabular-nums">
          <span className="text-sm font-semibold text-foreground">
            {money(effectivePrice)}
          </span>
          {isSale && (
            <span className="text-xs text-muted-foreground line-through">
              {money(product.basePrice)}
            </span>
          )}
        </div>
        <Link
          to={`/products/${product.id}`}
          className="store-link text-xs text-muted-foreground"
        >
          Chọn Màu & Size
        </Link>
      </div>
    </article>
  );
}

export function SkeletonCard() {
  return (
    <div className="animate-pulse flex flex-col overflow-hidden rounded-2xl border border-border bg-card pb-4">
      <div className="aspect-[3/4] bg-secondary" />
      <div className="mx-4 mt-4 h-3 bg-secondary w-1/3 rounded" />
      <div className="mx-4 mt-2 h-5 bg-secondary w-3/4 rounded" />
      <div className="mx-4 mt-3 h-4 bg-secondary w-1/2 rounded" />
    </div>
  );
}

export function StarRating({
  rating,
  size = 14,
}: {
  rating: number;
  size?: number;
}) {
  const rounded = Math.round(rating);
  return (
    <div
      className="flex items-center gap-0.5"
      aria-label={`${rating} trên 5 sao`}
    >
      {[1, 2, 3, 4, 5].map((i) => (
        <Star
          key={i}
          size={size}
          className={
            i <= rounded
              ? "fill-foreground stroke-foreground"
              : "fill-secondary stroke-secondary"
          }
        />
      ))}
    </div>
  );
}

export function StatusBadge({ status }: { status: OrderStatus }) {
  const styles: Record<OrderStatus, string> = {
    PENDING: "bg-amber-50 text-amber-800 border-amber-200",
    CONFIRMED: "bg-blue-50 text-blue-800 border-blue-200",
    PROCESSING: "bg-indigo-50 text-indigo-800 border-indigo-200",
    PACKED: "bg-purple-50 text-purple-800 border-purple-200",
    SHIPPING: "bg-cyan-50 text-cyan-800 border-cyan-200",
    DELIVERED: "bg-success-light text-success border-success/20",
    COMPLETED: "bg-green-50 text-green-900 border-green-300 font-semibold",
    CANCELLED: "bg-primary-light text-destructive border-destructive/20",
    RETURNED: "bg-orange-50 text-orange-800 border-orange-200",
    REFUNDED: "bg-gray-100 text-gray-800 border-gray-300",
  };

  return (
    <span
      className={`inline-flex items-center px-3 py-1 text-xs font-semibold border ${
        styles[status] || "bg-gray-50 text-gray-800 border-gray-200"
      }`}
    >
      {ORDER_LABEL[status] || status}
    </span>
  );
}
