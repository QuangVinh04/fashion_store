import { useState } from "react";
import { Link, useParams } from "react-router";
import {
  ArrowLeft, Pencil, Eye, Star, Package,
  TrendingUp, ShoppingCart, BarChart2, Check, X, AlertTriangle,
} from "lucide-react";
import { PRODUCTS, formatVND } from "../../data/products";
import ProductModal from "../components/ProductModal";

const STOCK_MAP: Record<number, number> = { 1: 142, 2: 56, 3: 28, 4: 14, 5: 89, 6: 37, 7: 9, 8: 63, 9: 201, 10: 45, 11: 72, 12: 33 };

const MOCK_ANALYTICS = {
  views: 2847,
  viewsChange: 12.4,
  cartAdds: 384,
  cartAddsChange: 8.1,
  sold: 128,
  soldChange: 15.3,
  revenue: null as number | null,
};

const MOCK_REVIEWS = [
  { name: "Nguyễn Minh Anh", rating: 5, date: "15/06/2025", comment: "Sản phẩm rất đẹp, chất vải mềm mại và thoáng mát.", size: "M", color: "Đen", status: "approved" },
  { name: "Trần Thị Hoa", rating: 4, date: "02/06/2025", comment: "Chất lượng tốt, đúng mô tả.", size: "S", color: "Trắng", status: "approved" },
  { name: "Lê Văn Hùng", rating: 5, date: "28/05/2025", comment: "Mua lần thứ 3 rồi vẫn hài lòng.", size: "L", color: "Xanh Biển", status: "pending" },
];

const MOCK_STOCK: { size: string; stock: number }[] = [
  { size: "XS", stock: 12 }, { size: "S", stock: 28 }, { size: "M", stock: 45 },
  { size: "L", stock: 31 }, { size: "XL", stock: 18 }, { size: "XXL", stock: 8 },
];

function StatCard({ label, value, change, up, icon: Icon }: { label: string; value: string | number; change?: number; up?: boolean; icon: React.ElementType }) {
  return (
    <div className="bg-white border border-[rgba(0,0,0,0.08)] p-5">
      <div className="flex items-center justify-between mb-4">
        <p className="text-xs uppercase tracking-widest text-[#888]">{label}</p>
        <div className="w-8 h-8 bg-[#f5f5f5] flex items-center justify-center">
          <Icon size={15} className="text-[#888]" />
        </div>
      </div>
      <p className="text-2xl font-bold text-[#111]" style={{ fontFamily: "'Barlow Condensed', sans-serif", letterSpacing: "-0.02em" }}>
        {value}
      </p>
      {change !== undefined && (
        <div className={`flex items-center gap-1 mt-2 text-xs font-medium ${up ? "text-[#2D5A3D]" : "text-[#E5001B]"}`}>
          <TrendingUp size={11} />
          {change > 0 ? "+" : ""}{change}% so với tháng trước
        </div>
      )}
    </div>
  );
}

type ProductStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

const STATUS_BADGE: Record<ProductStatus, string> = {
  DRAFT: "bg-yellow-50 text-yellow-700 border-yellow-200",
  PUBLISHED: "bg-green-50 text-[#2D5A3D] border-green-200",
  ARCHIVED: "bg-[#f5f5f5] text-[#888] border-[rgba(0,0,0,0.12)]",
};
const STATUS_LABEL: Record<ProductStatus, string> = {
  DRAFT: "Bản Nháp", PUBLISHED: "Đang Bán", ARCHIVED: "Lưu Trữ",
};

const VALIDATE_ITEMS = [
  { key: "price", label: "Giá gốc phải lớn hơn 0" },
  { key: "category", label: "Sản phẩm có ít nhất 1 danh mục" },
  { key: "variant", label: "Có ít nhất 1 biến thể đang hiển thị" },
  { key: "image", label: "Có ít nhất 1 ảnh chính (primary)" },
];

