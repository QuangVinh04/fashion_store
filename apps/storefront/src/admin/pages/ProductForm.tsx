import { useState } from "react";
import { Link, useParams, useNavigate } from "react-router";
import {
  ArrowLeft, Save, Eye, Trash2, Plus, X, Upload,
  GripVertical, ChevronDown, Info, Check,
} from "lucide-react";
import { PRODUCTS } from "../../data/products";

const CATEGORIES = ["Nam", "Nữ", "Trẻ Em"];
const SUBCATEGORIES: Record<string, string[]> = {
  "Nam": ["Áo Phông", "Áo Sơ Mi", "Áo Khoác", "Quần Dài", "Quần Short", "Vest & Blazer", "Áo Sweatshirt"],
  "Nữ": ["Áo Phông", "Áo Sơ Mi", "Áo Khoác", "Đầm", "Chân Váy", "Áo Len", "Quần Dài"],
  "Trẻ Em": ["Áo", "Quần", "Đồ Bộ", "Phụ Kiện"],
};
const ALL_SIZES_MAP: Record<string, string[]> = {
  "Áo Phông": ["XS", "S", "M", "L", "XL", "XXL"],
  "Áo Sơ Mi": ["XS", "S", "M", "L", "XL", "XXL"],
  "Áo Khoác": ["XS", "S", "M", "L", "XL", "XXL"],
  "Quần Dài": ["28", "29", "30", "31", "32", "33", "34"],
  "Quần Short": ["XS", "S", "M", "L", "XL"],
  "Vest & Blazer": ["XS", "S", "M", "L", "XL", "XXL"],
  "Áo Sweatshirt": ["XS", "S", "M", "L", "XL", "XXL"],
  "Đầm": ["XS", "S", "M", "L", "XL"],
  "Chân Váy": ["XS", "S", "M", "L", "XL"],
  "Áo Len": ["XS", "S", "M", "L", "XL"],
};
const DEFAULT_SIZES = ["XS", "S", "M", "L", "XL", "XXL"];

type ColorEntry = { name: string; hex: string };
type FormData = {
  name: string;
  category: string;
  subcategory: string;
  price: string;
  originalPrice: string;
  tag: string;
  description: string;
  material: string;
  care: string;
  colors: ColorEntry[];
  sizes: string[];
  status: "active" | "draft" | "archived";
  stock: string;
};

function SectionCard({ title, children, info }: { title: string; children: React.ReactNode; info?: string }) {
  return (
    <div className="bg-white border border-[rgba(0,0,0,0.08)]">
      <div className="flex items-center gap-2 px-5 py-4 border-b border-[rgba(0,0,0,0.06)]">
        <p className="text-xs font-semibold uppercase tracking-widest text-[#111]">{title}</p>
        {info && <Info size={13} className="text-[#aaa] ml-1" title={info} />}
      </div>
      <div className="p-5">{children}</div>
    </div>
  );
}

function FieldGroup({ label, required, children, error }: { label: string; required?: boolean; children: React.ReactNode; error?: string }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-xs font-semibold uppercase tracking-widest text-[#555]">
        {label}{required && <span className="text-[#E5001B] ml-0.5">*</span>}
      </label>
      {children}
      {error && <p className="text-xs text-[#E5001B]">{error}</p>}
    </div>
  );
}

const INPUT = "border border-[rgba(0,0,0,0.15)] px-3 py-2.5 text-sm text-[#111] placeholder:text-[#aaa] focus:outline-none focus:border-[#111] transition-colors w-full";

