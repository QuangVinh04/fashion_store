import { customerError } from "../api/client";
import { useState, useEffect, useMemo } from "react";
import { Link, useParams, useNavigate } from "react-router";
import {
  Heart,
  ChevronDown,
  ChevronUp,
  Truck,
  RefreshCw,
  Check,
  AlertCircle,
} from "lucide-react";
import { store } from "../api/store";
import { media, money } from "../api/client";
import type {
  ProductSummary,
  Variant,
} from "../api/types";
import {
  ProductCard,
  StarRating,
  Status,
  useLoad,
} from "../components/StoreUI";
import ProductGallery from "../components/products/ProductGallery";
import SizeGuide from "../components/products/SizeGuide";
import "../styles/product-detail.css";
import { useCart } from "../context/CartContext";
import { useAuth } from "../context/AuthContext";

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
  return <div className="pd-fold">
    <button type="button" aria-expanded={open} onClick={() => setOpen(!open)}>
      {title}{open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
    </button>
    <div className={`pd-fold-content ${open ? "is-open" : ""}`} aria-hidden={!open}><div>{children}</div></div>
  </div>;
}

export default function ProductDetail() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const { addItem } = useCart();
  const { isLoggedIn, login } = useAuth();

  const productLoad = useLoad(() => store.product(id), [id]);
  const reviewsLoad = useLoad(() => store.reviews(id), [id]);
  const sizeChartLoad = useLoad(() => store.productSizeChart(id), [id]);

  const [selectedColor, setSelectedColor] = useState<string>("");
  const [selectedSize, setSelectedSize] = useState<string>("");
  const [qty, setQty] = useState(1);
  const [sizeOpen, setSizeOpen] = useState(false);
  const [liked, setLiked] = useState(false);
  const [busy, setBusy] = useState(false);
  const [variantPrompt, setVariantPrompt] = useState(false);
  const [feedback, setFeedback] = useState<{
    type: "success" | "error";
    message: string;
  } | null>(null);

  const [relatedProducts, setRelatedProducts] = useState<ProductSummary[]>([]);

  const p = productLoad.data?.id === id ? productLoad.data : null;

  // Reset state when product id changes
  useEffect(() => {
    window.scrollTo(0, 0);
    setSelectedColor("");
    setSelectedSize("");
    setQty(1);
    setSizeOpen(false);
    setFeedback(null);
    setVariantPrompt(false);
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
        if (active)
          setRelatedProducts(
            res.items.filter((item) => item.id !== id).slice(0, 4),
          );
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
      const key = v.colorOptionId || v.color || "";
      if (key && !map.has(key)) map.set(key, v);
    });
    return Array.from(map.values());
  }, [activeVariants]);

  // Unique sizes filtered by selected color
  const sizeOptions = useMemo(() => {
    const map = new Map<string, Variant>();
    const filtered = selectedColor
      ? activeVariants.filter(
          (v) => (v.colorOptionId || v.color) === selectedColor,
        )
      : activeVariants;
    filtered.forEach((v) => {
      const key = v.sizeOptionId || v.size || "";
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
          (v.sizeOptionId || v.size) === selectedSize,
      ) || null
    );
  }, [activeVariants, selectedColor, selectedSize]);

  // Images filtered by color or default
  const filteredImages = useMemo(() => {
    const list = [...(p?.images || [])];
    if (!selectedColor) return list.sort((a, b) => a.sortOrder - b.sortOrder);
    const colorImages = list.filter(
      (img) => !img.colorOptionId || img.colorOptionId === selectedColor,
    );
    return (colorImages.length > 0 ? colorImages : list).sort(
      (a, b) => a.sortOrder - b.sortOrder,
    );
  }, [p?.images, selectedColor]);

  const galleryPhotos = filteredImages.map(img => ({ key: img.id, src: img.url || media(img.mediaId), alt: img.altText || p?.name || "Sản phẩm" }));
  if (!galleryPhotos.length && p) galleryPhotos.push({ key: "thumbnail", src: p.thumbnailUrl || media(p.thumbnailMediaId), alt: p.name });

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
      setVariantPrompt(true);
      setFeedback({
        type: "error",
        message: "Vui lòng chọn đầy đủ màu sắc và kích cỡ sản phẩm.",
      });
      return;
    }

    setBusy(true);
    setVariantPrompt(false);
    setFeedback(null);
    try {
      await addItem(selectedVariant.id, qty);
      if (buyNow) {
        navigate("/cart");
      } else {
        setFeedback({
          type: "success",
          message: "Đã thêm sản phẩm vào giỏ hàng thành công.",
        });
      }
    } catch (e) {
      setFeedback({
        type: "error",
        message:
          customerError(e) || "Không thể thêm sản phẩm vào giỏ hàng.",
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
      setFeedback({ type: "error", message: customerError(e) });
    } finally {
      setBusy(false);
    }
  }

  return <div className="pd-page">
    <div className="pd-container">
      <nav className="pd-breadcrumb" aria-label="Đường dẫn">
        <Link to="/">Trang chủ</Link><span>/</span><Link to="/products">Sản phẩm</Link>
        {p?.categoryName && <><span>/</span><Link to={`/products?categoryId=${p.categoryId || ""}`}>{p.categoryName}</Link></>}
        {p && <span className="pd-crumb-current">/ {p.name}</span>}
      </nav>
      <Status loading={productLoad.loading} error={productLoad.error} retry={() => void productLoad.refresh()} />
      {p && !productLoad.loading && !productLoad.error && <>
        <div className="pd-product">
          <div className="pd-media-column">
            <ProductGallery key={`${id}:${selectedColor}`} photos={galleryPhotos} name={p.name} />
            <section className="pd-description">
              <h2>Mô tả sản phẩm</h2>
              <AccordionItem title="Chi tiết sản phẩm" defaultOpen>
                <p>{p.description || p.shortDescription || "Chưa có mô tả sản phẩm."}</p>
                {!!p.attributes?.length && <dl className="pd-attributes">{p.attributes.map(attr => <div key={attr.id}><dt>{attr.name}</dt><dd>{attr.value || "—"}</dd></div>)}</dl>}
              </AccordionItem>
              <AccordionItem title="Giao hàng / Đổi trả"><p>Giao hàng từ 2–4 ngày làm việc. Hỗ trợ trả hàng trong 7 ngày đối với sản phẩm còn nguyên tem mác, chưa qua sử dụng.</p></AccordionItem>
            </section>
          </div>
          <section className="pd-info" aria-label="Chọn mua sản phẩm">
            <p className="pd-meta">{p.brandName || p.categoryName || "LINO"}</p>
            <h1>{p.name}</h1>
            <Link className="pd-rating-link" to={`/products/${id}/reviews`}>
              <StarRating rating={reviewsLoad.data?.averageRating || 0} size={15} />
              <span>{reviewsLoad.data ? `${reviewsLoad.data.averageRating.toFixed(1)} (${reviewsLoad.data.totalReviews} đánh giá)` : "Xem đánh giá"}</span>
            </Link>
            <div className="pd-price"><strong>{money(displayPrice)}</strong>{isSale && <del>{money(p.basePrice)}</del>}</div>
            {!!colorOptions.length && <div className={`pd-field ${variantPrompt && !selectedColor ? "pd-invalid" : ""}`}>
              <p className="pd-label">Màu sắc: <strong>{colorOptions.find(c => (c.colorOptionId || c.color) === selectedColor)?.color || "Chưa chọn"}</strong></p>
              <div className="pd-choices">{colorOptions.map(v => {
                const key = v.colorOptionId || v.color || "";
                return <button key={key} type="button" className="pd-swatch" aria-label={v.color || "Màu"} title={v.color} aria-pressed={selectedColor === key}
                  onClick={() => { setSelectedColor(key); setSelectedSize(""); setFeedback(null); setVariantPrompt(false); }}>
                  <span style={{ background: v.colorHex || "#bbb" }} />
                </button>;
              })}</div>
            </div>}
            <div className={`pd-field ${variantPrompt && !selectedSize ? "pd-invalid" : ""}`}>
              <div className="pd-label-row"><p className="pd-label">Kích cỡ: <strong>{sizeOptions.find(s => (s.sizeOptionId || s.size) === selectedSize)?.size || "Chưa chọn"}</strong></p><button type="button" id="pd-size-trigger" className="pd-text-link" onClick={() => setSizeOpen(true)}>Bảng kích cỡ</button></div>
              <div className="pd-choices">{sizeOptions.map(v => {
                const key = v.sizeOptionId || v.size || "";
                return <button key={key} type="button" className="pd-size-choice" aria-pressed={selectedSize === key} onClick={() => { setSelectedSize(key); setFeedback(null); setVariantPrompt(false); }}>{v.size || "Size"}</button>;
              })}</div>
            </div>
            <div className="pd-field"><label className="pd-label" htmlFor="pd-qty">Số lượng</label>
              <div className="pd-quantity"><button type="button" aria-label="Giảm số lượng" disabled={qty <= 1} onClick={() => setQty(q => Math.max(1, q - 1))}>−</button>
                <input id="pd-qty" aria-label="Số lượng sản phẩm" type="number" min="1" max="99" value={qty} onChange={e => setQty(Math.max(1, Math.min(99, Math.trunc(Number(e.target.value)) || 1)))} />
                <button type="button" aria-label="Tăng số lượng" disabled={qty >= 99} onClick={() => setQty(q => Math.min(99, q + 1))}>+</button></div>
            </div>
            <div className="pd-feedback" role={feedback?.type === "error" ? "alert" : "status"} aria-live="polite" data-tone={feedback?.type}>{feedback && <>{feedback.type === "success" ? <Check size={16} /> : <AlertCircle size={16} />}<span>{feedback.message}</span></>}</div>
            <div className="pd-actions">
              <button type="button" className="pd-add" disabled={busy} onClick={() => void handleAddToCart(false)}>{busy ? "Đang xử lý…" : "Thêm vào giỏ hàng"}</button>
              <button type="button" className="pd-wish" aria-label={liked ? "Bỏ yêu thích" : "Yêu thích"} aria-pressed={liked} disabled={busy} onClick={() => void toggleWishlist()}><Heart size={20} fill={liked ? "currentColor" : "none"} /></button>
              <button type="button" className="pd-buy" disabled={busy} onClick={() => void handleAddToCart(true)}>Mua ngay</button>
            </div>
            <div className="pd-services"><p><Truck size={17} />Miễn phí vận chuyển từ 500.000 ₫</p><p><RefreshCw size={17} />Đổi trả trong 7 ngày</p></div>
          </section>
        </div>
        <section className="pd-section" aria-labelledby="pd-reviews-title">
          <div className="pd-section-title"><h2 id="pd-reviews-title">Đánh giá sản phẩm</h2><Link className="pd-text-link" to={`/products/${id}/reviews`}>Xem thêm đánh giá</Link></div>
          <Status loading={reviewsLoad.loading} error={reviewsLoad.error} retry={() => void reviewsLoad.refresh()} />
          {!reviewsLoad.loading && !reviewsLoad.error && reviewsLoad.data && <div className="pd-review-preview"><div><strong className="pd-review-score">{reviewsLoad.data.averageRating.toFixed(1)} <small>/ 5</small></strong><StarRating rating={reviewsLoad.data.averageRating} size={16} /><p className="pd-meta">{reviewsLoad.data.totalReviews} đánh giá</p></div><div>{reviewsLoad.data.reviews.items.length ? reviewsLoad.data.reviews.items.slice(0, 3).map(review => <article key={review.id} className="pd-review-excerpt"><StarRating rating={review.rating} size={14} /><p>{review.comment}</p><time dateTime={review.createdAt}>{new Date(review.createdAt).toLocaleDateString("vi-VN")}</time></article>) : <p className="pd-empty">Chưa có đánh giá cho sản phẩm này.</p>}</div></div>}
        </section>
        {!!relatedProducts.length && <section className="pd-section"><h2>Sản phẩm liên quan</h2><div className="pd-related">{relatedProducts.map(item => <ProductCard key={item.id} product={item} />)}</div></section>}
        <SizeGuide open={sizeOpen} onOpenChange={setSizeOpen} rows={sizeChartLoad.data?.rows || []} loading={sizeChartLoad.loading} error={sizeChartLoad.error} retry={() => void sizeChartLoad.refresh()} />
      </>}
    </div>
  </div>;
}
