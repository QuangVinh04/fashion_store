import { useState, useMemo } from "react";
import { Link, useSearchParams } from "react-router";
import { Heart, SlidersHorizontal, X, ChevronDown, ChevronUp } from "lucide-react";
import { PRODUCTS, formatVND } from "../data/products";
import { useCart } from "../context/CartContext";

const CATEGORY_OPTIONS = ["Tất Cả", "Nam", "Nữ", "Outerwear"];
const SUBCATEGORY_OPTIONS = ["Áo Phông", "Áo Sơ Mi", "Áo Khoác", "Quần Dài", "Quần Short", "Đầm", "Chân Váy", "Áo Len", "Vest & Blazer", "Áo Sweatshirt"];
const SORT_OPTIONS = [
  { label: "Mới Nhất", value: "newest" },
  { label: "Giá Tăng Dần", value: "price_asc" },
  { label: "Giá Giảm Dần", value: "price_desc" },
  { label: "Đánh Giá Cao", value: "rating" },
];

function ProductCard({ product }: { product: (typeof PRODUCTS)[0] }) {
  const [liked, setLiked] = useState(false);
  const [activeColor, setActiveColor] = useState(0);
  const { addItem } = useCart();

  return (
    <div className="group cursor-pointer">
      <Link to={`/products/${product.id}`} className="block relative overflow-hidden bg-[#f5f5f5] aspect-[3/4]">
        <img
          src={product.img}
          alt={product.name}
          className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
        />
        {product.tag && (
          <span className={`absolute top-3 left-3 text-white text-[10px] font-medium tracking-widest uppercase px-2 py-1 ${product.tag === "Sale" ? "bg-[#E5001B]" : product.tag === "Mới" ? "bg-[#111]" : "bg-[#2D5A3D]"}`}>
            {product.tag}
          </span>
        )}
        <button
          onClick={(e) => { e.preventDefault(); setLiked(!liked); }}
          className="absolute top-3 right-3 w-8 h-8 flex items-center justify-center bg-white opacity-0 group-hover:opacity-100 transition-opacity duration-200"
          aria-label="Yêu thích"
        >
          <Heart size={16} className={liked ? "fill-[#E5001B] stroke-[#E5001B]" : "stroke-[#111]"} />
        </button>
      </Link>
      <button
        onClick={() => addItem(product, 1, product.sizes[2] ?? product.sizes[0], product.colors[activeColor].name)}
        className="w-full bg-[#111111] text-white text-xs tracking-widest uppercase py-3 opacity-0 group-hover:opacity-100 transition-opacity duration-300"
        style={{ fontWeight: 500 }}
      >
        Thêm Vào Giỏ
      </button>

      <div className="mt-3 space-y-1.5" style={{ fontFamily: "'Inter', sans-serif" }}>
        <p className="text-xs text-[#888] uppercase tracking-widest">{product.subcategory}</p>
        <Link to={`/products/${product.id}`} className="text-sm font-medium text-[#111] leading-snug hover:text-[#E5001B] transition-colors block">
          {product.name}
        </Link>
        <div className="flex items-center gap-1.5 flex-wrap">
          {product.colors.map((c, i) => (
            <button
              key={i}
              onClick={() => setActiveColor(i)}
              className="w-4 h-4 rounded-full transition-all"
              style={{
                backgroundColor: c.hex,
                border: activeColor === i ? "2px solid #111" : "1px solid #ddd",
                outline: activeColor === i ? "1px solid #fff" : "none",
                outlineOffset: "-2px",
              }}
              aria-label={c.name}
            />
          ))}
        </div>
        <div className="flex items-center gap-2">
          <p className="text-sm font-semibold text-[#111]">{formatVND(product.price)}</p>
          {product.originalPrice && (
            <p className="text-xs text-[#888] line-through">{formatVND(product.originalPrice)}</p>
          )}
        </div>
      </div>
    </div>
  );
}

function FilterSection({ title, children }: { title: string; children: React.ReactNode }) {
  const [open, setOpen] = useState(true);
  return (
    <div className="border-b border-[rgba(0,0,0,0.1)] py-5">
      <button
        className="flex items-center justify-between w-full text-left"
        onClick={() => setOpen(!open)}
      >
        <span className="text-xs font-semibold uppercase tracking-widest text-[#111]">{title}</span>
        {open ? <ChevronUp size={14} className="text-[#888]" /> : <ChevronDown size={14} className="text-[#888]" />}
      </button>
      {open && <div className="mt-4">{children}</div>}
    </div>
  );
}

