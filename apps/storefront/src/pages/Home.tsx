import { useState } from "react";
import { Link } from "react-router";
import { Heart, ArrowRight } from "lucide-react";
import { PRODUCTS, CATEGORIES, formatVND } from "../data/products";
import { useCart } from "../context/CartContext";

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
          <span className="absolute top-3 left-3 bg-[#E5001B] text-white text-[10px] font-medium tracking-widest uppercase px-2 py-1">
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
        className="w-full bg-[#111111] text-white text-xs tracking-widest uppercase py-3 opacity-0 group-hover:opacity-100 transition-opacity duration-300 -mt-0"
        style={{ fontWeight: 500 }}
      >
        Thêm Vào Giỏ
      </button>

      <div className="mt-3 space-y-1.5">
        <p className="text-xs text-[#888] uppercase tracking-widest">{product.subcategory}</p>
        <Link to={`/products/${product.id}`} className="text-sm font-medium text-[#111] leading-snug hover:text-[#E5001B] transition-colors block">
          {product.name}
        </Link>
        <div className="flex items-center gap-1.5">
          {product.colors.map((c, i) => (
            <button
              key={i}
              onClick={() => setActiveColor(i)}
              className="w-4 h-4 rounded-full transition-all duration-150"
              style={{
                backgroundColor: c.hex,
                border: activeColor === i ? "2px solid #111" : "1px solid #ddd",
                outline: activeColor === i ? "1px solid #fff" : "none",
                outlineOffset: "-2px",
              }}
              aria-label={c.name}
            />
          ))}
          <span className="text-xs text-[#888] ml-1">+{product.colors.length} màu</span>
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

