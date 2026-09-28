import { useState, useMemo } from "react";
import { Link } from "react-router";
import {
  Plus, Search, ChevronUp, ChevronDown, Eye, Pencil,
  Trash2, CheckSquare, Square, X, Download,
  ArrowUpDown, Star,
} from "lucide-react";
import { PRODUCTS, formatVND } from "../../data/products";
import ProductModal from "../components/ProductModal";

// Augment with stock data
const STOCK_MAP: Record<number, number> = { 1: 142, 2: 56, 3: 28, 4: 14, 5: 89, 6: 37, 7: 9, 8: 63, 9: 201, 10: 45, 11: 72, 12: 33 };
const STATUS_MAP: Record<number, "active" | "draft" | "archived"> = { 1: "active", 2: "active", 3: "active", 4: "active", 5: "active", 6: "active", 7: "active", 8: "active", 9: "draft", 10: "active", 11: "active", 12: "draft" };

const STATUS_BADGE: Record<string, string> = {
  active: "bg-green-50 text-[#2D5A3D] border border-green-200",
  draft: "bg-yellow-50 text-yellow-700 border border-yellow-200",
  archived: "bg-[#f5f5f5] text-[#888] border border-[rgba(0,0,0,0.1)]",
};
const STATUS_LABEL = { active: "Đang Bán", draft: "Nháp", archived: "Lưu Trữ" };

type SortKey = "name" | "price" | "stock" | "rating" | "id";
type SortDir = "asc" | "desc";

