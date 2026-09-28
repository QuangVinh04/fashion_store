import { useState } from "react";
import { Link, useParams, useNavigate } from "react-router";
import { Heart, Star, ChevronDown, ChevronUp, ShoppingBag, ArrowRight, Truck, RefreshCw, Shield } from "lucide-react";
import { PRODUCTS, formatVND } from "../data/products";
import { useCart } from "../context/CartContext";

const REVIEWS = [
  { name: "Nguyễn Minh Anh", rating: 5, date: "15/06/2025", comment: "Sản phẩm rất đẹp, chất vải mềm mại và thoáng mát. Giao hàng nhanh, đóng gói cẩn thận. Sẽ ủng hộ LINO dài dài!", size: "M", color: "Đen" },
  { name: "Trần Thị Hoa", rating: 4, date: "02/06/2025", comment: "Chất lượng tốt, đúng mô tả. Màu sắc đẹp, size chuẩn theo bảng hướng dẫn. Chỉ tiếc là giao hơi lâu một chút.", size: "S", color: "Trắng" },
  { name: "Lê Văn Hùng", rating: 5, date: "28/05/2025", comment: "Mua lần thứ 3 rồi vẫn hài lòng. LINO luôn giữ được chất lượng nhất quán. Áo này đặc biệt mềm, mặc cả ngày không khó chịu.", size: "L", color: "Xanh Biển" },
];

function StarRating({ rating, size = 14 }: { rating: number; size?: number }) {
  return (
    <div className="flex items-center gap-0.5">
      {[1, 2, 3, 4, 5].map((i) => (
        <Star
          key={i}
          size={size}
          className={i <= Math.round(rating) ? "fill-[#111] stroke-[#111]" : "fill-[#e0e0e0] stroke-[#e0e0e0]"}
        />
      ))}
    </div>
  );
}

function AccordionItem({ title, children }: { title: string; children: React.ReactNode }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="border-b border-[rgba(0,0,0,0.1)]">
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center justify-between w-full py-4 text-left"
      >
        <span className="text-sm font-semibold uppercase tracking-widest text-[#111]">{title}</span>
        {open ? <ChevronUp size={14} className="text-[#888] flex-shrink-0" /> : <ChevronDown size={14} className="text-[#888] flex-shrink-0" />}
      </button>
      {open && <div className="pb-5 text-sm text-[#555] leading-relaxed" style={{ fontWeight: 300 }}>{children}</div>}
    </div>
  );
}