export default function Home() {
  const [activeTab, setActiveTab] = useState("Tất Cả");
  const [email, setEmail] = useState("");
  const tabs = ["Tất Cả", "Nam", "Nữ", "Outerwear"];
  const filtered = activeTab === "Tất Cả" ? PRODUCTS.slice(0, 8) : PRODUCTS.filter((p) => p.category === activeTab).slice(0, 8);

  return (
    <div style={{ fontFamily: "'Inter', sans-serif" }}>
      {/* Hero */}
      <section className="max-w-[1400px] mx-auto px-6">
        <div className="grid lg:grid-cols-2 min-h-[85vh]">
          <div className="flex flex-col justify-center py-16 lg:pr-16 order-2 lg:order-1">
            <p className="text-xs tracking-[0.25em] uppercase text-[#888] mb-6">Bộ Sưu Tập Hè 2025</p>
            <h1
              className="leading-[0.9] mb-8 text-[#111]"
              style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "clamp(4rem, 9vw, 8rem)", letterSpacing: "-0.02em" }}
            >
              SỐNG<br />ĐƠN<br />GIẢN
            </h1>
            <p className="text-base text-[#555] leading-relaxed max-w-sm mb-10" style={{ fontWeight: 300 }}>
              Trang phục được thiết kế cho cuộc sống hiện đại — phom dáng chuẩn, chất liệu cao cấp, giá thành hợp lý cho mọi người.
            </p>
            <div className="flex items-center gap-4 flex-wrap">
              <Link
                to="/products"
                className="inline-flex items-center gap-3 bg-[#111] text-white px-8 py-4 text-xs tracking-widest uppercase font-medium hover:bg-[#E5001B] transition-colors duration-300"
              >
                Khám Phá Ngay <ArrowRight size={14} />
              </Link>
              <Link
                to="/products?tag=Sale"
                className="inline-flex items-center gap-2 text-xs tracking-widest uppercase text-[#111] font-medium border-b border-[#111] pb-0.5 hover:border-[#E5001B] hover:text-[#E5001B] transition-colors"
              >
                Xem Sale
              </Link>
            </div>
          </div>
          <div className="relative overflow-hidden bg-[#f5f5f5] order-1 lg:order-2 min-h-[55vw] lg:min-h-0">
            <img
              src="https://images.unsplash.com/photo-1665832102316-9fd8e8f77c88?w=1000&h=1200&fit=crop&auto=format"
              alt="Bộ sưu tập hè 2025"
              className="w-full h-full object-cover"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-black/20 to-transparent" />
            <div className="absolute bottom-6 right-6 bg-white px-4 py-3">
              <p className="text-[10px] uppercase tracking-widest text-[#888]">Xem Bộ Sưu Tập</p>
              <p className="text-sm font-semibold text-[#111] mt-0.5">Áo Sơ Mi Linen — 499.000 ₫</p>
            </div>
          </div>
        </div>
      </section>

      <div className="border-t border-[rgba(0,0,0,0.08)]" />

      {/* Categories */}
      <section className="max-w-[1400px] mx-auto px-6 py-16">
        <div className="flex items-end justify-between mb-8">
          <h2 style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 800, fontSize: "clamp(2rem, 4vw, 3.5rem)", letterSpacing: "-0.01em", lineHeight: 1 }}>
            DANH MỤC SẢN PHẨM
          </h2>
          <Link to="/products" className="hidden sm:flex items-center gap-2 text-xs tracking-widest uppercase text-[#888] hover:text-[#111] transition-colors border-b border-transparent hover:border-[#111] pb-0.5">
            Xem Tất Cả
          </Link>
        </div>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {CATEGORIES.map((cat) => (
            <Link key={cat.id} to={`/products?cat=${encodeURIComponent(cat.label)}`} className="group relative overflow-hidden bg-[#f5f5f5] aspect-[3/4] block">
              <img src={cat.img} alt={cat.label} className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105" />
              <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent" />
              <div className="absolute bottom-0 left-0 right-0 p-5">
                <p className="text-white text-xs tracking-widest uppercase mb-1">{cat.sub}</p>
                <p style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 800, fontSize: "clamp(1.5rem, 3vw, 2.25rem)", letterSpacing: "-0.01em" }} className="text-white leading-none">
                  {cat.label.toUpperCase()}
                </p>
              </div>
              <div className="absolute top-4 right-4 w-8 h-8 bg-white flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-300">
                <ArrowRight size={14} className="text-[#111]" />
              </div>
            </Link>
          ))}
        </div>
      </section>

      {/* Promo Banner */}
      <section className="bg-[#E5001B] text-white" style={{ fontFamily: "'Barlow Condensed', sans-serif" }}>
        <div className="max-w-[1400px] mx-auto px-6 py-14 grid lg:grid-cols-[1fr_auto] items-center gap-8">
          <div>
            <p className="text-sm tracking-[0.2em] uppercase mb-2 opacity-80" style={{ fontFamily: "'Inter', sans-serif", fontWeight: 400 }}>
              Ưu Đãi Đặc Biệt
            </p>
            <h2 style={{ fontWeight: 900, fontSize: "clamp(2.5rem, 6vw, 5rem)", letterSpacing: "-0.02em", lineHeight: 0.95 }}>
              GIẢM 30%<br />CHO ĐƠN HÀNG<br />ĐẦU TIÊN
            </h2>
          </div>
          <div className="flex flex-col gap-4">
            <p className="text-sm text-white/80 max-w-xs" style={{ fontFamily: "'Inter', sans-serif", fontWeight: 300 }}>
              Nhập mã <strong className="font-semibold text-white">LINOMOI</strong> khi thanh toán. Áp dụng cho tất cả sản phẩm.
            </p>
            <Link
              to="/products"
              className="inline-flex items-center gap-3 bg-white text-[#E5001B] px-7 py-4 text-xs tracking-widest uppercase font-medium hover:bg-[#111] hover:text-white transition-colors duration-300 self-start"
              style={{ fontFamily: "'Inter', sans-serif" }}
            >
              Mua Ngay <ArrowRight size={14} />
            </Link>
          </div>
        </div>
      </section>

      {/* New Arrivals */}
      <section className="max-w-[1400px] mx-auto px-6 py-16">
        <div className="flex items-end justify-between mb-8">
          <h2 style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 800, fontSize: "clamp(2rem, 4vw, 3.5rem)", letterSpacing: "-0.01em", lineHeight: 1 }}>
            SẢN PHẨM MỚI
          </h2>
          <Link to="/products" className="hidden sm:flex items-center gap-2 text-xs tracking-widest uppercase text-[#888] hover:text-[#111] transition-colors border-b border-transparent hover:border-[#111] pb-0.5">
            Xem Tất Cả
          </Link>
        </div>
        <div className="flex gap-0 mb-8 border-b border-[rgba(0,0,0,0.1)]">
          {tabs.map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className="px-5 py-3 text-xs tracking-widest uppercase font-medium transition-all duration-200 relative"
              style={{ color: activeTab === tab ? "#111" : "#888" }}
            >
              {tab}
              {activeTab === tab && <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-[#111]" />}
            </button>
          ))}
        </div>
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-x-5 gap-y-10">
          {filtered.map((p) => <ProductCard key={p.id} product={p} />)}
        </div>
      </section>

      {/* Featured Collection */}
      <section className="bg-[#f5f5f5]">
        <div className="max-w-[1400px] mx-auto px-6 py-16 grid lg:grid-cols-2 gap-10 items-center">
          <div className="relative overflow-hidden aspect-[4/5] bg-[#e8e8e8]">
            <img
              src="https://images.unsplash.com/photo-1665832102671-74fc84821a3b?w=900&h=1100&fit=crop&auto=format"
              alt="Bộ sưu tập công sở LINO"
              className="w-full h-full object-cover"
            />
          </div>
          <div className="lg:pl-10">
            <p className="text-xs tracking-[0.25em] uppercase text-[#888] mb-4">Bộ Sưu Tập Đặc Biệt</p>
            <h2
              className="text-[#111] mb-6"
              style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "clamp(2.5rem, 5vw, 4.5rem)", letterSpacing: "-0.02em", lineHeight: 0.95 }}
            >
              PHONG CÁCH<br /><span className="text-[#E5001B]">CÔNG SỞ</span><br />2025
            </h2>
            <p className="text-base text-[#555] leading-relaxed mb-8 max-w-md" style={{ fontWeight: 300 }}>
              Những thiết kế thanh lịch, tinh tế dành cho môi trường công sở hiện đại. Chất liệu cao cấp, phom dáng chuẩn.
            </p>
            <Link
              to="/products?cat=Nam"
              className="inline-flex items-center gap-3 bg-[#111] text-white px-8 py-4 text-xs tracking-widest uppercase font-medium hover:bg-[#E5001B] transition-colors duration-300"
            >
              Xem Bộ Sưu Tập <ArrowRight size={14} />
            </Link>
          </div>
        </div>
      </section>

      {/* Brand Values */}
      <section className="max-w-[1400px] mx-auto px-6 py-20">
        <h2 className="text-center text-[#111] mb-16" style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 800, fontSize: "clamp(2rem, 4vw, 3rem)", letterSpacing: "-0.01em" }}>
          TẠI SAO CHỌN LINO?
        </h2>
        <div className="grid md:grid-cols-3 gap-0 border-t border-[rgba(0,0,0,0.1)]">
          {[
            { num: "01", title: "Chất Liệu Cao Cấp", desc: "Cotton Nhật Bản, linen tự nhiên, len merino — chọn lựa kỹ càng để mang đến sự thoải mái vượt trội." },
            { num: "02", title: "Thiết Kế Tối Giản", desc: "Phom dáng chuẩn, đường cắt may tinh tế, dễ phối với mọi outfit, vượt qua xu hướng nhất thời." },
            { num: "03", title: "Sản Xuất Bền Vững", desc: "Giảm thiểu tác động môi trường qua quy trình sản xuất có trách nhiệm và nguyên liệu tái chế." },
          ].map((v, i) => (
            <div key={v.num} className={`pt-10 pb-8 ${i < 2 ? "md:border-r border-[rgba(0,0,0,0.1)]" : ""} md:px-10 first:pl-0 last:pr-0`}>
              <p className="mb-5 text-[#E5001B]" style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "3rem", lineHeight: 1, letterSpacing: "-0.02em" }}>
                {v.num}
              </p>
              <h3 className="text-[#111] font-semibold mb-4 text-base uppercase tracking-wide">{v.title}</h3>
              <p className="text-sm text-[#555] leading-relaxed" style={{ fontWeight: 300 }}>{v.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Newsletter */}
      <section className="border-t border-[rgba(0,0,0,0.08)] max-w-[1400px] mx-auto px-6 py-20 text-center">
        <h2 className="text-[#111] mb-4" style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 800, fontSize: "clamp(2rem, 4vw, 3rem)", letterSpacing: "-0.01em" }}>
          NHẬN ƯU ĐÃI ĐỘC QUYỀN
        </h2>
        <p className="text-sm text-[#555] mb-8 max-w-md mx-auto" style={{ fontWeight: 300 }}>
          Đăng ký để nhận thông tin về bộ sưu tập mới và ưu đãi đặc biệt.
        </p>
        <form onSubmit={(e) => e.preventDefault()} className="flex max-w-md mx-auto gap-0">
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="Địa chỉ email của bạn"
            className="flex-1 border border-[rgba(0,0,0,0.15)] px-4 py-3.5 text-sm text-[#111] bg-white focus:outline-none focus:border-[#111] transition-colors placeholder:text-[#aaa]"
          />
          <button type="submit" className="bg-[#111] text-white px-6 py-3.5 text-xs tracking-widest uppercase font-medium hover:bg-[#E5001B] transition-colors duration-300 flex-shrink-0">
            Đăng Ký
          </button>
        </form>
      </section>
    </div>
  );
}