export default function ProductList() {
  const [search, setSearch] = useState("");
  const [catFilter, setCatFilter] = useState("Tất Cả");
  const [statusFilter, setStatusFilter] = useState("Tất Cả");
  const [sortKey, setSortKey] = useState<SortKey>("id");
  const [sortDir, setSortDir] = useState<SortDir>("asc");
  const [selected, setSelected] = useState<number[]>([]);
  const [page, setPage] = useState(1);
  const [deleteConfirm, setDeleteConfirm] = useState<number | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [modalProductId, setModalProductId] = useState<string | undefined>(undefined);
  const PER_PAGE = 8;

  const openAddModal = () => { setModalProductId(undefined); setModalOpen(true); };
  const openEditModal = (id: number) => { setModalProductId(String(id)); setModalOpen(true); };

  const enriched = PRODUCTS.map((p) => ({
    ...p,
    stock: STOCK_MAP[p.id] ?? 0,
    status: STATUS_MAP[p.id] ?? "active",
  }));

  const filtered = useMemo(() => {
    let list = enriched;
    if (search) list = list.filter((p) => p.name.toLowerCase().includes(search.toLowerCase()) || p.subcategory.toLowerCase().includes(search.toLowerCase()));
    if (catFilter !== "Tất Cả") list = list.filter((p) => p.category === catFilter);
    if (statusFilter !== "Tất Cả") list = list.filter((p) => p.status === statusFilter);
    list = [...list].sort((a, b) => {
      let av: number | string = a[sortKey] as never;
      let bv: number | string = b[sortKey] as never;
      if (sortKey === "name") { av = a.name; bv = b.name; }
      if (typeof av === "string") return sortDir === "asc" ? av.localeCompare(bv as string) : (bv as string).localeCompare(av);
      return sortDir === "asc" ? (av as number) - (bv as number) : (bv as number) - (av as number);
    });
    return list;
  }, [search, catFilter, statusFilter, sortKey, sortDir]);

  const totalPages = Math.ceil(filtered.length / PER_PAGE);
  const paged = filtered.slice((page - 1) * PER_PAGE, page * PER_PAGE);

  const toggleSort = (key: SortKey) => {
    if (sortKey === key) setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    else { setSortKey(key); setSortDir("asc"); }
    setPage(1);
  };

  const toggleSelect = (id: number) => setSelected((s) => s.includes(id) ? s.filter((x) => x !== id) : [...s, id]);
  const toggleAll = () => setSelected(selected.length === paged.length ? [] : paged.map((p) => p.id));

  const SortIcon = ({ k }: { k: SortKey }) => (
    <span className="ml-1 inline-flex flex-col opacity-40">
      {sortKey === k ? (sortDir === "asc" ? <ChevronUp size={11} /> : <ChevronDown size={11} />) : <ArrowUpDown size={11} />}
    </span>
  );

  return (
    <div>
      {/* Page Header */}
      <div className="flex items-start justify-between gap-4 mb-6">
        <div>
          <p className="text-xs uppercase tracking-widest text-[#888] mb-1">Quản Lý</p>
          <h1 style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "2rem", letterSpacing: "-0.01em", color: "#111" }}>
            SẢN PHẨM
          </h1>
          <p className="text-sm text-[#888] mt-1">{PRODUCTS.length} sản phẩm tổng cộng</p>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <button className="flex items-center gap-2 border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-xs uppercase tracking-widest text-[#555] hover:border-[#111] hover:text-[#111] transition-colors">
            <Download size={13} /> Xuất Excel
          </button>
          <button
            onClick={openAddModal}
            className="flex items-center gap-2 bg-[#111] text-white px-5 py-2.5 text-xs uppercase tracking-widest font-medium hover:bg-[#E5001B] transition-colors"
          >
            <Plus size={14} /> Thêm Sản Phẩm
          </button>
        </div>
      </div>

      {/* Stats Row */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
        {[
          { label: "Tổng Sản Phẩm", value: enriched.length, color: "bg-white" },
          { label: "Đang Bán", value: enriched.filter((p) => p.status === "active").length, color: "bg-white" },
          { label: "Sắp Hết Hàng", value: enriched.filter((p) => p.stock < 20).length, color: "bg-white" },
          { label: "Bản Nháp", value: enriched.filter((p) => p.status === "draft").length, color: "bg-white" },
        ].map((s) => (
          <div key={s.label} className={`${s.color} border border-[rgba(0,0,0,0.08)] px-4 py-4`}>
            <p className="text-xs uppercase tracking-widest text-[#888]">{s.label}</p>
            <p className="text-2xl font-bold text-[#111] mt-1" style={{ fontFamily: "'Barlow Condensed', sans-serif", letterSpacing: "-0.02em" }}>{s.value}</p>
          </div>
        ))}
      </div>

      {/* Table Card */}
      <div className="bg-white border border-[rgba(0,0,0,0.08)]">
        {/* Toolbar */}
        <div className="flex items-center gap-3 p-4 border-b border-[rgba(0,0,0,0.06)] flex-wrap">
          {/* Search */}
          <div className="relative flex-1 min-w-[200px]">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-[#aaa]" />
            <input
              type="text"
              value={search}
              onChange={(e) => { setSearch(e.target.value); setPage(1); }}
              placeholder="Tìm kiếm theo tên, danh mục..."
              className="w-full pl-9 pr-4 py-2.5 text-sm border border-[rgba(0,0,0,0.12)] text-[#111] placeholder:text-[#bbb] focus:outline-none focus:border-[#111] transition-colors"
            />
          </div>

          {/* Filters */}
          <div className="flex items-center gap-2 flex-wrap">
            <select
              value={catFilter}
              onChange={(e) => { setCatFilter(e.target.value); setPage(1); }}
              className="border border-[rgba(0,0,0,0.12)] px-3 py-2.5 text-xs uppercase tracking-widest text-[#555] bg-white focus:outline-none focus:border-[#111] transition-colors cursor-pointer"
            >
              {["Tất Cả", "Nam", "Nữ"].map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
            <select
              value={statusFilter}
              onChange={(e) => { setStatusFilter(e.target.value); setPage(1); }}
              className="border border-[rgba(0,0,0,0.12)] px-3 py-2.5 text-xs uppercase tracking-widest text-[#555] bg-white focus:outline-none focus:border-[#111] transition-colors cursor-pointer"
            >
              <option value="Tất Cả">Mọi Trạng Thái</option>
              <option value="active">Đang Bán</option>
              <option value="draft">Nháp</option>
              <option value="archived">Lưu Trữ</option>
            </select>
            {(search || catFilter !== "Tất Cả" || statusFilter !== "Tất Cả") && (
              <button onClick={() => { setSearch(""); setCatFilter("Tất Cả"); setStatusFilter("Tất Cả"); setPage(1); }}
                className="flex items-center gap-1.5 text-xs text-[#888] hover:text-[#E5001B] transition-colors border border-[rgba(0,0,0,0.12)] px-3 py-2.5">
                <X size={12} /> Xóa lọc
              </button>
            )}
          </div>
        </div>

        {/* Bulk Actions Bar */}
        {selected.length > 0 && (
          <div className="flex items-center gap-4 px-4 py-3 bg-[#f8f8f8] border-b border-[rgba(0,0,0,0.06)]">
            <span className="text-sm font-medium text-[#111]">Đã chọn {selected.length} sản phẩm</span>
            <div className="flex items-center gap-2 ml-auto">
              {[
                { label: "Đang Bán", action: "active" },
                { label: "Nháp", action: "draft" },
              ].map(({ label, action }) => (
                <button key={action} onClick={() => setSelected([])}
                  className="text-xs uppercase tracking-widest px-3 py-2 border border-[rgba(0,0,0,0.12)] text-[#555] hover:border-[#111] hover:text-[#111] transition-colors">
                  → {label}
                </button>
              ))}
              <button onClick={() => setSelected([])}
                className="text-xs uppercase tracking-widest px-3 py-2 border border-[rgba(0,0,0,0.12)] text-[#E5001B] hover:bg-[#E5001B] hover:text-white transition-colors flex items-center gap-1.5">
                <Trash2 size={12} /> Xóa
              </button>
            </div>
          </div>
        )}

        {/* Table */}
        <div className="overflow-x-auto">
          <table className="w-full min-w-[900px]">
            <thead>
              <tr className="border-b border-[rgba(0,0,0,0.06)]">
                <th className="w-12 px-4 py-3 text-left">
                  <button onClick={toggleAll} className="text-[#888] hover:text-[#111] transition-colors">
                    {selected.length === paged.length && paged.length > 0 ? <CheckSquare size={16} className="text-[#111]" /> : <Square size={16} />}
                  </button>
                </th>
                <th className="px-4 py-3 text-left text-[10px] uppercase tracking-widest text-[#888] font-semibold">Sản Phẩm</th>
                <th className="px-4 py-3 text-left text-[10px] uppercase tracking-widest text-[#888] font-semibold">Danh Mục</th>
                <th className="px-4 py-3 text-right text-[10px] uppercase tracking-widest text-[#888] font-semibold cursor-pointer select-none hover:text-[#111] transition-colors" onClick={() => toggleSort("price")}>
                  Giá <SortIcon k="price" />
                </th>
                <th className="px-4 py-3 text-right text-[10px] uppercase tracking-widest text-[#888] font-semibold cursor-pointer select-none hover:text-[#111] transition-colors" onClick={() => toggleSort("stock")}>
                  Tồn Kho <SortIcon k="stock" />
                </th>
                <th className="px-4 py-3 text-right text-[10px] uppercase tracking-widest text-[#888] font-semibold cursor-pointer select-none hover:text-[#111] transition-colors" onClick={() => toggleSort("rating")}>
                  Đánh Giá <SortIcon k="rating" />
                </th>
                <th className="px-4 py-3 text-center text-[10px] uppercase tracking-widest text-[#888] font-semibold">Trạng Thái</th>
                <th className="px-4 py-3 text-center text-[10px] uppercase tracking-widest text-[#888] font-semibold">Thao Tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[rgba(0,0,0,0.04)]">
              {paged.map((product) => (
                <tr
                  key={product.id}
                  className={`hover:bg-[#fafafa] transition-colors ${selected.includes(product.id) ? "bg-blue-50/30" : ""}`}
                >
                  <td className="px-4 py-3.5">
                    <button onClick={() => toggleSelect(product.id)} className="text-[#888] hover:text-[#111] transition-colors">
                      {selected.includes(product.id) ? <CheckSquare size={16} className="text-[#111]" /> : <Square size={16} />}
                    </button>
                  </td>

                  {/* Product */}
                  <td className="px-4 py-3.5">
                    <div className="flex items-center gap-3">
                      <Link to={`/admin/products/${product.id}`} className="w-12 h-14 flex-shrink-0 bg-[#f5f5f5] overflow-hidden hover:opacity-80 transition-opacity">
                        <img src={product.img} alt={product.name} className="w-full h-full object-cover" />
                      </Link>
                      <div className="min-w-0">
                        <Link to={`/admin/products/${product.id}`} className="text-sm font-medium text-[#111] hover:text-[#E5001B] transition-colors block truncate max-w-[200px]">
                          {product.name}
                        </Link>
                        <p className="text-xs text-[#888] mt-0.5">{product.colors.length} màu · {product.sizes.length} size</p>
                        {product.tag && (
                          <span className="text-[9px] uppercase tracking-widest bg-[#111] text-white px-1.5 py-0.5 mt-1 inline-block">{product.tag}</span>
                        )}
                      </div>
                    </div>
                  </td>

                  {/* Category */}
                  <td className="px-4 py-3.5">
                    <p className="text-xs font-medium text-[#111]">{product.category}</p>
                    <p className="text-xs text-[#888] mt-0.5">{product.subcategory}</p>
                  </td>

                  {/* Price */}
                  <td className="px-4 py-3.5 text-right">
                    <p className="text-sm font-semibold text-[#111]">{formatVND(product.price)}</p>
                    {product.originalPrice && (
                      <p className="text-xs text-[#aaa] line-through">{formatVND(product.originalPrice)}</p>
                    )}
                  </td>

                  {/* Stock */}
                  <td className="px-4 py-3.5 text-right">
                    <p className={`text-sm font-semibold ${product.stock < 20 ? "text-[#E5001B]" : product.stock < 50 ? "text-orange-500" : "text-[#111]"}`}>
                      {product.stock}
                    </p>
                    {product.stock < 20 && <p className="text-[9px] text-[#E5001B] uppercase tracking-widest">Sắp hết</p>}
                  </td>

                  {/* Rating */}
                  <td className="px-4 py-3.5 text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Star size={11} className="fill-[#111] stroke-[#111]" />
                      <span className="text-sm font-medium text-[#111]">{product.rating}</span>
                    </div>
                    <p className="text-xs text-[#888] mt-0.5">{product.reviewCount} đánh giá</p>
                  </td>

                  {/* Status */}
                  <td className="px-4 py-3.5 text-center">
                    <span className={`text-[9px] uppercase tracking-widest px-2.5 py-1.5 font-semibold ${STATUS_BADGE[product.status]}`}>
                      {STATUS_LABEL[product.status as keyof typeof STATUS_LABEL]}
                    </span>
                  </td>

                  {/* Actions */}
                  <td className="px-4 py-3.5">
                    <div className="flex items-center justify-center gap-1">
                      <Link to={`/admin/products/${product.id}`}
                        className="w-8 h-8 flex items-center justify-center text-[#888] hover:text-[#111] hover:bg-[#f5f5f5] transition-colors"
                        title="Xem chi tiết">
                        <Eye size={14} />
                      </Link>
                      <button
                        onClick={() => openEditModal(product.id)}
                        className="w-8 h-8 flex items-center justify-center text-[#888] hover:text-[#111] hover:bg-[#f5f5f5] transition-colors"
                        title="Chỉnh sửa"
                      >
                        <Pencil size={14} />
                      </button>
                      <button
                        onClick={() => setDeleteConfirm(product.id)}
                        className="w-8 h-8 flex items-center justify-center text-[#888] hover:text-[#E5001B] hover:bg-red-50 transition-colors"
                        title="Xóa"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>

                    {/* Delete Confirm */}
                    {deleteConfirm === product.id && (
                      <div className="absolute right-16 mt-1 bg-white border border-[rgba(0,0,0,0.12)] shadow-lg p-3 z-20 w-48">
                        <p className="text-xs text-[#555] mb-3">Xác nhận xóa sản phẩm này?</p>
                        <div className="flex gap-2">
                          <button onClick={() => setDeleteConfirm(null)}
                            className="flex-1 text-xs py-1.5 border border-[rgba(0,0,0,0.12)] text-[#555] hover:border-[#111] transition-colors">Hủy</button>
                          <button onClick={() => setDeleteConfirm(null)}
                            className="flex-1 text-xs py-1.5 bg-[#E5001B] text-white hover:bg-[#c00018] transition-colors">Xóa</button>
                        </div>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {paged.length === 0 && (
            <div className="text-center py-16">
              <Package size={36} className="text-[#ddd] mx-auto mb-3" />
              <p className="text-sm text-[#888]">Không tìm thấy sản phẩm phù hợp.</p>
            </div>
          )}
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between px-4 py-4 border-t border-[rgba(0,0,0,0.06)]">
            <p className="text-xs text-[#888]">
              Hiển thị {(page - 1) * PER_PAGE + 1}–{Math.min(page * PER_PAGE, filtered.length)} / {filtered.length} sản phẩm
            </p>
            <div className="flex items-center gap-1">
              <button
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                disabled={page === 1}
                className="w-8 h-8 flex items-center justify-center border border-[rgba(0,0,0,0.12)] text-[#888] hover:border-[#111] hover:text-[#111] disabled:opacity-40 disabled:cursor-not-allowed transition-colors text-sm"
              >
                ‹
              </button>
              {Array.from({ length: totalPages }, (_, i) => i + 1).map((n) => (
                <button key={n} onClick={() => setPage(n)}
                  className={`w-8 h-8 flex items-center justify-center border text-xs font-medium transition-colors ${n === page ? "border-[#111] bg-[#111] text-white" : "border-[rgba(0,0,0,0.12)] text-[#888] hover:border-[#111] hover:text-[#111]"}`}>
                  {n}
                </button>
              ))}
              <button
                onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                disabled={page === totalPages}
                className="w-8 h-8 flex items-center justify-center border border-[rgba(0,0,0,0.12)] text-[#888] hover:border-[#111] hover:text-[#111] disabled:opacity-40 disabled:cursor-not-allowed transition-colors text-sm"
              >
                ›
              </button>
            </div>
          </div>
        )}
      </div>

      <ProductModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        productId={modalProductId}
        onSuccess={() => setModalOpen(false)}
      />
    </div>
  );
}
