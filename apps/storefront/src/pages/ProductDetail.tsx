import { useState, useEffect, useMemo } from 'react';
import { Link, useParams, useNavigate } from 'react-router';
import {
  Heart,
  Star,
  ChevronDown,
  ChevronUp,
  ShoppingBag,
  ArrowRight,
  Truck,
  RefreshCw,
  ShieldCheck,
  Check,
  AlertCircle,
} from 'lucide-react';
import { store } from '../api/store';
import { media, money } from '../api/client';
import type { Product, ProductImage, ProductSummary, Review, Variant } from '../api/types';
import {
  PageTitle,
  ProductCard,
  ProductPhoto,
  StarRating,
  Status,
  useLoad,
} from '../components/StoreUI';
import { useCart } from '../context/CartContext';
import { useAuth } from '../context/AuthContext';

function AccordionItem({
  title,
  defaultOpen = false,
  children,
}: {
  title: string;
  defaultOpen?: boolean;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="border-b border-[rgba(0,0,0,0.08)] py-4">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="flex items-center justify-between w-full py-1 text-left font-bold text-xs uppercase tracking-widest text-[#111] hover:text-[#E5001B] transition-colors"
      >
        <span>{title}</span>
        {open ? (
          <ChevronUp size={16} className="text-[#888] shrink-0" />
        ) : (
          <ChevronDown size={16} className="text-[#888] shrink-0" />
        )}
      </button>
      {open && <div className="pt-3 pb-2 text-sm text-[#555] leading-relaxed">{children}</div>}
    </div>
  );
}

