import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { media, money } from '../api/client';
import { store } from '../api/store';
import type { Address, Checkout, Payment, Product, ReviewSummary, Variant } from '../api/types';
import { PageTitle, ProductCard, ProductPhoto, Status, useLoad } from '../components/StoreUI';
import { useCart } from '../context/CartContext';
import { useAuth } from '../context/AuthContext';

const shell = 'max-w-[1400px] mx-auto px-6 py-10';
const primary = 'bg-[#111] text-white px-6 py-3 text-xs font-medium uppercase tracking-widest hover:bg-[#E5001B] disabled:opacity-50';
const field = 'w-full border border-[#ddd] bg-white px-4 py-3 text-sm outline-none focus:border-[#111]';

export function HomePage() {
  const listing = useLoad(() => store.products({ page: 0, size: 8 }), []);
  return <>
    <section className={`${shell} grid min-h-[60vh] items-center gap-10 lg:grid-cols-2`}>
      <div><p className="mb-5 text-xs uppercase tracking-[.25em] text-[#888]">LINO · Phong cách tối giản</p><h1 className="font-display text-[clamp(4rem,10vw,9rem)] font-black uppercase leading-[.8]">Sống<br />đơn<br /><span className="text-[#E5001B]">giản</span></h1><p className="my-8 max-w-md text-[#555]">Trang phục cho cuộc sống hiện đại, với phom dáng tinh tế và chất liệu được lựa chọn kỹ.</p><Link className={primary} to="/products">Khám phá sản phẩm →</Link></div>
      <div className="aspect-[4/5] overflow-hidden bg-[#f5f5f5]"><img className="h-full w-full object-cover" src="https://images.unsplash.com/photo-1665832102316-9fd8e8f77c88?w=1000&h=1200&fit=crop&auto=format" alt="Bộ sưu tập thời trang LINO" /></div>
    </section>
    <section className={shell}><div className="mb-8 flex items-center justify-between"><h2 className="font-display text-4xl font-black uppercase">Sản phẩm mới</h2><Link to="/products" className="text-xs uppercase tracking-widest underline">Xem tất cả</Link></div><Status loading={listing.loading} error={listing.error} retry={() => void listing.refresh()} /><div className="grid grid-cols-2 gap-5 md:grid-cols-3 lg:grid-cols-4">{listing.data?.items.map(p => <ProductCard key={p.id} product={p} />)}</div>{listing.data && !listing.data.items.length && <p className="py-10 text-sm text-[#888]">Chưa có sản phẩm đang mở bán.</p>}</section>
  </>;
}

