import { useState } from "react";
import {
  Search, Palette, Ruler, Plus, ChevronDown, Pencil, Trash2, X,
  Copy, Check, ChevronRight, ChevronLeft, AlertTriangle,
} from "lucide-react";
import { useAdminData, type Color, type Size } from "../../context/AdminDataContext";

type ActiveTab = "colors" | "sizes" | "sizecharts";
type ModalMode = "color" | "size" | null;
type NewColor = { name: string; hex: string; order: number; active: boolean };
type NewSize  = { name: string; code: string; category: string; order: number; active: boolean };

const SIZE_CHART = [
  { size: "XS",  chest: "80–84",   waist: "60–64", hip: "86–90",   length: "56" },
  { size: "S",   chest: "84–88",   waist: "64–68", hip: "90–94",   length: "57" },
  { size: "M",   chest: "88–92",   waist: "68–72", hip: "94–98",   length: "58" },
  { size: "L",   chest: "92–96",   waist: "72–76", hip: "98–102",  length: "59" },
  { size: "XL",  chest: "96–100",  waist: "76–80", hip: "102–106", length: "60" },
  { size: "XXL", chest: "100–104", waist: "80–84", hip: "106–110", length: "61" },
];

// ─── Micro Components ─────────────────────────────────────────────────────────
function Toggle({ checked, onChange }: { checked: boolean; onChange: () => void }) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      onClick={onChange}
      className={`relative inline-flex h-5 w-9 flex-shrink-0 cursor-pointer items-center rounded-full border transition-colors duration-200 focus-visible:outline-none ${
        checked ? "bg-[#111] border-[#111]" : "bg-gray-200 border-gray-200"
      }`}
    >
      <span
        className={`inline-block h-3.5 w-3.5 rounded-full bg-white shadow-sm transition-transform duration-200 ${
          checked ? "translate-x-[18px]" : "translate-x-0.5"
        }`}
      />
    </button>
  );
}

const CAT_PILL: Record<string, string> = {
  APPAREL:   "bg-slate-100 text-slate-600",
  SHOE:      "bg-zinc-100 text-zinc-600",
  KIDS:      "bg-stone-100 text-stone-600",
  ACCESSORY: "bg-neutral-100 text-neutral-600",
};

function CategoryPill({ cat }: { cat: string }) {
  return (
    <span className={`inline-block rounded px-2 py-0.5 text-[10px] font-bold tracking-widest uppercase ${CAT_PILL[cat] ?? "bg-gray-100 text-gray-500"}`}>
      {cat}
    </span>
  );
}

