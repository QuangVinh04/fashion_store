import { useState } from "react";
import { Check } from "lucide-react";
import { useAuth } from "../context/AuthContext";
import ProfileLayout from "../components/ProfileLayout";

export default function Profile() {
  const { user, updateUser } = useAuth();
  const [form, setForm] = useState({
    fullName: user?.fullName ?? "",
    email: user?.email ?? "",
    phone: user?.phone ?? "",
    gender: user?.gender ?? "",
    birthDate: user?.birthDate ?? "",
  });
  const [saved, setSaved] = useState(false);

  const set = (key: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setForm((f) => ({ ...f, [key]: e.target.value }));
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    updateUser(form as never);
    setSaved(true);
    setTimeout(() => setSaved(false), 2500);
  };

  return (
    <ProfileLayout>
      <div>
        <h1
          className="text-[#111] mb-8"
          style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "clamp(1.75rem, 3vw, 2.5rem)", letterSpacing: "-0.01em", lineHeight: 1 }}
        >
          HỒ SƠ CÁ NHÂN
        </h1>

        <form onSubmit={handleSave} className="max-w-xl space-y-6">
          {/* Avatar Section */}
          <div className="flex items-center gap-6 pb-6 border-b border-[rgba(0,0,0,0.1)]">
            <div className="w-20 h-20 bg-[#111] flex items-center justify-center flex-shrink-0">
              <span className="text-white text-3xl font-bold" style={{ fontFamily: "'Barlow Condensed', sans-serif" }}>
                {form.fullName?.[0]?.toUpperCase() ?? "U"}
              </span>
            </div>
            <div>
              <p className="text-sm font-medium text-[#111]">{form.fullName}</p>
              <p className="text-xs text-[#888] mt-1">Thành viên LINO</p>
              <button type="button" className="text-xs uppercase tracking-widest text-[#888] hover:text-[#111] transition-colors border-b border-transparent hover:border-[#111] pb-0.5 mt-2">
                Đổi ảnh đại diện
              </button>
            </div>
          </div>

          {/* Fields */}
          <div className="space-y-5">
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-semibold uppercase tracking-widest text-[#111]">Họ Và Tên <span className="text-[#E5001B]">*</span></label>
              <input
                type="text"
                value={form.fullName}
                onChange={set("fullName")}
                className="border border-[rgba(0,0,0,0.15)] px-4 py-3 text-sm text-[#111] focus:outline-none focus:border-[#111] transition-colors"
              />
            </div>

            <div className="grid sm:grid-cols-2 gap-5">
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-semibold uppercase tracking-widest text-[#111]">Email <span className="text-[#E5001B]">*</span></label>
                <input
                  type="email"
                  value={form.email}
                  onChange={set("email")}
                  className="border border-[rgba(0,0,0,0.15)] px-4 py-3 text-sm text-[#111] focus:outline-none focus:border-[#111] transition-colors"
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-semibold uppercase tracking-widest text-[#111]">Số Điện Thoại</label>
                <input
                  type="tel"
                  value={form.phone}
                  onChange={set("phone")}
                  className="border border-[rgba(0,0,0,0.15)] px-4 py-3 text-sm text-[#111] focus:outline-none focus:border-[#111] transition-colors"
                />
              </div>
            </div>

            <div className="grid sm:grid-cols-2 gap-5">
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-semibold uppercase tracking-widest text-[#111]">Giới Tính</label>
                <select
                  value={form.gender}
                  onChange={set("gender")}
                  className="border border-[rgba(0,0,0,0.15)] px-4 py-3 text-sm text-[#111] bg-white focus:outline-none focus:border-[#111] transition-colors"
                >
                  <option value="">Chọn giới tính</option>
                  <option value="Nam">Nam</option>
                  <option value="Nữ">Nữ</option>
                  <option value="Khác">Khác</option>
                </select>
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-semibold uppercase tracking-widest text-[#111]">Ngày Sinh</label>
                <input
                  type="date"
                  value={form.birthDate}
                  onChange={set("birthDate")}
                  className="border border-[rgba(0,0,0,0.15)] px-4 py-3 text-sm text-[#111] focus:outline-none focus:border-[#111] transition-colors"
                />
              </div>
            </div>
          </div>

          {/* Password Section */}
          <div className="border-t border-[rgba(0,0,0,0.1)] pt-6">
            <p className="text-xs font-semibold uppercase tracking-widest text-[#111] mb-4">Đổi Mật Khẩu</p>
            <div className="space-y-4">
              {["Mật Khẩu Hiện Tại", "Mật Khẩu Mới", "Xác Nhận Mật Khẩu Mới"].map((label) => (
                <div key={label} className="flex flex-col gap-1.5">
                  <label className="text-xs font-semibold uppercase tracking-widest text-[#888]">{label}</label>
                  <input
                    type="password"
                    placeholder="••••••••"
                    className="border border-[rgba(0,0,0,0.15)] px-4 py-3 text-sm text-[#111] placeholder:text-[#aaa] focus:outline-none focus:border-[#111] transition-colors"
                  />
                </div>
              ))}
            </div>
          </div>

          <div className="flex items-center gap-4 pt-2">
            <button
              type="submit"
              className={`flex items-center gap-2 px-8 py-3.5 text-xs tracking-widest uppercase font-medium transition-colors duration-300 ${saved ? "bg-[#2D5A3D] text-white" : "bg-[#111] text-white hover:bg-[#E5001B]"}`}
            >
              {saved ? <><Check size={14} /> Đã Lưu</> : "Lưu Thay Đổi"}
            </button>
          </div>
        </form>
      </div>
    </ProfileLayout>
  );
}