export function ProductsPage() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get('page') || 0));
  const key = params.toString();
  const listing = useLoad(() => store.products({ page, size: 12, keyword: params.get('q'), gender: params.get('gender'), categoryId: params.get('categoryId'), brandId: params.get('brandId'), color: params.get('color'), minPrice: params.get('minPrice'), maxPrice: params.get('maxPrice'), sort: params.get('sort') }, params.get('fit')), [key]);
  const categories = useLoad(store.categories, []); const brands = useLoad(store.brands, []); const sizes = useLoad(store.sizes, []); const colors = useLoad(store.colors, []);
  const [term, setTerm] = useState(params.get('q') || '');
  const set = (name: string, value: string) => { const next = new URLSearchParams(params); if (value) next.set(name, value); else next.delete(name); next.delete('page'); setParams(next); };
  const setPage = (value: number) => { const next = new URLSearchParams(params); next.set('page', String(value)); setParams(next); };
  return <div className={shell}><PageTitle eyebrow="Trang chủ / Sản phẩm">Tất cả sản phẩm</PageTitle>
    <form onSubmit={e => { e.preventDefault(); set('q', term.trim()); }} className="mb-6 flex max-w-xl"><input className={field} aria-label="Tìm sản phẩm" placeholder="Tìm tên sản phẩm" value={term} onChange={e => setTerm(e.target.value)} /><button className={primary}>Tìm</button></form>
    <div className="mb-8 flex flex-wrap gap-3 text-sm">
      <select className={field + ' !w-auto'} aria-label="Giới tính" value={params.get('gender') || ''} onChange={e => set('gender', e.target.value)}><option value="">Tất cả</option><option value="MEN">Nam</option><option value="WOMEN">Nữ</option><option value="UNISEX">Unisex</option></select>
      <select className={field + ' !w-auto'} aria-label="Danh mục" value={params.get('categoryId') || ''} onChange={e => set('categoryId', e.target.value)}><option value="">Danh mục</option>{categories.data?.flatMap(c => [<option key={c.id} value={c.id}>{c.name}</option>, ...(c.children || []).map(child => <option key={child.id} value={child.id}>— {child.name}</option>)])}</select>
      <select className={field + ' !w-auto'} aria-label="Thương hiệu" value={params.get('brandId') || ''} onChange={e => set('brandId', e.target.value)}><option value="">Thương hiệu</option>{brands.data?.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}</select>
      <select className={field + ' !w-auto'} aria-label="Size" value={params.get('fit') || ''} onChange={e => set('fit', e.target.value)}><option value="">Size</option>{sizes.data?.map(s => <option key={s.id} value={s.name}>{s.name}</option>)}</select>
      <select className={field + ' !w-auto'} aria-label="Màu" value={params.get('color') || ''} onChange={e => set('color', e.target.value)}><option value="">Màu</option>{colors.data?.map(c => <option key={c.id} value={c.name}>{c.name}</option>)}</select>
      <select className={field + ' !w-auto'} aria-label="Sắp xếp" value={params.get('sort') || ''} onChange={e => set('sort', e.target.value)}><option value="">Mới nhất</option><option value="basePrice,asc">Giá tăng dần</option><option value="basePrice,desc">Giá giảm dần</option></select>
      <input className={field + ' !w-32'} aria-label="Giá từ" type="number" min="0" placeholder="Giá từ" value={params.get('minPrice') || ''} onChange={e => set('minPrice', e.target.value)} />
      <input className={field + ' !w-32'} aria-label="Giá đến" type="number" min="0" placeholder="Giá đến" value={params.get('maxPrice') || ''} onChange={e => set('maxPrice', e.target.value)} />
      <button type="button" className="text-xs uppercase underline" onClick={() => { setTerm(''); setParams({}); }}>Xóa lọc</button>
    </div>
    <Status loading={listing.loading} error={listing.error} retry={() => void listing.refresh()} />
    {listing.data && <><p className="mb-5 text-xs uppercase tracking-widest text-[#888]">Trang {page + 1} / {Math.max(1, listing.data.totalPage)}</p><div className="grid grid-cols-2 gap-x-5 gap-y-10 md:grid-cols-3 xl:grid-cols-4">{listing.data.items.map(p => <ProductCard key={p.id} product={p} />)}</div>{!listing.data.items.length && <p className="py-20 text-center text-sm text-[#888]">Không tìm thấy sản phẩm phù hợp.</p>}<div className="mt-12 flex gap-3"><button className={primary} disabled={page === 0} onClick={() => setPage(page - 1)}>Trước</button><button className={primary} disabled={page + 1 >= listing.data.totalPage} onClick={() => setPage(page + 1)}>Sau</button></div></>}
  </div>;
}

