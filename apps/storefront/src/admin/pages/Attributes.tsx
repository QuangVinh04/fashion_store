import { useState } from "react";
import { Plus, Pencil, Trash2, ChevronDown, ChevronRight, X, Check } from "lucide-react";
import { useAdminData, type Attribute, type AttrValue } from "../../context/AdminDataContext";

const RESERVED = ["color", "size", "màu"];

function genId() {
  return Math.random().toString(36).slice(2, 9);
}

/* ── Modal 1: Add Attribute ── */
function AddAttributeModal({
  onClose,
  onAdd,
}: {
  onClose: () => void;
  onAdd: (attr: Attribute) => void;
}) {
  const [attrName, setAttrName] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [tagInput, setTagInput] = useState("");
  const [tags, setTags] = useState<string[]>([]);
  const [nameErr, setNameErr] = useState("");

  const addTag = () => {
    const v = tagInput.trim();
    if (v && !tags.includes(v)) setTags((t) => [...t, v]);
    setTagInput("");
  };

  const validateName = (val: string) => {
    if (RESERVED.includes(val.toLowerCase())) {
      return "Không được đặt tên là Color hoặc Size — 2 thuộc tính này thuộc tab Biến Thể";
    }
    return "";
  };

  const handleSubmit = () => {
    const err = validateName(attrName);
    if (err) { setNameErr(err); return; }
    if (!attrName.trim()) { setNameErr("Tên thuộc tính là bắt buộc"); return; }

    onAdd({
      id: genId(),
      name: attrName.trim(),
      displayName: displayName.trim() || attrName.trim(),
      published: false,
      values: tags.map((t) => ({ id: genId(), value: t, displayName: t, published: false })),
    });
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ fontFamily: "'Inter', sans-serif" }}>
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-white w-full max-w-md shadow-2xl">
        <div className="flex items-center justify-between px-6 py-4 border-b border-[rgba(0,0,0,0.1)]">
          <h2 style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "1.35rem", letterSpacing: "-0.01em", color: "#111" }}>
            THÊM THUỘC TÍNH
          </h2>
          <button onClick={onClose} className="w-8 h-8 flex items-center justify-center text-[#888] hover:text-[#111] transition-colors">
            <X size={18} />
          </button>
        </div>

        <div className="p-6 space-y-4">
          <div className="p-3 bg-amber-50 border border-amber-200 text-xs text-amber-700">
            <strong>Lưu ý:</strong> Không tạo thuộc tính tên "Color" hoặc "Size" — 2 thuộc tính đó quản lý riêng trong tab <strong>Biến Thể</strong> của sản phẩm.
          </div>

          <div>
            <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">
              Tên Thuộc Tính <span className="text-[#E5001B]">*</span>
            </label>
            <input
              type="text"
              value={attrName}
              onChange={(e) => { setAttrName(e.target.value); setNameErr(validateName(e.target.value)); }}
              placeholder="Material, Origin, Style, Season..."
              className={`w-full border px-4 py-2.5 text-sm text-[#111] placeholder:text-[#bbb] focus:outline-none transition-colors ${nameErr ? "border-[#E5001B]" : "border-[rgba(0,0,0,0.15)] focus:border-[#111]"}`}
            />
            {nameErr && <p className="text-xs text-[#E5001B] mt-1">{nameErr}</p>}
          </div>

          <div>
            <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">Tên Hiển Thị</label>
            <input
              type="text"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              placeholder="Chất Liệu, Xuất Xứ, Phong Cách..."
              className="w-full border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-sm text-[#111] placeholder:text-[#bbb] focus:outline-none focus:border-[#111] transition-colors"
            />
          </div>

          <div>
            <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">
              Giá Trị Ban Đầu{" "}
              <span className="text-[#888] text-[9px] normal-case tracking-normal font-normal">— gõ rồi nhấn Enter</span>
            </label>
            <div className="border border-[rgba(0,0,0,0.15)] p-3 min-h-[52px] flex flex-wrap gap-2 items-center focus-within:border-[#111] transition-colors">
              {tags.map((t) => (
                <span key={t} className="flex items-center gap-1.5 bg-[#111] text-white text-xs px-2.5 py-1">
                  {t}
                  <button type="button" onClick={() => setTags((ts) => ts.filter((x) => x !== t))} className="text-white/50 hover:text-white">
                    <X size={10} />
                  </button>
                </span>
              ))}
              <input
                type="text"
                value={tagInput}
                onChange={(e) => setTagInput(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addTag(); } }}
                placeholder={tags.length === 0 ? "Cotton, Polyester, Vietnam... → Enter" : "Thêm giá trị..."}
                className="flex-1 min-w-[150px] text-sm text-[#111] placeholder:text-[#bbb] focus:outline-none bg-transparent"
              />
            </div>
            <p className="text-[11px] text-[#aaa] mt-1">Gọi POST /admin/attributes · displayName và published từng value sẽ chỉnh sau</p>
          </div>
        </div>

        <div className="flex items-center justify-between px-6 py-4 border-t border-[rgba(0,0,0,0.1)] bg-[#fafafa]">
          <button onClick={onClose} className="border border-[rgba(0,0,0,0.15)] px-5 py-2.5 text-xs uppercase tracking-widest text-[#555] hover:border-[#111] transition-colors">
            Hủy
          </button>
          <button
            onClick={handleSubmit}
            className="bg-[#111] text-white px-6 py-2.5 text-xs uppercase tracking-widest font-medium hover:bg-[#E5001B] transition-colors"
          >
            Thêm Thuộc Tính
          </button>
        </div>
      </div>
    </div>
  );
}

