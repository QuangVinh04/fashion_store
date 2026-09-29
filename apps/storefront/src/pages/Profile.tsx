import { useState, useEffect } from 'react';
import { Camera, Check, AlertCircle, ExternalLink, ShieldCheck } from 'lucide-react';
import { store } from '../api/store';
import type { Profile } from '../api/types';
import ProfileLayout from '../components/ProfileLayout';
import { PageTitle, Status, useLoad } from '../components/StoreUI';
import { useAuth } from '../context/AuthContext';

export default function ProfilePage() {
  const { refresh: refreshAuth } = useAuth();
  const profileLoad = useLoad(store.profile, []);

  const [form, setForm] = useState({
    fullName: '',
    phone: '',
    address: '',
    avatar: '',
  });

  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  useEffect(() => {
    if (profileLoad.data) {
      setForm({
        fullName: profileLoad.data.fullName || '',
        phone: profileLoad.data.phone || '',
        address: profileLoad.data.address || '',
        avatar: profileLoad.data.avatar || '',
      });
    }
  }, [profileLoad.data]);

  async function handleAvatarUpload(file?: File) {
    if (!file) return;
    setBusy(true);
    setNotice(null);
    try {
      const avatarUrl = await store.uploadImage(file, 'avatars');
      setForm((prev) => ({ ...prev, avatar: avatarUrl }));
      setNotice({
        type: 'success',
        text: 'Ảnh đã được tải lên. Nhấn "Lưu Thay Đổi" để hoàn tất cập nhật.',
      });
    } catch (err) {
      setNotice({
        type: 'error',
        text: (err as Error).message || 'Tải ảnh đại diện thất bại.',
      });
    } finally {
      setBusy(false);
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setNotice(null);
    try {
      await store.saveProfile(form);
      await Promise.all([profileLoad.refresh(), refreshAuth()]);
      setNotice({
        type: 'success',
        text: 'Cập nhật thông tin hồ sơ thành công.',
      });
    } catch (err) {
      setNotice({
        type: 'error',
        text: (err as Error).message || 'Lưu thông tin thất bại.',
      });
    } finally {
      setBusy(false);
    }
  }

  return (
    <ProfileLayout>
      <div style={{ fontFamily: "'Inter', sans-serif" }} className="w-full max-w-xl">
        <PageTitle eyebrow="Tài Khoản">HỒ SƠ CÁ NHÂN</PageTitle>

        <Status
          loading={profileLoad.loading}
          error={profileLoad.error}
          retry={() => void profileLoad.refresh()}
        />

        {notice && (
          <div
            className={`mb-6 p-4 text-xs font-medium flex items-center gap-2.5 border ${
              notice.type === 'success'
                ? 'bg-emerald-50 text-emerald-900 border-emerald-200'
                : 'bg-red-50 text-red-900 border-red-200'
            }`}
          >
            {notice.type === 'success' ? (
              <Check size={16} className="text-emerald-700 shrink-0" />
            ) : (
              <AlertCircle size={16} className="text-red-700 shrink-0" />
            )}
            <span>{notice.text}</span>
          </div>
        )}

        {profileLoad.data && (
          <form onSubmit={handleSubmit} className="space-y-6">
            {/* Avatar block */}
            <div className="flex items-center gap-6 pb-6 border-b border-[rgba(0,0,0,0.08)]">
              <div className="relative w-20 h-20 bg-[#111] rounded-full overflow-hidden shrink-0 flex items-center justify-center border-2 border-white shadow-sm">
                {form.avatar ? (
                  <img src={form.avatar} alt="Avatar" className="w-full h-full object-cover" />
                ) : (
                  <span className="text-white text-2xl font-bold uppercase">
                    {profileLoad.data.fullName?.[0] || 'U'}
                  </span>
                )}
              </div>

              <div>
                <label className="inline-flex items-center gap-2 bg-[#f5f5f5] hover:bg-[#eee] border border-[#ddd] px-4 py-2 text-xs uppercase tracking-wider font-semibold text-[#111] cursor-pointer transition-colors">
                  <Camera size={14} /> Thay Ảnh Đại Diện
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    disabled={busy}
                    onChange={(e) => void handleAvatarUpload(e.target.files?.[0])}
                    className="hidden"
                  />
                </label>
                <p className="text-[11px] text-[#888] mt-1.5">
                  Định dạng JPG, PNG hoặc WebP tối đa 5MB.
                </p>
              </div>
            </div>

            {/* Email (Read-only) */}
            <div>
              <label className="block text-xs uppercase font-bold tracking-widest text-[#888] mb-1.5">
                Địa Chỉ Email (Tài Khoản Xác Thực)
              </label>
              <input
                type="email"
                disabled
                value={profileLoad.data.email}
                className="w-full border border-[#ddd] bg-[#f5f5f5] px-4 py-3 text-xs text-[#777] cursor-not-allowed select-none"
              />
              <span className="text-[11px] text-[#888] mt-1 block">
                Email được quản lý qua tài khoản bảo mật Keycloak của LINO.
              </span>
            </div>

            {/* Full Name */}
            <div>
              <label
                htmlFor="fullName"
                className="block text-xs uppercase font-bold tracking-widest text-[#111] mb-1.5"
              >
                Họ Và Tên <span className="text-[#E5001B]">*</span>
              </label>
              <input
                id="fullName"
                required
                type="text"
                value={form.fullName}
                onChange={(e) => setForm({ ...form, fullName: e.target.value })}
                placeholder="Nhập họ và tên đầy đủ"
                className="w-full border border-[#ddd] bg-white px-4 py-3 text-xs text-[#111] outline-none focus:border-[#111]"
              />
            </div>

            {/* Phone */}
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
                className="w-full border border-[#ddd] bg-white px-4 py-3 text-xs text-[#111] outline-none focus:border-[#111]"
              />
            </div>

            {/* Address */}
            <div>
              <label
                htmlFor="address"
                className="block text-xs uppercase font-bold tracking-widest text-[#111] mb-1.5"
              >
                Địa Chỉ Liên Hệ <span className="text-[#E5001B]">*</span>
              </label>
              <input
                id="address"
                required
                type="text"
                value={form.address}
                onChange={(e) => setForm({ ...form, address: e.target.value })}
                placeholder="Tỉnh/Thành phố hoặc địa chỉ cư trú"
                className="w-full border border-[#ddd] bg-white px-4 py-3 text-xs text-[#111] outline-none focus:border-[#111]"
              />
            </div>

            <div className="pt-2">
              <button
                type="submit"
                disabled={busy}
                className="bg-[#111] text-white py-3.5 px-8 text-xs uppercase tracking-widest font-bold hover:bg-[#E5001B] active:scale-[0.98] disabled:opacity-50 transition-all"
              >
                {busy ? 'Đang Lưu Thay Đổi…' : 'Lưu Thay Đổi'}
              </button>
            </div>

            {/* Keycloak security portal */}
            <div className="mt-10 p-5 bg-[#fafafa] border border-[rgba(0,0,0,0.08)] flex items-center justify-between gap-4">
              <div className="text-xs">
                <p className="font-bold uppercase tracking-wider text-[#111] mb-0.5 flex items-center gap-1.5">
                  <ShieldCheck size={15} /> Bảo Mật & Mật Khẩu
                </p>
                <p className="text-[#666]">
                  Đổi mật khẩu và cài đặt xác thực 2 lớp qua cổng quản lý tài khoản Keycloak.
                </p>
              </div>

              <a
                href="http://localhost:8180/realms/fashion-store/account"
                target="_blank"
                rel="noopener noreferrer"
                className="shrink-0 text-xs uppercase tracking-widest font-bold text-[#111] hover:text-[#E5001B] underline underline-offset-4 flex items-center gap-1"
              >
                Cài Đặt <ExternalLink size={12} />
              </a>
            </div>
          </form>
        )}
      </div>
    </ProfileLayout>
  );
}
