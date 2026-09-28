import { Link, useLocation } from "react-router";
import { User, MapPin, ShoppingBag, Heart, LogOut, ChevronRight } from "lucide-react";
import { useAuth } from "../context/AuthContext";

const NAV = [
  { href: "/profile", label: "Hồ Sơ Cá Nhân", icon: User },
  { href: "/profile/orders", label: "Đơn Hàng Của Tôi", icon: ShoppingBag },
  { href: "/profile/addresses", label: "Quản Lý Địa Chỉ", icon: MapPin },
  { href: "/wishlist", label: "Sản Phẩm Yêu Thích", icon: Heart },
];

export default function ProfileLayout({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuth();
  const location = useLocation();

  const handleLogout = () => {
    void logout();
  };

  return (
    <div style={{ fontFamily: "'Inter', sans-serif" }}>
      {/* Breadcrumb */}
      <div className="max-w-[1400px] mx-auto px-6 py-5 border-b border-[rgba(0,0,0,0.08)]">
        <nav className="flex items-center gap-2 text-xs text-[#888] uppercase tracking-widest">
          <Link to="/" className="hover:text-[#111] transition-colors">Trang Chủ</Link>
          <span>/</span>
          <span className="text-[#111]">Tài Khoản</span>
        </nav>
      </div>

      <div className="max-w-[1400px] mx-auto px-6 py-10">
        <div className="grid lg:grid-cols-[260px_1fr] gap-10">
          {/* Sidebar */}
          <aside>
            {/* Avatar */}
            <div className="flex items-center gap-4 mb-8 p-5 bg-[#f5f5f5]">
              <div className="w-14 h-14 bg-[#111] flex items-center justify-center flex-shrink-0">
                <span className="text-white text-xl font-bold" style={{ fontFamily: "'Barlow Condensed', sans-serif" }}>
                  {user?.fullName?.[0]?.toUpperCase() ?? "U"}
                </span>
              </div>
              <div className="min-w-0">
                <p className="text-sm font-semibold text-[#111] truncate">{user?.fullName}</p>
                <p className="text-xs text-[#888] truncate mt-0.5">{user?.email}</p>
              </div>
            </div>

            <nav className="space-y-0.5">
              {NAV.map(({ href, label, icon: Icon }) => {
                const active = location.pathname === href;
                return (
                  <Link
                    key={href}
                    to={href}
                    className={`flex items-center justify-between px-4 py-3 text-sm transition-colors duration-150 ${active ? "bg-[#111] text-white" : "text-[#555] hover:bg-[#f5f5f5] hover:text-[#111]"}`}
                  >
                    <div className="flex items-center gap-3">
                      <Icon size={16} />
                      <span>{label}</span>
                    </div>
                    <ChevronRight size={14} className={active ? "text-white/60" : "text-[#ccc]"} />
                  </Link>
                );
              })}
              <button
                onClick={handleLogout}
                className="w-full flex items-center gap-3 px-4 py-3 text-sm text-[#888] hover:text-[#E5001B] hover:bg-red-50 transition-colors duration-150"
              >
                <LogOut size={16} />
                <span>Đăng Xuất</span>
              </button>
            </nav>
          </aside>

          {/* Content */}
          <main>{children}</main>
        </div>
      </div>
    </div>
  );
}