/* ── Modal 2: Add / Edit single Value ── */
function ValueModal({
  attributeId,
  value,
  onClose,
  onSave,
}: {
  attributeId: string;
  value: AttrValue | null; // null = add new
  onClose: () => void;
  onSave: (v: AttrValue) => void;
}) {
  const isEdit = !!value;
  const [valText, setValText] = useState(value?.value ?? "");
  const [displayName, setDisplayName] = useState(value?.displayName ?? "");
  const [published, setPublished] = useState(value?.published ?? false);
  const [saving, setSaving] = useState(false);

  const handleSave = async () => {
    if (!valText.trim()) return;
    setSaving(true);
    // Simulate POST /admin/attributes/{attributeId}/values
    // or PUT /admin/attributes/{attributeId}/values/{valueId}
    await new Promise((r) => setTimeout(r, 500));
    onSave({
      id: value?.id ?? genId(),
      value: valText.trim(),
      displayName: displayName.trim() || valText.trim(),
      published,
    });
    setSaving(false);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ fontFamily: "'Inter', sans-serif" }}>
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-white w-full max-w-sm shadow-2xl">
        <div className="flex items-center justify-between px-5 py-4 border-b border-[rgba(0,0,0,0.1)]">
          <h2 style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "1.2rem", letterSpacing: "-0.01em", color: "#111" }}>
            {isEdit ? "SỬA GIÁ TRỊ" : "THÊM GIÁ TRỊ"}
          </h2>
          <button onClick={onClose} className="w-7 h-7 flex items-center justify-center text-[#888] hover:text-[#111] transition-colors">
            <X size={16} />
          </button>
        </div>

        <div className="p-5 space-y-4">
          <p className="text-[11px] text-[#aaa]">
            {isEdit
              ? `PUT /admin/attributes/${attributeId}/values/${value?.id}`
              : `POST /admin/attributes/${attributeId}/values`}
          </p>

          <div>
            <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">
              Giá Trị (value) <span className="text-[#E5001B]">*</span>
            </label>
            <input
              type="text"
              value={valText}
              onChange={(e) => setValText(e.target.value)}
              placeholder="Cotton, Vietnam, Casual..."
              className="w-full border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-sm text-[#111] placeholder:text-[#bbb] focus:outline-none focus:border-[#111] transition-colors"
            />
          </div>

          <div>
            <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-1.5">Tên Hiển Thị</label>
            <input
              type="text"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              placeholder="Cotton 100%, Việt Nam, Thường Ngày..."
              className="w-full border border-[rgba(0,0,0,0.15)] px-4 py-2.5 text-sm text-[#111] placeholder:text-[#bbb] focus:outline-none focus:border-[#111] transition-colors"
            />
          </div>

          <div>
            <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#111] mb-2">Trạng Thái Published</label>
            <div className="flex gap-4">
              {[true, false].map((val) => (
                <label key={String(val)} className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="radio"
                    name="published"
                    checked={published === val}
                    onChange={() => setPublished(val)}
                    className="accent-[#111]"
                  />
                  <span className="text-sm text-[#555]">{val ? "Có" : "Không"}</span>
                </label>
              ))}
            </div>
          </div>
        </div>

        <div className="flex items-center justify-between px-5 py-4 border-t border-[rgba(0,0,0,0.1)] bg-[#fafafa]">
          <button onClick={onClose} className="border border-[rgba(0,0,0,0.15)] px-4 py-2 text-xs uppercase tracking-widest text-[#555] hover:border-[#111] transition-colors">
            Hủy
          </button>
          <button
            onClick={handleSave}
            disabled={saving || !valText.trim()}
            className="flex items-center gap-2 bg-[#111] text-white px-5 py-2 text-xs uppercase tracking-widest font-medium hover:bg-[#E5001B] transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {saving ? <span className="w-3 h-3 border-2 border-white/30 border-t-white rounded-full animate-spin" /> : null}
            {isEdit ? "Lưu Thay Đổi" : "Thêm Giá Trị"}
          </button>
        </div>
      </div>
    </div>
  );
}