export function ProductDetailPage() {
  const { id = '' } = useParams();
  const product = useLoad(() => store.product(id), [id]);
  const reviews = useLoad(() => store.reviews(id), [id]);
  const { addItem } = useCart();
  const { isLoggedIn, login } = useAuth();
  const [color, setColor] = useState(''); const [size, setSize] = useState(''); const [qty, setQty] = useState(1); const [image, setImage] = useState(''); const [error, setError] = useState(''); const [busy, setBusy] = useState(false); const [liked, setLiked] = useState(false);
  useEffect(() => { setColor(''); setSize(''); setQty(1); setImage(''); setError(''); }, [id]);
  useEffect(() => {
    if (!isLoggedIn) { setLiked(false); return; }
    let active = true;
    void store.wishCheck(id).then(result => { if (active) setLiked(result.inWishlist); }).catch(() => {});
    return () => { active = false; };
  }, [id, isLoggedIn]);
  const p = product.data;
  const variants = (p?.variants || []).filter(v => v.active);
  const colors = Array.from(new Map(variants.map(v => [v.colorOptionId || v.color || '', v])).values());
  const sizes = Array.from(new Map(variants.filter(v => !color || (v.colorOptionId || v.color) === color).map(v => [v.sizeOptionId || v.size || '', v])).values());
  const selected = variants.find(v => (v.colorOptionId || v.color) === color && (v.sizeOptionId || v.size) === size);
  const images = (p?.images || []).filter(i => !color || !i.colorOptionId || i.colorOptionId === color).sort((a, b) => a.sortOrder - b.sortOrder);
  const shownImage = image || images[0]?.url || p?.thumbnailUrl || media(images[0]?.mediaId || p?.thumbnailMediaId);
  async function add(buy = false) {
    if (!isLoggedIn) { login(`/products/${id}`); return; }
    if (!selected) { setError('Vui lòng chọn màu và size còn bán.'); return; }
    setBusy(true); setError('');
    try { await addItem(selected.id, qty); if (buy) window.location.assign('/cart'); else setError('Đã thêm vào giỏ hàng.'); }
    catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  }
  async function wish() {
    if (!isLoggedIn) { login(`/products/${id}`); return; }
    try { if (liked) await store.wishRemove(id); else await store.wishAdd(id); setLiked(!liked); }
    catch (e) { setError((e as Error).message); }
  }
  return <div className={shell}><Status loading={product.loading} error={product.error} retry={() => void product.refresh()} />{p && <>
    <nav className="mb-6 text-xs uppercase tracking-widest text-[#888]"><Link to="/">Trang chủ</Link> / <Link to="/products">Sản phẩm</Link> / {p.name}</nav>
    <div className="grid gap-12 lg:grid-cols-2"><div><div className="aspect-[3/4] bg-[#f5f5f5]"><ProductPhoto src={shownImage} name={p.name} /></div><div className="mt-3 flex gap-2 overflow-auto">{images.map(i => <button key={i.id} onClick={() => setImage(i.url || media(i.mediaId))} className="h-20 w-16 shrink-0 border border-[#ddd]"><img className="h-full w-full object-cover" src={i.url || media(i.mediaId)} alt={i.altText || p.name} /></button>)}</div></div>
      <div><PageTitle eyebrow={p.categoryName || p.brandName}>{p.name}</PageTitle><p className="mb-6 text-2xl font-bold">{money(selected?.salePrice ?? selected?.price ?? p.salePrice ?? p.price ?? p.basePrice)}</p><p className="mb-8 text-sm leading-7 text-[#555]">{p.shortDescription || p.description}</p>
        <p className="mb-3 text-xs font-bold uppercase tracking-widest">Màu sắc</p><div className="mb-6 flex flex-wrap gap-2">{colors.map(v => { const key = v.colorOptionId || v.color || ''; return <button key={key} onClick={() => { setColor(key); setSize(''); setImage(''); }} className={`flex items-center gap-2 border px-3 py-2 text-sm ${color === key ? 'border-[#111]' : 'border-[#ddd]'}`}><span className="h-4 w-4 rounded-full border" style={{ backgroundColor: v.colorHex || '#ddd' }} />{v.color || 'Màu'}</button>; })}</div>
        <p className="mb-3 text-xs font-bold uppercase tracking-widest">Size</p><div className="mb-6 flex flex-wrap gap-2">{sizes.map(v => { const key = v.sizeOptionId || v.size || ''; return <button key={key} onClick={() => setSize(key)} className={`min-w-12 border px-3 py-2 text-sm ${size === key ? 'bg-[#111] text-white' : 'border-[#ddd]'}`}>{v.size || 'Size'}</button>; })}</div>
        <label className="mb-6 block text-xs font-bold uppercase tracking-widest">Số lượng <input type="number" min="1" max="99" value={qty} onChange={e => setQty(Math.max(1, Number(e.target.value)))} className={field + ' mt-2 !w-24'} /></label>
        <div className="mb-6 flex flex-wrap gap-3"><button className={primary} disabled={busy} onClick={() => void add()}>Thêm vào giỏ</button><button className={primary} disabled={busy} onClick={() => void add(true)}>Mua ngay</button><button className="border border-[#111] px-4" onClick={() => void wish()} aria-label="Yêu thích">{liked ? '♥' : '♡'}</button></div>{error && <p role="status" className="mb-4 text-sm text-[#B00018]">{error}</p>}
        <details className="border-t py-4 text-sm"><summary className="cursor-pointer font-semibold uppercase">Mô tả sản phẩm</summary><p className="pt-3 leading-7 text-[#555]">{p.description}</p></details><SizeChart productId={id} />
      </div></div>
    <section className="mt-20 border-t pt-10"><h2 className="font-display text-3xl font-black uppercase">Đánh giá</h2><Status loading={reviews.loading} error={reviews.error} retry={() => void reviews.refresh()} /><p className="my-4 text-sm text-[#555]">{reviews.data?.averageRating?.toFixed(1) || '0'} / 5 · {reviews.data?.totalReviews || 0} đánh giá</p><div className="grid gap-4 md:grid-cols-3">{reviews.data?.reviews.items.map(r => <article key={r.id} className="border p-5 text-sm"><p className="font-semibold">{'★'.repeat(r.rating)}{'☆'.repeat(5-r.rating)}</p><p className="mt-2 text-[#555]">{r.comment}</p><p className="mt-3 text-xs text-[#888]">{new Date(r.createdAt).toLocaleDateString('vi-VN')}</p></article>)}</div></section>
  </>}</div>;
}

