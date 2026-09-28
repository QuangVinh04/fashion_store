import { useState, useEffect } from "react";
import { X, Lock, RefreshCw, Trash2 } from "lucide-react";

const BRANDS = [
  { id: "b1", name: "LINO Original" },
  { id: "b2", name: "LINO Premium" },
  { id: "b3", name: "LINO Essentials" },
];

const CATEGORY_TREE = [
  {
    id: "c1", name: "Áo", children: [
      { id: "c1a", name: "Áo Thun" },
      { id: "c1b", name: "Áo Sơ Mi" },
      { id: "c1c", name: "Áo Khoác" },
      { id: "c1d", name: "Áo Hoodie" },
    ],
  },
  {
    id: "c2", name: "Quần", children: [
      { id: "c2a", name: "Quần Jeans" },
      { id: "c2b", name: "Quần Tây" },
      { id: "c2c", name: "Quần Short" },
    ],
  },
  { id: "c3", name: "Váy & Đầm", children: [] },
  { id: "c4", name: "Phụ Kiện", children: [] },
];

const SIZE_CHARTS = [
  { id: "sc1", name: "Chuẩn Châu Á" },
  { id: "sc2", name: "Chuẩn EU" },
  { id: "sc3", name: "Chuẩn US" },
];

const GENDER_OPTIONS = ["MALE", "FEMALE", "UNISEX", "KIDS"];
const GENDER_LABELS: Record<string, string> = { MALE: "Nam", FEMALE: "Nữ", UNISEX: "Unisex", KIDS: "Trẻ Em" };
const TYPE_OPTIONS = ["TOPS", "BOTTOMS", "DRESS", "OUTERWEAR", "FOOTWEAR", "ACCESSORY"];
const TYPE_LABELS: Record<string, string> = {
  TOPS: "Áo", BOTTOMS: "Quần", DRESS: "Váy/Đầm",
  OUTERWEAR: "Áo Khoác", FOOTWEAR: "Giày Dép", ACCESSORY: "Phụ Kiện",
};

interface ColorTag { name: string; hex: string; }

interface VariantRow {
  tempId: string;
  variantId: string | null;
  color: string;
  colorHex: string;
  size: string;
  sku: string;
  barcode: string;
  price: number;
  salePrice: number | "";
  active: boolean;
}

interface ProductModalProps {
  open: boolean;
  onClose: () => void;
  productId?: string;
  onSuccess?: () => void;
}

function genId() {
  return Math.random().toString(36).slice(2, 10);
}

function toSlug(val: string) {
  return val
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[đĐ]/g, "d")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}

function Toggle({ on, onToggle }: { on: boolean; onToggle: () => void }) {
  return (
    <button
      type="button"
      onClick={onToggle}
      className={`w-9 h-5 relative flex-shrink-0 transition-colors duration-200 ${on ? "bg-[#111]" : "bg-[#d4d4d4]"}`}
    >
      <span className={`absolute top-0.5 w-4 h-4 bg-white transition-transform duration-200 ${on ? "translate-x-4" : "translate-x-0.5"}`} />
    </button>
  );
}

function SmallToggle({ on, onToggle }: { on: boolean; onToggle: () => void }) {
  return (
    <button
      type="button"
      onClick={onToggle}
      className={`w-8 h-4 relative flex-shrink-0 transition-colors duration-200 ${on ? "bg-[#111]" : "bg-[#d4d4d4]"}`}
    >
      <span className={`absolute top-0.5 w-3 h-3 bg-white transition-transform duration-200 ${on ? "translate-x-4" : "translate-x-0.5"}`} />
    </button>
  );
}

