import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router';
import { Heart, Star, AlertCircle, RefreshCw, ShoppingBag } from 'lucide-react';
import { media, money } from '../api/client';
import type { OrderStatus, ProductSummary } from '../api/types';
import { ORDER_LABEL } from '../api/types';
import { store } from '../api/store';
import { useAuth } from '../context/AuthContext';

export function useLoad<T>(load: () => Promise<T>, keys: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setData(await load());
      setError('');
    } catch (e) {
      setError((e as Error).message);
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
      <div role="status" className="flex items-center justify-center py-16 gap-3 text-sm text-[#777]">
        <RefreshCw size={18} className="animate-spin text-[#111]" />
        <span>Đang tải dữ liệu…</span>
      </div>
    );
  }
  if (error) {
    return (
      <div
        role="alert"
        className="my-6 border border-[#E5001B]/20 bg-red-50/60 p-5 text-sm text-[#B00018] flex items-center justify-between gap-4"
      >
        <div className="flex items-center gap-3">
          <AlertCircle size={18} className="shrink-0 text-[#E5001B]" />
          <span>{error}</span>
        </div>
        {retry && (
          <button
            onClick={retry}
            className="shrink-0 text-xs uppercase tracking-widest font-semibold text-[#111] hover:text-[#E5001B] underline underline-offset-4"
          >
            Thử lại
          </button>
        )}
      </div>
    );
  }
  return null;
}

export function PageTitle({ eyebrow, children }: { eyebrow?: string; children: React.ReactNode }) {
  return (
    <div className="mb-8">
      {eyebrow && (
        <p className="mb-2 text-xs uppercase tracking-[0.2em] text-[#888] font-medium">
          {eyebrow}
        </p>
      )}
      <h1
        className="text-[#111] font-black uppercase leading-none tracking-tight"
        style={{
          fontFamily: "'Barlow Condensed', sans-serif",
          fontSize: 'clamp(2rem, 5vw, 3.5rem)',
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
        className="flex h-full w-full flex-col items-center justify-center bg-[#f5f5f5] text-xs text-[#aaa] gap-2 select-none"
        aria-label={`${name}: chưa có ảnh`}
      >
        <ShoppingBag size={24} className="text-[#ccc]" />
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
    } finally {
      setBusy(false);
    }
  }

  const effectivePrice = product.salePrice ?? product.price ?? product.basePrice;
  const isSale = product.salePrice != null && product.salePrice < product.basePrice;
  const photoUrl = product.thumbnailUrl || media(product.thumbnailMediaId);

  return (
    <article className="group min-w-0 flex flex-col">
      <div className="relative aspect-[3/4] bg-[#f5f5f5] overflow-hidden">
        <Link
          to={`/products/${product.id}`}
          aria-label={product.name}
          className="block h-full w-full"
        >
          <ProductPhoto src={photoUrl} name={product.name} />
        </Link>

        {/* Sale / Status Tag */}
        {isSale && (
          <span className="absolute top-3 left-3 bg-[#E5001B] text-white text-[10px] font-bold tracking-widest uppercase px-2 py-0.5 pointer-events-none">
            Sale
          </span>
        )}

        {/* Wishlist Button */}
        <button
          type="button"
          disabled={busy}
          onClick={toggleWish}
          aria-label={liked ? 'Bỏ yêu thích' : 'Yêu thích'}
          className="absolute right-3 top-3 w-8 h-8 flex items-center justify-center bg-white/90 backdrop-blur-xs transition-opacity duration-200 opacity-90 sm:opacity-0 sm:group-hover:opacity-100 hover:bg-white focus-visible:opacity-100 focus-visible:outline-2 focus-visible:outline-[#111]"
        >
          <Heart
            size={16}
            className={
              liked ? 'fill-[#E5001B] stroke-[#E5001B]' : 'stroke-[#111] hover:stroke-[#E5001B]'
            }
          />
        </button>

        {/* Quick link on desktop hover */}
        <Link
          to={`/products/${product.id}`}
          className="absolute bottom-0 left-0 right-0 py-3 bg-[#111]/90 text-white text-center text-[11px] font-semibold uppercase tracking-widest translate-y-full group-hover:translate-y-0 transition-transform duration-300 hidden sm:block hover:bg-[#E5001B]"
        >
          Chọn Màu & Size
        </Link>
      </div>

      <div className="mt-3 flex-1 flex flex-col justify-between">
        <div>
          <p className="text-[11px] uppercase tracking-widest text-[#888] font-medium truncate">
            {product.categoryName || product.brandName || 'LINO'}
          </p>
          <Link
            to={`/products/${product.id}`}
            className="mt-1 block text-sm font-medium text-[#111] leading-snug hover:text-[#E5001B] transition-colors line-clamp-1"
          >
            {product.name}
          </Link>
        </div>

        <div className="mt-2 flex items-baseline gap-2">
          <span className="text-sm font-semibold text-[#111]">{money(effectivePrice)}</span>
          {isSale && (
            <span className="text-xs text-[#999] line-through">{money(product.basePrice)}</span>
          )}
        </div>
      </div>
    </article>
  );
}

export function SkeletonCard() {
  return (
    <div className="animate-pulse flex flex-col">
      <div className="aspect-[3/4] bg-[#eee]" />
      <div className="mt-3 h-3 bg-[#eee] w-1/3" />
      <div className="mt-2 h-4 bg-[#eee] w-3/4" />
      <div className="mt-2 h-4 bg-[#eee] w-1/2" />
    </div>
  );
}

export function StarRating({ rating, size = 14 }: { rating: number; size?: number }) {
  const rounded = Math.round(rating);
  return (
    <div className="flex items-center gap-0.5" aria-label={`${rating} trên 5 sao`}>
      {[1, 2, 3, 4, 5].map((i) => (
        <Star
          key={i}
          size={size}
          className={
            i <= rounded
              ? 'fill-[#111] stroke-[#111]'
              : 'fill-[#e5e5e5] stroke-[#e5e5e5]'
          }
        />
      ))}
    </div>
  );
}

export function StatusBadge({ status }: { status: OrderStatus }) {
  const styles: Record<OrderStatus, string> = {
    PENDING: 'bg-amber-50 text-amber-800 border-amber-200',
    CONFIRMED: 'bg-blue-50 text-blue-800 border-blue-200',
    PROCESSING: 'bg-indigo-50 text-indigo-800 border-indigo-200',
    PACKED: 'bg-purple-50 text-purple-800 border-purple-200',
    SHIPPING: 'bg-cyan-50 text-cyan-800 border-cyan-200',
    DELIVERED: 'bg-emerald-50 text-emerald-800 border-emerald-200',
    COMPLETED: 'bg-green-50 text-green-900 border-green-300 font-semibold',
    CANCELLED: 'bg-rose-50 text-rose-800 border-rose-200',
    RETURNED: 'bg-orange-50 text-orange-800 border-orange-200',
    REFUNDED: 'bg-gray-100 text-gray-800 border-gray-300',
  };

  return (
    <span
      className={`inline-flex items-center px-3 py-1 text-[11px] font-semibold uppercase tracking-widest border ${
        styles[status] || 'bg-gray-50 text-gray-800 border-gray-200'
      }`}
    >
      {ORDER_LABEL[status] || status}
    </span>
  );
}