function SizeChart({ productId }: { productId: string }) {
  const chart = useLoad(() => store.productSizeChart(productId), [productId]);
  if (!chart.data?.rows?.length) return null;
  return <details className="border-t py-4 text-sm"><summary className="cursor-pointer font-semibold uppercase">Bảng size</summary><div className="overflow-x-auto"><table className="mt-4 w-full text-left"><thead><tr><th>Size</th><th>Ngực</th><th>Eo</th><th>Chiều cao</th><th>Cân nặng</th></tr></thead><tbody>{chart.data.rows.map(r => <tr key={r.id} className="border-t"><td className="py-2">{r.sizeCode}</td><td>{r.chest || '—'}</td><td>{r.waist || '—'}</td><td>{r.heightMin && r.heightMax ? `${r.heightMin}–${r.heightMax} cm` : '—'}</td><td>{r.weightMin && r.weightMax ? `${r.weightMin}–${r.weightMax} kg` : '—'}</td></tr>)}</tbody></table></div></details>;
}

export function CartPage() {
  const { cart, loading, error, refresh, updateQty, removeItem, clearCart } = useCart();
  const [busy, setBusy] = useState(''); const [notice, setNotice] = useState('');
  async function act(id: string, run: () => Promise<void>) { setBusy(id); try { await run(); setNotice(''); } catch (e) { setNotice((e as Error).message); } finally { setBusy(''); } }
  return <div className={shell}><PageTitle eyebrow="Trang chủ / Giỏ hàng">Giỏ hàng ({cart?.totalQuantity || 0})</PageTitle><Status loading={loading} error={error} retry={() => void refresh()} />{notice && <p role="alert" className="mb-5 text-sm text-[#B00018]">{notice}</p>}
    {cart && (!cart.items.length ? <div className="py-24 text-center"><p className="mb-6 text-[#888]">Giỏ hàng của bạn đang trống.</p><Link className={primary} to="/products">Khám phá sản phẩm</Link></div> : <div className="grid gap-12 lg:grid-cols-[1fr_360px]"><div className="divide-y border-y">{cart.items.map(i => <div key={i.id} className="flex flex-wrap items-center gap-4 py-6"><div className="flex-1"><Link className="font-medium hover:text-[#E5001B]" to={`/products/${i.productId}`}>{i.productName}</Link><p className="mt-1 text-xs text-[#888]">{i.color} · {i.size} · {i.sku}</p><p className="mt-2 text-sm">{money(i.unitPrice)}</p>{i.available === false && <p className="text-xs text-[#B00018]">Sản phẩm hiện không đủ tồn kho</p>}{i.available === null && <p className="text-xs text-[#888]">Đang kiểm tra tồn kho</p>}</div><div className="flex items-center border text-sm"><button aria-label={`Giảm số lượng ${i.productName}`} className="px-3 py-2" disabled={busy === i.id || i.quantity <= 1} onClick={() => void act(i.id, () => updateQty(i.id, i.quantity - 1))}>−</button><span>{i.quantity}</span><button aria-label={`Tăng số lượng ${i.productName}`} className="px-3 py-2" disabled={busy === i.id} onClick={() => void act(i.id, () => updateQty(i.id, i.quantity + 1))}>+</button></div><strong className="w-28 text-right text-sm">{money(i.totalPrice)}</strong><button className="text-xs uppercase text-[#B00018] underline" disabled={busy === i.id} onClick={() => void act(i.id, () => removeItem(i.id))}>Xóa</button></div>)}</div><aside className="h-fit border p-6"><h2 className="mb-5 text-sm font-semibold uppercase tracking-widest">Tóm tắt giỏ hàng</h2><div className="flex justify-between text-sm"><span>Tạm tính</span><strong>{money(cart.totalPrice)}</strong></div><p className="my-5 text-xs text-[#888]">Phí giao hàng và giảm giá được tính khi thanh toán.</p><Link className={primary + ' block text-center'} to="/checkout">Tiến hành thanh toán</Link><button className="mt-5 w-full text-xs uppercase underline" disabled={busy === 'all'} onClick={() => void act('all', clearCart)}>Xóa tất cả</button></aside></div>)}
  </div>;
}