export default function Attributes() {
  const { attrs, setAttrs } = useAdminData();
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [addAttrOpen, setAddAttrOpen] = useState(false);
  const [valueModal, setValueModal] = useState<{ attrId: string; value: AttrValue | null } | null>(null);
  const [deleteAttrConfirm, setDeleteAttrConfirm] = useState<string | null>(null);

  const toggleExpand = (id: string) => {
    setExpanded((s) => {
      const n = new Set(s);
      n.has(id) ? n.delete(id) : n.add(id);
      return n;
    });
  };

  const toggleAttrPublished = (id: string) => {
    setAttrs((as) => as.map((a) => (a.id === id ? { ...a, published: !a.published } : a)));
  };

  const toggleValuePublished = (attrId: string, valueId: string) => {
    setAttrs((as) =>
      as.map((a) =>
        a.id === attrId
          ? { ...a, values: a.values.map((v) => (v.id === valueId ? { ...v, published: !v.published } : v)) }
          : a
      )
    );
  };

  const handleAddAttr = (attr: Attribute) => {
    setAttrs((as) => [...as, attr]);
  };

  const handleSaveValue = (attrId: string, val: AttrValue) => {
    setAttrs((as) =>
      as.map((a) => {
        if (a.id !== attrId) return a;
        const exists = a.values.find((v) => v.id === val.id);
        return {
          ...a,
          values: exists ? a.values.map((v) => (v.id === val.id ? val : v)) : [...a.values, val],
        };
      })
    );
  };

  const handleDeleteValue = (attrId: string, valueId: string) => {
    setAttrs((as) =>
      as.map((a) => (a.id === attrId ? { ...a, values: a.values.filter((v) => v.id !== valueId) } : a))
    );
  };

  const handleDeleteAttr = (id: string) => {
    setAttrs((as) => as.filter((a) => a.id !== id));
    setDeleteAttrConfirm(null);
  };

  return (
    <div style={{ fontFamily: "'Inter', sans-serif" }}>
      {/* Header */}
      <div className="flex items-start justify-between gap-4 mb-6">
        <div>
          <p className="text-xs uppercase tracking-widest text-[#888] mb-1">Quản Lý</p>
          <h1
            style={{
              fontFamily: "'Barlow Condensed', sans-serif",
              fontWeight: 900,
              fontSize: "2rem",
              letterSpacing: "-0.01em",
              color: "#111",
            }}
          >
            THUỘC TÍNH
          </h1>
          <p className="text-sm text-[#888] mt-1">
            Master data mô tả sản phẩm (Chất liệu, Xuất xứ, Phong cách...) — <strong>không</strong> bao gồm Color và Size
          </p>
        </div>
        <button
          onClick={() => setAddAttrOpen(true)}
          className="flex items-center gap-2 bg-[#111] text-white px-5 py-2.5 text-xs uppercase tracking-widest font-medium hover:bg-[#E5001B] transition-colors flex-shrink-0"
        >
          <Plus size={14} /> Thêm Thuộc Tính
        </button>
      </div>

      {/* Notice */}
      <div className="mb-5 p-4 border border-amber-200 bg-amber-50 text-xs text-amber-800">
        <strong>Quy tắc nghiệp vụ:</strong> Attributes ở đây chỉ phục vụ AI dự đoán và search nội bộ — không hiển thị ra trang khách hàng.
        Tuyệt đối không tạo attribute tên <code className="font-mono bg-amber-100 px-1">"Color"</code> hay{" "}
        <code className="font-mono bg-amber-100 px-1">"Size"</code> — 2 thuộc tính đó thuộc tab Biến Thể của từng sản phẩm.
      </div>

      {/* Table */}
      <div className="bg-white border border-[rgba(0,0,0,0.08)]">
        {/* Table Header */}
        <div className="grid grid-cols-[40px_1fr_1fr_100px_100px] items-center px-4 py-2.5 border-b border-[rgba(0,0,0,0.08)] bg-[#f8f8f8]">
          <span />
          <span className="text-[10px] uppercase tracking-widest text-[#888] font-semibold">Tên (code)</span>
          <span className="text-[10px] uppercase tracking-widest text-[#888] font-semibold">Tên Hiển Thị</span>
          <span className="text-[10px] uppercase tracking-widest text-[#888] font-semibold text-center">Published</span>
          <span className="text-[10px] uppercase tracking-widest text-[#888] font-semibold text-center">Thao Tác</span>
        </div>

        <div className="divide-y divide-[rgba(0,0,0,0.04)]">
          {attrs.map((attr) => (
            <div key={attr.id}>
              {/* Attribute Row */}
              <div className="grid grid-cols-[40px_1fr_1fr_100px_100px] items-center px-4 py-3.5 hover:bg-[#fafafa] transition-colors">
                <button
                  onClick={() => toggleExpand(attr.id)}
                  className="w-7 h-7 flex items-center justify-center text-[#888] hover:text-[#111] transition-colors"
                >
                  {expanded.has(attr.id) ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
                </button>

                <div>
                  <p className="text-sm font-semibold text-[#111]">{attr.name}</p>
                  <p className="text-[11px] font-mono text-[#aaa] mt-0.5">{attr.id}</p>
                </div>

                <p className="text-sm text-[#555]">{attr.displayName}</p>

                <div className="flex justify-center">
                  <button
                    onClick={() => toggleAttrPublished(attr.id)}
                    className={`w-9 h-5 relative transition-colors duration-200 ${attr.published ? "bg-[#111]" : "bg-[#d4d4d4]"}`}
                  >
                    <span className={`absolute top-0.5 w-4 h-4 bg-white transition-transform duration-200 ${attr.published ? "translate-x-4" : "translate-x-0.5"}`} />
                  </button>
                </div>

                <div className="flex items-center justify-center gap-1">
                  <button
                    onClick={() => toggleExpand(attr.id)}
                    className="w-8 h-8 flex items-center justify-center text-[#888] hover:text-[#111] hover:bg-[#f5f5f5] transition-colors"
                    title="Xem giá trị"
                  >
                    <Pencil size={14} />
                  </button>
                  <button
                    onClick={() => setDeleteAttrConfirm(attr.id)}
                    className="w-8 h-8 flex items-center justify-center text-[#888] hover:text-[#E5001B] hover:bg-red-50 transition-colors"
                    title="Xóa thuộc tính"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>

              {/* Values Panel */}
              {expanded.has(attr.id) && (
                <div className="bg-[#f8f8f8] border-t border-[rgba(0,0,0,0.06)] px-10 py-4">
                  <div className="flex items-center justify-between mb-3">
                    <p className="text-[10px] uppercase tracking-widest text-[#888] font-semibold">
                      Giá Trị ({attr.values.length})
                      <span className="ml-2 text-[#bbb] font-normal normal-case tracking-normal">
                        · Mỗi value: POST /admin/attributes/{attr.id}/values hoặc PUT .../{attr.id}/values/{"{valueId}"}
                      </span>
                    </p>
                    <button
                      onClick={() => setValueModal({ attrId: attr.id, value: null })}
                      className="flex items-center gap-1.5 border border-[rgba(0,0,0,0.15)] px-3 py-1.5 text-[10px] uppercase tracking-widest text-[#555] hover:border-[#111] hover:text-[#111] transition-colors"
                    >
                      <Plus size={11} /> Thêm Giá Trị
                    </button>
                  </div>

                  {attr.values.length === 0 ? (
                    <p className="text-xs text-[#bbb] py-2">Chưa có giá trị nào. Bấm Thêm Giá Trị để bắt đầu.</p>
                  ) : (
                    <div className="space-y-1.5">
                      {attr.values.map((v) => (
                        <div
                          key={v.id}
                          className="grid grid-cols-[1fr_1fr_80px_80px] items-center bg-white border border-[rgba(0,0,0,0.08)] px-4 py-2.5"
                        >
                          <div>
                            <p className="text-xs font-semibold text-[#111]">{v.value}</p>
                            <p className="text-[11px] font-mono text-[#ccc] mt-0.5">{v.id}</p>
                          </div>
                          <p className="text-xs text-[#555]">{v.displayName}</p>

                          <div className="flex justify-center">
                            <button
                              onClick={() => toggleValuePublished(attr.id, v.id)}
                              title={v.published ? "Đang hiển thị" : "Đang ẩn"}
                              className={`flex items-center gap-1 text-[10px] px-2 py-1 border font-medium transition-colors ${
                                v.published
                                  ? "bg-green-50 text-[#2D5A3D] border-green-200"
                                  : "bg-[#f5f5f5] text-[#888] border-[rgba(0,0,0,0.1)]"
                              }`}
                            >
                              {v.published ? <Check size={9} /> : null}
                              {v.published ? "Có" : "Không"}
                            </button>
                          </div>

                          <div className="flex items-center justify-end gap-1">
                            <button
                              onClick={() => setValueModal({ attrId: attr.id, value: v })}
                              className="w-7 h-7 flex items-center justify-center text-[#888] hover:text-[#111] hover:bg-[#f5f5f5] transition-colors"
                              title="Sửa giá trị"
                            >
                              <Pencil size={12} />
                            </button>
                            <button
                              onClick={() => handleDeleteValue(attr.id, v.id)}
                              className="w-7 h-7 flex items-center justify-center text-[#bbb] hover:text-[#E5001B] hover:bg-red-50 transition-colors"
                              title="Xóa giá trị"
                            >
                              <Trash2 size={12} />
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>

        {attrs.length === 0 && (
          <div className="text-center py-16">
            <p className="text-sm text-[#888]">Chưa có thuộc tính nào. Bấm Thêm Thuộc Tính để bắt đầu.</p>
          </div>
        )}
      </div>

      {/* Delete Attribute Confirm */}
      {deleteAttrConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ fontFamily: "'Inter', sans-serif" }}>
          <div className="absolute inset-0 bg-black/50" onClick={() => setDeleteAttrConfirm(null)} />
          <div className="relative bg-white p-6 max-w-sm w-full shadow-xl">
            <h3
              className="text-[#111] mb-2"
              style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "1.2rem" }}
            >
              XÁC NHẬN XÓA
            </h3>
            <p className="text-sm text-[#555] mb-5">
              Xóa thuộc tính này sẽ xóa toàn bộ giá trị và liên kết với sản phẩm.
            </p>
            <div className="flex gap-3">
              <button
                onClick={() => setDeleteAttrConfirm(null)}
                className="flex-1 border border-[rgba(0,0,0,0.15)] py-2.5 text-xs uppercase tracking-widest text-[#555] hover:border-[#111] transition-colors"
              >
                Hủy
              </button>
              <button
                onClick={() => handleDeleteAttr(deleteAttrConfirm)}
                className="flex-1 bg-[#E5001B] text-white py-2.5 text-xs uppercase tracking-widest font-medium hover:bg-[#c00018] transition-colors"
              >
                Xóa
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add Attribute Modal */}
      {addAttrOpen && (
        <AddAttributeModal onClose={() => setAddAttrOpen(false)} onAdd={handleAddAttr} />
      )}

      {/* Add/Edit Value Modal */}
      {valueModal && (
        <ValueModal
          attributeId={valueModal.attrId}
          value={valueModal.value}
          onClose={() => setValueModal(null)}
          onSave={(v) => handleSaveValue(valueModal.attrId, v)}
        />
      )}
    </div>
  );
}