export default function ProductModal({ open, onClose, productId, onSuccess }: ProductModalProps) {
  const [savedId, setSavedId] = useState<string | null>(productId ?? null);
  const [activeTab, setActiveTab] = useState<"basic" | "combination" | "seo">("basic");
  const [saving, setSaving] = useState(false);

  // Basic Info
  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const [description, setDescription] = useState("");
  const [shortDesc, setShortDesc] = useState("");
  const [brandId, setBrandId] = useState("");
  const [categoryIds, setCategoryIds] = useState<string[]>([]);
  const [gender, setGender] = useState("UNISEX");
  const [productType, setProductType] = useState("TOPS");
  const [basePrice, setBasePrice] = useState<number | "">("");
  const [salePrice, setSalePrice] = useState<number | "">("");
  const [sizeChartId, setSizeChartId] = useState("");
  const [featured, setFeatured] = useState(false);
  const [hasVariants, setHasVariants] = useState(true);

  // SEO
  const [metaTitle, setMetaTitle] = useState("");
  const [metaKeyword, setMetaKeyword] = useState("");
  const [metaDesc, setMetaDesc] = useState("");

  // Combination
  const [sizes, setSizes] = useState<string[]>([]);
  const [sizeInput, setSizeInput] = useState("");
  const [colors, setColors] = useState<ColorTag[]>([]);
  const [colorInput, setColorInput] = useState("");
  const [variants, setVariants] = useState<VariantRow[]>([]);

  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!open) return;
    setSavedId(productId ?? null);
    setActiveTab("basic");
    setErrors({});
    setSizeInput("");
    setColorInput("");

    if (productId) {
      // Pre-fill mock data for edit mode
      setName("Áo Thun Cotton Premium");
      setSlug("ao-thun-cotton-premium");
      setDescription("Áo thun cotton cao cấp, mềm mại và thoáng mát, phù hợp mọi hoạt động hàng ngày.");
      setShortDesc("Cotton 100%, nhiều màu sắc tươi sáng.");
      setBrandId("b1");
      setCategoryIds(["c1a"]);
      setGender("UNISEX");
      setProductType("TOPS");
      setBasePrice(299000);
      setSalePrice(249000);
      setSizeChartId("sc1");
      setFeatured(true);
      setHasVariants(true);
      setMetaTitle("Áo Thun Cotton Premium - LINO");
      setMetaKeyword("áo thun, cotton, premium, lino");
      setMetaDesc("Mua áo thun cotton premium tại LINO. Chất liệu cao cấp, thoáng mát, nhiều màu sắc.");
      setSizes(["S", "M", "L", "XL"]);
      setColors([
        { name: "Đen", hex: "#111111" },
        { name: "Trắng", hex: "#FFFFFF" },
        { name: "Xanh Biển", hex: "#1D3461" },
      ]);
      setVariants([]);
    } else {
      setName(""); setSlug(""); setDescription(""); setShortDesc("");
      setBrandId(""); setCategoryIds([]); setGender("UNISEX"); setProductType("TOPS");
      setBasePrice(""); setSalePrice(""); setSizeChartId(""); setFeatured(false); setHasVariants(true);
      setMetaTitle(""); setMetaKeyword(""); setMetaDesc("");
      setSizes([]); setColors([]); setVariants([]);
    }
  }, [open, productId]);

  if (!open) return null;

  const isNew = !savedId;
  const canCombination = !isNew && hasVariants;
  const canSeo = !isNew;

  const handleNameChange = (val: string) => {
    setName(val);
    if (isNew) setSlug(toSlug(val));
  };

  const toggleCategory = (id: string) => {
    setCategoryIds((prev) => prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]);
  };

  const addSize = () => {
    const v = sizeInput.trim().toUpperCase();
    if (v && !sizes.includes(v)) setSizes((s) => [...s, v]);
    setSizeInput("");
  };

  const addColor = () => {
    const v = colorInput.trim();
    if (v && !colors.find((c) => c.name.toLowerCase() === v.toLowerCase())) {
      setColors((cs) => [...cs, { name: v, hex: "#888888" }]);
    }
    setColorInput("");
  };

  const updateColorHex = (idx: number, hex: string) => {
    setColors((cs) => cs.map((c, i) => (i === idx ? { ...c, hex } : c)));
  };

  const generateVariants = () => {
    const bp = typeof basePrice === "number" ? basePrice : 0;
    const colorList = colors.length > 0 ? colors : [{ name: "Default", hex: "#888888" }];
    const sizeList = sizes.length > 0 ? sizes : ["One Size"];
    const rows: VariantRow[] = [];
    colorList.forEach((color) => {
      sizeList.forEach((size) => {
        const existing = variants.find((v) => v.color === color.name && v.size === size);
        rows.push(
          existing
            ? { ...existing, colorHex: color.hex }
            : {
                tempId: genId(),
                variantId: null,
                color: color.name,
                colorHex: color.hex,
                size,
                sku: `LINO-${toSlug(color.name).slice(0, 3).toUpperCase()}-${size}-${genId().slice(0, 4).toUpperCase()}`,
                barcode: `89${Math.floor(Math.random() * 1e10).toString().padStart(10, "0")}`,
                price: bp,
                salePrice: "",
                active: true,
              }
        );
      });
    });
    setVariants(rows);
  };

  const updateVariant = <K extends keyof VariantRow>(idx: number, key: K, val: VariantRow[K]) => {
    setVariants((vs) => vs.map((v, i) => (i === idx ? { ...v, [key]: val } : v)));
  };

  const validateBasic = () => {
    const e: Record<string, string> = {};
    if (!name.trim()) e.name = "Tên sản phẩm là bắt buộc";
    if (typeof basePrice !== "number" || basePrice < 0) e.basePrice = "Giá gốc phải >= 0";
    if (typeof salePrice === "number" && typeof basePrice === "number" && salePrice > basePrice)
      e.salePrice = "Giá sale phải <= giá gốc";
    if (categoryIds.length === 0) e.categoryIds = "Chọn ít nhất 1 danh mục";
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSave = async () => {
    if (!validateBasic()) { setActiveTab("basic"); return; }
    setSaving(true);

    if (isNew) {
      // Simulate POST /admin/products → receive productId
      await new Promise((r) => setTimeout(r, 900));
      const newId = genId();
      setSavedId(newId);
      setActiveTab(hasVariants ? "combination" : "seo");
    } else {
      // Simulate PUT /admin/products/{id} (basic + seo)
      // + PUT /admin/products/{id}/variants (if has variants)
      await new Promise((r) => setTimeout(r, 1000));
      onSuccess?.();
      onClose();
    }
    setSaving(false);
  };

  const TABS = [
    { id: "basic" as const, label: "Thông Tin Cơ Bản", locked: false },
    { id: "combination" as const, label: "Biến Thể", locked: !canCombination },
    { id: "seo" as const, label: "SEO", locked: !canSeo },
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6" style={{ fontFamily: "'Inter', sans-serif" }}>
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />

      <div className="relative bg-white w-full max-w-3xl max-h-[92vh] flex flex-col shadow-2xl">
        {/* Modal Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[rgba(0,0,0,0.1)] flex-shrink-0">
          <div>
            <h2
              style={{
                fontFamily: "'Barlow Condensed', sans-serif",
                fontWeight: 900,
                fontSize: "1.5rem",
                letterSpacing: "-0.01em",
                color: "#111",
              }}
            >
              {isNew ? "THÊM SẢN PHẨM" : "CẬP NHẬT SẢN PHẨM"}
            </h2>
            {savedId && (
              <p className="text-[11px] text-[#888] mt-0.5">
                ID: <span className="font-mono text-[#555]">{savedId}</span>
                <span className="ml-3 text-[9px] uppercase tracking-widest border border-yellow-300 bg-yellow-50 text-yellow-700 px-1.5 py-0.5">DRAFT</span>
              </p>
            )}
          </div>
          <button onClick={onClose} className="w-8 h-8 flex items-center justify-center text-[#888] hover:text-[#111] hover:bg-[#f5f5f5] transition-colors">
            <X size={18} />
          </button>
        </div>

        {/* Tabs */}
        <div className="flex items-stretch border-b border-[rgba(0,0,0,0.08)] flex-shrink-0 bg-[#fafafa]">
          {TABS.map((tab) => (
            <button
              key={tab.id}
              onClick={() => !tab.locked && setActiveTab(tab.id)}
              disabled={tab.locked}
              className={`flex items-center gap-1.5 px-5 py-3.5 text-[11px] font-semibold uppercase tracking-widest transition-colors relative flex-shrink-0 ${
                tab.locked
                  ? "text-[#ccc] cursor-not-allowed"
                  : activeTab === tab.id
                  ? "text-[#111] bg-white"
                  : "text-[#888] hover:text-[#555] cursor-pointer hover:bg-white/70"
              }`}
            >
              {tab.locked && <Lock size={10} />}
              {tab.label}
              {activeTab === tab.id && !tab.locked && (
                <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-[#111]" />
              )}
            </button>
          ))}

          {isNew && (
            <div className="flex items-center ml-auto px-4 flex-shrink-0">
              <span className="text-[10px] text-[#bbb] tracking-wide">Lưu Bước 1 để mở khóa tab tiếp theo</span>
            </div>
          )}
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-6">
          {/* ───── TAB: Basic Info ───── */}
          {activeTab === "basic" && (
            <div className="space-y-5">
              {/* Name */}
              <div>
                <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">
                  Tên Sản Phẩm <span className="text-[#E5001B]">*</span>
                </label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => handleNameChange(e.target.value)}
                  placeholder="VD: Áo Thun Cotton Premium"
                  className={`w-full border px-4 py-2.5 text-sm text-[#111] placeholder:text-[#bbb] focus:outline-none transition-colors ${
                    errors.name ? "border-[#E5001B]" : "border-[rgba(0,0,0,0.15)] focus:border-[#111]"
                  }`}
                />
                {errors.name && <p className="text-xs text-[#E5001B] mt-1">{errors.name}</p>}
              </div>

              {/* Slug */}
              <div>
                <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">
                  Slug{" "}
                  <span className="text-[#888] text-[9px] normal-case tracking-normal font-normal">
                    (tự động từ tên · không sửa được khi đã PUBLISHED)
                  </span>
                </label>
                <input
                  type="text"
                  value={slug}
                  onChange={(e) => setSlug(e.target.value)}
                  placeholder="ao-thun-cotton-premium"
                  className="w-full border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-sm text-[#888] font-mono bg-[#fafafa] focus:outline-none focus:border-[#111] transition-colors"
                />
              </div>

              {/* Description */}
              <div>
                <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">Mô Tả</label>
                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  rows={4}
                  placeholder="Mô tả chi tiết sản phẩm..."
                  className="w-full border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-sm text-[#111] placeholder:text-[#bbb] focus:outline-none focus:border-[#111] transition-colors resize-none"
                />
              </div>

              {/* Short Desc */}
              <div>
                <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">Mô Tả Ngắn</label>
                <input
                  type="text"
                  value={shortDesc}
                  onChange={(e) => setShortDesc(e.target.value)}
                  placeholder="Tóm tắt ngắn gọn"
                  className="w-full border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-sm text-[#111] placeholder:text-[#bbb] focus:outline-none focus:border-[#111] transition-colors"
                />
              </div>

              {/* Brand + SizeChart */}
              <div className="grid sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">Thương Hiệu</label>
                  <select
                    value={brandId}
                    onChange={(e) => setBrandId(e.target.value)}
                    className="w-full border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-sm text-[#111] bg-white focus:outline-none focus:border-[#111] transition-colors cursor-pointer"
                  >
                    <option value="">-- Chọn thương hiệu --</option>
                    {BRANDS.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">Bảng Size</label>
                  <select
                    value={sizeChartId}
                    onChange={(e) => setSizeChartId(e.target.value)}
                    className="w-full border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-sm text-[#111] bg-white focus:outline-none focus:border-[#111] transition-colors cursor-pointer"
                  >
                    <option value="">-- Không áp dụng --</option>
                    {SIZE_CHARTS.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                  </select>
                </div>
              </div>

              {/* Categories */}
              <div>
                <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">
                  Danh Mục <span className="text-[#E5001B]">*</span>
                </label>
                <div className={`border px-4 py-3 space-y-3 ${errors.categoryIds ? "border-[#E5001B]" : "border-[rgba(0,0,0,0.15)]"}`}>
                  {CATEGORY_TREE.map((cat) => {
                    const children = cat.children.length > 0 ? cat.children : [cat];
                    return (
                      <div key={cat.id}>
                        <p className="text-[10px] uppercase tracking-widest text-[#888] font-semibold mb-1.5">{cat.name}</p>
                        <div className="flex flex-wrap gap-3 pl-2">
                          {children.map((sub) => (
                            <label key={sub.id} className="flex items-center gap-1.5 cursor-pointer">
                              <input
                                type="checkbox"
                                checked={categoryIds.includes(sub.id)}
                                onChange={() => toggleCategory(sub.id)}
                                className="w-3.5 h-3.5 accent-[#111]"
                              />
                              <span className="text-xs text-[#555]">{sub.name}</span>
                            </label>
                          ))}
                        </div>
                      </div>
                    );
                  })}
                </div>
                {errors.categoryIds && <p className="text-xs text-[#E5001B] mt-1">{errors.categoryIds}</p>}
              </div>

              {/* Gender + Type */}
              <div className="grid sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">Giới Tính</label>
                  <select
                    value={gender}
                    onChange={(e) => setGender(e.target.value)}
                    className="w-full border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-sm text-[#111] bg-white focus:outline-none focus:border-[#111] transition-colors cursor-pointer"
                  >
                    {GENDER_OPTIONS.map((g) => <option key={g} value={g}>{GENDER_LABELS[g]}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">Loại Sản Phẩm</label>
                  <select
                    value={productType}
                    onChange={(e) => setProductType(e.target.value)}
                    className="w-full border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-sm text-[#111] bg-white focus:outline-none focus:border-[#111] transition-colors cursor-pointer"
                  >
                    {TYPE_OPTIONS.map((t) => <option key={t} value={t}>{TYPE_LABELS[t]}</option>)}
                  </select>
                </div>
              </div>

              {/* Prices */}
              <div className="grid sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">
                    Giá Gốc (₫) <span className="text-[#E5001B]">*</span>
                  </label>
                  <input
                    type="number"
                    value={basePrice}
                    onChange={(e) => setBasePrice(e.target.value === "" ? "" : Number(e.target.value))}
                    placeholder="299000"
                    min={0}
                    className={`w-full border px-4 py-2.5 text-sm text-[#111] placeholder:text-[#bbb] focus:outline-none transition-colors ${
                      errors.basePrice ? "border-[#E5001B]" : "border-[rgba(0,0,0,0.15)] focus:border-[#111]"
                    }`}
                  />
                  {errors.basePrice && <p className="text-xs text-[#E5001B] mt-1">{errors.basePrice}</p>}
                </div>
                <div>
                  <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">Giá Sale (₫)</label>
                  <input
                    type="number"
                    value={salePrice}
                    onChange={(e) => setSalePrice(e.target.value === "" ? "" : Number(e.target.value))}
                    placeholder="Để trống nếu không sale"
                    min={0}
                    className={`w-full border px-4 py-2.5 text-sm text-[#111] placeholder:text-[#bbb] focus:outline-none transition-colors ${
                      errors.salePrice ? "border-[#E5001B]" : "border-[rgba(0,0,0,0.15)] focus:border-[#111]"
                    }`}
                  />
                  {errors.salePrice && <p className="text-xs text-[#E5001B] mt-1">{errors.salePrice}</p>}
                  {typeof basePrice === "number" && typeof salePrice === "number" && salePrice > 0 && salePrice < basePrice && (
                    <p className="text-[11px] text-[#2D5A3D] mt-1">
                      Giảm {Math.round((1 - salePrice / basePrice) * 100)}%
                    </p>
                  )}
                </div>
              </div>

              {/* Toggles */}
              <div className="flex flex-wrap items-center gap-6 pt-4 border-t border-[rgba(0,0,0,0.06)]">
                <label className="flex items-center gap-2.5 cursor-pointer" onClick={() => setFeatured(!featured)}>
                  <Toggle on={featured} onToggle={() => setFeatured(!featured)} />
                  <span className="text-sm text-[#555]">Sản phẩm nổi bật</span>
                </label>

                <label className="flex items-center gap-2.5 cursor-pointer" onClick={() => setHasVariants(!hasVariants)}>
                  <Toggle on={hasVariants} onToggle={() => setHasVariants(!hasVariants)} />
                  <div>
                    <span className="text-sm text-[#555]">Sản phẩm có biến thể (Màu / Size)</span>
                    {!hasVariants && (
                      <p className="text-[10px] text-[#aaa] mt-0.5">Bán trực tiếp theo Giá Gốc — tab Biến Thể sẽ bị ẩn</p>
                    )}
                  </div>
                </label>
              </div>
            </div>
          )}

          {/* ───── TAB: Combination ───── */}
          {activeTab === "combination" && (
            <div className="space-y-6">
              <div className="p-3 bg-[#f8f8f8] border border-[rgba(0,0,0,0.06)] text-xs text-[#888]">
                Color và Size là tag input tự do — gõ tên rồi nhấn <kbd className="border border-[rgba(0,0,0,0.15)] px-1 py-0.5 text-[10px] bg-white">Enter</kbd> để thêm. Không chọn từ danh sách Master Data.
              </div>

              {/* Sizes */}
              <div>
                <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-2">
                  Chọn Size{" "}
                  <span className="text-[#888] text-[9px] normal-case tracking-normal font-normal">— gõ tự do, Enter để thêm</span>
                </label>
                <div className="border border-[rgba(0,0,0,0.15)] p-3 min-h-[54px] flex flex-wrap gap-2 items-center focus-within:border-[#111] transition-colors">
                  {sizes.map((s) => (
                    <span key={s} className="flex items-center gap-1.5 bg-[#111] text-white text-xs px-2.5 py-1">
                      {s}
                      <button
                        type="button"
                        onClick={() => setSizes((prev) => prev.filter((x) => x !== s))}
                        className="text-white/50 hover:text-white transition-colors"
                      >
                        <X size={10} />
                      </button>
                    </span>
                  ))}
                  <input
                    type="text"
                    value={sizeInput}
                    onChange={(e) => setSizeInput(e.target.value)}
                    onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addSize(); } }}
                    placeholder={sizes.length === 0 ? "S, M, L, XL, 28, 30... → Enter để thêm" : "Thêm size..."}
                    className="flex-1 min-w-[160px] text-sm text-[#111] placeholder:text-[#bbb] focus:outline-none bg-transparent"
                  />
                </div>
              </div>

              {/* Colors */}
              <div>
                <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-2">
                  Chọn Màu Sắc{" "}
                  <span className="text-[#888] text-[9px] normal-case tracking-normal font-normal">— gõ tên màu, Enter để thêm · click ô màu để đổi hex</span>
                </label>
                <div className="border border-[rgba(0,0,0,0.15)] p-3 min-h-[54px] flex flex-wrap gap-2 items-center focus-within:border-[#111] transition-colors">
                  {colors.map((c, idx) => (
                    <span key={c.name} className="flex items-center gap-2 border border-[rgba(0,0,0,0.12)] bg-white px-2.5 py-1.5">
                      <label className="cursor-pointer" title="Chọn mã hex">
                        <span
                          className="w-4 h-4 block border border-[rgba(0,0,0,0.12)] relative"
                          style={{ backgroundColor: c.hex }}
                        >
                          <input
                            type="color"
                            value={c.hex}
                            onChange={(e) => updateColorHex(idx, e.target.value)}
                            className="absolute inset-0 opacity-0 cursor-pointer w-full h-full"
                          />
                        </span>
                      </label>
                      <span className="text-xs text-[#555]">{c.name}</span>
                      <span className="text-[10px] font-mono text-[#aaa]">{c.hex}</span>
                      <button
                        type="button"
                        onClick={() => setColors((prev) => prev.filter((_, i) => i !== idx))}
                        className="text-[#bbb] hover:text-[#E5001B] transition-colors"
                      >
                        <X size={10} />
                      </button>
                    </span>
                  ))}
                  <input
                    type="text"
                    value={colorInput}
                    onChange={(e) => setColorInput(e.target.value)}
                    onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addColor(); } }}
                    placeholder={colors.length === 0 ? "Đen, Trắng, Xanh Biển... → Enter để thêm" : "Thêm màu..."}
                    className="flex-1 min-w-[200px] text-sm text-[#111] placeholder:text-[#bbb] focus:outline-none bg-transparent"
                  />
                </div>
              </div>

              {/* Generate */}
              <div className="flex items-center justify-between py-3 border-t border-b border-[rgba(0,0,0,0.06)]">
                <div>
                  <p className="text-sm text-[#555]">
                    <strong className="text-[#111]">{Math.max(colors.length, 1)}</strong> màu ×{" "}
                    <strong className="text-[#111]">{Math.max(sizes.length, 1)}</strong> size ={" "}
                    <strong className="text-[#E5001B]">{Math.max(colors.length, 1) * Math.max(sizes.length, 1)}</strong> biến thể
                  </p>
                  <p className="text-[11px] text-[#aaa] mt-0.5">
                    Tính Cartesian product ở client — không gọi API
                  </p>
                </div>
                <button
                  onClick={generateVariants}
                  className="flex items-center gap-2 bg-[#111] text-white px-5 py-2.5 text-xs uppercase tracking-widest font-medium hover:bg-[#333] transition-colors"
                >
                  <RefreshCw size={13} />
                  {variants.length > 0 ? "Tạo Lại" : "Tạo Biến Thể"}
                </button>
              </div>

              {/* Variants table */}
              {variants.length > 0 ? (
                <div>
                  <p className="text-[10px] uppercase tracking-widest text-[#888] font-semibold mb-2">
                    Bảng Biến Thể ({variants.length} dòng)
                    <span className="ml-2 text-[#bbb] font-normal normal-case tracking-normal">· Sẽ gửi qua PUT /admin/products/{"{id}"}/variants khi Cập Nhật</span>
                  </p>
                  <div className="border border-[rgba(0,0,0,0.1)] overflow-x-auto">
                    <table className="w-full min-w-[780px]">
                      <thead>
                        <tr className="border-b border-[rgba(0,0,0,0.08)] bg-[#f8f8f8]">
                          <th className="px-3 py-2.5 text-left text-[10px] uppercase tracking-widest text-[#888] font-semibold">Kết Hợp</th>
                          <th className="px-3 py-2.5 text-left text-[10px] uppercase tracking-widest text-[#888] font-semibold">SKU</th>
                          <th className="px-3 py-2.5 text-left text-[10px] uppercase tracking-widest text-[#888] font-semibold">Barcode</th>
                          <th className="px-3 py-2.5 text-right text-[10px] uppercase tracking-widest text-[#888] font-semibold">Giá (₫)</th>
                          <th className="px-3 py-2.5 text-right text-[10px] uppercase tracking-widest text-[#888] font-semibold">Giá Sale</th>
                          <th className="px-3 py-2.5 text-center text-[10px] uppercase tracking-widest text-[#888] font-semibold">Hiển Thị</th>
                          <th className="px-2 py-2.5 w-8" />
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-[rgba(0,0,0,0.04)]">
                        {variants.map((v, idx) => (
                          <tr key={v.tempId} className="hover:bg-[#fafafa]">
                            <td className="px-3 py-2.5">
                              <div className="flex items-center gap-2">
                                <span
                                  className="w-4 h-4 rounded-full border border-[rgba(0,0,0,0.15)] flex-shrink-0"
                                  style={{ backgroundColor: v.colorHex }}
                                />
                                <span className="text-xs text-[#555] font-medium whitespace-nowrap">
                                  {v.color} / {v.size}
                                </span>
                              </div>
                            </td>
                            <td className="px-3 py-2.5">
                              <input
                                type="text"
                                value={v.sku}
                                onChange={(e) => updateVariant(idx, "sku", e.target.value)}
                                className="w-full border border-[rgba(0,0,0,0.12)] px-2 py-1.5 text-[11px] font-mono text-[#555] focus:outline-none focus:border-[#111] transition-colors"
                              />
                            </td>
                            <td className="px-3 py-2.5">
                              <input
                                type="text"
                                value={v.barcode}
                                onChange={(e) => updateVariant(idx, "barcode", e.target.value)}
                                className="w-full border border-[rgba(0,0,0,0.12)] px-2 py-1.5 text-[11px] font-mono text-[#555] focus:outline-none focus:border-[#111] transition-colors"
                              />
                            </td>
                            <td className="px-3 py-2.5">
                              <input
                                type="number"
                                value={v.price}
                                onChange={(e) => updateVariant(idx, "price", Number(e.target.value))}
                                className="w-24 text-right border border-[rgba(0,0,0,0.12)] px-2 py-1.5 text-xs text-[#555] focus:outline-none focus:border-[#111] transition-colors"
                              />
                            </td>
                            <td className="px-3 py-2.5">
                              <input
                                type="number"
                                value={v.salePrice}
                                onChange={(e) =>
                                  updateVariant(idx, "salePrice", e.target.value === "" ? "" : Number(e.target.value))
                                }
                                placeholder="—"
                                className="w-24 text-right border border-[rgba(0,0,0,0.12)] px-2 py-1.5 text-xs text-[#555] placeholder:text-[#ccc] focus:outline-none focus:border-[#111] transition-colors"
                              />
                            </td>
                            <td className="px-3 py-2.5 text-center">
                              <div className="flex justify-center">
                                <SmallToggle on={v.active} onToggle={() => updateVariant(idx, "active", !v.active)} />
                              </div>
                            </td>
                            <td className="px-2 py-2.5">
                              <button
                                onClick={() => setVariants((vs) => vs.filter((_, i) => i !== idx))}
                                className="w-7 h-7 flex items-center justify-center text-[#ccc] hover:text-[#E5001B] hover:bg-red-50 transition-colors"
                              >
                                <Trash2 size={13} />
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center py-10 border border-dashed border-[rgba(0,0,0,0.12)] text-center">
                  <p className="text-sm text-[#aaa]">Thêm màu và size, sau đó bấm <strong className="text-[#555]">Tạo Biến Thể</strong></p>
                  <p className="text-xs text-[#ccc] mt-1">Tổ hợp được tính ở client-side, không gọi API</p>
                </div>
              )}
            </div>
          )}

          {/* ───── TAB: SEO ───── */}
          {activeTab === "seo" && (
            <div className="space-y-5">
              <div className="p-3 bg-[#f8f8f8] border border-[rgba(0,0,0,0.06)] text-xs text-[#888]">
                Dữ liệu SEO được gộp vào{" "}
                <code className="font-mono text-[#555]">PUT /admin/products/{"{id}"}</code> — không có endpoint riêng.
              </div>

              <div>
                <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">Meta Title</label>
                <input
                  type="text"
                  value={metaTitle}
                  onChange={(e) => setMetaTitle(e.target.value)}
                  placeholder="Tiêu đề hiển thị trên Google (50-60 ký tự)"
                  className="w-full border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-sm text-[#111] placeholder:text-[#bbb] focus:outline-none focus:border-[#111] transition-colors"
                />
                <div className="flex justify-between mt-1">
                  <span />
                  <span className={`text-[11px] ${metaTitle.length > 60 ? "text-[#E5001B]" : "text-[#aaa]"}`}>
                    {metaTitle.length}/60
                  </span>
                </div>
              </div>

              <div>
                <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">Meta Keywords</label>
                <input
                  type="text"
                  value={metaKeyword}
                  onChange={(e) => setMetaKeyword(e.target.value)}
                  placeholder="áo thun, cotton, premium, lino (phân cách bằng dấu phẩy)"
                  className="w-full border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-sm text-[#111] placeholder:text-[#bbb] focus:outline-none focus:border-[#111] transition-colors"
                />
              </div>

              <div>
                <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">Meta Description</label>
                <textarea
                  value={metaDesc}
                  onChange={(e) => setMetaDesc(e.target.value)}
                  rows={4}
                  placeholder="Mô tả ngắn hiển thị trên kết quả tìm kiếm (150-160 ký tự)"
                  className="w-full border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-sm text-[#111] placeholder:text-[#bbb] focus:outline-none focus:border-[#111] transition-colors resize-none"
                />
                <div className="flex justify-end mt-1">
                  <span className={`text-[11px] ${metaDesc.length > 160 ? "text-[#E5001B]" : "text-[#aaa]"}`}>
                    {metaDesc.length}/160
                  </span>
                </div>
              </div>

              {/* Google Preview */}
              {(metaTitle || metaDesc) && (
                <div className="border border-[rgba(0,0,0,0.1)] p-4 bg-white">
                  <p className="text-[10px] uppercase tracking-widest text-[#888] mb-3 font-semibold">Xem Trước Google</p>
                  <div className="space-y-0.5">
                    <p className="text-[#1a0dab] text-base hover:underline cursor-pointer">{metaTitle || name || "Tiêu đề sản phẩm"}</p>
                    <p className="text-[#006621] text-xs">lino.vn › products › {slug || "..."}</p>
                    <p className="text-[#545454] text-sm mt-1 line-clamp-2">{metaDesc || description || "Mô tả sản phẩm sẽ hiển thị tại đây..."}</p>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between px-6 py-4 border-t border-[rgba(0,0,0,0.1)] bg-[#fafafa] flex-shrink-0">
          <button
            onClick={onClose}
            className="border border-[rgba(0,0,0,0.15)] px-5 py-2.5 text-xs uppercase tracking-widest text-[#555] hover:border-[#111] hover:text-[#111] transition-colors"
          >
            Hủy
          </button>

          <div className="flex items-center gap-4">
            {!isNew && (
              <p className="text-[11px] text-[#aaa] hidden sm:block">
                {activeTab === "combination"
                  ? "Sẽ gọi PUT /variants khi lưu"
                  : activeTab === "seo"
                  ? "SEO gộp vào PUT /products"
                  : `PUT /admin/products/${savedId}`}
              </p>
            )}
            <button
              onClick={handleSave}
              disabled={saving}
              className="flex items-center gap-2 bg-[#111] text-white px-6 py-2.5 text-xs uppercase tracking-widest font-medium hover:bg-[#E5001B] transition-colors duration-200 disabled:opacity-60 disabled:cursor-not-allowed min-w-[170px] justify-center"
            >
              {saving ? (
                <>
                  <span className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  Đang lưu...
                </>
              ) : isNew ? (
                "Thêm Sản Phẩm →"
              ) : (
                "Cập Nhật Sản Phẩm"
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