export function CheckoutPage() {
  const { cart, refresh } = useCart(); const { user } = useAuth(); const navigate = useNavigate();
  const addresses = useLoad(store.addresses, []);
  const [addressId, setAddressId] = useState(''); const [shippingMethod, setShipping] = useState<'STANDARD' | 'EXPRESS'>('STANDARD'); const [paymentProvider, setProvider] = useState<'COD' | 'VNPAY' | 'PAYOS'>('COD'); const [couponCode, setCoupon] = useState(''); const [checkout, setCheckout] = useState<Checkout | null>(null); const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  useEffect(() => {
    const id = sessionStorage.getItem('lino:checkoutId');
    if (!id) return;
    let active = true;
    void store.checkout(id).then(value => {
      if (!active) return;
      if (value.orderId) { sessionStorage.removeItem('lino:checkoutId'); navigate(`/profile/orders/${value.orderId}`, { replace: true }); }
      else if (value.status === 'SUBMITTED') { setCheckout(value); setAddressId(value.addressId || ''); setShipping(value.shippingMethod); setProvider(value.paymentProvider); setCoupon(value.couponCode || ''); }
      else sessionStorage.removeItem('lino:checkoutId');
    }).catch(() => sessionStorage.removeItem('lino:checkoutId'));
    return () => { active = false; };
  }, [navigate]);
  useEffect(() => { if (!addressId && addresses.data?.length) setAddressId(addresses.data.find(a => a.isDefault)?.id || addresses.data[0].id); }, [addresses.data, addressId]);
  const selectedAddress = addresses.data?.find(a => a.id === addressId);
  const paymentMethod = paymentProvider === 'COD' ? 'COD' : 'ONLINE';
  function invalidatePreview() {
    setCheckout(null);
    sessionStorage.removeItem('lino:checkoutId');
  }
  async function preview() {
    if (!addressId) { setError('Vui lòng chọn địa chỉ giao hàng.'); return; }
    setBusy(true); setError('');
    try { const current = await store.checkoutCreate({ addressId, shippingMethod, paymentMethod, paymentProvider, couponCode: couponCode.trim() || undefined }); setCheckout(current); sessionStorage.setItem('lino:checkoutId', current.id); await refresh(); }
    catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  }
  async function place() {
    if (!checkout) return;
    setBusy(true); setError('');
    try { const order = await store.orderCreate(checkout.id, addressId); sessionStorage.setItem('lino:pendingOrder', order.id); sessionStorage.removeItem('lino:checkoutId'); navigate(`/profile/orders/${order.id}`); }
    catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  }
  return <div className={shell}><PageTitle eyebrow="Giỏ hàng / Thanh toán">Thanh toán</PageTitle><Status loading={addresses.loading} error={addresses.error} retry={() => void addresses.refresh()} />
    {!cart?.items.length && !checkout ? <p className="text-sm">Giỏ hàng trống. <Link to="/products" className="underline">Tiếp tục mua sắm</Link></p> : <div className="grid gap-10 lg:grid-cols-[1fr_360px]"><div className="space-y-8">
      <section><h2 className="mb-4 text-sm font-bold uppercase tracking-widest">Thông tin khách hàng</h2><p className="text-sm text-[#555]">{user?.fullName} · {user?.email}</p></section>
      <section><h2 className="mb-4 text-sm font-bold uppercase tracking-widest">Địa chỉ giao hàng</h2>{addresses.data?.map(a => <label key={a.id} className="mb-3 flex cursor-pointer gap-3 border p-4 text-sm"><input type="radio" checked={addressId === a.id} onChange={() => { setAddressId(a.id); invalidatePreview(); }} /><span><strong>{a.recipientName}</strong> · {a.phone}<br />{a.fullAddress || `${a.detailAddress}, ${a.ward}, ${a.district}, ${a.province}`}</span></label>)}<Link to="/profile/addresses" className="text-xs uppercase underline">Quản lý địa chỉ</Link>{!addresses.data?.length && <p className="text-sm text-[#B00018]">Cần thêm địa chỉ trước khi thanh toán.</p>}{selectedAddress && (!selectedAddress.districtId || !selectedAddress.wardCode) && <p className="mt-3 text-xs text-[#B00018]">Địa chỉ thiếu mã huyện/xã để tính phí GHN. Vui lòng cập nhật địa chỉ.</p>}</section>
      <section><h2 className="mb-4 text-sm font-bold uppercase tracking-widest">Vận chuyển</h2>{(['STANDARD', 'EXPRESS'] as const).map(m => <label key={m} className="mr-6 text-sm"><input type="radio" checked={shippingMethod === m} onChange={() => { setShipping(m); invalidatePreview(); }} /> {m === 'STANDARD' ? 'Tiêu chuẩn' : 'Nhanh'}</label>)}</section>
      <section><h2 className="mb-4 text-sm font-bold uppercase tracking-widest">Thanh toán</h2>{(['COD', 'VNPAY', 'PAYOS'] as const).map(m => <label key={m} className="mb-2 block text-sm"><input type="radio" checked={paymentProvider === m} onChange={() => { setProvider(m); invalidatePreview(); }} /> {m === 'COD' ? 'Thanh toán khi nhận hàng' : m}</label>)}</section>
      <section><label className="mb-2 block text-xs font-bold uppercase tracking-widest">Mã giảm giá</label><input className={field} value={couponCode} onChange={e => { setCoupon(e.target.value); invalidatePreview(); }} placeholder="Nhập mã nếu có" /></section>
    </div><aside className="h-fit border p-6"><h2 className="mb-5 text-sm font-bold uppercase tracking-widest">Đơn hàng của bạn</h2>{(checkout?.items || cart?.items || []).map((i, index) => <p key={index} className="mb-3 flex justify-between gap-4 text-sm"><span>{i.productName} × {i.quantity}</span><span>{money('lineTotal' in i ? i.lineTotal : i.totalPrice)}</span></p>)}<div className="mt-5 space-y-2 border-t pt-4 text-sm"><p className="flex justify-between"><span>Tạm tính</span><span>{money(checkout?.subtotalAmount ?? cart?.totalPrice)}</span></p>{checkout && <><p className="flex justify-between"><span>Giảm giá</span><span>−{money(checkout.discountAmount)}</span></p><p className="flex justify-between"><span>Vận chuyển</span><span>{money(checkout.shippingFee)}</span></p></>}<p className="flex justify-between border-t pt-3 font-bold"><span>Tổng</span><span>{money(checkout?.totalAmount ?? cart?.totalPrice)}</span></p></div>{error && <p role="alert" className="my-4 text-sm text-[#B00018]">{error}</p>}{checkout && <p className="my-4 text-xs text-[#555]">Giá và phí giao hàng đã được hệ thống cập nhật. Đơn sẽ ở trạng thái đang xử lý sau khi gửi.</p>}<button className={primary + ' mt-5 w-full'} disabled={busy || !addressId} onClick={() => void (checkout ? place() : preview())}>{busy ? 'Đang xử lý…' : checkout ? 'Xác nhận đặt hàng' : 'Xem tổng tiền chính xác'}</button></aside></div>}
  </div>;
}

