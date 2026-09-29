import { useEffect, useState } from 'react';
import { Plus, Edit2, Trash2, CheckCircle2, AlertCircle, MapPin } from 'lucide-react';
import ProfileLayout from '../components/ProfileLayout';
import { PageTitle, Status, useLoad } from '../components/StoreUI';
import { store } from '../api/store';
import type { Address } from '../api/types';

type Location = { code: string; name: string };
type Form = Omit<Address, 'id' | 'fullAddress'>;

const emptyForm: Form = {
  recipientName: '',
  phone: '',
  province: '',
  ward: '',
  detailAddress: '',
  provinceId: undefined,
  wardId: undefined,
  isDefault: false,
};

export default function AddressesPage() {
  const addressesLoad = useLoad(store.addresses, []);
  const provincesLoad = useLoad(store.provinces, []);

  const [wards, setWards] = useState<Location[]>([]);

  const [provinceCode, setProvinceCode] = useState<string>('');
  const [wardId, setWardId] = useState<string>('');

  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<Form>(emptyForm);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // GHN wards belong directly to a province.
  useEffect(() => {
    if (!provinceCode) {
      setWards([]);
      setWardId('');
      return;
    }
    let active = true;
    store
      .wards(provinceCode)
      .then((rows) => {
        if (active) setWards(rows);
      })
      .catch((err) => {
        if (active) setMessage({ type: 'error', text: (err as Error).message });
      });
    return () => {
      active = false;
    };
  }, [provinceCode]);

  function resetForm() {
    setEditingId(null);
    setForm(emptyForm);
    setProvinceCode('');
    setWardId('');
    setWards([]);
  }

  function handleEdit(address: Address) {
    setEditingId(address.id);
    const prov = provincesLoad.data?.find((p) => Number(p.code) === address.provinceId);
    setProvinceCode(prov?.code || '');
    setWardId(address.wardId ? String(address.wardId) : '');

    setForm({
      recipientName: address.recipientName,
      phone: address.phone,
      province: address.province,
      ward: address.ward,
      detailAddress: address.detailAddress,
      provinceId: address.provinceId,
      wardId: address.wardId,
      isDefault: address.isDefault,
    });

    if (!prov) {
      setMessage({
        type: 'error',
        text: 'Tỉnh/Thành phố trong địa chỉ cũ cần được chọn lại từ danh mục GHN.',
      });
    } else {
      setMessage(null);
    }
  }

  async function handleSave(e: React.FormEvent) {
    e.preventDefault();
    if (!provinceCode || !wardId) {
      setMessage({
        type: 'error',
        text: 'Vui lòng chọn tỉnh/thành phố và phường/xã từ danh mục GHN.',
      });
      return;
    }

    const selectedProv = provincesLoad.data?.find((p) => p.code === provinceCode);
    const selectedWard = wards.find((w) => w.code === wardId);
    if (!selectedProv || !selectedWard) return;

    const payload: Form = {
      ...form,
      province: selectedProv?.name || form.province,
      district: undefined,
      ward: selectedWard.name,
      provinceId: Number(provinceCode),
      wardId: Number(wardId),
      districtId: undefined,
      wardCode: undefined,
    };

    setBusy(true);
    setMessage(null);
    try {
      await store.saveAddress(payload, editingId || undefined);
      await addressesLoad.refresh();
      setMessage({
        type: 'success',
        text: editingId ? 'Cập nhật địa chỉ thành công.' : 'Thêm địa chỉ giao hàng mới thành công.',
      });
      resetForm();
    } catch (err) {
      setMessage({ type: 'error', text: (err as Error).message });
    } finally {
      setBusy(false);
    }
  }

  async function handleSetDefault(id: string) {
    setBusy(true);
    setMessage(null);
    try {
      await store.defaultAddress(id);
      await addressesLoad.refresh();
      setMessage({ type: 'success', text: 'Đã đặt làm địa chỉ mặc định.' });
    } catch (err) {
      setMessage({ type: 'error', text: (err as Error).message });
    } finally {
      setBusy(false);
    }
  }

  async function handleDelete(id: string) {
    if (!window.confirm('Bạn có chắc chắn muốn xóa địa chỉ này?')) return;
    setBusy(true);
    setMessage(null);
    try {
      await store.deleteAddress(id);
      await addressesLoad.refresh();
      setMessage({ type: 'success', text: 'Đã xóa địa chỉ thành công.' });
    } catch (err) {
      setMessage({ type: 'error', text: (err as Error).message });
    } finally {
      setBusy(false);
    }
  }

  return (
    <ProfileLayout>
      <div style={{ fontFamily: "'Inter', sans-serif" }} className="w-full max-w-3xl">
        <PageTitle eyebrow="Tài Khoản">ĐỊA CHỈ GIAO HÀNG</PageTitle>

        <Status
          loading={addressesLoad.loading}
          error={addressesLoad.error}
          retry={() => void addressesLoad.refresh()}
        />

        {message && (
          <div
            className={`mb-6 p-4 text-xs font-medium flex items-center gap-2.5 border ${
              message.type === 'success'
                ? 'bg-emerald-50 text-emerald-900 border-emerald-200'
                : 'bg-red-50 text-red-900 border-red-200'
            }`}
          >
            {message.type === 'success' ? (
              <CheckCircle2 size={16} className="text-emerald-700 shrink-0" />
            ) : (
              <AlertCircle size={16} className="text-red-700 shrink-0" />
            )}
            <span>{message.text}</span>
          </div>
        )}

        {/* Existing Addresses List */}
        {addressesLoad.data && addressesLoad.data.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-10">
            {addressesLoad.data.map((addr) => {
              const isMissingGhn = !addr.provinceId || !addr.wardId;
              return (
                <article
                  key={addr.id}
                  className={`p-5 border bg-white flex flex-col justify-between ${
                    addr.isDefault ? 'border-[#111] shadow-xs' : 'border-[#ddd]'
                  }`}
                >
                  <div>
                    <div className="flex items-center justify-between gap-2 mb-2">
                      <strong className="text-sm text-[#111] font-bold">{addr.recipientName}</strong>
                      {addr.isDefault && (
                        <span className="text-[10px] uppercase font-bold tracking-wider text-[#2D5A3D] bg-emerald-50 px-2 py-0.5 border border-emerald-200">
                          Mặc định
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-[#555] mb-1">
                      <strong>Điện thoại:</strong> {addr.phone}
                    </p>
                    <p className="text-xs text-[#555] leading-relaxed">
                      {addr.fullAddress ||
                        [addr.detailAddress, addr.ward, addr.district, addr.province].filter(Boolean).join(', ')}
                    </p>

                    {isMissingGhn && (
                      <p className="text-[11px] text-[#E5001B] font-semibold mt-2">
                        * Cần chọn lại tỉnh và phường/xã theo danh mục GHN mới.
                      </p>
                    )}
                  </div>

                  <div className="mt-4 pt-3 border-t border-[rgba(0,0,0,0.06)] flex items-center justify-between text-xs font-semibold uppercase tracking-wider">
                    <button
                      type="button"
                      onClick={() => handleEdit(addr)}
                      className="text-[#111] hover:text-[#E5001B] inline-flex items-center gap-1 underline underline-offset-4"
                    >
                      <Edit2 size={12} /> Sửa
                    </button>

                    <div className="flex items-center gap-3">
                      {!addr.isDefault && (
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => void handleSetDefault(addr.id)}
                          className="text-[#555] hover:text-[#111] underline underline-offset-4"
                        >
                          Đặt mặc định
                        </button>
                      )}
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => void handleDelete(addr.id)}
                        className="text-rose-700 hover:text-rose-900 inline-flex items-center gap-1"
                      >
                        <Trash2 size={12} /> Xóa
                      </button>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}

        {/* Add / Edit Form */}
        <form
          onSubmit={handleSave}
          className="border border-[rgba(0,0,0,0.08)] p-6 md:p-8 bg-white space-y-5"
        >
          <div className="flex items-center justify-between pb-3 border-b border-[rgba(0,0,0,0.06)]">
            <h2 className="text-xs uppercase font-bold tracking-widest text-[#111] flex items-center gap-2">
              <MapPin size={15} />
              {editingId ? 'Cập Nhật Địa Chỉ Giao Hàng' : 'Thêm Địa Chỉ Giao Hàng Mới'}
            </h2>
            {editingId && (
              <button
                type="button"
                onClick={resetForm}
                className="text-xs uppercase tracking-wider text-[#888] hover:text-[#111] underline"
              >
                Hủy Chỉnh Sửa
              </button>
            )}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label
                htmlFor="recipientName"
                className="block text-xs uppercase font-bold tracking-widest text-[#111] mb-1.5"
              >
                Tên Người Nhận <span className="text-[#E5001B]">*</span>
              </label>
              <input
                id="recipientName"
                required
                type="text"
                value={form.recipientName}
                onChange={(e) => setForm({ ...form, recipientName: e.target.value })}
                placeholder="Nguyễn Văn A"
                className="w-full border border-[#ddd] px-4 py-2.5 text-xs text-[#111] outline-none focus:border-[#111]"
              />
            </div>

            <div>
              <label
                htmlFor="phone"
                className="block text-xs uppercase font-bold tracking-widest text-[#111] mb-1.5"
              >
                Số Điện Thoại <span className="text-[#E5001B]">*</span>
              </label>
              <input
                id="phone"
                required
                type="tel"
                value={form.phone}
                onChange={(e) => setForm({ ...form, phone: e.target.value })}
                placeholder="0912345678"
                className="w-full border border-[#ddd] px-4 py-2.5 text-xs text-[#111] outline-none focus:border-[#111]"
              />
            </div>
          </div>

          {/* GHN two-level address catalogue */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* Province */}
            <div>
              <label
                htmlFor="province"
                className="block text-xs uppercase font-bold tracking-widest text-[#111] mb-1.5"
              >
                Tỉnh / Thành Phố <span className="text-[#E5001B]">*</span>
              </label>
              <select
                id="province"
                required
                value={provinceCode}
                onChange={(e) => { setProvinceCode(e.target.value); setWardId(''); }}
                className="w-full border border-[#ddd] bg-white px-3 py-2.5 text-xs text-[#111] outline-none focus:border-[#111]"
              >
                <option value="">Chọn Tỉnh/Thành</option>
                {provincesLoad.data?.map((p) => (
                  <option key={p.code} value={p.code}>
                    {p.name}
                  </option>
                ))}
              </select>
            </div>

            {/* Ward */}
            <div>
              <label
                htmlFor="ward"
                className="block text-xs uppercase font-bold tracking-widest text-[#111] mb-1.5"
              >
                Phường / Xã <span className="text-[#E5001B]">*</span>
              </label>
              <select
                id="ward"
                required
                disabled={!provinceCode || wards.length === 0}
                value={wardId}
                onChange={(e) => setWardId(e.target.value)}
                className="w-full border border-[#ddd] bg-white px-3 py-2.5 text-xs text-[#111] outline-none focus:border-[#111] disabled:bg-[#f5f5f5] disabled:cursor-not-allowed"
              >
                <option value="">Chọn Phường/Xã</option>
                {wards.map((w) => (
                  <option key={w.code} value={w.code}>
                    {w.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Detail Address */}
          <div>
            <label
              htmlFor="detailAddress"
              className="block text-xs uppercase font-bold tracking-widest text-[#111] mb-1.5"
            >
              Số Nhà, Tên Đường, Tòa Nhà <span className="text-[#E5001B]">*</span>
            </label>
            <input
              id="detailAddress"
              required
              type="text"
              value={form.detailAddress}
              onChange={(e) => setForm({ ...form, detailAddress: e.target.value })}
              placeholder="Ví dụ: 123 Đường Nguyễn Trãi, Tòa nhà Landmark"
              className="w-full border border-[#ddd] px-4 py-2.5 text-xs text-[#111] outline-none focus:border-[#111]"
            />
          </div>

          {/* Default checkbox */}
          <div className="pt-1">
            <label className="flex items-center gap-2.5 text-xs text-[#333] cursor-pointer">
              <input
                type="checkbox"
                checked={form.isDefault}
                onChange={(e) => setForm({ ...form, isDefault: e.target.checked })}
                className="w-4 h-4 accent-[#111]"
              />
              <span>Đặt làm địa chỉ giao hàng mặc định</span>
            </label>
          </div>

          <div className="pt-2 flex items-center gap-3">
            <button
              type="submit"
              disabled={busy}
              className="bg-[#111] text-white px-8 py-3 text-xs uppercase tracking-widest font-bold hover:bg-[#E5001B] active:scale-[0.98] disabled:opacity-50 transition-all"
            >
              {busy ? 'Đang Lưu…' : editingId ? 'Cập Nhật Địa Chỉ' : 'Lưu Địa Chỉ Mới'}
            </button>
            {editingId && (
              <button
                type="button"
                onClick={resetForm}
                className="border border-[#ddd] text-[#555] px-6 py-3 text-xs uppercase tracking-widest font-semibold hover:border-[#111] hover:text-[#111]"
              >
                Hủy
              </button>
            )}
          </div>

          <p className="text-[11px] text-[#888] pt-2 border-t border-[rgba(0,0,0,0.06)]">
            * Danh mục tỉnh và phường/xã do GHN cung cấp để tính phí giao hàng.
          </p>
        </form>
      </div>
    </ProfileLayout>
  );
}