export default function ProductDetailAdmin() {
  const { id } = useParams();
  const product = PRODUCTS.find((p) => p.id === Number(id));
  const [activeImg, setActiveImg] = useState(0);
  const [activeTab, setActiveTab] = useState<"overview" | "reviews" | "stock">("overview");
  const [status, setStatus] = useState<ProductStatus>("DRAFT");
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [archiveConfirm, setArchiveConfirm] = useState(false);
  const [validating, setValidating] = useState(false);
  const [validateResult, setValidateResult] = useState<{ valid: boolean; errors: Record<string, boolean> } | null>(null);
  const [publishing, setPublishing] = useState(false);

  const handleValidateAndPublish = async () => {
    setValidating(true);
    setValidateResult(null);
    // Simulate GET /admin/products/{id}/publish/validate
    await new Promise((r) => setTimeout(r, 800));
    const mockErrors = {
      price: true,
      category: true,
      variant: product ? product.sizes.length > 0 : false,
      image: true,
    };
    const valid = Object.values(mockErrors).every(Boolean);
    setValidateResult({ valid, errors: mockErrors });
    setValidating(false);

    if (valid) {
      setPublishing(true);
      // Simulate POST /admin/products/{id}/publish
      await new Promise((r) => setTimeout(r, 600));
      setStatus("PUBLISHED");
      setValidateResult(null);
      setPublishing(false);
    }
  };

  const handleUnpublish = async () => {
    // Simulate POST /admin/products/{id}/unpublish
    setStatus("DRAFT");
  };

  const handleArchive = async () => {
    // Simulate DELETE /admin/products/{id} (soft delete)
    setStatus("ARCHIVED");
    setArchiveConfirm(false);
  };

  if (!product) {
    return (
      <div className="text-center py-24">
        <p className="text-[#888] text-sm mb-4">Không tìm thấy sản phẩm.</p>
        <Link to="/admin/products" className="text-xs uppercase tracking-widest text-[#111] underline">Về danh sách</Link>
      </div>
    );
  }

  const totalStock = STOCK_MAP[product.id] ?? 0;
  MOCK_ANALYTICS.revenue = product.price * MOCK_ANALYTICS.sold;

  return (
    <div>
      {/* Header */}
      <div className="flex items-start justify-between gap-4 mb-6 flex-wrap">
        <div className="flex items-center gap-4">
          <Link to="/admin/products" className="w-8 h-8 flex items-center justify-center border border-[rgba(0,0,0,0.12)] text-[#888] hover:border-[#111] hover:text-[#111] transition-colors">
            <ArrowLeft size={16} />
          </Link>
          <div>
            <p className="text-xs uppercase tracking-widest text-[#888] mb-0.5">Chi Tiết Sản Phẩm</p>
            <h1 style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "2rem", letterSpacing: "-0.01em", color: "#111" }}>
              {product.name.toUpperCase()}
            </h1>
            <div className="flex items-center gap-3 mt-1.5 flex-wrap">
              <span className="text-xs uppercase tracking-widest bg-[#111] text-white px-2 py-0.5">#{product.id}</span>
              <span className="text-xs text-[#888]">{product.category} · {product.subcategory}</span>
              {product.tag && <span className="text-xs uppercase tracking-widest bg-[#E5001B] text-white px-2 py-0.5">{product.tag}</span>}
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          {/* Status badge */}
          <span className={`text-[10px] uppercase tracking-widest px-3 py-1.5 border font-semibold ${STATUS_BADGE[status]}`}>
            {STATUS_LABEL[status]}
          </span>

          <Link
            to={`/products/${product.id}`}
            target="_blank"
            className="flex items-center gap-2 border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-xs uppercase tracking-widest text-[#555] hover:border-[#111] hover:text-[#111] transition-colors"
          >
            <Eye size={13} /> Xem Trên Web
          </Link>
          <button
            onClick={() => setEditModalOpen(true)}
            className="flex items-center gap-2 border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-xs uppercase tracking-widest text-[#555] hover:border-[#111] hover:text-[#111] transition-colors"
          >
            <Pencil size={13} /> Chỉnh Sửa
          </button>

          {/* Publish / Unpublish */}
          {status === "PUBLISHED" ? (
            <button
              onClick={handleUnpublish}
              className="flex items-center gap-2 border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-xs uppercase tracking-widest text-[#555] hover:border-[#111] hover:text-[#111] transition-colors"
            >
              Hủy Đăng
            </button>
          ) : status === "DRAFT" ? (
            <button
              onClick={handleValidateAndPublish}
              disabled={validating || publishing}
              className="flex items-center gap-2 bg-[#111] text-white px-5 py-2.5 text-xs uppercase tracking-widest font-medium hover:bg-[#2D5A3D] transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
            >
              {validating ? (
                <><span className="w-3 h-3 border-2 border-white/30 border-t-white rounded-full animate-spin" /> Đang kiểm tra...</>
              ) : publishing ? (
                <><span className="w-3 h-3 border-2 border-white/30 border-t-white rounded-full animate-spin" /> Đang đăng...</>
              ) : (
                "Đăng Sản Phẩm"
              )}
            </button>
          ) : null}

          {/* Archive */}
          {status !== "ARCHIVED" && (
            <button
              onClick={() => setArchiveConfirm(true)}
              className="flex items-center gap-2 border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-xs uppercase tracking-widest text-[#E5001B] hover:bg-red-50 hover:border-[#E5001B] transition-colors"
            >
              Lưu Trữ
            </button>
          )}
        </div>
      </div>

      {/* Validate Checklist */}
      {validateResult && (
        <div className={`mb-6 border p-5 ${validateResult.valid ? "border-green-200 bg-green-50" : "border-[#E5001B]/30 bg-red-50/50"}`}>
          <div className="flex items-center gap-2 mb-3">
            {validateResult.valid ? (
              <Check size={16} className="text-[#2D5A3D]" />
            ) : (
              <AlertTriangle size={16} className="text-[#E5001B]" />
            )}
            <p className="text-sm font-semibold text-[#111]">
              {validateResult.valid ? "Tất cả điều kiện đã đáp ứng" : "Chưa đủ điều kiện để đăng sản phẩm"}
            </p>
            <button onClick={() => setValidateResult(null)} className="ml-auto text-[#aaa] hover:text-[#111]"><X size={14} /></button>
          </div>
          <div className="space-y-2">
            {VALIDATE_ITEMS.map((item) => {
              const ok = validateResult.errors[item.key];
              return (
                <div key={item.key} className="flex items-center gap-2.5">
                  {ok ? (
                    <Check size={13} className="text-[#2D5A3D] flex-shrink-0" />
                  ) : (
                    <X size={13} className="text-[#E5001B] flex-shrink-0" />
                  )}
                  <span className={`text-sm ${ok ? "text-[#2D5A3D]" : "text-[#E5001B]"}`}>{item.label}</span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Archive Confirm Dialog */}
      {archiveConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ fontFamily: "'Inter', sans-serif" }}>
          <div className="absolute inset-0 bg-black/50" onClick={() => setArchiveConfirm(false)} />
          <div className="relative bg-white p-6 max-w-sm w-full shadow-xl">
            <h3 className="text-base font-bold text-[#111] mb-2" style={{ fontFamily: "'Barlow Condensed', sans-serif", fontSize: "1.2rem" }}>
              XÁC NHẬN LƯU TRỮ
            </h3>
            <p className="text-sm text-[#555] mb-5">
              Sản phẩm sẽ bị ẩn khỏi trang bán hàng (soft delete). Gọi{" "}
              <code className="font-mono text-[#E5001B] text-xs">DELETE /admin/products/{id}</code>
            </p>
            <div className="flex gap-3">
              <button onClick={() => setArchiveConfirm(false)}
                className="flex-1 border border-[rgba(0,0,0,0.15)] py-2.5 text-xs uppercase tracking-widest text-[#555] hover:border-[#111] transition-colors">
                Hủy
              </button>
              <button onClick={handleArchive}
                className="flex-1 bg-[#E5001B] text-white py-2.5 text-xs uppercase tracking-widest font-medium hover:bg-[#c00018] transition-colors">
                Lưu Trữ
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Analytics Cards */}
      <div className="grid grid-cols-2 xl:grid-cols-4 gap-3 mb-6">
        <StatCard label="Lượt Xem" value={MOCK_ANALYTICS.views.toLocaleString()} change={MOCK_ANALYTICS.viewsChange} up icon={BarChart2} />
        <StatCard label="Thêm Vào Giỏ" value={MOCK_ANALYTICS.cartAdds} change={MOCK_ANALYTICS.cartAddsChange} up icon={ShoppingCart} />
        <StatCard label="Đã Bán" value={MOCK_ANALYTICS.sold} change={MOCK_ANALYTICS.soldChange} up icon={Package} />
        <StatCard label="Doanh Thu" value={formatVND(MOCK_ANALYTICS.revenue!)} icon={TrendingUp} />
      </div>

      <div className="grid xl:grid-cols-[360px_1fr] gap-5">
        {/* Left: Product Preview */}
        <div className="space-y-5">
          {/* Image Gallery */}
          <div className="bg-white border border-[rgba(0,0,0,0.08)]">
            <div className="aspect-[3/4] overflow-hidden bg-[#f5f5f5] relative">
              <img src={product.images[activeImg]} alt={product.name} className="w-full h-full object-cover" />
              {product.tag && (
                <span className="absolute top-3 left-3 bg-[#E5001B] text-white text-[10px] font-medium tracking-widest uppercase px-2 py-1">{product.tag}</span>
              )}
            </div>
            <div className="p-3 grid grid-cols-4 gap-2">
              {product.images.map((img, i) => (
                <button key={i} onClick={() => setActiveImg(i)}
                  className={`aspect-square overflow-hidden bg-[#f5f5f5] transition-all ${activeImg === i ? "ring-2 ring-[#111]" : "opacity-50 hover:opacity-100"}`}>
                  <img src={img} alt={`${i + 1}`} className="w-full h-full object-cover" />
                </button>
              ))}
            </div>
          </div>

          {/* Quick Info */}
          <div className="bg-white border border-[rgba(0,0,0,0.08)] p-5 space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-2xl font-bold text-[#111]">{formatVND(product.price)}</p>
                {product.originalPrice && (
                  <div className="flex items-center gap-2 mt-1">
                    <p className="text-sm text-[#aaa] line-through">{formatVND(product.originalPrice)}</p>
                    <span className="text-xs bg-[#E5001B] text-white px-1.5 py-0.5 font-medium">
                      -{Math.round((1 - product.price / product.originalPrice) * 100)}%
                    </span>
                  </div>
                )}
              </div>
              <div className="text-right">
                <div className="flex items-center justify-end gap-1">
                  <Star size={14} className="fill-[#111] stroke-[#111]" />
                  <span className="text-base font-bold text-[#111]">{product.rating}</span>
                </div>
                <p className="text-xs text-[#888]">{product.reviewCount} đánh giá</p>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3 text-sm pt-2 border-t border-[rgba(0,0,0,0.08)]">
              {[
                { label: "Tổng Tồn Kho", value: totalStock, warn: totalStock < 20 },
                { label: "Số Màu Sắc", value: `${product.colors.length} màu` },
                { label: "Số Size", value: `${product.sizes.length} size` },
                { label: "Danh Mục", value: product.category },
              ].map(({ label, value, warn }) => (
                <div key={label}>
                  <p className="text-[10px] uppercase tracking-widest text-[#888]">{label}</p>
                  <p className={`text-sm font-semibold mt-0.5 ${warn ? "text-[#E5001B]" : "text-[#111]"}`}>{value}</p>
                </div>
              ))}
            </div>

            {/* Colors */}
            <div className="pt-2 border-t border-[rgba(0,0,0,0.08)]">
              <p className="text-[10px] uppercase tracking-widest text-[#888] mb-2">Màu Sắc</p>
              <div className="flex flex-wrap gap-2">
                {product.colors.map((c, i) => (
                  <div key={i} className="flex items-center gap-1.5 border border-[rgba(0,0,0,0.1)] px-2 py-1.5">
                    <span className="w-3.5 h-3.5 rounded-full border border-[#ddd]" style={{ backgroundColor: c.hex }} />
                    <span className="text-xs text-[#555]">{c.name}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* Right: Detail Tabs */}
        <div className="bg-white border border-[rgba(0,0,0,0.08)]">
          {/* Tabs */}
          <div className="flex border-b border-[rgba(0,0,0,0.08)]">
            {(["overview", "reviews", "stock"] as const).map((tab) => {
              const labels = { overview: "Thông Tin", reviews: `Đánh Giá (${product.reviewCount})`, stock: "Tồn Kho" };
              return (
                <button
                  key={tab}
                  onClick={() => setActiveTab(tab)}
                  className={`px-5 py-4 text-xs uppercase tracking-widest font-medium transition-colors relative ${activeTab === tab ? "text-[#111]" : "text-[#888] hover:text-[#555]"}`}
                >
                  {labels[tab]}
                  {activeTab === tab && <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-[#111]" />}
                </button>
              );
            })}
          </div>

          <div className="p-6">
            {/* Overview Tab */}
            {activeTab === "overview" && (
              <div className="space-y-7">
                <div>
                  <p className="text-xs uppercase tracking-widest text-[#888] font-semibold mb-3">Mô Tả</p>
                  <p className="text-sm text-[#555] leading-relaxed" style={{ fontWeight: 300 }}>{product.description}</p>
                </div>
                <div>
                  <p className="text-xs uppercase tracking-widest text-[#888] font-semibold mb-3">Chất Liệu</p>
                  <p className="text-sm text-[#555]">{product.material}</p>
                </div>
                <div>
                  <p className="text-xs uppercase tracking-widest text-[#888] font-semibold mb-3">Hướng Dẫn Bảo Quản</p>
                  <ul className="space-y-2">
                    {product.care.map((c, i) => (
                      <li key={i} className="flex items-start gap-2 text-sm text-[#555]">
                        <span className="text-[#E5001B] mt-0.5">—</span> {c}
                      </li>
                    ))}
                  </ul>
                </div>
                <div>
                  <p className="text-xs uppercase tracking-widest text-[#888] font-semibold mb-3">Sizes Có Sẵn</p>
                  <div className="flex flex-wrap gap-2">
                    {product.sizes.map((s) => (
                      <span key={s} className="min-w-[40px] h-9 flex items-center justify-center px-3 border border-[rgba(0,0,0,0.15)] text-sm text-[#555]">{s}</span>
                    ))}
                  </div>
                </div>
              </div>
            )}

            {/* Reviews Tab */}
            {activeTab === "reviews" && (
              <div className="space-y-5">
                {/* Rating Summary */}
                <div className="flex items-center gap-6 p-4 bg-[#f8f8f8] border border-[rgba(0,0,0,0.06)]">
                  <div className="text-center">
                    <p className="text-4xl font-bold text-[#111]" style={{ fontFamily: "'Barlow Condensed', sans-serif" }}>{product.rating}</p>
                    <div className="flex gap-0.5 justify-center mt-1">
                      {[1,2,3,4,5].map((i) => (
                        <Star key={i} size={12} className={i <= Math.round(product.rating) ? "fill-[#111] stroke-[#111]" : "fill-[#ddd] stroke-[#ddd]"} />
                      ))}
                    </div>
                    <p className="text-xs text-[#888] mt-1">{product.reviewCount} đánh giá</p>
                  </div>
                  <div className="flex-1 space-y-1.5">
                    {[5, 4, 3, 2, 1].map((star) => (
                      <div key={star} className="flex items-center gap-2">
                        <span className="text-xs text-[#888] w-4">{star}★</span>
                        <div className="flex-1 h-1.5 bg-[#e0e0e0]">
                          <div className="h-full bg-[#111] transition-all" style={{ width: `${star === 5 ? 65 : star === 4 ? 22 : star === 3 ? 8 : 3}%` }} />
                        </div>
                        <span className="text-xs text-[#888] w-6">{star === 5 ? 65 : star === 4 ? 22 : star === 3 ? 8 : star === 2 ? 3 : 2}%</span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Reviews List */}
                <div className="space-y-4">
                  {MOCK_REVIEWS.map((r, i) => (
                    <div key={i} className="border border-[rgba(0,0,0,0.08)] p-4">
                      <div className="flex items-start justify-between gap-3 flex-wrap">
                        <div>
                          <div className="flex items-center gap-2">
                            <p className="text-sm font-semibold text-[#111]">{r.name}</p>
                            <span className={`text-[9px] uppercase tracking-widest px-2 py-0.5 border font-medium ${r.status === "approved" ? "bg-green-50 text-[#2D5A3D] border-green-200" : "bg-yellow-50 text-yellow-700 border-yellow-200"}`}>
                              {r.status === "approved" ? "Đã duyệt" : "Chờ duyệt"}
                            </span>
                          </div>
                          <p className="text-xs text-[#888] mt-0.5">{r.date}</p>
                        </div>
                        <div className="flex gap-1.5">
                          {[1,2,3,4,5].map((s) => <Star key={s} size={11} className={s <= r.rating ? "fill-[#111] stroke-[#111]" : "fill-[#ddd] stroke-[#ddd]"} />)}
                        </div>
                      </div>
                      <p className="text-sm text-[#555] my-3" style={{ fontWeight: 300 }}>{r.comment}</p>
                      <div className="flex items-center justify-between">
                        <div className="flex gap-2">
                          <span className="text-[10px] uppercase tracking-widest bg-[#f5f5f5] text-[#888] px-2 py-1">Size: {r.size}</span>
                          <span className="text-[10px] uppercase tracking-widest bg-[#f5f5f5] text-[#888] px-2 py-1">Màu: {r.color}</span>
                        </div>
                        <div className="flex gap-2">
                          {r.status === "pending" && (
                            <button className="flex items-center gap-1 text-xs text-[#2D5A3D] hover:underline">
                              <Check size={11} /> Duyệt
                            </button>
                          )}
                          <button className="flex items-center gap-1 text-xs text-[#E5001B] hover:underline">
                            <X size={11} /> Xóa
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Stock Tab */}
            {activeTab === "stock" && (
              <div className="space-y-5">
                <div className="flex items-center justify-between mb-4">
                  <div>
                    <p className="text-xs uppercase tracking-widest text-[#888]">Tổng Tồn Kho</p>
                    <p className="text-3xl font-bold text-[#111] mt-1" style={{ fontFamily: "'Barlow Condensed', sans-serif" }}>
                      {MOCK_STOCK.reduce((s, r) => s + r.stock, 0)}
                    </p>
                  </div>
                  <button className="flex items-center gap-2 bg-[#111] text-white px-4 py-2.5 text-xs uppercase tracking-widest font-medium hover:bg-[#E5001B] transition-colors">
                    Cập Nhật Kho
                  </button>
                </div>

                {/* Stock by size */}
                <div className="border border-[rgba(0,0,0,0.08)]">
                  <div className="grid grid-cols-3 px-4 py-2.5 border-b border-[rgba(0,0,0,0.06)] text-[10px] uppercase tracking-widest text-[#888] font-semibold">
                    <span>Size</span>
                    <span className="text-center">Tồn Kho</span>
                    <span className="text-right">Trạng Thái</span>
                  </div>
                  {MOCK_STOCK.map((row) => (
                    <div key={row.size} className="grid grid-cols-3 items-center px-4 py-3.5 border-b border-[rgba(0,0,0,0.04)] last:border-b-0 hover:bg-[#fafafa] transition-colors">
                      <span className="text-sm font-semibold text-[#111]">{row.size}</span>
                      <div className="flex items-center gap-2 justify-center">
                        <div className="w-20 h-1.5 bg-[#f0f0f0]">
                          <div className="h-full transition-all" style={{ width: `${Math.min((row.stock / 50) * 100, 100)}%`, backgroundColor: row.stock < 15 ? "#E5001B" : row.stock < 30 ? "#f59e0b" : "#2D5A3D" }} />
                        </div>
                        <span className="text-sm text-[#111] font-medium w-8">{row.stock}</span>
                      </div>
                      <div className="text-right">
                        <span className={`text-[9px] uppercase tracking-widest px-2 py-1 font-semibold border ${row.stock < 15 ? "bg-red-50 text-[#E5001B] border-red-200" : row.stock < 30 ? "bg-yellow-50 text-yellow-700 border-yellow-200" : "bg-green-50 text-[#2D5A3D] border-green-200"}`}>
                          {row.stock < 15 ? "Sắp hết" : row.stock < 30 ? "Thấp" : "Còn hàng"}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>

                {/* Colors stock */}
                <div>
                  <p className="text-xs uppercase tracking-widest text-[#888] font-semibold mb-3">Phân Bổ Theo Màu</p>
                  <div className="space-y-2.5">
                    {product.colors.map((c, i) => {
                      const pct = [42, 28, 20, 10][i % 4];
                      return (
                        <div key={i} className="flex items-center gap-3">
                          <span className="w-4 h-4 rounded-full border border-[#ddd] flex-shrink-0" style={{ backgroundColor: c.hex }} />
                          <span className="text-xs text-[#555] w-24">{c.name}</span>
                          <div className="flex-1 h-1.5 bg-[#f0f0f0]">
                            <div className="h-full bg-[#111] transition-all" style={{ width: `${pct}%` }} />
                          </div>
                          <span className="text-xs text-[#888] w-8">{pct}%</span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      <ProductModal
        open={editModalOpen}
        onClose={() => setEditModalOpen(false)}
        productId={String(product.id)}
        onSuccess={() => setEditModalOpen(false)}
      />
    </div>
  );
}