export default function ProductForm() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEdit = !!id;
  const existing = isEdit ? PRODUCTS.find((p) => p.id === Number(id)) : null;

  const [form, setForm] = useState<FormData>({
    name: existing?.name ?? "",
    category: existing?.category ?? "Nam",
    subcategory: existing?.subcategory ?? "Áo Phông",
    price: existing?.price.toString() ?? "",
    originalPrice: existing?.originalPrice?.toString() ?? "",
    tag: existing?.tag ?? "",
    description: existing?.description ?? "",
    material: existing?.material ?? "",
    care: existing?.care.join("\n") ?? "",
    colors: existing?.colors ?? [{ name: "Đen", hex: "#111111" }],
    sizes: existing?.sizes ?? ["S", "M", "L"],
    status: "active",
    stock: "100",
  });

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saved, setSaved] = useState(false);
  const [newColor, setNewColor] = useState({ name: "", hex: "#111111" });
  const [showColorPicker, setShowColorPicker] = useState(false);

  const set = (k: keyof FormData) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  const validate = () => {
    const e: Record<string, string> = {};
    if (!form.name.trim()) e.name = "Vui lòng nhập tên sản phẩm";
    if (!form.price || isNaN(Number(form.price))) e.price = "Vui lòng nhập giá hợp lệ";
    if (!form.description.trim()) e.description = "Vui lòng nhập mô tả";
    if (form.colors.length === 0) e.colors = "Cần ít nhất 1 màu sắc";
    if (form.sizes.length === 0) e.sizes = "Cần ít nhất 1 size";
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSave = (status: "active" | "draft") => {
    if (!validate()) return;
    setForm((f) => ({ ...f, status }));
    setSaved(true);
    setTimeout(() => { setSaved(false); navigate("/admin/products"); }, 1500);
  };

  const addColor = () => {
    if (!newColor.name.trim()) return;
    setForm((f) => ({ ...f, colors: [...f.colors, { ...newColor }] }));
    setNewColor({ name: "", hex: "#111111" });
    setShowColorPicker(false);
  };

  const removeColor = (i: number) => setForm((f) => ({ ...f, colors: f.colors.filter((_, idx) => idx !== i) }));

  const toggleSize = (size: string) => setForm((f) => ({
    ...f,
    sizes: f.sizes.includes(size) ? f.sizes.filter((s) => s !== size) : [...f.sizes, size],
  }));

  const availableSizes = ALL_SIZES_MAP[form.subcategory] ?? DEFAULT_SIZES;

  return (
    <div>
      {/* Page Header */}
      <div className="flex items-center justify-between mb-6 flex-wrap gap-4">
        <div className="flex items-center gap-4">
          <Link to="/admin/products" className="w-8 h-8 flex items-center justify-center border border-[rgba(0,0,0,0.12)] text-[#888] hover:border-[#111] hover:text-[#111] transition-colors">
            <ArrowLeft size={16} />
          </Link>
          <div>
            <p className="text-xs uppercase tracking-widest text-[#888] mb-0.5">{isEdit ? "Chỉnh Sửa" : "Thêm Mới"}</p>
            <h1 style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "2rem", letterSpacing: "-0.01em", color: "#111" }}>
              {isEdit ? form.name.toUpperCase() || "SẢN PHẨM" : "THÊM SẢN PHẨM"}
            </h1>
          </div>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          {isEdit && (
            <Link to={`/admin/products/${id}`}
              className="flex items-center gap-2 border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-xs uppercase tracking-widest text-[#555] hover:border-[#111] hover:text-[#111] transition-colors">
              <Eye size={13} /> Xem Chi Tiết
            </Link>
          )}
          <button onClick={() => handleSave("draft")}
            className="flex items-center gap-2 border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-xs uppercase tracking-widest text-[#555] hover:border-[#111] hover:text-[#111] transition-colors">
            Lưu Nháp
          </button>
          <button onClick={() => handleSave("active")}
            className={`flex items-center gap-2 px-5 py-2.5 text-xs uppercase tracking-widest font-medium transition-colors ${saved ? "bg-[#2D5A3D] text-white" : "bg-[#111] text-white hover:bg-[#E5001B]"}`}>
            {saved ? <><Check size={13} /> Đã Lưu</> : <><Save size={13} /> {isEdit ? "Cập Nhật" : "Xuất Bản"}</>}
          </button>
        </div>
      </div>

      <div className="grid xl:grid-cols-[1fr_300px] gap-5">
        {/* Left Column */}
        <div className="space-y-5">
          {/* Basic Info */}
          <SectionCard title="Thông Tin Cơ Bản">
            <div className="space-y-4">
              <FieldGroup label="Tên Sản Phẩm" required error={errors.name}>
                <input value={form.name} onChange={set("name")} placeholder="VD: Áo Phông Cotton Cơ Bản" className={INPUT} />
              </FieldGroup>
              <div className="grid sm:grid-cols-2 gap-4">
                <FieldGroup label="Danh Mục">
                  <div className="relative">
                    <select value={form.category} onChange={set("category")} className={`${INPUT} appearance-none pr-8`}>
                      {CATEGORIES.map((c) => <option key={c}>{c}</option>)}
                    </select>
                    <ChevronDown size={13} className="absolute right-3 top-1/2 -translate-y-1/2 text-[#888] pointer-events-none" />
                  </div>
                </FieldGroup>
                <FieldGroup label="Danh Mục Phụ">
                  <div className="relative">
                    <select value={form.subcategory} onChange={set("subcategory")} className={`${INPUT} appearance-none pr-8`}>
                      {(SUBCATEGORIES[form.category] ?? []).map((s) => <option key={s}>{s}</option>)}
                    </select>
                    <ChevronDown size={13} className="absolute right-3 top-1/2 -translate-y-1/2 text-[#888] pointer-events-none" />
                  </div>
                </FieldGroup>
              </div>
              <FieldGroup label="Tag">
                <div className="relative">
                  <select value={form.tag} onChange={set("tag")} className={`${INPUT} appearance-none pr-8`}>
                    <option value="">Không có tag</option>
                    <option>Mới</option>
                    <option>Bán Chạy</option>
                    <option>Sale</option>
                  </select>
                  <ChevronDown size={13} className="absolute right-3 top-1/2 -translate-y-1/2 text-[#888] pointer-events-none" />
                </div>
              </FieldGroup>
            </div>
          </SectionCard>

          {/* Pricing */}
          <SectionCard title="Giá Bán">
            <div className="grid sm:grid-cols-3 gap-4">
              <FieldGroup label="Giá Bán (₫)" required error={errors.price}>
                <input type="number" value={form.price} onChange={set("price")} placeholder="299000" className={INPUT} />
              </FieldGroup>
              <FieldGroup label="Giá Gốc (₫)" info="Hiển thị dưới dạng gạch ngang nếu có">
                <input type="number" value={form.originalPrice} onChange={set("originalPrice")} placeholder="399000" className={INPUT} />
              </FieldGroup>
              <FieldGroup label="Tồn Kho">
                <input type="number" value={form.stock} onChange={set("stock")} placeholder="100" className={INPUT} />
              </FieldGroup>
            </div>
            {form.price && form.originalPrice && Number(form.originalPrice) > Number(form.price) && (
              <div className="mt-3 p-3 bg-[#f0f8f0] border border-green-200 flex items-center gap-2">
                <Check size={13} className="text-[#2D5A3D]" />
                <p className="text-xs text-[#2D5A3D]">
                  Giảm {Math.round((1 - Number(form.price) / Number(form.originalPrice)) * 100)}% — Tiết kiệm {(Number(form.originalPrice) - Number(form.price)).toLocaleString("vi-VN")} ₫
                </p>
              </div>
            )}
          </SectionCard>

          {/* Description */}
          <SectionCard title="Mô Tả Sản Phẩm">
            <div className="space-y-4">
              <FieldGroup label="Mô Tả Chi Tiết" required error={errors.description}>
                <textarea
                  value={form.description}
                  onChange={set("description")}
                  rows={5}
                  placeholder="Nhập mô tả chi tiết về sản phẩm, tính năng nổi bật, phong cách sử dụng..."
                  className={`${INPUT} resize-none`}
                />
              </FieldGroup>
              <FieldGroup label="Chất Liệu & Thành Phần">
                <input value={form.material} onChange={set("material")} placeholder="VD: 100% Cotton tự nhiên, 180gsm" className={INPUT} />
              </FieldGroup>
              <FieldGroup label="Hướng Dẫn Bảo Quản" info="Mỗi hướng dẫn trên một dòng">
                <textarea
                  value={form.care}
                  onChange={set("care")}
                  rows={4}
                  placeholder={"Giặt máy ở 30°C\nKhông sấy khô ở nhiệt độ cao\nÚi ở nhiệt độ thấp"}
                  className={`${INPUT} resize-none`}
                />
              </FieldGroup>
            </div>
          </SectionCard>

          {/* Colors */}
          <SectionCard title="Màu Sắc" info="Thêm các màu sắc có sẵn của sản phẩm">
            <div className="space-y-3">
              {errors.colors && <p className="text-xs text-[#E5001B]">{errors.colors}</p>}
              <div className="flex flex-wrap gap-2">
                {form.colors.map((c, i) => (
                  <div key={i} className="flex items-center gap-2 border border-[rgba(0,0,0,0.12)] px-3 py-2 bg-[#fafafa] group">
                    <span className="w-4 h-4 rounded-full border border-[#ddd] flex-shrink-0" style={{ backgroundColor: c.hex }} />
                    <span className="text-xs font-medium text-[#111]">{c.name}</span>
                    <button onClick={() => removeColor(i)} className="text-[#ccc] hover:text-[#E5001B] transition-colors ml-1">
                      <X size={12} />
                    </button>
                  </div>
                ))}
                <button
                  onClick={() => setShowColorPicker(!showColorPicker)}
                  className="flex items-center gap-2 border border-dashed border-[rgba(0,0,0,0.2)] px-3 py-2 text-xs text-[#888] hover:border-[#111] hover:text-[#111] transition-colors"
                >
                  <Plus size={13} /> Thêm Màu
                </button>
              </div>

              {showColorPicker && (
                <div className="border border-[rgba(0,0,0,0.12)] p-4 bg-[#fafafa] space-y-3">
                  <div className="flex gap-3 items-end">
                    <div className="flex flex-col gap-1.5 flex-1">
                      <label className="text-[10px] uppercase tracking-widest text-[#888] font-semibold">Tên màu</label>
                      <input
                        value={newColor.name}
                        onChange={(e) => setNewColor((c) => ({ ...c, name: e.target.value }))}
                        placeholder="VD: Đen, Trắng, Xanh Navy..."
                        className={INPUT}
                        onKeyDown={(e) => e.key === "Enter" && addColor()}
                      />
                    </div>
                    <div className="flex flex-col gap-1.5">
                      <label className="text-[10px] uppercase tracking-widest text-[#888] font-semibold">Màu sắc</label>
                      <input
                        type="color"
                        value={newColor.hex}
                        onChange={(e) => setNewColor((c) => ({ ...c, hex: e.target.value }))}
                        className="w-12 h-10 border border-[rgba(0,0,0,0.15)] cursor-pointer p-0.5"
                      />
                    </div>
                    <button onClick={addColor} className="flex items-center gap-1.5 bg-[#111] text-white px-4 py-2.5 text-xs uppercase tracking-widest font-medium hover:bg-[#E5001B] transition-colors h-10">
                      <Plus size={13} /> Thêm
                    </button>
                  </div>
                </div>
              )}
            </div>
          </SectionCard>

          {/* Sizes */}
          <SectionCard title="Kích Thước (Size)">
            <div className="space-y-3">
              {errors.sizes && <p className="text-xs text-[#E5001B]">{errors.sizes}</p>}
              <div className="flex flex-wrap gap-2">
                {availableSizes.map((size) => (
                  <button
                    key={size}
                    onClick={() => toggleSize(size)}
                    className={`min-w-[48px] h-10 px-3 text-sm font-medium border transition-all duration-150 ${form.sizes.includes(size) ? "border-[#111] bg-[#111] text-white" : "border-[rgba(0,0,0,0.15)] text-[#888] hover:border-[#111] hover:text-[#111]"}`}
                  >
                    {size}
                  </button>
                ))}
              </div>
              <p className="text-xs text-[#888]">Đã chọn: {form.sizes.length > 0 ? form.sizes.join(", ") : "Chưa chọn size nào"}</p>
            </div>
          </SectionCard>
        </div>

        {/* Right Column */}
        <div className="space-y-5">
          {/* Status */}
          <SectionCard title="Trạng Thái">
            <div className="space-y-2">
              {(["active", "draft", "archived"] as const).map((s) => {
                const labels = { active: "Đang Bán", draft: "Bản Nháp", archived: "Lưu Trữ" };
                const descs = { active: "Hiển thị trên website", draft: "Chưa công khai", archived: "Ẩn khỏi website" };
                return (
                  <button
                    key={s}
                    onClick={() => setForm((f) => ({ ...f, status: s }))}
                    className={`w-full text-left flex items-start gap-3 p-3 border transition-all ${form.status === s ? "border-[#111] bg-[#fafafa]" : "border-[rgba(0,0,0,0.1)] hover:border-[#888]"}`}
                  >
                    <div className={`w-4 h-4 rounded-full border-2 flex items-center justify-center mt-0.5 flex-shrink-0 ${form.status === s ? "border-[#111]" : "border-[#ccc]"}`}>
                      {form.status === s && <div className="w-2 h-2 rounded-full bg-[#111]" />}
                    </div>
                    <div>
                      <p className="text-sm font-medium text-[#111]">{labels[s]}</p>
                      <p className="text-xs text-[#888] mt-0.5">{descs[s]}</p>
                    </div>
                  </button>
                );
              })}
            </div>
          </SectionCard>

          {/* Images */}
          <SectionCard title="Hình Ảnh Sản Phẩm">
            <div className="space-y-3">
              {/* Main Image Upload */}
              <div className="aspect-square border-2 border-dashed border-[rgba(0,0,0,0.15)] flex flex-col items-center justify-center gap-3 text-center p-6 hover:border-[#111] transition-colors cursor-pointer group bg-[#fafafa]">
                {existing?.img ? (
                  <div className="relative w-full h-full">
                    <img src={existing.img} alt="preview" className="w-full h-full object-cover" />
                    <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                      <Upload size={24} className="text-white" />
                    </div>
                  </div>
                ) : (
                  <>
                    <Upload size={24} className="text-[#ccc] group-hover:text-[#888] transition-colors" />
                    <div>
                      <p className="text-sm font-medium text-[#888]">Tải ảnh chính lên</p>
                      <p className="text-xs text-[#aaa] mt-1">PNG, JPG tối đa 5MB</p>
                    </div>
                  </>
                )}
              </div>

              {/* Thumbnail Grid */}
              <div className="grid grid-cols-4 gap-2">
                {(existing?.images ?? [null, null, null, null]).map((img, i) => (
                  <div
                    key={i}
                    className="aspect-square border border-dashed border-[rgba(0,0,0,0.15)] flex items-center justify-center bg-[#fafafa] hover:border-[#111] transition-colors cursor-pointer overflow-hidden"
                  >
                    {img ? (
                      <img src={img} alt={`thumb ${i}`} className="w-full h-full object-cover" />
                    ) : (
                      <Plus size={16} className="text-[#ccc]" />
                    )}
                  </div>
                ))}
              </div>
              <p className="text-xs text-[#888]">Thêm tối đa 4 ảnh phụ. Kéo để sắp xếp thứ tự.</p>
            </div>
          </SectionCard>

          {/* SEO Preview */}
          <SectionCard title="SEO Preview">
            <div className="border border-[rgba(0,0,0,0.08)] p-3 bg-[#fafafa]">
              <p className="text-sm font-medium text-blue-600 truncate">{form.name || "Tên sản phẩm"}</p>
              <p className="text-xs text-green-700 mt-0.5">lino.vn/products/{String(existing?.id ?? "new")}</p>
              <p className="text-xs text-[#555] mt-1 line-clamp-2">{form.description || "Mô tả sản phẩm sẽ hiển thị tại đây..."}</p>
            </div>
          </SectionCard>

          {/* Danger Zone */}
          {isEdit && (
            <SectionCard title="Vùng Nguy Hiểm">
              <button className="w-full flex items-center justify-center gap-2 border border-[#E5001B]/30 text-[#E5001B] py-3 text-xs uppercase tracking-widest font-medium hover:bg-[#E5001B] hover:text-white transition-colors">
                <Trash2 size={13} /> Xóa Sản Phẩm
              </button>
            </SectionCard>
          )}
        </div>
      </div>
    </div>
  );
}
