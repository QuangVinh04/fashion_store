import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router';
import { media, money } from '../api/client';
import type { ProductSummary } from '../api/types';
import { store } from '../api/store';
import { useAuth } from '../context/AuthContext';

export function useLoad<T>(load: () => Promise<T>, keys: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const refresh = useCallback(async () => {
    setLoading(true);
    try { setData(await load()); setError(''); }
    catch (e) { setError((e as Error).message); }
    finally { setLoading(false); }
  }, keys); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { void refresh(); }, [refresh]);
  return { data, loading, error, refresh, setData };
}

export function Status({ loading, error, retry }: { loading: boolean; error?: string; retry?: () => void }) {
  if (loading) return <div role="status" className="py-16 text-center text-sm text-[#888]">Đang tải dữ liệu…</div>;
  if (error) return <div role="alert" className="my-6 border border-[#E5001B]/30 bg-red-50 p-5 text-sm text-[#B00018]">{error} {retry && <button onClick={retry} className="ml-3 underline">Thử lại</button>}</div>;
  return null;
}

export function PageTitle({ eyebrow, children }: { eyebrow?: string; children: React.ReactNode }) {
  return <div className="mb-8">{eyebrow && <p className="mb-2 text-xs uppercase tracking-[.2em] text-[#888]">{eyebrow}</p>}<h1 className="font-display text-4xl font-black uppercase leading-none text-[#111]">{children}</h1></div>;
}

export function ProductPhoto({ src, name }: { src?: string; name: string }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [src]);
  if (!src || failed) return <div className="flex h-full w-full items-center justify-center text-sm text-[#888]" aria-label={`${name}: chưa có ảnh`}>Chưa có ảnh</div>;
  return <img loading="lazy" src={src} alt={name} className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105" onError={() => setFailed(true)} />;
}

export function ProductCard({ product }: { product: ProductSummary }) {
  const { isLoggedIn, login } = useAuth();
  const [liked, setLiked] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!isLoggedIn) { setLiked(false); return; }
    let active = true;
    void store.wishCheck(product.id).then(result => { if (active) setLiked(result.inWishlist); }).catch(() => {});
    return () => { active = false; };
  }, [isLoggedIn, product.id]);
  async function toggleWish() {
    if (!isLoggedIn) { login(`/products/${product.id}`); return; }
    setBusy(true);
    try { if (liked) await store.wishRemove(product.id); else await store.wishAdd(product.id); setLiked(!liked); }
    finally { setBusy(false); }
  }
  return <article className="group min-w-0">
    <div className="relative aspect-[3/4] bg-[#f5f5f5] overflow-hidden">
      <Link to={`/products/${product.id}`} aria-label={product.name} className="block h-full"><ProductPhoto src={product.thumbnailUrl || media(product.thumbnailMediaId)} name={product.name} /></Link>
      <button type="button" disabled={busy} onClick={() => void toggleWish()} aria-label={liked ? 'Bỏ yêu thích' : 'Yêu thích'} className="absolute right-3 top-3 bg-white p-2 text-[#E5001B] focus-visible:outline-2 focus-visible:outline-[#111]">{liked ? '♥' : '♡'}</button>
    </div>
    <p className="mt-3 text-xs uppercase tracking-widest text-[#888]">{product.categoryName || product.brandName}</p>
    <Link to={`/products/${product.id}`} className="mt-1 block text-sm font-medium text-[#111] hover:text-[#E5001B]">{product.name}</Link>
    <p className="mt-2 text-sm font-semibold">{money(product.salePrice ?? product.price ?? product.basePrice)} {product.salePrice != null && product.salePrice < product.basePrice && <span className="ml-2 text-xs font-normal text-[#999] line-through">{money(product.basePrice)}</span>}</p>
    <Link to={`/products/${product.id}`} className="mt-3 inline-block border-b border-[#111] text-xs uppercase tracking-widest">Chọn màu và size</Link>
  </article>;
}
