import { useState } from "react";
import { Plus, Edit2, Trash2, Check, X, ChevronDown } from "lucide-react";
import { useAuth, type Address } from "../context/AuthContext";
import ProfileLayout from "../components/ProfileLayout";

const PROVINCES = ["Hà Nội", "Hồ Chí Minh", "Đà Nẵng", "Hải Phòng", "Cần Thơ", "An Giang", "Bình Dương", "Đồng Nai", "Khánh Hòa", "Lâm Đồng"];

const EMPTY_FORM = { fullName: "", phone: "", address: "", ward: "", district: "", province: "", isDefault: false };

function AddressForm({
  initial,
  onSave,
  onCancel,
}: {
  initial?: Partial<Address>;
  onSave: (data: Omit<Address, "id">) => void;
  onCancel: () => void;
}) {
  const [form, setForm] = useState({ ...EMPTY_FORM, ...initial });
  const [errors, setErrors] = useState<Record<string, string>>({});

  const set = (key: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setForm((f) => ({ ...f, [key]: e.target.value }));
    setErrors((er) => ({ ...er, [key]: "" }));
  };

  const validate = () => {
    const e: Record<string, string> = {};
    if (!form.fullName.trim()) e.fullName = "Bắt buộc";
    if (!form.phone.match(/^[0-9]{9,11}$/)) e.phone = "Không hợp lệ";
    if (!form.address.trim()) e.address = "Bắt buộc";
    if (!form.ward.trim()) e.ward = "Bắt buộc";
    if (!form.district.trim()) e.district = "Bắt buộc";
    if (!form.province) e.province = "Bắt buộc";
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    onSave(form);
  };

  return (
    <form onSubmit={handleSubmit} className="border border-[rgba(0,0,0,0.12)] p-6 bg-[#fafafa] space-y-4">
      <div className="grid sm:grid-cols-2 gap-4">
        {[
          { key: "fullName", label: "Họ Tên", placeholder: "Nguyễn Văn An" },
          { key: "phone", label: "Số Điện Thoại", placeholder: "0901 234 567" },
        ].map(({ key, label, placeholder }) => (
          <div key={key} className="flex flex-col gap-1.5">
            <label className="text-xs font-semibold uppercase tracking-widest text-[#111]">{label} <span className="text-[#E5001B]">*</span></label>
            <input
              value={form[key as keyof typeof form] as string}
              onChange={set(key)}
              placeholder={placeholder}
              className={`border px-4 py-3 text-sm text-[#111] placeholder:text-[#aaa] focus:outline-none transition-colors ${errors[key] ? "border-[#E5001B]" : "border-[rgba(0,0,0,0.15)] focus:border-[#111]"}`}
            />
            {errors[key] && <p className="text-[10px] text-[#E5001B]">{errors[key]}</p>}
          </div>
        ))}
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="text-xs font-semibold uppercase tracking-widest text-[#111]">Địa Chỉ <span className="text-[#E5001B]">*</span></label>
        <input
          value={form.address}
          onChange={set("address")}
          placeholder="Số nhà, tên đường"
          className={`border px-4 py-3 text-sm text-[#111] placeholder:text-[#aaa] focus:outline-none transition-colors ${errors.address ? "border-[#E5001B]" : "border-[rgba(0,0,0,0.15)] focus:border-[#111]"}`}
        />
        {errors.address && <p className="text-[10px] text-[#E5001B]">{errors.address}</p>}
      </div>

      <div className="grid sm:grid-cols-3 gap-4">
        {[
          { key: "ward", label: "Phường/Xã", placeholder: "Phường Bến Nghé" },
          { key: "district", label: "Quận/Huyện", placeholder: "Quận 1" },
        ].map(({ key, label, placeholder }) => (
          <div key={key} className="flex flex-col gap-1.5">
            <label className="text-xs font-semibold uppercase tracking-widest text-[#111]">{label} <span className="text-[#E5001B]">*</span></label>
            <input
              value={form[key as keyof typeof form] as string}
              onChange={set(key)}
              placeholder={placeholder}
              className={`border px-4 py-3 text-sm text-[#111] placeholder:text-[#aaa] focus:outline-none transition-colors ${errors[key] ? "border-[#E5001B]" : "border-[rgba(0,0,0,0.15)] focus:border-[#111]"}`}
            />
            {errors[key] && <p className="text-[10px] text-[#E5001B]">{errors[key]}</p>}
          </div>
        ))}
        <div className="flex flex-col gap-1.5">
          <label className="text-xs font-semibold uppercase tracking-widest text-[#111]">Tỉnh/Thành <span className="text-[#E5001B]">*</span></label>
          <div className="relative">
            <select
              value={form.province}
              onChange={set("province")}
              className={`w-full border px-4 py-3 text-sm text-[#111] bg-white appearance-none focus:outline-none transition-colors ${errors.province ? "border-[#E5001B]" : "border-[rgba(0,0,0,0.15)] focus:border-[#111]"}`}
            >
              <option value="">Chọn tỉnh</option>
              {PROVINCES.map((p) => <option key={p}>{p}</option>)}
            </select>
            <ChevronDown size={13} className="absolute right-3 top-1/2 -translate-y-1/2 text-[#888] pointer-events-none" />
          </div>
          {errors.province && <p className="text-[10px] text-[#E5001B]">{errors.province}</p>}
        </div>
      </div>

      <label className="flex items-center gap-3 cursor-pointer">
        <input
          type="checkbox"
          checked={form.isDefault}
          onChange={(e) => setForm((f) => ({ ...f, isDefault: e.target.checked }))}
          className="w-4 h-4 accent-[#111]"
        />
        <span className="text-sm text-[#555]">Đặt làm địa chỉ mặc định</span>
      </label>

      <div className="flex items-center gap-3 pt-2">
        <button type="submit" className="flex items-center gap-2 bg-[#111] text-white px-6 py-3 text-xs tracking-widest uppercase font-medium hover:bg-[#E5001B] transition-colors">
          <Check size={13} /> Lưu Địa Chỉ
        </button>
        <button type="button" onClick={onCancel} className="flex items-center gap-2 border border-[rgba(0,0,0,0.15)] text-[#555] px-6 py-3 text-xs tracking-widest uppercase font-medium hover:border-[#111] hover:text-[#111] transition-colors">
          <X size={13} /> Hủy
        </button>
      </div>
    </form>
  );
}