export function PaymentReturnPage() {
  const [params] = useSearchParams(); const orderId = params.get('orderId') || sessionStorage.getItem('lino:pendingOrder');
  const isVnpay = window.location.pathname.includes('vnpay');
  const [verification, setVerification] = useState('');
  const payment = useLoad(() => orderId ? store.payment(orderId) : Promise.reject(new Error('Không tìm thấy mã đơn hàng để kiểm tra.')), [orderId]);
  useEffect(() => {
    if (!isVnpay) return;
    void store.vnpayVerify(params).then(() => { setVerification('Đã kiểm tra phản hồi VNPay.'); void payment.refresh(); }).catch(e => setVerification((e as Error).message));
  }, [isVnpay]);
  useEffect(() => {
    if (!orderId || ['COMPLETED', 'FAILED', 'CANCELLED', 'REFUNDED', 'REFUND_FAILED'].includes(payment.data?.status || '')) return;
    let attempts = 0;
    const timer = window.setInterval(() => { if (!document.hidden && attempts++ < 12) void payment.refresh(); }, 5000);
    return () => window.clearInterval(timer);
  }, [orderId, payment.data?.status]);
  return <div className={shell}><PageTitle>Trạng thái thanh toán</PageTitle><p className="mb-5 text-sm text-[#555]">Kết quả cuối cùng được xác nhận theo trạng thái trên hệ thống.</p><Status loading={payment.loading} error={payment.error} retry={() => void payment.refresh()} />{verification && <p className="mb-3 text-sm">{verification}</p>}{payment.data && <p className="mb-6 text-lg font-semibold">{payment.data.status} · {money(payment.data.amount)}</p>}{orderId && <Link className={primary} to={`/profile/orders/${orderId}`}>Xem đơn hàng</Link>}</div>;
}
