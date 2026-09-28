import { useState } from "react";
import { Link } from "react-router";
import { Heart, X, ShoppingBag, ArrowRight } from "lucide-react";
import { PRODUCTS, formatVND } from "../data/products";
import { useCart } from "../context/CartContext";
import ProfileLayout from "../components/ProfileLayout";

const INITIAL_WISHLIST = [1, 3, 5, 7, 11];

export default function Wishlist() {
  const { addItem } = useCart();
  const [wishlist, setWishlist] = useState<number[]>(INITIAL_WISHLIST);
  const [addedId, setAddedId] = useState<number | null>(null);

  const items = PRODUCTS.filter((p) => wishlist.includes(p.id));

  const remove = (id: number) => setWishlist((w) => w.filter((x) => x !== id));

  const handleAdd = (p: (typeof PRODUCTS)[0]) => {
    addItem(p, 1, p.sizes[2] ?? p.sizes[0], p.colors[0].name);
    setAddedId(p.id);
    setTimeout(() => setAddedId(null), 2000);
  };

  return (
    <ProfileLayout>
      <div style={{ fontFamily: "'Inter', sans-serif" }}>
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1
              className="text-[#111]"
              style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "clamp(1.75rem, 3vw, 2.5rem)", letterSpacing: "-0.01em", lineHeight: 1 }}
            >
              YÊU THÍCH
            </h1>
            <p className="text-sm text-[#888] mt-2">{items.length} sản phẩm</p>
          </div>
          {items.length > 0 && (
            <button
              onClick={() => items.forEach((p) => addItem(p, 1, p.sizes[2] ?? p.sizes[0], p.colors[0].name))}
              className="flex items-center gap-2 bg-[#111] text-white px-5 py-3 text-xs tracking-widest uppercase font-medium hover:bg-[#E5001B] transition-colors"
            >
              <ShoppingBag size={14} /> Thêm Tất Cả Vào Giỏ
            </button>
          )}
        </div>

        {items.length === 0 && (
          <div className="text-center py-16 border border-dashed border-[rgba(0,0,0,0.15)]">
            <Heart size={36} className="text-[#ddd] mx-auto mb-4" />
            <p className="text-[#888] text-sm mb-4">Chưa có sản phẩm yêu thích nào.</p>
            <Link to="/products" className="inline-flex items-center gap-2 text-xs uppercase tracking-widest text-[#111] underline hover:text-[#E5001B] transition-colors">
              Khám phá sản phẩm <ArrowRight size={12} />
            </Link>
          </div>
        )}

        <div className="grid grid-cols-2 md:grid-cols-3 gap-x-5 gap-y-8">
          {items.map((product) => (
            <div key={product.id} className="group">
              <div className="relative overflow-hidden bg-[#f5f5f5] aspect-[3/4]">
                <Link to={`/products/${product.id}`}>
                  <img
                    src={product.img}
                    alt={product.name}
                    className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
                  />
                </Link>
                {product.tag && (
                  <span className="absolute top-3 left-3 bg-[#111] text-white text-[10px] font-medium tracking-widest uppercase px-2 py-1">
                    {product.tag}
                  </span>
                )}
                <button
                  onClick={() => remove(product.id)}
                  className="absolute top-3 right-3 w-8 h-8 flex items-center justify-center bg-white text-[#E5001B] hover:bg-[#E5001B] hover:text-white transition-colors duration-200"
                  aria-label="Bỏ yêu thích"
                >
                  <X size={15} />
                </button>
                <button
                  onClick={() => handleAdd(product)}
                  className={`absolute bottom-0 left-0 right-0 py-3 text-xs tracking-widest uppercase font-medium translate-y-full group-hover:translate-y-0 transition-transform duration-300 ${addedId === product.id ? "bg-[#2D5A3D] text-white" : "bg-[#111] text-white"}`}
                >
                  {addedId === product.id ? "Đã Thêm ✓" : "Thêm Vào Giỏ"}
                </button>
              </div>
              <div className="mt-3">
                <p className="text-xs text-[#888] uppercase tracking-widest mb-1">{product.subcategory}</p>
                <Link to={`/products/${product.id}`} className="text-sm font-medium text-[#111] hover:text-[#E5001B] transition-colors block leading-snug">
                  {product.name}
                </Link>
                <div className="flex items-center gap-2 mt-1.5">
                  <p className="text-sm font-semibold text-[#111]">{formatVND(product.price)}</p>
                  {product.originalPrice && (
                    <p className="text-xs text-[#aaa] line-through">{formatVND(product.originalPrice)}</p>
                  )}
                </div>
                <div className="flex items-center gap-1.5 mt-1.5">
                  {product.colors.map((c, i) => (
                    <span
                      key={i}
                      className="w-3.5 h-3.5 rounded-full border border-[#ddd] inline-block"
                      style={{ backgroundColor: c.hex }}
                    />
                  ))}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </ProfileLayout>
  );
}