export default function Addresses() {
  const { user, addAddress, updateAddress, removeAddress, setDefaultAddress } = useAuth();
  const [showAdd, setShowAdd] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const handleAdd = (data: Omit<Address, "id">) => {
    addAddress(data);
    setShowAdd(false);
  };

  const handleEdit = (id: string, data: Omit<Address, "id">) => {
    updateAddress(id, data);
    setEditId(null);
  };

  return (
    <ProfileLayout>
      <div>
        <div className="flex items-center justify-between mb-8">
          <h1
            className="text-[#111]"
            style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "clamp(1.75rem, 3vw, 2.5rem)", letterSpacing: "-0.01em", lineHeight: 1 }}
          >
            QUẢN LÝ ĐỊA CHỈ
          </h1>
          <button
            onClick={() => { setShowAdd(true); setEditId(null); }}
            className="flex items-center gap-2 bg-[#111] text-white px-5 py-3 text-xs tracking-widest uppercase font-medium hover:bg-[#E5001B] transition-colors"
          >
            <Plus size={14} /> Thêm Địa Chỉ
          </button>
        </div>

        {showAdd && (
          <div className="mb-6">
            <p className="text-xs font-semibold uppercase tracking-widest text-[#888] mb-4">Địa Chỉ Mới</p>
            <AddressForm onSave={handleAdd} onCancel={() => setShowAdd(false)} />
          </div>
        )}

        {user?.addresses.length === 0 && !showAdd && (
          <div className="text-center py-16 border border-dashed border-[rgba(0,0,0,0.15)]">
            <p className="text-[#888] text-sm mb-4">Chưa có địa chỉ nào được lưu.</p>
            <button onClick={() => setShowAdd(true)} className="text-xs uppercase tracking-widest text-[#111] underline hover:text-[#E5001B] transition-colors">
              Thêm địa chỉ ngay
            </button>
          </div>
        )}

        <div className="space-y-4">
          {user?.addresses.map((addr) => (
            <div key={addr.id}>
              {editId === addr.id ? (
                <div>
                  <p className="text-xs font-semibold uppercase tracking-widest text-[#888] mb-4">Chỉnh Sửa Địa Chỉ</p>
                  <AddressForm
                    initial={addr}
                    onSave={(data) => handleEdit(addr.id, data)}
                    onCancel={() => setEditId(null)}
                  />
                </div>
              ) : (
                <div className={`border p-5 transition-colors ${addr.isDefault ? "border-[#111] bg-[#fafafa]" : "border-[rgba(0,0,0,0.12)]"}`}>
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-3 mb-2 flex-wrap">
                        <p className="text-sm font-semibold text-[#111]">{addr.fullName}</p>
                        <span className="text-[#aaa]">|</span>
                        <p className="text-sm text-[#555]">{addr.phone}</p>
                        {addr.isDefault && (
                          <span className="text-[10px] uppercase tracking-widest bg-[#111] text-white px-2 py-0.5">
                            Mặc Định
                          </span>
                        )}
                      </div>
                      <p className="text-sm text-[#555]">{addr.address}</p>
                      <p className="text-sm text-[#555]">{addr.ward}, {addr.district}, {addr.province}</p>
                    </div>
                    <div className="flex items-center gap-2 flex-shrink-0">
                      <button
                        onClick={() => { setEditId(addr.id); setShowAdd(false); }}
                        className="w-8 h-8 flex items-center justify-center text-[#888] hover:text-[#111] transition-colors border border-[rgba(0,0,0,0.12)] hover:border-[#111]"
                        aria-label="Chỉnh sửa"
                      >
                        <Edit2 size={13} />
                      </button>
                      {!addr.isDefault && (
                        <button
                          onClick={() => setDeletingId(addr.id)}
                          className="w-8 h-8 flex items-center justify-center text-[#888] hover:text-[#E5001B] transition-colors border border-[rgba(0,0,0,0.12)] hover:border-[#E5001B]"
                          aria-label="Xóa"
                        >
                          <Trash2 size={13} />
                        </button>
                      )}
                    </div>
                  </div>

                  {deletingId === addr.id && (
                    <div className="mt-4 pt-4 border-t border-[rgba(0,0,0,0.08)] flex items-center gap-3">
                      <p className="text-xs text-[#555] flex-1">Xác nhận xóa địa chỉ này?</p>
                      <button onClick={() => { removeAddress(addr.id); setDeletingId(null); }} className="text-xs uppercase tracking-widest text-[#E5001B] font-medium hover:underline">Xóa</button>
                      <button onClick={() => setDeletingId(null)} className="text-xs uppercase tracking-widest text-[#888] font-medium hover:text-[#111] transition-colors">Hủy</button>
                    </div>
                  )}

                  {!addr.isDefault && deletingId !== addr.id && (
                    <button
                      onClick={() => setDefaultAddress(addr.id)}
                      className="mt-3 text-xs uppercase tracking-widest text-[#888] hover:text-[#111] transition-colors border-b border-transparent hover:border-[#888] pb-0.5"
                    >
                      Đặt Làm Mặc Định
                    </button>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </ProfileLayout>
  );
}