// ─── Color Modal ──────────────────────────────────────────────────────────────
function ColorModal({ onClose, onSave }: { onClose: () => void; onSave: (c: NewColor) => void }) {
  const [form, setForm] = useState<NewColor>({ name: "", hex: "#000000", order: 0, active: true });

  const isLight = (hex: string) => {
    if (hex.length < 7) return false;
    const r = parseInt(hex.slice(1, 3), 16);
    const g = parseInt(hex.slice(3, 5), 16);
    const b = parseInt(hex.slice(5, 7), 16);
    return (r * 299 + g * 587 + b * 114) / 1000 > 150;
  };

  return (
    <div className="fixed inset-0 z-50 flex">
      <div className="flex-1 bg-black/25" onClick={onClose} />
      <div className="w-[400px] bg-white h-full flex flex-col shadow-2xl">
        <div className="flex items-center justify-between px-6 py-5 border-b border-gray-100">
          <div>
            <p className="text-[9px] font-extrabold tracking-[0.2em] text-gray-400 uppercase mb-0.5">Thêm mới</p>
            <h3 className="text-[15px] font-bold text-gray-900">Màu Sắc</h3>
          </div>
          <button onClick={onClose} className="p-1.5 rounded hover:bg-gray-100 transition-colors">
            <X size={16} className="text-gray-500" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-6 space-y-5" style={{ scrollbarWidth: "none" }}>
          {/* Preview */}
          <div className="rounded-lg overflow-hidden border border-gray-100">
            <div
              className="h-20 flex items-end px-4 pb-3 transition-all duration-200"
              style={{ backgroundColor: form.hex }}
            >
              <span className="text-sm font-bold" style={{ color: isLight(form.hex) ? "#111827" : "#ffffff" }}>
                {form.name || "Tên màu..."}
              </span>
            </div>
            <div className="bg-gray-50 px-4 py-2 flex items-center gap-2">
              <span className="w-3 h-3 rounded-full border border-gray-200" style={{ backgroundColor: form.hex }} />
              <code className="text-[11px] font-mono text-gray-500">{form.hex.toUpperCase()}</code>
            </div>
          </div>

          <div>
            <label className="block text-[10px] font-bold tracking-widest text-gray-400 uppercase mb-1.5">
              Tên màu <span className="text-red-400">*</span>
            </label>
            <input
              type="text"
              placeholder="e.g. Xanh Navy"
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              className="w-full border border-gray-200 rounded-md px-3 py-2.5 text-[13px] text-gray-800 focus:outline-none focus:border-gray-400 transition-all"
            />
          </div>

          <div>
            <label className="block text-[10px] font-bold tracking-widest text-gray-400 uppercase mb-1.5">
              Màu (HEX) <span className="text-red-400">*</span>
            </label>
            <div className="flex items-center gap-2">
              <label className="cursor-pointer flex-shrink-0">
                <input
                  type="color"
                  value={form.hex.length === 7 ? form.hex : "#000000"}
                  onChange={(e) => setForm((f) => ({ ...f, hex: e.target.value }))}
                  className="sr-only"
                />
                <div className="w-10 h-10 rounded-md border border-gray-200 shadow-sm" style={{ backgroundColor: form.hex }} />
              </label>
              <input
                type="text"
                value={form.hex}
                onChange={(e) => {
                  const v = e.target.value.startsWith("#") ? e.target.value : `#${e.target.value}`;
                  setForm((f) => ({ ...f, hex: v }));
                }}
                placeholder="#000000"
                maxLength={7}
                className="flex-1 border border-gray-200 rounded-md px-3 py-2.5 text-[13px] font-mono text-gray-700 focus:outline-none focus:border-gray-400 transition-all uppercase"
              />
            </div>
          </div>

          <div>
            <label className="block text-[10px] font-bold tracking-widest text-gray-400 uppercase mb-1.5">
              Thứ tự hiển thị
            </label>
            <input
              type="number"
              value={form.order}
              min={0}
              onChange={(e) => setForm((f) => ({ ...f, order: Number(e.target.value) }))}
              className="w-full border border-gray-200 rounded-md px-3 py-2.5 text-[13px] text-gray-800 focus:outline-none focus:border-gray-400 transition-all"
            />
          </div>

          <div className="flex items-center justify-between pt-3 border-t border-gray-100">
            <div>
              <p className="text-[13px] font-medium text-gray-700">Kích hoạt</p>
              <p className="text-[11px] text-gray-400 mt-0.5">Hiển thị trong tùy chọn biến thể</p>
            </div>
            <Toggle checked={form.active} onChange={() => setForm((f) => ({ ...f, active: !f.active }))} />
          </div>
        </div>

        <div className="border-t border-gray-100 px-6 py-4 flex gap-2.5">
          <button onClick={onClose} className="flex-1 border border-gray-200 rounded-md py-2.5 text-[13px] font-medium text-gray-600 hover:bg-gray-50 transition-colors">
            Hủy
          </button>
          <button
            onClick={() => { onSave(form); onClose(); }}
            disabled={!form.name.trim()}
            className="flex-1 bg-[#111] text-white rounded-md py-2.5 text-[13px] font-semibold hover:bg-gray-800 disabled:opacity-40 transition-colors"
          >
            Lưu màu
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Size Modal ───────────────────────────────────────────────────────────────
function SizeModal({ onClose, onSave }: { onClose: () => void; onSave: (s: NewSize) => void }) {
  const [form, setForm] = useState<NewSize>({ name: "", code: "", category: "APPAREL", order: 0, active: true });

  function buildCode(name: string, cat: string) {
    return `${cat}_${name.toUpperCase().replace(/[^A-Z0-9]/g, "")}`;
  }

  return (
    <div className="fixed inset-0 z-50 flex">
      <div className="flex-1 bg-black/25" onClick={onClose} />
      <div className="w-[400px] bg-white h-full flex flex-col shadow-2xl">
        <div className="flex items-center justify-between px-6 py-5 border-b border-gray-100">
          <div>
            <p className="text-[9px] font-extrabold tracking-[0.2em] text-gray-400 uppercase mb-0.5">Thêm mới</p>
            <h3 className="text-[15px] font-bold text-gray-900">Kích Cỡ</h3>
          </div>
          <button onClick={onClose} className="p-1.5 rounded hover:bg-gray-100 transition-colors">
            <X size={16} className="text-gray-500" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-6 space-y-5" style={{ scrollbarWidth: "none" }}>
          <div>
            <label className="block text-[10px] font-bold tracking-widest text-gray-400 uppercase mb-1.5">
              Tên kích cỡ <span className="text-red-400">*</span>
            </label>
            <input
              type="text"
              placeholder="e.g. XL hoặc 42"
              value={form.name}
              onChange={(e) => {
                const name = e.target.value;
                setForm((f) => ({ ...f, name, code: buildCode(name, f.category) }));
              }}
              className="w-full border border-gray-200 rounded-md px-3 py-2.5 text-[13px] text-gray-800 focus:outline-none focus:border-gray-400 transition-all"
            />
          </div>

          <div>
            <label className="block text-[10px] font-bold tracking-widest text-gray-400 uppercase mb-1.5">
              Phân nhóm
            </label>
            <select
              value={form.category}
              onChange={(e) => {
                const category = e.target.value;
                setForm((f) => ({ ...f, category, code: buildCode(f.name, category) }));
              }}
              className="w-full border border-gray-200 rounded-md px-3 py-2.5 text-[13px] text-gray-800 focus:outline-none focus:border-gray-400 bg-white transition-all"
            >
              <option value="APPAREL">Quần áo (Apparel)</option>
              <option value="SHOE">Giày dép (Shoe)</option>
              <option value="KIDS">Trẻ em (Kids)</option>
              <option value="ACCESSORY">Phụ kiện (Accessory)</option>
            </select>
          </div>

          <div>
            <label className="block text-[10px] font-bold tracking-widest text-gray-400 uppercase mb-1.5">
              Mã chuẩn hóa (tự động)
            </label>
            <div className="bg-gray-50 border border-gray-100 rounded-md px-3 py-2.5">
              <code className="text-[12px] font-mono text-gray-500">{form.code || "—"}</code>
            </div>
          </div>

          <div>
            <label className="block text-[10px] font-bold tracking-widest text-gray-400 uppercase mb-1.5">
              Thứ tự hiển thị
            </label>
            <input
              type="number"
              value={form.order}
              min={0}
              onChange={(e) => setForm((f) => ({ ...f, order: Number(e.target.value) }))}
              className="w-full border border-gray-200 rounded-md px-3 py-2.5 text-[13px] text-gray-800 focus:outline-none focus:border-gray-400 transition-all"
            />
          </div>

          <div className="flex items-center justify-between pt-3 border-t border-gray-100">
            <div>
              <p className="text-[13px] font-medium text-gray-700">Kích hoạt</p>
              <p className="text-[11px] text-gray-400 mt-0.5">Hiển thị trong tùy chọn biến thể</p>
            </div>
            <Toggle checked={form.active} onChange={() => setForm((f) => ({ ...f, active: !f.active }))} />
          </div>
        </div>

        <div className="border-t border-gray-100 px-6 py-4 flex gap-2.5">
          <button onClick={onClose} className="flex-1 border border-gray-200 rounded-md py-2.5 text-[13px] font-medium text-gray-600 hover:bg-gray-50 transition-colors">
            Hủy
          </button>
          <button
            onClick={() => { onSave(form); onClose(); }}
            disabled={!form.name.trim()}
            className="flex-1 bg-[#111] text-white rounded-md py-2.5 text-[13px] font-semibold hover:bg-gray-800 disabled:opacity-40 transition-colors"
          >
            Lưu kích cỡ
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Colors Tab ───────────────────────────────────────────────────────────────
function ColorsTab({ colors, setColors }: { colors: Color[]; setColors: (fn: (p: Color[]) => Color[]) => void }) {
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<"all" | "active" | "inactive">("all");
  const [copiedId, setCopiedId] = useState<number | null>(null);
  const [page, setPage] = useState(1);
  const PER = 10;

  const filtered = colors.filter((c) => {
    const q = search.toLowerCase();
    const matchQ = c.name.toLowerCase().includes(q) || c.code.toLowerCase().includes(q) || c.hex.toLowerCase().includes(q);
    const matchS = status === "all" || (status === "active" ? c.active : !c.active);
    return matchQ && matchS;
  });

  const total = filtered.length;
  const totalPages = Math.max(1, Math.ceil(total / PER));
  const rows = filtered.slice((page - 1) * PER, page * PER);

  function copy(id: number, hex: string) {
    navigator.clipboard.writeText(hex).catch(() => {});
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 1500);
  }

  return (
    <div>
      <div className="flex items-center gap-3 mb-5 flex-wrap">
        <div className="flex items-center gap-2 bg-white border border-[rgba(0,0,0,0.08)] px-3 py-2 w-64">
          <Search size={13} className="text-gray-400 flex-shrink-0" />
          <input
            type="text"
            placeholder="Tìm tên màu, mã HEX..."
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(1); }}
            className="flex-1 bg-transparent text-[13px] text-gray-700 placeholder-gray-400 focus:outline-none"
          />
        </div>
        <div className="flex overflow-hidden border border-[rgba(0,0,0,0.08)] bg-white">
          {(["all", "active", "inactive"] as const).map((s) => (
            <button
              key={s}
              onClick={() => { setStatus(s); setPage(1); }}
              className={`px-3 py-2 text-[11px] font-semibold transition-colors ${
                status === s ? "bg-[#111] text-white" : "text-gray-500 hover:bg-gray-50"
              }`}
            >
              {s === "all" ? "Tất cả" : s === "active" ? "Đang kích hoạt" : "Tạm dừng"}
            </button>
          ))}
        </div>
        <span className="text-xs text-gray-400 ml-auto">{total} màu</span>
      </div>

      <div className="bg-white border border-[rgba(0,0,0,0.08)]">
        <table className="w-full">
          <thead>
            <tr className="border-b border-[rgba(0,0,0,0.06)] bg-gray-50/60">
              {["MÃ", "MÀU HIỂN THỊ", "MÃ HEX", "THỨ TỰ", "TRẠNG THÁI", "THAO TÁC"].map((h) => (
                <th key={h} className="text-left px-4 py-3 text-[9px] font-extrabold tracking-[0.15em] text-gray-400 uppercase">
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr>
                <td colSpan={6} className="text-center py-12 text-sm text-gray-400">Không tìm thấy kết quả</td>
              </tr>
            )}
            {rows.map((color, i) => (
              <tr key={color.id} className={`hover:bg-gray-50/50 transition-colors ${i < rows.length - 1 ? "border-b border-[rgba(0,0,0,0.04)]" : ""}`}>
                <td className="px-4 py-3.5">
                  <code className="text-[11px] font-mono bg-gray-100 text-gray-600 px-2 py-1 rounded">{color.code}</code>
                </td>
                <td className="px-4 py-3.5">
                  <div className="flex items-center gap-3">
                    <div className="w-6 h-6 rounded-full border border-gray-200 shadow-sm flex-shrink-0" style={{ backgroundColor: color.hex }} />
                    <span className="text-[13px] font-medium text-gray-800">{color.name}</span>
                  </div>
                </td>
                <td className="px-4 py-3.5">
                  <button
                    onClick={() => copy(color.id, color.hex)}
                    className="group flex items-center gap-1.5 font-mono text-[11px] bg-gray-50 border border-gray-100 px-2.5 py-1.5 hover:border-gray-300 hover:bg-white transition-all text-gray-600"
                  >
                    <span className="w-3 h-3 rounded-sm border border-gray-200" style={{ backgroundColor: color.hex }} />
                    {color.hex.toUpperCase()}
                    {copiedId === color.id
                      ? <Check size={10} className="text-emerald-500 ml-0.5" />
                      : <Copy size={10} className="text-gray-300 group-hover:text-gray-500 ml-0.5 transition-colors" />
                    }
                  </button>
                </td>
                <td className="px-4 py-3.5">
                  <span className="text-[13px] font-mono text-gray-400">{color.order}</span>
                </td>
                <td className="px-4 py-3.5">
                  <Toggle
                    checked={color.active}
                    onChange={() => setColors((prev) => prev.map((c) => c.id === color.id ? { ...c, active: !c.active } : c))}
                  />
                </td>
                <td className="px-4 py-3.5">
                  <div className="flex items-center gap-1">
                    <button className="p-1.5 hover:bg-gray-100 transition-colors text-gray-400 hover:text-gray-700">
                      <Pencil size={13} />
                    </button>
                    <button
                      onClick={() => setColors((prev) => prev.filter((c) => c.id !== color.id))}
                      className="p-1.5 hover:bg-red-50 transition-colors text-gray-400 hover:text-red-500"
                    >
                      <Trash2 size={13} />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="flex items-center justify-between px-4 py-3 border-t border-[rgba(0,0,0,0.04)]">
          <p className="text-[11px] text-gray-400">
            {total > 0 ? `Hiển thị ${(page - 1) * PER + 1}–${Math.min(page * PER, total)} / ${total} màu` : "Không có dữ liệu"}
          </p>
          <div className="flex items-center gap-1">
            <button disabled={page === 1} onClick={() => setPage((p) => p - 1)} className="w-7 h-7 flex items-center justify-center border border-gray-200 text-gray-400 hover:bg-gray-50 disabled:opacity-30 transition-colors">
              <ChevronLeft size={13} />
            </button>
            {Array.from({ length: totalPages }, (_, i) => i + 1).map((p) => (
              <button key={p} onClick={() => setPage(p)} className={`w-7 h-7 text-[11px] font-semibold transition-colors ${p === page ? "bg-[#111] text-white" : "border border-gray-200 text-gray-500 hover:bg-gray-50"}`}>
                {p}
              </button>
            ))}
            <button disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)} className="w-7 h-7 flex items-center justify-center border border-gray-200 text-gray-400 hover:bg-gray-50 disabled:opacity-30 transition-colors">
              <ChevronRight size={13} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── Sizes Tab ────────────────────────────────────────────────────────────────
function SizesTab({ sizes, setSizes }: { sizes: Size[]; setSizes: (fn: (p: Size[]) => Size[]) => void }) {
  const [search, setSearch] = useState("");
  const [cat, setCat] = useState("ALL");
  const [page, setPage] = useState(1);
  const PER = 10;

  const filtered = sizes.filter((s) => {
    const q = search.toLowerCase();
    const matchQ = s.name.toLowerCase().includes(q) || s.code.toLowerCase().includes(q);
    const matchC = cat === "ALL" || s.category === cat;
    return matchQ && matchC;
  });

  const total = filtered.length;
  const totalPages = Math.max(1, Math.ceil(total / PER));
  const rows = filtered.slice((page - 1) * PER, page * PER);

  return (
    <div>
      <div className="flex items-center gap-3 mb-5 flex-wrap">
        <div className="flex items-center gap-2 bg-white border border-[rgba(0,0,0,0.08)] px-3 py-2 w-64">
          <Search size={13} className="text-gray-400 flex-shrink-0" />
          <input
            type="text"
            placeholder="Tìm tên size, mã chuẩn..."
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(1); }}
            className="flex-1 bg-transparent text-[13px] text-gray-700 placeholder-gray-400 focus:outline-none"
          />
        </div>
        <select
          value={cat}
          onChange={(e) => { setCat(e.target.value); setPage(1); }}
          className="border border-[rgba(0,0,0,0.08)] bg-white px-3 py-2 text-[13px] text-gray-600 focus:outline-none hover:border-gray-300 transition-colors"
        >
          <option value="ALL">Tất cả phân nhóm</option>
          <option value="APPAREL">Áo / Quần (Apparel)</option>
          <option value="SHOE">Giày dép (Shoe)</option>
          <option value="KIDS">Trẻ em (Kids)</option>
          <option value="ACCESSORY">Phụ kiện (Accessory)</option>
        </select>
        <span className="text-xs text-gray-400 ml-auto">{total} kích cỡ</span>
      </div>

      <div className="bg-white border border-[rgba(0,0,0,0.08)]">
        <table className="w-full">
          <thead>
            <tr className="border-b border-[rgba(0,0,0,0.06)] bg-gray-50/60">
              {["TÊN SIZE", "MÃ CHUẨN HÓA", "PHÂN NHÓM", "THỨ TỰ", "TRẠNG THÁI", "THAO TÁC"].map((h) => (
                <th key={h} className="text-left px-4 py-3 text-[9px] font-extrabold tracking-[0.15em] text-gray-400 uppercase">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr>
                <td colSpan={6} className="text-center py-12 text-sm text-gray-400">Không tìm thấy kết quả</td>
              </tr>
            )}
            {rows.map((size, i) => (
              <tr key={size.id} className={`hover:bg-gray-50/50 transition-colors ${i < rows.length - 1 ? "border-b border-[rgba(0,0,0,0.04)]" : ""}`}>
                <td className="px-4 py-3.5">
                  <span className="text-base font-black text-gray-900">{size.name}</span>
                </td>
                <td className="px-4 py-3.5">
                  <code className="text-[11px] font-mono bg-gray-100 text-gray-600 px-2 py-1 rounded">{size.code}</code>
                </td>
                <td className="px-4 py-3.5"><CategoryPill cat={size.category} /></td>
                <td className="px-4 py-3.5">
                  <span className="text-[13px] font-mono text-gray-400">{size.order}</span>
                </td>
                <td className="px-4 py-3.5">
                  <Toggle
                    checked={size.active}
                    onChange={() => setSizes((prev) => prev.map((s) => s.id === size.id ? { ...s, active: !s.active } : s))}
                  />
                </td>
                <td className="px-4 py-3.5">
                  <div className="flex items-center gap-1">
                    <button className="p-1.5 hover:bg-gray-100 transition-colors text-gray-400 hover:text-gray-700"><Pencil size={13} /></button>
                    <button onClick={() => setSizes((prev) => prev.filter((s) => s.id !== size.id))} className="p-1.5 hover:bg-red-50 transition-colors text-gray-400 hover:text-red-500"><Trash2 size={13} /></button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="flex items-center justify-between px-4 py-3 border-t border-[rgba(0,0,0,0.04)]">
          <p className="text-[11px] text-gray-400">
            {total > 0 ? `Hiển thị ${(page - 1) * PER + 1}–${Math.min(page * PER, total)} / ${total} kích cỡ` : "Không có dữ liệu"}
          </p>
          <div className="flex items-center gap-1">
            <button disabled={page === 1} onClick={() => setPage((p) => p - 1)} className="w-7 h-7 flex items-center justify-center border border-gray-200 text-gray-400 hover:bg-gray-50 disabled:opacity-30 transition-colors"><ChevronLeft size={13} /></button>
            {Array.from({ length: totalPages }, (_, i) => i + 1).map((p) => (
              <button key={p} onClick={() => setPage(p)} className={`w-7 h-7 text-[11px] font-semibold transition-colors ${p === page ? "bg-[#111] text-white" : "border border-gray-200 text-gray-500 hover:bg-gray-50"}`}>{p}</button>
            ))}
            <button disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)} className="w-7 h-7 flex items-center justify-center border border-gray-200 text-gray-400 hover:bg-gray-50 disabled:opacity-30 transition-colors"><ChevronRight size={13} /></button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── Size Charts Tab ──────────────────────────────────────────────────────────
function SizeChartsTab() {
  return (
    <div className="space-y-4">
      <div className="bg-white border border-[rgba(0,0,0,0.08)]">
        <div className="flex items-center justify-between px-4 py-3.5 border-b border-[rgba(0,0,0,0.06)]">
          <div>
            <h4 className="text-[13px] font-bold text-gray-900">Bảng Thông Số Áo</h4>
            <p className="text-[11px] text-gray-400 mt-0.5">Phân nhóm: APPAREL · Đơn vị: cm</p>
          </div>
          <button className="flex items-center gap-1.5 text-[12px] font-semibold text-gray-600 border border-gray-200 px-3 py-1.5 hover:bg-gray-50 transition-colors">
            <Pencil size={12} /> Chỉnh sửa
          </button>
        </div>
        <table className="w-full">
          <thead>
            <tr className="border-b border-[rgba(0,0,0,0.06)] bg-gray-50/40">
              {["SIZE", "NGỰC (CM)", "EO (CM)", "HÔNG (CM)", "DÀI ÁO (CM)"].map((h) => (
                <th key={h} className="text-left px-4 py-3 text-[9px] font-extrabold tracking-[0.15em] text-gray-400 uppercase">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {SIZE_CHART.map((row, i) => (
              <tr key={row.size} className={`hover:bg-gray-50/50 transition-colors ${i < SIZE_CHART.length - 1 ? "border-b border-[rgba(0,0,0,0.04)]" : ""}`}>
                <td className="px-4 py-3.5 font-black text-sm text-gray-900">{row.size}</td>
                <td className="px-4 py-3.5 font-mono text-[12px] text-gray-500">{row.chest}</td>
                <td className="px-4 py-3.5 font-mono text-[12px] text-gray-500">{row.waist}</td>
                <td className="px-4 py-3.5 font-mono text-[12px] text-gray-500">{row.hip}</td>
                <td className="px-4 py-3.5 font-mono text-[12px] text-gray-500">{row.length}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <button className="flex items-center gap-2 text-[12px] font-semibold text-gray-500 border border-dashed border-gray-200 w-full py-3.5 justify-center hover:bg-white hover:border-gray-300 transition-colors">
        <Plus size={14} /> Thêm bảng thông số mới
      </button>
    </div>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────────────
export default function ColorsSizes() {
  const { colors, setColors, sizes, setSizes } = useAdminData();
  const [tab, setTab] = useState<ActiveTab>("colors");
  const [modal, setModal] = useState<ModalMode>(null);
  const [addOpen, setAddOpen] = useState(false);

  const TABS = [
    { id: "colors" as ActiveTab,     label: "Màu Sắc",            count: colors.length },
    { id: "sizes" as ActiveTab,      label: "Kích Cỡ",            count: sizes.length  },
    { id: "sizecharts" as ActiveTab, label: "Bảng Thông Số Size", count: null          },
  ];

  function handleAddColor(c: NewColor) {
    const code = c.name.toUpperCase().replace(/[^A-Z0-9 ]/g, "").trim().replace(/\s+/g, "_").slice(0, 12);
    setColors((prev) => [...prev, { id: Date.now(), code, name: c.name, hex: c.hex, order: c.order, active: c.active }]);
  }

  function handleAddSize(s: NewSize) {
    setSizes((prev) => [...prev, { id: Date.now(), ...s }]);
  }

  return (
    <div>
      {/* Breadcrumb */}
      <div className="flex items-center gap-1.5 mb-4">
        <span className="text-[10px] font-extrabold tracking-[0.18em] text-[#888] uppercase">Quản Lý</span>
        <ChevronRight size={11} className="text-[#ccc]" />
        <span className="text-[10px] font-extrabold tracking-[0.18em] text-[#555] uppercase">Màu &amp; Kích Cỡ</span>
      </div>

      {/* Page header */}
      <div className="flex items-start justify-between mb-6">
        <div>
          <h1 style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "2rem", letterSpacing: "-0.01em", color: "#111" }}>
            MÀU &amp; KÍCH CỠ
          </h1>
          <p className="mt-1.5 text-[13px] text-[#888] max-w-lg leading-relaxed">
            Quản lý bảng màu, hệ thống size và bảng thông số đo kích cỡ chuẩn cho các biến thể sản phẩm.
          </p>
        </div>

        {/* CTA */}
        <div className="relative flex-shrink-0">
          <div className="flex">
            <button
              onClick={() => { setModal(tab === "sizes" ? "size" : "color"); setAddOpen(false); }}
              className="flex items-center gap-1.5 bg-[#111] text-white text-[11px] font-extrabold tracking-[0.15em] px-4 py-2.5 hover:bg-gray-800 transition-colors"
            >
              <Plus size={13} /> THÊM MỚI
            </button>
            <button
              onClick={() => setAddOpen((v) => !v)}
              className="bg-[#111] text-white px-2.5 py-2.5 border-l border-white/20 hover:bg-gray-800 transition-colors"
            >
              <ChevronDown size={13} />
            </button>
          </div>
          {addOpen && (
            <div className="absolute right-0 top-full mt-1 w-48 bg-white border border-[rgba(0,0,0,0.1)] shadow-xl z-20 py-1">
              <button
                onClick={() => { setModal("color"); setAddOpen(false); }}
                className="w-full flex items-center gap-2.5 px-4 py-2.5 text-[13px] text-gray-700 hover:bg-gray-50 transition-colors"
              >
                <Palette size={14} className="text-gray-400" /> Thêm màu mới
              </button>
              <button
                onClick={() => { setModal("size"); setAddOpen(false); }}
                className="w-full flex items-center gap-2.5 px-4 py-2.5 text-[13px] text-gray-700 hover:bg-gray-50 transition-colors"
              >
                <Ruler size={14} className="text-gray-400" /> Thêm kích cỡ mới
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Alert */}
      <div className="bg-[#FEFCE8] border border-[#FDE047] px-4 py-3.5 mb-6 flex items-start gap-3">
        <AlertTriangle size={14} className="text-[#854D0E] flex-shrink-0 mt-0.5" />
        <p className="text-[12px] text-[#854D0E] leading-relaxed">
          <span className="font-bold">Quy tắc nghiệp vụ:</span> Màu sắc và Kích cỡ là các thuộc tính định danh biến thể{" "}
          <span className="font-mono bg-amber-100 px-1 py-0.5 text-[11px]">(Product Variant)</span>.
          {" "}Sau khi tạo tại đây, chúng sẽ xuất hiện trong màn hình tạo/sửa sản phẩm để sinh tổ hợp SKU và tồn kho.
        </p>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-[rgba(0,0,0,0.08)] mb-6">
        {TABS.map(({ id, label, count }) => (
          <button
            key={id}
            onClick={() => setTab(id)}
            className={`flex items-center gap-2 px-4 py-3 text-[13px] font-semibold border-b-2 -mb-px transition-all ${
              tab === id ? "border-[#111] text-[#111]" : "border-transparent text-[#888] hover:text-[#555]"
            }`}
          >
            {label}
            {count !== null && (
              <span className={`text-[9px] font-extrabold px-1.5 py-0.5 leading-none ${tab === id ? "bg-[#111] text-white" : "bg-gray-100 text-gray-400"}`}>
                {count}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Tab content */}
      {tab === "colors"     && <ColorsTab    colors={colors} setColors={setColors} />}
      {tab === "sizes"      && <SizesTab     sizes={sizes}   setSizes={setSizes}   />}
      {tab === "sizecharts" && <SizeChartsTab />}

      {/* Modals */}
      {modal === "color" && <ColorModal onClose={() => setModal(null)} onSave={handleAddColor} />}
      {modal === "size"  && <SizeModal  onClose={() => setModal(null)} onSave={handleAddSize}  />}

      {addOpen && <div className="fixed inset-0 z-10" onClick={() => setAddOpen(false)} />}
    </div>
  );
}