export default function ProductDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { addItem } = useCart();

  const product = PRODUCTS.find((p) => p.id === Number(id));

  const [activeImg, setActiveImg] = useState(0);
  const [selectedColor, setSelectedColor] = useState(0);
  const [selectedSize, setSelectedSize] = useState<string | null>(null);
  const [qty, setQty] = useState(1);
  const [liked, setLiked] = useState(false);
  const [sizeError, setSizeError] = useState(false);
  const [added, setAdded] = useState(false);

  if (!product) {
    return (
      <div className="max-w-[1400px] mx-auto px-6 py-24 text-center">
        <p className="text-[#888] text-sm mb-4">Không tìm thấy sản phẩm.</p>
        <Link to="/products" className="text-xs uppercase tracking-widest text-[#111] underline">Xem tất cả sản phẩm</Link>
      </div>
    );
  }

  const related = PRODUCTS.filter((p) => p.category === product.category && p.id !== product.id).slice(0, 4);

  const handleAddToCart = () => {
    if (!selectedSize) { setSizeError(true); return; }
    setSizeError(false);
    addItem(product, qty, selectedSize, product.colors[selectedColor].name);
    setAdded(true);
    setTimeout(() => setAdded(false), 2000);
  };

  const handleBuyNow = () => {
    if (!selectedSize) { setSizeError(true); return; }
    setSizeError(false);
    addItem(product, qty, selectedSize, product.colors[selectedColor].name);
    navigate("/cart");
  };

  return (
    <div style={{ fontFamily: "'Inter', sans-serif" }}>
      {/* Breadcrumb */}
      <div className="max-w-[1400px] mx-auto px-6 py-5 border-b border-[rgba(0,0,0,0.08)]">
        <nav className="flex items-center gap-2 text-xs text-[#888] uppercase tracking-widest">
          <Link to="/" className="hover:text-[#111] transition-colors">Trang Chủ</Link>
          <span>/</span>
          <Link to="/products" className="hover:text-[#111] transition-colors">Sản Phẩm</Link>
          <span>/</span>
          <Link to={`/products?cat=${encodeURIComponent(product.category)}`} className="hover:text-[#111] transition-colors">{product.category}</Link>
          <span>/</span>
          <span className="text-[#111] truncate max-w-[200px]">{product.name}</span>
        </nav>
      </div>

      {/* Main Content */}
      <div className="max-w-[1400px] mx-auto px-6 py-10">
        <div className="grid lg:grid-cols-[1fr_480px] xl:grid-cols-[1fr_520px] gap-12 xl:gap-16">

          {/* Image Gallery */}
          <div className="flex gap-4">
            {/* Thumbnails */}
            <div className="hidden sm:flex flex-col gap-2 w-20 flex-shrink-0">
              {product.images.map((img, i) => (
                <button
                  key={i}
                  onClick={() => setActiveImg(i)}
                  className={`aspect-square overflow-hidden bg-[#f5f5f5] transition-all duration-150 ${activeImg === i ? "ring-2 ring-[#111]" : "opacity-60 hover:opacity-100"}`}
                >
                  <img src={img} alt={`${product.name} ${i + 1}`} className="w-full h-full object-cover" />
                </button>
              ))}
            </div>
            {/* Main Image */}
            <div className="flex-1 relative overflow-hidden bg-[#f5f5f5] aspect-[3/4]">
              <img
                src={product.images[activeImg]}
                alt={product.name}
                className="w-full h-full object-cover"
              />
              {product.tag && (
                <span className={`absolute top-4 left-4 text-white text-[10px] font-medium tracking-widest uppercase px-2 py-1 ${product.tag === "Sale" ? "bg-[#E5001B]" : product.tag === "Mới" ? "bg-[#111]" : "bg-[#2D5A3D]"}`}>
                  {product.tag}
                </span>
              )}
              <button
                onClick={() => setLiked(!liked)}
                className="absolute top-4 right-4 w-9 h-9 flex items-center justify-center bg-white"
                aria-label="Yêu thích"
              >
                <Heart size={18} className={liked ? "fill-[#E5001B] stroke-[#E5001B]" : "stroke-[#111]"} />
              </button>
            </div>
          </div>

          {/* Product Info */}
          <div>
            <p className="text-xs uppercase tracking-widest text-[#888] mb-2">{product.subcategory}</p>
            <h1
              className="text-[#111] mb-3"
              style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 800, fontSize: "clamp(1.75rem, 3vw, 2.5rem)", letterSpacing: "-0.01em", lineHeight: 1.1 }}
            >
              {product.name.toUpperCase()}
            </h1>

            {/* Rating */}
            <div className="flex items-center gap-3 mb-5">
              <StarRating rating={product.rating} />
              <span className="text-sm text-[#555]">{product.rating}</span>
              <span className="text-xs text-[#888]">({product.reviewCount} đánh giá)</span>
            </div>

            {/* Price */}
            <div className="flex items-baseline gap-3 mb-6">
              <p className="text-2xl font-bold text-[#111]">{formatVND(product.price)}</p>
              {product.originalPrice && (
                <>
                  <p className="text-base text-[#aaa] line-through">{formatVND(product.originalPrice)}</p>
                  <span className="bg-[#E5001B] text-white text-xs px-2 py-0.5 font-medium">
                    -{Math.round((1 - product.price / product.originalPrice) * 100)}%
                  </span>
                </>
              )}
            </div>

            {/* Color */}
            <div className="mb-6">
              <div className="flex items-center justify-between mb-3">
                <p className="text-xs font-semibold uppercase tracking-widest text-[#111]">Màu Sắc</p>
                <p className="text-sm text-[#555]">{product.colors[selectedColor].name}</p>
              </div>
              <div className="flex items-center gap-3">
                {product.colors.map((c, i) => (
                  <button
                    key={i}
                    onClick={() => setSelectedColor(i)}
                    className="w-8 h-8 rounded-full transition-all duration-150 relative"
                    style={{
                      backgroundColor: c.hex,
                      border: selectedColor === i ? "2px solid #111" : "1px solid #ddd",
                      outline: selectedColor === i ? "2px solid #fff" : "none",
                      outlineOffset: "-3px",
                    }}
                    title={c.name}
                  />
                ))}
              </div>
            </div>

            {/* Size */}
            <div className="mb-6">
              <div className="flex items-center justify-between mb-3">
                <p className="text-xs font-semibold uppercase tracking-widest text-[#111]">Size</p>
                <button className="text-xs text-[#888] underline hover:text-[#111] transition-colors">Hướng Dẫn Chọn Size</button>
              </div>
              <div className="flex flex-wrap gap-2">
                {product.sizes.map((size) => (
                  <button
                    key={size}
                    onClick={() => { setSelectedSize(size); setSizeError(false); }}
                    className={`min-w-[48px] h-11 px-3 text-sm font-medium transition-all duration-150 border ${
                      selectedSize === size
                        ? "border-[#111] bg-[#111] text-white"
                        : "border-[rgba(0,0,0,0.2)] text-[#111] hover:border-[#111]"
                    }`}
                  >
                    {size}
                  </button>
                ))}
              </div>
              {sizeError && (
                <p className="text-xs text-[#E5001B] mt-2">Vui lòng chọn size trước khi thêm vào giỏ.</p>
              )}
            </div>

            {/* Quantity */}
            <div className="mb-7">
              <p className="text-xs font-semibold uppercase tracking-widest text-[#111] mb-3">Số Lượng</p>
              <div className="flex items-center border border-[rgba(0,0,0,0.2)] w-fit">
                <button
                  onClick={() => setQty(Math.max(1, qty - 1))}
                  className="w-11 h-11 flex items-center justify-center text-[#111] hover:bg-[#f5f5f5] transition-colors text-lg"
                >
                  −
                </button>
                <span className="w-12 text-center text-sm font-medium text-[#111]">{qty}</span>
                <button
                  onClick={() => setQty(qty + 1)}
                  className="w-11 h-11 flex items-center justify-center text-[#111] hover:bg-[#f5f5f5] transition-colors text-lg"
                >
                  +
                </button>
              </div>
            </div>

            {/* CTAs */}
            <div className="flex flex-col gap-3 mb-8">
              <button
                onClick={handleAddToCart}
                className={`w-full flex items-center justify-center gap-3 py-4 text-xs tracking-widest uppercase font-medium transition-colors duration-300 ${
                  added ? "bg-[#2D5A3D] text-white" : "bg-[#111] text-white hover:bg-[#333]"
                }`}
              >
                <ShoppingBag size={16} />
                {added ? "Đã Thêm Vào Giỏ ✓" : "Thêm Vào Giỏ Hàng"}
              </button>
              <button
                onClick={handleBuyNow}
                className="w-full flex items-center justify-center gap-3 py-4 text-xs tracking-widest uppercase font-medium border-2 border-[#111] text-[#111] hover:bg-[#111] hover:text-white transition-colors duration-300"
              >
                Mua Ngay <ArrowRight size={14} />
              </button>
            </div>

            {/* Shipping Info */}
            <div className="border-t border-[rgba(0,0,0,0.1)] pt-6 space-y-3 mb-6">
              {[
                { icon: Truck, text: "Miễn phí vận chuyển cho đơn hàng từ 500.000 ₫" },
                { icon: RefreshCw, text: "Đổi trả miễn phí trong 30 ngày" },
                { icon: Shield, text: "Bảo hành chính hãng 6 tháng" },
              ].map(({ icon: Icon, text }) => (
                <div key={text} className="flex items-center gap-3">
                  <Icon size={14} className="text-[#888] flex-shrink-0" />
                  <p className="text-xs text-[#555]">{text}</p>
                </div>
              ))}
            </div>

            {/* Accordion */}
            <div className="border-t border-[rgba(0,0,0,0.1)]">
              <AccordionItem title="Mô Tả Sản Phẩm">
                <p>{product.description}</p>
              </AccordionItem>
              <AccordionItem title="Chất Liệu & Thành Phần">
                <p className="mb-3">{product.material}</p>
              </AccordionItem>
              <AccordionItem title="Hướng Dẫn Bảo Quản">
                <ul className="space-y-2">
                  {product.care.map((c) => (
                    <li key={c} className="flex items-start gap-2">
                      <span className="text-[#E5001B] flex-shrink-0 mt-0.5">—</span>
                      <span>{c}</span>
                    </li>
                  ))}
                </ul>
              </AccordionItem>
            </div>
          </div>
        </div>

        {/* Reviews */}
        <section className="mt-20 border-t border-[rgba(0,0,0,0.1)] pt-16">
          <div className="flex items-end justify-between mb-10">
            <div>
              <h2 style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 800, fontSize: "clamp(1.75rem, 3vw, 2.5rem)", letterSpacing: "-0.01em", lineHeight: 1, color: "#111" }}>
                ĐÁNH GIÁ KHÁCH HÀNG
              </h2>
              <div className="flex items-center gap-4 mt-3">
                <div className="flex items-center gap-2">
                  <StarRating rating={product.rating} size={16} />
                  <span className="text-2xl font-bold text-[#111]">{product.rating}</span>
                </div>
                <span className="text-sm text-[#888]">Dựa trên {product.reviewCount} đánh giá</span>
              </div>
            </div>
          </div>
          <div className="grid md:grid-cols-3 gap-6">
            {REVIEWS.map((r, i) => (
              <div key={i} className="border border-[rgba(0,0,0,0.1)] p-6">
                <div className="flex items-start justify-between mb-3">
                  <div>
                    <p className="text-sm font-semibold text-[#111]">{r.name}</p>
                    <p className="text-xs text-[#888] mt-0.5">{r.date}</p>
                  </div>
                  <StarRating rating={r.rating} size={12} />
                </div>
                <p className="text-sm text-[#555] leading-relaxed mb-4" style={{ fontWeight: 300 }}>{r.comment}</p>
                <div className="flex gap-3">
                  <span className="text-[10px] uppercase tracking-widest bg-[#f5f5f5] text-[#888] px-2 py-1">Size: {r.size}</span>
                  <span className="text-[10px] uppercase tracking-widest bg-[#f5f5f5] text-[#888] px-2 py-1">Màu: {r.color}</span>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* Related Products */}
        {related.length > 0 && (
          <section className="mt-20 border-t border-[rgba(0,0,0,0.1)] pt-16">
            <div className="flex items-end justify-between mb-8">
              <h2 style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 800, fontSize: "clamp(1.75rem, 3vw, 2.5rem)", letterSpacing: "-0.01em", lineHeight: 1, color: "#111" }}>
                SẢN PHẨM TƯƠNG TỰ
              </h2>
              <Link to="/products" className="hidden sm:flex items-center gap-2 text-xs tracking-widest uppercase text-[#888] hover:text-[#111] transition-colors border-b border-transparent hover:border-[#111] pb-0.5">
                Xem Thêm
              </Link>
            </div>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-x-5 gap-y-10">
              {related.map((p) => {
                const [relLiked, setRelLiked] = useState(false);
                return (
                  <div key={p.id} className="group cursor-pointer">
                    <Link to={`/products/${p.id}`} className="block relative overflow-hidden bg-[#f5f5f5] aspect-[3/4]">
                      <img src={p.img} alt={p.name} className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105" />
                      {p.tag && <span className="absolute top-3 left-3 bg-[#111] text-white text-[10px] font-medium tracking-widest uppercase px-2 py-1">{p.tag}</span>}
                      <button onClick={(e) => { e.preventDefault(); setRelLiked(!relLiked); }} className="absolute top-3 right-3 w-8 h-8 flex items-center justify-center bg-white opacity-0 group-hover:opacity-100 transition-opacity">
                        <Heart size={16} className={relLiked ? "fill-[#E5001B] stroke-[#E5001B]" : "stroke-[#111]"} />
                      </button>
                    </Link>
                    <div className="mt-3">
                      <Link to={`/products/${p.id}`} className="text-sm font-medium text-[#111] hover:text-[#E5001B] transition-colors block">{p.name}</Link>
                      <p className="text-sm font-semibold text-[#111] mt-1">{formatVND(p.price)}</p>
                    </div>
                  </div>
                );
              })}
            </div>
          </section>
        )}
      </div>
    </div>
  );
}
