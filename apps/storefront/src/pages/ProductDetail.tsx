import { useState, useEffect, useMemo } from "react";
import { Link, useParams, useNavigate } from "react-router";
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
} from "lucide-react";
import { store } from "../api/store";
import { media, money } from "../api/client";
import type {
  Product,
  ProductImage,
  ProductSummary,
  Review,
  Variant,
} from "../api/types";
import {
  PageTitle,
  ProductCard,
  ProductPhoto,
  StarRating,
  Status,
  useLoad,
} from "../components/StoreUI";
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
  return (
    <div className="border-b border-border py-4">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="flex items-center justify-between w-full py-1 text-left font-semibold text-xs text-foreground hover:text-primary transition-colors store-button"
      >
        <span>{title}</span>
        {open ? (
          <ChevronUp size={16} className="text-muted-foreground shrink-0" />
        ) : (
          <ChevronDown size={16} className="text-muted-foreground shrink-0" />
        )}
      </button>
      {open && (
        <div className="pt-3 pb-2 text-sm text-muted-foreground leading-relaxed">
          {children}
        </div>
      )}
    </div>
  );
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
  const [activeImg, setActiveImg] = useState<string>("");
  const [liked, setLiked] = useState(false);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{
    type: "success" | "error";
    message: string;
  } | null>(null);

  const [relatedProducts, setRelatedProducts] = useState<ProductSummary[]>([]);

  const p = productLoad.data;

  // Reset state when product id changes
  useEffect(() => {
    setSelectedColor("");
    setSelectedSize("");
    setQty(1);
    setActiveImg("");
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
    const list = p?.images || [];
    if (!selectedColor) return list.sort((a, b) => a.sortOrder - b.sortOrder);
    const colorImages = list.filter(
      (img) => !img.colorOptionId || img.colorOptionId === selectedColor,
    );
    return (colorImages.length > 0 ? colorImages : list).sort(
      (a, b) => a.sortOrder - b.sortOrder,
    );
  }, [p?.images, selectedColor]);

  const currentPhotoUrl =
    activeImg ||
    filteredImages[0]?.url ||
    (filteredImages[0]?.mediaId ? media(filteredImages[0].mediaId) : "") ||
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
        type: "error",
        message: "Vui lòng chọn đầy đủ màu sắc và kích cỡ sản phẩm.",
      });
      return;
    }

    setBusy(true);
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
          (e as Error).message || "Không thể thêm sản phẩm vào giỏ hàng.",
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
      setFeedback({ type: "error", message: (e as Error).message });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="w-full">
      {/* Breadcrumb */}
      <div className="border-b border-border bg-background">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-4">
          <nav className="flex items-center gap-2 text-xs text-muted-foreground font-medium overflow-x-auto whitespace-nowrap">
            <Link to="/" className="hover:text-foreground store-text-link">
              Trang Chủ
            </Link>
            <span>/</span>
            <Link
              to="/products"
              className="hover:text-foreground store-text-link"
            >
              Sản Phẩm
            </Link>
            {p?.categoryName && (
              <>
                <span>/</span>
                <Link
                  to={`/products?categoryId=${p.categoryId || ""}`}
                  className="hover:text-foreground store-text-link"
                >
                  {p.categoryName}
                </Link>
              </>
            )}
            <span>/</span>
            <span className="text-foreground truncate max-w-xs">
              {p?.name || "Chi Tiết Sản Phẩm"}
            </span>
          </nav>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-10">
        <Status
          loading={productLoad.loading}
          error={productLoad.error}
          retry={() => void productLoad.refresh()}
        />

        {p && (
          <>
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 xl:gap-6">
              {/* Product Gallery */}
              <div className="space-y-4">
                <div className="relative aspect-[3/4] rounded-2xl bg-card overflow-hidden group">
                  <ProductPhoto src={currentPhotoUrl} name={p.name} />
                  {isSale && (
                    <span className="absolute top-4 left-4 bg-primary text-white text-xs font-semibold px-3 py-1">
                      Sale
                    </span>
                  )}
                </div>

                {/* Thumbnails */}
                {filteredImages.length > 1 && (
                  <div className="flex gap-3 overflow-x-auto pb-2">
                    {filteredImages.map((img) => {
                      const url =
                        img.url || (img.mediaId ? media(img.mediaId) : "");
                      const isActive = url === currentPhotoUrl;
                      return (
                        <button
                          key={img.id}
                          type="button"
                          onClick={() => setActiveImg(url)}
                          className={`w-20 h-24 shrink-0 bg-background overflow-hidden border-2 transition-all ${
                            isActive
                              ? "border-border-strong"
                              : "border-transparent hover:border-border-strong"
                          } store-button`}
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
              <div className="store-panel flex flex-col">
                <p className="text-xs text-muted-foreground font-semibold mb-2">
                  {p.brandName || p.categoryName || "LINO"}
                </p>
                <h1 className="text-foreground font-medium text-base leading-snug mb-4 text-balance">
                  {p.name}
                </h1>

                {/* Reviews Summary Rating */}
                {reviewsLoad.data && (
                  <div className="flex items-center gap-2 mb-6">
                    <StarRating
                      rating={reviewsLoad.data.averageRating}
                      size={15}
                    />
                    <span className="text-xs text-muted-foreground font-medium">
                      {reviewsLoad.data.averageRating.toFixed(1)} / 5 (
                      {reviewsLoad.data.totalReviews} đánh giá)
                    </span>
                  </div>
                )}

                {/* Price Display */}
                <div className="flex items-baseline gap-3 mb-6 pb-6 border-b border-border">
                  <span className="text-lg font-semibold tabular-nums text-foreground">
                    {money(displayPrice)}
                  </span>
                  {isSale && (
                    <span className="text-base text-muted-foreground line-through">
                      {money(p.basePrice)}
                    </span>
                  )}
                  {isSale && (
                    <span className="text-xs font-semibold text-primary bg-red-50 border border-red-200 px-2 py-0.5">
                      Tiết kiệm {money(p.basePrice - displayPrice)}
                    </span>
                  )}
                </div>

                {/* Short Description */}
                {(p.shortDescription || p.description) && (
                  <p className="text-sm text-muted-foreground leading-relaxed mb-6">
                    {p.shortDescription || p.description}
                  </p>
                )}

                {/* Color Selector */}
                {colorOptions.length > 0 && (
                  <div className="mb-6">
                    <div className="flex items-center justify-between mb-2.5">
                      <span className="text-xs font-semibold text-foreground">
                        Màu Sắc:{" "}
                        <strong className="text-primary font-semibold">
                          {colorOptions.find(
                            (c) =>
                              (c.colorOptionId || c.color) === selectedColor,
                          )?.color || "Chưa chọn"}
                        </strong>
                      </span>
                    </div>
                    <div className="flex flex-wrap gap-2.5">
                      {colorOptions.map((v) => {
                        const key = v.colorOptionId || v.color || "";
                        const isSelected = selectedColor === key;
                        return (
                          <button
                            key={key}
                            type="button"
                            onClick={() => {
                              setSelectedColor(key);
                              setSelectedSize("");
                              setActiveImg("");
                              setFeedback(null);
                            }}
                            className={`flex items-center gap-2 px-3 py-2 border text-xs font-medium transition-all ${
                              isSelected
                                ? "border-transparent bg-secondary text-foreground "
                                : "border-border-strong bg-white text-foreground hover:border-border-strong"
                            } rounded-2xl store-button`}
                          >
                            <span
                              className="w-3.5 h-3.5 rounded-full border border-black/10 shrink-0"
                              style={{ backgroundColor: v.colorHex || "#bbb" }}
                            />
                            <span>{v.color || "Màu"}</span>
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
                      <span className="text-xs font-semibold text-foreground">
                        Kích Cỡ:{" "}
                        <strong className="text-primary font-semibold">
                          {sizeOptions.find(
                            (s) => (s.sizeOptionId || s.size) === selectedSize,
                          )?.size || "Chưa chọn"}
                        </strong>
                      </span>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {sizeOptions.map((v) => {
                        const key = v.sizeOptionId || v.size || "";
                        const isSelected = selectedSize === key;
                        return (
                          <button
                            key={key}
                            type="button"
                            onClick={() => {
                              setSelectedSize(key);
                              setFeedback(null);
                            }}
                            className={`min-w-12 h-10 px-3 border text-xs font-semibold transition-all ${
                              isSelected
                                ? "bg-secondary text-foreground border-border-strong"
                                : "bg-white text-foreground border-border-strong hover:border-border-strong"
                            } rounded-2xl store-button`}
                          >
                            {v.size || "Size"}
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* Quantity */}
                <div className="mb-8 flex items-center gap-4">
                  <span className="text-xs font-semibold text-foreground">
                    Số Lượng:
                  </span>
                  <div className="flex items-center border border-border-strong">
                    <button
                      type="button"
                      disabled={qty <= 1}
                      onClick={() => setQty((q) => Math.max(1, q - 1))}
                      className="w-10 h-10 flex items-center justify-center text-sm font-semibold text-foreground hover:bg-background disabled:opacity-30 store-button"
                    >
                      −
                    </button>
                    <input
                      type="number"
                      min="1"
                      max="99"
                      value={qty}
                      onChange={(e) =>
                        setQty(
                          Math.max(
                            1,
                            Math.min(99, Number(e.target.value) || 1),
                          ),
                        )
                      }
                      className="w-12 text-center text-sm font-semibold outline-none store-input"
                    />
                    <button
                      type="button"
                      disabled={qty >= 99}
                      onClick={() => setQty((q) => Math.min(99, q + 1))}
                      className="w-10 h-10 flex items-center justify-center text-sm font-semibold text-foreground hover:bg-background disabled:opacity-30 store-button"
                    >
                      +
                    </button>
                  </div>
                </div>

                {/* Feedback Toast */}
                {feedback && (
                  <div
                    className={`mb-6 p-4 text-xs font-medium flex items-center gap-3 border ${
                      feedback.type === "success"
                        ? "bg-emerald-50 text-emerald-900 border-emerald-200"
                        : "bg-red-50 text-red-900 border-red-200"
                    }`}
                  >
                    {feedback.type === "success" ? (
                      <Check size={16} className="text-emerald-700 shrink-0" />
                    ) : (
                      <AlertCircle
                        size={16}
                        className="text-red-700 shrink-0"
                      />
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
                    className="flex min-h-11 flex-1 items-center justify-center gap-2 rounded-xl bg-primary px-4 text-sm font-medium text-white transition-colors hover:bg-primary-hover disabled:opacity-45 md:min-h-10"
                  >
                    <ShoppingBag size={15} /> Thêm Vào Giỏ
                  </button>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => void handleAddToCart(true)}
                    className="min-h-11 flex-1 rounded-xl border border-border-strong bg-card px-4 text-sm font-medium text-foreground transition-colors hover:bg-accent disabled:opacity-45 md:min-h-10"
                  >
                    Mua Ngay
                  </button>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => void toggleWishlist()}
                    aria-label={liked ? "Bỏ yêu thích" : "Yêu thích"}
                    className={`size-11 md:size-10 flex items-center justify-center rounded-xl border transition-colors shrink-0 ${
                      liked
                        ? "border-primary bg-red-50 text-primary"
                        : "border-border-strong hover:border-border-strong text-foreground"
                    } store-button`}
                  >
                    <Heart
                      size={18}
                      className={
                        liked ? "fill-primary stroke-primary" : "stroke-current"
                      }
                    />
                  </button>
                </div>

                {/* Guarantees */}
                <div className="bg-background border border-border p-5 space-y-3 mb-8 text-xs text-muted-foreground">
                  <div className="flex items-center gap-3">
                    <Truck size={16} className="text-foreground shrink-0" />
                    <span>Miễn phí vận chuyển cho đơn hàng từ 500.000 ₫</span>
                  </div>
                  <div className="flex items-center gap-3">
                    <RefreshCw size={16} className="text-foreground shrink-0" />
                    <span>Đổi trả thuận tiện trong vòng 7 ngày</span>
                  </div>
                  <div className="flex items-center gap-3">
                    <ShieldCheck
                      size={16}
                      className="text-foreground shrink-0"
                    />
                    <span>Cam kết chất lượng chính hãng từ LINO</span>
                  </div>
                </div>

                {/* Accordions */}
                <div className="border-t border-border">
                  <AccordionItem title="Mô Tả Sản Phẩm" defaultOpen={true}>
                    <div className="space-y-4">
                      <p>
                        {p.description || "Chưa có thông tin mô tả chi tiết."}
                      </p>
                      {p.attributes && p.attributes.length > 0 && (
                        <div className="pt-2 border-t border-border">
                          <p className="font-semibold text-xs text-foreground mb-2">
                            Đặc tính nổi bật:
                          </p>
                          <ul className="list-disc pl-5 space-y-1 text-xs text-muted-foreground">
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
                  {sizeChartLoad.data?.rows &&
                    sizeChartLoad.data.rows.length > 0 && (
                      <AccordionItem title="Bảng Thông Số Kích Cỡ">
                        <div className="overflow-x-auto">
                          <table className="w-full text-left text-xs border border-secondary">
                            <thead className="bg-background text-foreground font-semibold">
                              <tr>
                                <th className="p-2.5 border-b">Size</th>
                                <th className="p-2.5 border-b">
                                  Vòng Ngực (cm)
                                </th>
                                <th className="p-2.5 border-b">Vòng Eo (cm)</th>
                                <th className="p-2.5 border-b">
                                  Chiều Cao (cm)
                                </th>
                                <th className="p-2.5 border-b">
                                  Cân Nặng (kg)
                                </th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-secondary">
                              {sizeChartLoad.data.rows.map((r) => (
                                <tr key={r.id}>
                                  <td className="p-2.5 font-semibold">
                                    {r.sizeCode}
                                  </td>
                                  <td className="p-2.5">{r.chest || "—"}</td>
                                  <td className="p-2.5">{r.waist || "—"}</td>
                                  <td className="p-2.5">
                                    {r.heightMin && r.heightMax
                                      ? `${r.heightMin}–${r.heightMax}`
                                      : "—"}
                                  </td>
                                  <td className="p-2.5">
                                    {r.weightMin && r.weightMax
                                      ? `${r.weightMin}–${r.weightMax}`
                                      : "—"}
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                        <p className="mt-2 text-xs text-muted-foreground">
                          * Bảng kích cỡ mang tính chất tham khảo chuẩn theo
                          form dáng thiết kế của LINO.
                        </p>
                      </AccordionItem>
                    )}

                  <AccordionItem title="Chính Sách Giao Hàng & Đổi Trả">
                    <div className="space-y-2 text-xs">
                      <p>
                        <strong>Thời gian giao hàng:</strong> Từ 2–4 ngày làm
                        việc trên toàn quốc qua đơn vị vận chuyển Giao Hàng
                        Nhanh (GHN).
                      </p>
                      <p>
                        <strong>Chính sách đổi trả:</strong> Hỗ trợ trả hàng
                        trong vòng 7 ngày kể từ ngày nhận hàng đối với sản phẩm
                        còn nguyên tem mác và chưa qua sử dụng.
                      </p>
                    </div>
                  </AccordionItem>
                </div>
              </div>
            </div>

            {/* Customer Reviews Section */}
            <section className="mt-10 pt-6 border-t border-border">
              <div className="flex flex-col sm:flex-row sm:items-end justify-between mb-8 gap-4">
                <div>
                  <p className="text-xs text-muted-foreground font-semibold mb-1">
                    Trải Nghiệm Khách Hàng
                  </p>
                  <h2 className="text-foreground font-semibold text-lg text-balance">
                    ĐÁNH GIÁ SẢN PHẨM
                  </h2>
                </div>
                {reviewsLoad.data && (
                  <div className="flex items-center gap-3">
                    <span className="text-2xl font-semibold text-foreground">
                      {reviewsLoad.data.averageRating.toFixed(1)}
                    </span>
                    <div>
                      <StarRating
                        rating={reviewsLoad.data.averageRating}
                        size={16}
                      />
                      <p className="text-xs text-muted-foreground mt-0.5">
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
                      className="border border-border p-4 sm:p-5 bg-white flex flex-col justify-between rounded-2xl"
                    >
                      <div>
                        <div className="flex items-center justify-between mb-3">
                          <StarRating rating={r.rating} size={14} />
                          {r.verifiedPurchase && (
                            <span className="text-xs font-semibold text-success bg-emerald-50 px-2 py-0.5 border border-emerald-200">
                              Đã Mua Hàng
                            </span>
                          )}
                        </div>
                        <p className="text-sm text-foreground leading-relaxed mb-4">
                          {r.comment}
                        </p>
                      </div>
                      <p className="text-xs text-muted-foreground border-t border-border pt-3">
                        {new Date(r.createdAt).toLocaleDateString("vi-VN")}
                      </p>
                    </article>
                  ))}
                </div>
              ) : (
                !reviewsLoad.loading && (
                  <div className="text-center py-12 border border-dashed border-border-strong p-6">
                    <p className="text-sm text-muted-foreground">
                      Chưa có đánh giá nào cho sản phẩm này.
                    </p>
                  </div>
                )
              )}
            </section>

            {/* Related Products */}
            {relatedProducts.length > 0 && (
              <section className="mt-10 pt-6 border-t border-border">
                <div className="mb-8">
                  <p className="text-xs text-muted-foreground font-semibold mb-1">
                    Gợi Ý Dành Cho Bạn
                  </p>
                  <h2 className="text-foreground font-semibold text-lg text-balance">
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