export default function Products() {
  const [searchParams] = useSearchParams();
  const initialCat = searchParams.get("cat") ?? "Tất Cả";

  const [selectedCat, setSelectedCat] = useState(
    CATEGORY_OPTIONS.includes(initialCat) ? initialCat : "Tất Cả"
  );
  const [selectedSubs, setSelectedSubs] = useState<string[]>([]);
  const [priceRange, setPriceRange] = useState<[number, number]>([0, 2000000]);
  const [sortBy, setSortBy] = useState("newest");
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const toggleSub = (sub: string) => {
    setSelectedSubs((prev) =>
      prev.includes(sub) ? prev.filter((s) => s !== sub) : [...prev, sub]
    );
  };

  const filtered = useMemo(() => {
    let list = [...PRODUCTS];
    if (selectedCat !== "Tất Cả") list = list.filter((p) => p.category === selectedCat);
    if (selectedSubs.length > 0) list = list.filter((p) => selectedSubs.includes(p.subcategory));
    list = list.filter((p) => p.price >= priceRange[0] && p.price <= priceRange[1]);
    if (sortBy === "price_asc") list.sort((a, b) => a.price - b.price);
    else if (sortBy === "price_desc") list.sort((a, b) => b.price - a.price);
    else if (sortBy === "rating") list.sort((a, b) => b.rating - a.rating);
    return list;
  }, [selectedCat, selectedSubs, priceRange, sortBy]);

  const activeFiltersCount = (selectedCat !== "Tất Cả" ? 1 : 0) + selectedSubs.length;

  return (
    <div style={{ fontFamily: "'Inter', sans-serif" }}>
      {/* Page Header */}
      <div className="border-b border-[rgba(0,0,0,0.08)]">
        <div className="max-w-[1400px] mx-auto px-6 py-10">
          <nav className="flex items-center gap-2 text-xs text-[#888] mb-4 uppercase tracking-widest">
            <Link to="/" className="hover:text-[#111] transition-colors">Trang Chủ</Link>
            <span>/</span>
            <span className="text-[#111]">Sản Phẩm</span>
          </nav>
          <div className="flex items-end justify-between flex-wrap gap-4">
            <div>
              <h1 style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "clamp(2.5rem, 5vw, 4rem)", letterSpacing: "-0.02em", lineHeight: 1, color: "#111" }}>
                {selectedCat === "Tất Cả" ? "TẤT CẢ SẢN PHẨM" : selectedCat.toUpperCase()}
              </h1>
              <p className="text-sm text-[#888] mt-2">{filtered.length} sản phẩm</p>
            </div>
            <div className="flex items-center gap-3">
              <button
                onClick={() => setSidebarOpen(!sidebarOpen)}
                className="lg:hidden flex items-center gap-2 border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-xs uppercase tracking-widest text-[#111] hover:border-[#111] transition-colors"
              >
                <SlidersHorizontal size={14} />
                Lọc {activeFiltersCount > 0 && `(${activeFiltersCount})`}
              </button>
              <div className="flex items-center gap-2">
                <span className="text-xs uppercase tracking-widest text-[#888] hidden sm:block">Sắp xếp:</span>
                <select
                  value={sortBy}
                  onChange={(e) => setSortBy(e.target.value)}
                  className="border border-[rgba(0,0,0,0.15)] px-3 py-2.5 text-xs uppercase tracking-widest text-[#111] bg-white focus:outline-none focus:border-[#111] cursor-pointer"
                >
                  {SORT_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>{opt.label}</option>
                  ))}
                </select>
              </div>
            </div>
          </div>

          {/* Category Tabs */}
          <div className="flex gap-0 mt-6 border-b border-[rgba(0,0,0,0.1)]">
            {CATEGORY_OPTIONS.map((cat) => (
              <button
                key={cat}
                onClick={() => setSelectedCat(cat)}
                className="px-5 py-3 text-xs tracking-widest uppercase font-medium transition-all duration-200 relative whitespace-nowrap"
                style={{ color: selectedCat === cat ? "#111" : "#888" }}
              >
                {cat}
                {selectedCat === cat && <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-[#111]" />}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="max-w-[1400px] mx-auto px-6 py-10">
        <div className="flex gap-10">
          {/* Sidebar — desktop */}
          <aside className={`w-64 flex-shrink-0 hidden lg:block`}>
            <FilterSection title="Danh Mục Phụ">
              <div className="space-y-2.5">
                {SUBCATEGORY_OPTIONS.map((sub) => (
                  <label key={sub} className="flex items-center gap-3 cursor-pointer group">
                    <input
                      type="checkbox"
                      checked={selectedSubs.includes(sub)}
                      onChange={() => toggleSub(sub)}
                      className="w-4 h-4 border-[#ccc] accent-[#111] cursor-pointer"
                    />
                    <span className="text-sm text-[#555] group-hover:text-[#111] transition-colors">{sub}</span>
                  </label>
                ))}
              </div>
            </FilterSection>

            <FilterSection title="Giá">
              <div className="space-y-3">
                {[
                  { label: "Dưới 300.000 ₫", range: [0, 299999] as [number, number] },
                  { label: "300.000 – 600.000 ₫", range: [300000, 599999] as [number, number] },
                  { label: "600.000 – 1.000.000 ₫", range: [600000, 999999] as [number, number] },
                  { label: "Trên 1.000.000 ₫", range: [1000000, 9999999] as [number, number] },
                ].map((opt) => (
                  <label key={opt.label} className="flex items-center gap-3 cursor-pointer group">
                    <input
                      type="radio"
                      name="price"
                      checked={priceRange[0] === opt.range[0] && priceRange[1] === opt.range[1]}
                      onChange={() => setPriceRange(opt.range)}
                      className="w-4 h-4 accent-[#111] cursor-pointer"
                    />
                    <span className="text-sm text-[#555] group-hover:text-[#111] transition-colors">{opt.label}</span>
                  </label>
                ))}
                <button
                  onClick={() => setPriceRange([0, 2000000])}
                  className="text-xs uppercase tracking-widest text-[#888] hover:text-[#111] transition-colors mt-1"
                >
                  Xóa bộ lọc giá
                </button>
              </div>
            </FilterSection>

            {activeFiltersCount > 0 && (
              <button
                onClick={() => { setSelectedCat("Tất Cả"); setSelectedSubs([]); setPriceRange([0, 2000000]); }}
                className="mt-4 flex items-center gap-2 text-xs uppercase tracking-widest text-[#E5001B] hover:text-[#111] transition-colors"
              >
                <X size={12} /> Xóa tất cả bộ lọc
              </button>
            )}
          </aside>

          {/* Mobile Sidebar Drawer */}
          {sidebarOpen && (
            <div className="fixed inset-0 z-50 lg:hidden">
              <div className="absolute inset-0 bg-black/40" onClick={() => setSidebarOpen(false)} />
              <div className="absolute right-0 top-0 bottom-0 w-80 bg-white overflow-y-auto p-6">
                <div className="flex items-center justify-between mb-6">
                  <p className="text-sm font-semibold uppercase tracking-widest">Bộ Lọc</p>
                  <button onClick={() => setSidebarOpen(false)}><X size={18} /></button>
                </div>
                <div className="space-y-2.5 mb-6">
                  <p className="text-xs font-semibold uppercase tracking-widest text-[#888] mb-3">Danh Mục Phụ</p>
                  {SUBCATEGORY_OPTIONS.map((sub) => (
                    <label key={sub} className="flex items-center gap-3 cursor-pointer">
                      <input type="checkbox" checked={selectedSubs.includes(sub)} onChange={() => toggleSub(sub)} className="w-4 h-4 accent-[#111]" />
                      <span className="text-sm text-[#555]">{sub}</span>
                    </label>
                  ))}
                </div>
                <button
                  onClick={() => setSidebarOpen(false)}
                  className="w-full bg-[#111] text-white py-3 text-xs uppercase tracking-widest font-medium"
                >
                  Áp Dụng ({filtered.length} sản phẩm)
                </button>
              </div>
            </div>
          )}

          {/* Product Grid */}
          <div className="flex-1 min-w-0">
            {filtered.length === 0 ? (
              <div className="text-center py-24">
                <p className="text-[#888] text-sm">Không tìm thấy sản phẩm phù hợp.</p>
                <button
                  onClick={() => { setSelectedCat("Tất Cả"); setSelectedSubs([]); setPriceRange([0, 2000000]); }}
                  className="mt-4 text-xs uppercase tracking-widest text-[#111] underline"
                >
                  Xóa bộ lọc
                </button>
              </div>
            ) : (
              <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-x-5 gap-y-10">
                {filtered.map((p) => <ProductCard key={p.id} product={p} />)}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
