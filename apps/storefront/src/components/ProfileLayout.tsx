import { Link, useLocation } from "react-router";
import {
  User,
  MapPin,
  ShoppingBag,
  Heart,
  LogOut,
  ChevronRight,
} from "lucide-react";
import { useAuth } from "../context/AuthContext";

const NAV = [
  { href: "/profile", label: "Hồ Sơ Cá Nhân", icon: User },
  { href: "/profile/orders", label: "Đơn Hàng Của Tôi", icon: ShoppingBag },
  { href: "/profile/addresses", label: "Quản Lý Địa Chỉ", icon: MapPin },
  { href: "/wishlist", label: "Sản Phẩm Yêu Thích", icon: Heart },
];

export default function ProfileLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user, logout } = useAuth();
  const location = useLocation();

  const handleLogout = () => {
    void logout();
  };

  return (
    <div style={{ fontFamily: "var(--app-font)" }}>
      {/* Breadcrumb */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-5 border-b border-border">
        <nav className="flex items-center gap-2 text-xs text-muted-foreground">
          <Link
            to="/"
            className="hover:text-foreground transition-colors store-text-link"
          >
            Trang Chủ
          </Link>
          <span>/</span>
          <span className="text-foreground">Tài Khoản</span>
        </nav>
      </div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-10">
        <div className="grid lg:grid-cols-[260px_1fr] gap-6">
          {/* Sidebar */}
          <aside className="rounded-2xl bg-card p-4 lg:self-start">
            {/* Avatar */}
            <div className="flex items-center gap-3 mb-5 p-3">
              <div className="w-12 h-12 rounded-full bg-secondary flex items-center justify-center flex-shrink-0">
                <span
                  className="text-foreground text-base font-semibold"
                  style={{ fontFamily: "var(--app-font)" }}
                >
                  {user?.fullName?.[0]?.toUpperCase() ?? "U"}
                </span>
              </div>
              <div className="min-w-0">
                <p className="text-sm font-semibold text-foreground truncate">
                  {user?.fullName}
                </p>
                <p className="text-xs text-muted-foreground truncate mt-0.5">
                  {user?.email}
                </p>
              </div>
            </div>

            <nav className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-1 gap-1">
              {NAV.map(({ href, label, icon: Icon }) => {
                const active = location.pathname === href;
                return (
                  <Link
                    key={href}
                    to={href}
                    className={`flex min-h-11 items-center justify-between rounded-xl px-3 py-2.5 text-sm transition-colors duration-150 ${active ? "bg-secondary text-foreground font-medium" : "text-muted-foreground hover:bg-background hover:text-foreground"}`}
                  >
                    <div className="flex items-center gap-3">
                      <Icon size={16} />
                      <span>{label}</span>
                    </div>
                    <ChevronRight size={14} className="text-muted-foreground" />
                  </Link>
                );
              })}
              <button
                onClick={handleLogout}
                className="w-full flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-destructive bg-primary-light hover:bg-rose-100 transition-colors duration-150 store-button"
              >
                <LogOut size={16} />
                <span>Đăng Xuất</span>
              </button>
            </nav>
          </aside>

          {/* Content */}
          <div className="min-w-0 rounded-2xl border border-border bg-card p-4 sm:p-6">
            {children}
          </div>
        </div>
      </div>
    </div>
  );
}