export default function ProductDetail() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const { addItem } = useCart();
  const { isLoggedIn, login } = useAuth();

  const productLoad = useLoad(() => store.product(id), [id]);
  const reviewsLoad = useLoad(() => store.reviews(id), [id]);
  const sizeChartLoad = useLoad(() => store.productSizeChart(id), [id]);

  const [selectedColor, setSelectedColor] = useState<string>('');
  const [selectedSize, setSelectedSize] = useState<string>('');
  const [qty, setQty] = useState(1);
  const [activeImg, setActiveImg] = useState<string>('');
  const [liked, setLiked] = useState(false);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(
    null
  );

  const [relatedProducts, setRelatedProducts] = useState<ProductSummary[]>([]);

  const p = productLoad.data;

  // Reset state when product id changes
  useEffect(() => {
    setSelectedColor('');
    setSelectedSize('');
    setQty(1);
    setActiveImg('');
    setFeedback(null);
  }, [id]);

  // Wishlist check
  useEffect(() => {
    if (!isLoggedIn) {
      setLiked(false);
      return;
    }
    let active = true;
    void store
      .wishCheck(id)
      .then((res) => {
        if (active) setLiked(res.inWishlist);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [id, isLoggedIn]);

  // Load related products
  useEffect(() => {
    if (!p?.categoryId) return;
    let active = true;
    store
      .products({ categoryId: p.categoryId, size: 4 })
      .then((res) => {
        if (active) setRelatedProducts(res.items.filter((item) => item.id !== id).slice(0, 4));
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [p?.categoryId, id]);

  const activeVariants = useMemo(() => {
    return (p?.variants || []).filter((v) => v.active);
  }, [p?.variants]);

  // Unique colors
  const colorOptions = useMemo(() => {
    const map = new Map<string, Variant>();
    activeVariants.forEach((v) => {
      const key = v.colorOptionId || v.color || '';
      if (key && !map.has(key)) map.set(key, v);
    });
    return Array.from(map.values());
  }, [activeVariants]);

  // Unique sizes filtered by selected color
  const sizeOptions = useMemo(() => {
    const map = new Map<string, Variant>();
    const filtered = selectedColor
      ? activeVariants.filter((v) => (v.colorOptionId || v.color) === selectedColor)
      : activeVariants;
    filtered.forEach((v) => {
      const key = v.sizeOptionId || v.size || '';
      if (key && !map.has(key)) map.set(key, v);
    });
    return Array.from(map.values());
  }, [activeVariants, selectedColor]);

  // Matching resolved variant
  const selectedVariant = useMemo(() => {
    if (!selectedColor || !selectedSize) return null;
    return (
      activeVariants.find(
        (v) =>
          (v.colorOptionId || v.color) === selectedColor &&
          (v.sizeOptionId || v.size) === selectedSize
      ) || null
    );
  }, [activeVariants, selectedColor, selectedSize]);

  // Images filtered by color or default
  const filteredImages = useMemo(() => {
    const list = p?.images || [];
    if (!selectedColor) return list.sort((a, b) => a.sortOrder - b.sortOrder);
    const colorImages = list.filter(
      (img) => !img.colorOptionId || img.colorOptionId === selectedColor
    );
    return (colorImages.length > 0 ? colorImages : list).sort((a, b) => a.sortOrder - b.sortOrder);
  }, [p?.images, selectedColor]);

  const currentPhotoUrl =
    activeImg ||
    filteredImages[0]?.url ||
    (filteredImages[0]?.mediaId ? media(filteredImages[0].mediaId) : '') ||
    p?.thumbnailUrl ||
    media(p?.thumbnailMediaId);

  const displayPrice =
    selectedVariant?.salePrice ??
    selectedVariant?.price ??
    p?.salePrice ??
    p?.price ??
    p?.basePrice ??
    0;
  const isSale = p?.basePrice != null && displayPrice < p.basePrice;

  async function handleAddToCart(buyNow = false) {
    if (!isLoggedIn) {
      login(`/products/${id}`);
      return;
    }
    if (!selectedColor || !selectedSize || !selectedVariant) {
      setFeedback({
        type: 'error',
        message: 'Vui lòng chọn đầy đủ màu sắc và kích cỡ sản phẩm.',
      });
      return;
    }

    setBusy(true);
    setFeedback(null);
    try {
      await addItem(selectedVariant.id, qty);
      if (buyNow) {
        navigate('/cart');
      } else {
        setFeedback({
          type: 'success',
          message: 'Đã thêm sản phẩm vào giỏ hàng thành công.',
        });
      }
    } catch (e) {
      setFeedback({
        type: 'error',
        message: (e as Error).message || 'Không thể thêm sản phẩm vào giỏ hàng.',
      });
    } finally {
      setBusy(false);
    }
  }

  async function toggleWishlist() {
    if (!isLoggedIn) {
      login(`/products/${id}`);
      return;
    }
    setBusy(true);
    try {
      if (liked) {
        await store.wishRemove(id);
        setLiked(false);
      } else {
        await store.wishAdd(id);
        setLiked(true);
      }
    } catch (e) {
      setFeedback({ type: 'error', message: (e as Error).message });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ fontFamily: "'Inter', sans-serif" }} className="w-full">
      {/* Breadcrumb */}
      <div className="border-b border-[rgba(0,0,0,0.08)] bg-[#fafafa]">
        <div className="max-w-[1400px] mx-auto px-6 py-4">
          <nav className="flex items-center gap-2 text-xs text-[#888] uppercase tracking-widest font-medium overflow-x-auto whitespace-nowrap">
            <Link to="/" className="hover:text-[#111]">
              Trang Chủ
            </Link>
            <span>/</span>
            <Link to="/products" className="hover:text-[#111]">
              Sản Phẩm
            </Link>
            {p?.categoryName && (
              <>
                <span>/</span>
                <Link
                  to={`/products?categoryId=${p.categoryId || ''}`}
                  className="hover:text-[#111]"
                >
                  {p.categoryName}
                </Link>
              </>
            )}
            <span>/</span>
            <span className="text-[#111] truncate max-w-xs">{p?.name || 'Chi Tiết Sản Phẩm'}</span>
          </nav>
        </div>
      </div>

      <div className="max-w-[1400px] mx-auto px-6 py-10">
        <Status
          loading={productLoad.loading}
          error={productLoad.error}
          retry={() => void productLoad.refresh()}
        />

        {p && (
          <>
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 xl:gap-16">
              {/* Product Gallery */}
              <div className="space-y-4">
                <div className="relative aspect-[3/4] bg-[#f5f5f5] overflow-hidden group">
                  <ProductPhoto src={currentPhotoUrl} name={p.name} />
                  {isSale && (
                    <span className="absolute top-4 left-4 bg-[#E5001B] text-white text-xs font-bold uppercase tracking-widest px-3 py-1">
                      Sale
                    </span>
                  )}
                </div>

                {/* Thumbnails */}
                {filteredImages.length > 1 && (
                  <div className="flex gap-3 overflow-x-auto pb-2">
                    {filteredImages.map((img) => {
                      const url = img.url || (img.mediaId ? media(img.mediaId) : '');
                      const isActive = url === currentPhotoUrl;
                      return (
                        <button
                          key={img.id}
                          type="button"
                          onClick={() => setActiveImg(url)}
                          className={`w-20 h-24 shrink-0 bg-[#f5f5f5] overflow-hidden border-2 transition-all ${
                            isActive ? 'border-[#111]' : 'border-transparent hover:border-[#aaa]'
                          }`}
                        >
                          <img
                            src={url}
                            alt={img.altText || p.name}
                            className="w-full h-full object-cover"
                          />
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Product Info & Controls */}
              <div className="flex flex-col">
                <p className="text-xs uppercase tracking-[0.25em] text-[#888] font-bold mb-2">
                  {p.brandName || p.categoryName || 'LINO'}
                </p>
                <h1
                  className="text-[#111] font-black uppercase text-3xl md:text-4xl leading-tight mb-4"
                  style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
                >
                  {p.name}
                </h1>

                {/* Reviews Summary Rating */}
                {reviewsLoad.data && (
                  <div className="flex items-center gap-2 mb-6">
                    <StarRating rating={reviewsLoad.data.averageRating} size={15} />
                    <span className="text-xs text-[#555] font-medium">
                      {reviewsLoad.data.averageRating.toFixed(1)} / 5 (
                      {reviewsLoad.data.totalReviews} đánh giá)
                    </span>
                  </div>
                )}

                {/* Price Display */}
                <div className="flex items-baseline gap-3 mb-6 pb-6 border-b border-[rgba(0,0,0,0.08)]">
                  <span className="text-3xl font-extrabold text-[#111]">
                    {money(displayPrice)}
                  </span>
                  {isSale && (
                    <span className="text-base text-[#999] line-through">
                      {money(p.basePrice)}
                    </span>
                  )}
                  {isSale && (
                    <span className="text-xs font-bold text-[#E5001B] bg-red-50 border border-red-200 px-2 py-0.5 uppercase tracking-wider">
                      Tiết kiệm {money(p.basePrice - displayPrice)}
                    </span>
                  )}
                </div>

                {/* Short Description */}
                {(p.shortDescription || p.description) && (
                  <p className="text-sm text-[#555] leading-relaxed mb-6" style={{ fontWeight: 300 }}>
                    {p.shortDescription || p.description}
                  </p>
                )}

                {/* Color Selector */}
                {colorOptions.length > 0 && (
                  <div className="mb-6">
                    <div className="flex items-center justify-between mb-2.5">
                      <span className="text-xs uppercase font-bold tracking-widest text-[#111]">
                        Màu Sắc:{' '}
                        <strong className="text-[#E5001B] font-bold">
                          {colorOptions.find((c) => (c.colorOptionId || c.color) === selectedColor)
                            ?.color || 'Chưa chọn'}
                        </strong>
                      </span>
                    </div>
                    <div className="flex flex-wrap gap-2.5">
                      {colorOptions.map((v) => {
                        const key = v.colorOptionId || v.color || '';
                        const isSelected = selectedColor === key;
                        return (
                          <button
                            key={key}
                            type="button"
                            onClick={() => {
                              setSelectedColor(key);
                              setSelectedSize('');
                              setActiveImg('');
                              setFeedback(null);
                            }}
                            className={`flex items-center gap-2 px-3 py-2 border text-xs font-medium uppercase transition-all ${
                              isSelected
                                ? 'border-[#111] bg-[#111] text-white shadow-xs'
                                : 'border-[#ddd] bg-white text-[#333] hover:border-[#888]'
                            }`}
                          >
                            <span
                              className="w-3.5 h-3.5 rounded-full border border-black/10 shrink-0"
                              style={{ backgroundColor: v.colorHex || '#bbb' }}
                            />
                            <span>{v.color || 'Màu'}</span>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* Size Selector */}
                {sizeOptions.length > 0 && (
                  <div className="mb-6">
                    <div className="flex items-center justify-between mb-2.5">
                      <span className="text-xs uppercase font-bold tracking-widest text-[#111]">
                        Kích Cỡ:{' '}
                        <strong className="text-[#E5001B] font-bold">
                          {sizeOptions.find((s) => (s.sizeOptionId || s.size) === selectedSize)
                            ?.size || 'Chưa chọn'}
                        </strong>
                      </span>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {sizeOptions.map((v) => {
                        const key = v.sizeOptionId || v.size || '';
                        const isSelected = selectedSize === key;
                        return (
                          <button
                            key={key}
                            type="button"
                            onClick={() => {
                              setSelectedSize(key);
                              setFeedback(null);
                            }}
                            className={`min-w-12 h-10 px-3 border text-xs font-bold uppercase transition-all ${
                              isSelected
                                ? 'bg-[#111] text-white border-[#111]'
                                : 'bg-white text-[#111] border-[#ddd] hover:border-[#111]'
                            }`}
                          >
                            {v.size || 'Size'}
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* Quantity */}
                <div className="mb-8 flex items-center gap-4">
                  <span className="text-xs uppercase font-bold tracking-widest text-[#111]">
                    Số Lượng:
                  </span>
                  <div className="flex items-center border border-[#ddd]">
                    <button
                      type="button"
                      disabled={qty <= 1}
                      onClick={() => setQty((q) => Math.max(1, q - 1))}
                      className="w-10 h-10 flex items-center justify-center text-sm font-bold text-[#111] hover:bg-[#f5f5f5] disabled:opacity-30"
                    >
                      −
                    </button>
                    <input
                      type="number"
                      min="1"
                      max="99"
                      value={qty}
                      onChange={(e) => setQty(Math.max(1, Math.min(99, Number(e.target.value) || 1)))}
                      className="w-12 text-center text-sm font-semibold outline-none"
                    />
                    <button
                      type="button"
                      disabled={qty >= 99}
                      onClick={() => setQty((q) => Math.min(99, q + 1))}
                      className="w-10 h-10 flex items-center justify-center text-sm font-bold text-[#111] hover:bg-[#f5f5f5] disabled:opacity-30"
                    >
                      +
                    </button>
                  </div>
                </div>

                {/* Feedback Toast */}
                {feedback && (
                  <div
                    className={`mb-6 p-4 text-xs font-medium flex items-center gap-3 border ${
                      feedback.type === 'success'
                        ? 'bg-emerald-50 text-emerald-900 border-emerald-200'
                        : 'bg-red-50 text-red-900 border-red-200'
                    }`}
                  >
                    {feedback.type === 'success' ? (
                      <Check size={16} className="text-emerald-700 shrink-0" />
                    ) : (
                      <AlertCircle size={16} className="text-red-700 shrink-0" />
                    )}
                    <span>{feedback.message}</span>
                  </div>
                )}

                {/* Action Buttons */}
                <div className="flex flex-col sm:flex-row gap-3 mb-8">
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => void handleAddToCart(false)}
                    className="flex-1 bg-[#111] text-white py-4 px-6 text-xs uppercase tracking-widest font-bold hover:bg-[#E5001B] active:scale-[0.98] transition-all disabled:opacity-50 flex items-center justify-center gap-2"
                  >
                    <ShoppingBag size={15} /> Thêm Vào Giỏ
                  </button>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => void handleAddToCart(true)}
                    className="flex-1 border-2 border-[#111] text-[#111] py-4 px-6 text-xs uppercase tracking-widest font-bold hover:bg-[#111] hover:text-white active:scale-[0.98] transition-all disabled:opacity-50"
                  >
                    Mua Ngay
                  </button>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => void toggleWishlist()}
                    aria-label={liked ? 'Bỏ yêu thích' : 'Yêu thích'}
                    className={`w-14 h-12 flex items-center justify-center border transition-colors shrink-0 ${
                      liked
                        ? 'border-[#E5001B] bg-red-50 text-[#E5001B]'
                        : 'border-[#ddd] hover:border-[#111] text-[#111]'
                    }`}
                  >
                    <Heart
                      size={18}
                      className={liked ? 'fill-[#E5001B] stroke-[#E5001B]' : 'stroke-current'}
                    />
                  </button>
                </div>

                {/* Guarantees */}
                <div className="bg-[#fafafa] border border-[rgba(0,0,0,0.06)] p-5 space-y-3 mb-8 text-xs text-[#555]">
                  <div className="flex items-center gap-3">
                    <Truck size={16} className="text-[#111] shrink-0" />
                    <span>Miễn phí vận chuyển cho đơn hàng từ 500.000 ₫</span>
                  </div>
                  <div className="flex items-center gap-3">
                    <RefreshCw size={16} className="text-[#111] shrink-0" />
                    <span>Đổi trả thuận tiện trong vòng 7 ngày</span>
                  </div>
                  <div className="flex items-center gap-3">
                    <ShieldCheck size={16} className="text-[#111] shrink-0" />
                    <span>Cam kết chất lượng chính hãng từ LINO</span>
                  </div>
                </div>

                {/* Accordions */}
                <div className="border-t border-[rgba(0,0,0,0.08)]">
                  <AccordionItem title="Mô Tả Sản Phẩm" defaultOpen={true}>
                    <div className="space-y-4">
                      <p>{p.description || 'Chưa có thông tin mô tả chi tiết.'}</p>
                      {p.attributes && p.attributes.length > 0 && (
                        <div className="pt-2 border-t border-[rgba(0,0,0,0.06)]">
                          <p className="font-bold text-xs uppercase tracking-wider text-[#111] mb-2">
                            Đặc tính nổi bật:
                          </p>
                          <ul className="list-disc pl-5 space-y-1 text-xs text-[#666]">
                            {p.attributes.map((attr) => (
                              <li key={attr.id}>
                                <strong>{attr.name}:</strong> {attr.value}
                              </li>
                            ))}
                          </ul>
                        </div>
                      )}
                    </div>
                  </AccordionItem>

                  {/* Size Chart Accordion */}
                  {sizeChartLoad.data?.rows && sizeChartLoad.data.rows.length > 0 && (
                    <AccordionItem title="Bảng Thông Số Kích Cỡ">
                      <div className="overflow-x-auto">
                        <table className="w-full text-left text-xs border border-[#eee]">
                          <thead className="bg-[#f5f5f5] text-[#111] font-bold">
                            <tr>
                              <th className="p-2.5 border-b">Size</th>
                              <th className="p-2.5 border-b">Vòng Ngực (cm)</th>
                              <th className="p-2.5 border-b">Vòng Eo (cm)</th>
                              <th className="p-2.5 border-b">Chiều Cao (cm)</th>
                              <th className="p-2.5 border-b">Cân Nặng (kg)</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-[#eee]">
                            {sizeChartLoad.data.rows.map((r) => (
                              <tr key={r.id}>
                                <td className="p-2.5 font-bold">{r.sizeCode}</td>
                                <td className="p-2.5">{r.chest || '—'}</td>
                                <td className="p-2.5">{r.waist || '—'}</td>
                                <td className="p-2.5">
                                  {r.heightMin && r.heightMax
                                    ? `${r.heightMin}–${r.heightMax}`
                                    : '—'}
                                </td>
                                <td className="p-2.5">
                                  {r.weightMin && r.weightMax
                                    ? `${r.weightMin}–${r.weightMax}`
                                    : '—'}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                      <p className="mt-2 text-[11px] text-[#888]">
                        * Bảng kích cỡ mang tính chất tham khảo chuẩn theo form dáng thiết kế của LINO.
                      </p>
                    </AccordionItem>
                  )}

                  <AccordionItem title="Chính Sách Giao Hàng & Đổi Trả">
                    <div className="space-y-2 text-xs">
                      <p>
                        <strong>Thời gian giao hàng:</strong> Từ 2–4 ngày làm việc trên toàn quốc qua
                        đơn vị vận chuyển Giao Hàng Nhanh (GHN).
                      </p>
                      <p>
                        <strong>Chính sách đổi trả:</strong> Hỗ trợ trả hàng trong vòng 7 ngày kể từ
                        ngày nhận hàng đối với sản phẩm còn nguyên tem mác và chưa qua sử dụng.
                      </p>
                    </div>
                  </AccordionItem>
                </div>
              </div>
            </div>

            {/* Customer Reviews Section */}
            <section className="mt-20 pt-12 border-t border-[rgba(0,0,0,0.08)]">
              <div className="flex flex-col sm:flex-row sm:items-end justify-between mb-8 gap-4">
                <div>
                  <p className="text-xs uppercase tracking-[0.25em] text-[#888] font-bold mb-1">
                    Trải Nghiệm Khách Hàng
                  </p>
                  <h2
                    className="text-[#111] font-black uppercase text-3xl"
                    style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
                  >
                    ĐÁNH GIÁ SẢN PHẨM
                  </h2>
                </div>
                {reviewsLoad.data && (
                  <div className="flex items-center gap-3">
                    <span className="text-2xl font-black text-[#111]">
                      {reviewsLoad.data.averageRating.toFixed(1)}
                    </span>
                    <div>
                      <StarRating rating={reviewsLoad.data.averageRating} size={16} />
                      <p className="text-[11px] text-[#888] mt-0.5">
                        Dựa trên {reviewsLoad.data.totalReviews} lượt đánh giá
                      </p>
                    </div>
                  </div>
                )}
              </div>

              <Status
                loading={reviewsLoad.loading}
                error={reviewsLoad.error}
                retry={() => void reviewsLoad.refresh()}
              />

              {reviewsLoad.data && reviewsLoad.data.reviews.items.length > 0 ? (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                  {reviewsLoad.data.reviews.items.map((r: Review) => (
                    <article
                      key={r.id}
                      className="border border-[rgba(0,0,0,0.08)] p-6 bg-white flex flex-col justify-between"
                    >
                      <div>
                        <div className="flex items-center justify-between mb-3">
                          <StarRating rating={r.rating} size={14} />
                          {r.verifiedPurchase && (
                            <span className="text-[10px] uppercase font-bold tracking-widest text-[#2D5A3D] bg-emerald-50 px-2 py-0.5 border border-emerald-200">
                              Đã Mua Hàng
                            </span>
                          )}
                        </div>
                        <p className="text-sm text-[#333] leading-relaxed mb-4">{r.comment}</p>
                      </div>
                      <p className="text-xs text-[#888] border-t border-[rgba(0,0,0,0.05)] pt-3">
                        {new Date(r.createdAt).toLocaleDateString('vi-VN')}
                      </p>
                    </article>
                  ))}
                </div>
              ) : (
                !reviewsLoad.loading && (
                  <div className="text-center py-12 border border-dashed border-[#ddd] p-6">
                    <p className="text-sm text-[#888]">Chưa có đánh giá nào cho sản phẩm này.</p>
                  </div>
                )
              )}
            </section>

            {/* Related Products */}
            {relatedProducts.length > 0 && (
              <section className="mt-20 pt-12 border-t border-[rgba(0,0,0,0.08)]">
                <div className="mb-8">
                  <p className="text-xs uppercase tracking-[0.25em] text-[#888] font-bold mb-1">
                    Gợi Ý Dành Cho Bạn
                  </p>
                  <h2
                    className="text-[#111] font-black uppercase text-3xl"
                    style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
                  >
                    SẢN PHẨM LIÊN QUAN
                  </h2>
                </div>
                <div className="grid grid-cols-2 md:grid-cols-4 gap-5">
                  {relatedProducts.map((item) => (
                    <ProductCard key={item.id} product={item} />
                  ))}
                </div>
              </section>
            )}
          </>
        )}
      </div>
    </div>
  );
}
