import { useEffect, useState } from 'react';
import ProfileLayout from '../components/ProfileLayout';
import { PageTitle, Status, useLoad } from '../components/StoreUI';
import { store } from '../api/store';
import type { Address } from '../api/types';

type Location = { code: string; name: string };
type Form = Omit<Address, 'id' | 'fullAddress'>;
const empty: Form = { recipientName: '', phone: '', province: '', district: '', ward: '', detailAddress: '', districtId: undefined, wardCode: '', isDefault: false };
const input = 'w-full border border-[#ddd] px-4 py-3 text-sm focus:border-[#111] outline-none';
const label = 'mb-2 block text-xs font-semibold uppercase tracking-widest';
const button = 'bg-[#111] px-5 py-3 text-xs font-medium uppercase tracking-widest text-white hover:bg-[#E5001B] disabled:opacity-50';

export default function AddressesPage() {
  const addresses = useLoad(store.addresses, []);
  const provinces = useLoad(store.provinces, []);
  const [districts, setDistricts] = useState<Location[]>([]);
  const [wards, setWards] = useState<Location[]>([]);
  const [provinceCode, setProvinceCode] = useState('');
  const [editing, setEditing] = useState<string | null>(null);
  const [form, setForm] = useState<Form>(empty);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!provinceCode) { setDistricts([]); return; }
    let active = true;
    void store.districts(provinceCode).then(rows => { if (active) setDistricts(rows); }).catch(error => { if (active) setMessage((error as Error).message); });
    return () => { active = false; };
  }, [provinceCode]);
  useEffect(() => {
    if (!form.districtId) { setWards([]); return; }
    let active = true;
    void store.wards(String(form.districtId)).then(rows => { if (active) setWards(rows); }).catch(error => { if (active) setMessage((error as Error).message); });
    return () => { active = false; };
  }, [form.districtId]);

  function reset() { setEditing(null); setProvinceCode(''); setForm(empty); setDistricts([]); setWards([]); }
  function edit(address: Address) {
    const province = provinces.data?.find(row => row.name === address.province);
    setEditing(address.id);
    setProvinceCode(province?.code || '');
    setForm({ recipientName: address.recipientName, phone: address.phone, province: address.province, district: address.district, ward: address.ward, detailAddress: address.detailAddress, districtId: address.districtId, wardCode: address.wardCode || '', isDefault: address.isDefault });
    setMessage(province ? '' : 'Tỉnh trong địa chỉ cũ không còn trong danh mục GHN. Vui lòng chọn lại tỉnh.');
  }
  async function run(action: () => Promise<unknown>) {
    setBusy(true); setMessage('');
    try { await action(); await addresses.refresh(); reset(); }
    catch (error) { setMessage((error as Error).message); }
    finally { setBusy(false); }
  }
  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (!provinceCode || !form.districtId || !form.wardCode) { setMessage('Vui lòng chọn tỉnh, huyện và xã trong danh mục giao hàng.'); return; }
    await run(() => store.saveAddress(form, editing || undefined));
  }

  return <ProfileLayout><PageTitle>Địa chỉ giao hàng</PageTitle>
    <Status loading={addresses.loading} error={addresses.error} retry={() => void addresses.refresh()} />
    {message && <p role="alert" className="mb-4 text-sm text-[#B00018]">{message}</p>}
    <div className="grid gap-4 md:grid-cols-2">{addresses.data?.map(address => <article key={address.id} className="border p-5 text-sm">
      <p className="font-semibold">{address.recipientName} {address.isDefault && <span className="ml-2 text-xs text-[#2D5A3D]">Mặc định</span>}</p>
      <p className="mt-2 text-[#555]">{address.phone}<br />{address.fullAddress || `${address.detailAddress}, ${address.ward}, ${address.district}, ${address.province}`}</p>
      <div className="mt-4 flex flex-wrap gap-4 text-xs uppercase underline"><button onClick={() => edit(address)}>Sửa</button>
        {!address.isDefault && <button disabled={busy} onClick={() => void run(() => store.defaultAddress(address.id))}>Đặt mặc định</button>}
        <button disabled={busy} onClick={() => { if (window.confirm('Xóa địa chỉ này?')) void run(() => store.deleteAddress(address.id)); }}>Xóa</button></div>
    </article>)}</div>
    <form className="mt-10 max-w-xl space-y-4 border-t pt-7" onSubmit={event => void save(event)}>
      <h2 className="text-sm font-bold uppercase tracking-widest">{editing ? 'Sửa địa chỉ' : 'Thêm địa chỉ'}</h2>
      {(['recipientName', 'phone'] as const).map(key => <div key={key}><label className={label} htmlFor={key}>{key === 'phone' ? 'Điện thoại' : 'Tên người nhận'}</label><input id={key} className={input} required value={form[key]} onChange={event => setForm({ ...form, [key]: event.target.value })} /></div>)}
      <div><label className={label} htmlFor="province">Tỉnh/thành phố</label><select id="province" required className={input} value={provinceCode} disabled={!!provinces.error} onChange={event => { const row = provinces.data?.find(item => item.code === event.target.value); setProvinceCode(event.target.value); setForm({ ...form, province: row?.name || '', district: '', ward: '', districtId: undefined, wardCode: '' }); setDistricts([]); setWards([]); }}><option value="">Chọn tỉnh/thành phố</option>{provinces.data?.map(row => <option key={row.code} value={row.code}>{row.name}</option>)}</select></div>
      <div><label className={label} htmlFor="district">Quận/huyện</label><select id="district" required className={input} value={form.districtId || ''} disabled={!provinceCode} onChange={event => { const row = districts.find(item => item.code === event.target.value); setForm({ ...form, districtId: row ? Number(row.code) : undefined, district: row?.name || '', ward: '', wardCode: '' }); setWards([]); }}><option value="">Chọn quận/huyện</option>{districts.map(row => <option key={row.code} value={row.code}>{row.name}</option>)}</select></div>
      <div><label className={label} htmlFor="ward">Phường/xã</label><select id="ward" required className={input} value={form.wardCode || ''} disabled={!form.districtId} onChange={event => { const row = wards.find(item => item.code === event.target.value); setForm({ ...form, wardCode: row?.code || '', ward: row?.name || '' }); }}><option value="">Chọn phường/xã</option>{wards.map(row => <option key={row.code} value={row.code}>{row.name}</option>)}</select></div>
      <div><label className={label} htmlFor="detailAddress">Số nhà, tên đường</label><input id="detailAddress" className={input} required value={form.detailAddress} onChange={event => setForm({ ...form, detailAddress: event.target.value })} /></div>
      <label className="block text-sm"><input type="checkbox" checked={form.isDefault} onChange={event => setForm({ ...form, isDefault: event.target.checked })} /> Đặt làm địa chỉ mặc định</label>
      <div className="flex gap-3"><button className={button} disabled={busy || !!provinces.error}>Lưu địa chỉ</button>{editing && <button type="button" className="text-xs uppercase underline" onClick={reset}>Hủy sửa</button>}</div>
      <Status loading={provinces.loading} error={provinces.error} retry={() => void provinces.refresh()} />
      <p className="text-xs text-[#888]">{provinces.data?.some(row => row.code === '999')
        ? 'Môi trường local dùng khu vực thử nghiệm để kiểm tra đặt hàng; không tạo vận đơn GHN thật.'
        : 'Danh mục địa chỉ do đơn vị vận chuyển cung cấp. Nếu chưa tải được, vui lòng thử lại.'}</p>
    </form>
  </ProfileLayout>;
}
