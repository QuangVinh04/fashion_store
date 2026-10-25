import { Link, useLocation } from "react-router";
import {
  User,
  Wallet,
  MapPin,
  ShoppingBag,
  Heart,
  LogOut,
  ChevronRight,
  ShieldCheck,
} from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { Avatar, AvatarFallback, AvatarImage } from "../app/components/ui/avatar";

const NAV = [
  { href: "/profile/wallet", label: "Ví LINO", icon: Wallet },
  { href: "/profile", label: "Hồ Sơ Cá Nhân", icon: User },
  { href: "/profile/orders", label: "Đơn Hàng Của Tôi", icon: ShoppingBag },
  { href: "/profile/addresses", label: "Sổ Địa Chỉ", icon: MapPin },
  { href: "/wishlist", label: "Sản Phẩm Yêu Thích", icon: Heart },
];

const BREADCRUMB_MAP: Record<string, string> = {
  "/profile": "Hồ Sơ Cá Nhân",
  "/profile/wallet": "Ví LINO",
  "/profile/wallet/top-up": "Nạp Tiền Vào Ví",
  "/profile/orders": "Đơn Hàng Của Tôi",
  "/profile/addresses": "Sổ Địa Chỉ",
  "/wishlist": "Sản Phẩm Yêu Thích",
};

export default function ProfileLayout({
  children,
  flush = false,
}: {
  children: React.ReactNode;
  flush?: boolean;
}) {
  const { user, logout } = useAuth();
  const location = useLocation();

  const handleLogout = () => {
    void logout();
  };

  const currentLabel =
    BREADCRUMB_MAP[location.pathname] ||
    (location.pathname.startsWith("/profile/orders/")
      ? "Chi Tiết Đơn Hàng"
      : undefined);

  return (
    <div className="w-full min-h-[calc(100vh-220px)] bg-background" style={{ fontFamily: "var(--app-font)" }}>
      {/* Top Breadcrumb Bar - Synchronized with the whole store (max-w-7xl) */}
      <div className="border-b border-border bg-background">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-4">
          <nav className="flex items-center gap-2 text-xs text-muted-foreground font-medium">
            <Link
              to="/"
              className="hover:text-foreground transition-colors store-text-link"
            >
              Trang Chủ
            </Link>
            <span>/</span>
            <Link
              to="/profile"
              className={`hover:text-foreground transition-colors store-text-link ${
                !currentLabel || location.pathname === "/profile"
                  ? "text-foreground font-semibold"
                  : ""
              }`}
            >
              Tài Khoản
            </Link>
            {currentLabel && location.pathname !== "/profile" && (
              <>
                <span>/</span>
                <span className="text-foreground font-semibold">
                  {currentLabel}
                </span>
              </>
            )}
          </nav>
        </div>
      </div>

      {/* Main Container - Synchronized with 1280px grid system */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8 md:py-10">
        <div className="grid grid-cols-1 lg:grid-cols-[260px_1fr] gap-8 items-start">
          {/* Sidebar */}
          <aside className="rounded-xl border border-border bg-card p-4 sm:p-5 lg:sticky lg:top-24 shadow-xs">
            {/* User Profile Summary */}
            <div className="flex items-center gap-3.5 mb-5 p-3 rounded-lg bg-secondary/50 border border-border/40">
              <Avatar className="w-12 h-12 ring-2 ring-primary/20 shrink-0">
                <AvatarImage
                  src={user?.avatarUrl || user?.avatar || undefined}
                  alt={user?.fullName || "User"}
                  className="object-cover"
                />
                <AvatarFallback className="bg-primary/10 text-primary font-bold text-base">
                  {user?.fullName?.[0]?.toUpperCase() ?? "U"}
                </AvatarFallback>
              </Avatar>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold text-foreground truncate">
                  {user?.fullName || "Khách hàng LINO"}
                </p>
                <p className="text-xs text-muted-foreground truncate mt-0.5">
                  {user?.email}
                </p>
                {user?.isEmailVerified && (
                  <span className="inline-flex items-center gap-1 mt-1 text-[11px] font-medium text-emerald-600 dark:text-emerald-400">
                    <ShieldCheck size={12} /> Đã xác thực
                  </span>
                )}
              </div>
            </div>

            {/* Navigation */}
            <nav className="flex lg:flex-col gap-1.5 overflow-x-auto pb-2 lg:pb-0 scrollbar-none">
              {NAV.map(({ href, label, icon: Icon }) => {
                const active =
                  location.pathname === href ||
                  (href !== "/profile" && location.pathname.startsWith(`${href}/`));
                return (
                  <Link
                    key={href}
                    to={href}
                    aria-current={active ? "page" : undefined}
                    className={`flex min-h-11 items-center justify-between rounded-lg px-3.5 py-2.5 text-sm whitespace-nowrap transition-all duration-150 shrink-0 lg:shrink ${
                      active
                        ? "bg-primary/10 text-primary font-semibold border border-primary/20 shadow-2xs"
                        : "text-muted-foreground hover:bg-secondary/70 hover:text-foreground"
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <Icon
                        size={16}
                        className={active ? "text-primary" : "text-muted-foreground"}
                      />
                      <span>{label}</span>
                    </div>
                    <ChevronRight
                      size={14}
                      className={`hidden lg:block ${
                        active ? "text-primary" : "text-muted-foreground/50"
                      }`}
                    />
                  </Link>
                );
              })}
              <button
                onClick={handleLogout}
                className="flex min-h-11 items-center gap-3 rounded-lg px-3.5 py-2.5 text-sm text-muted-foreground hover:text-destructive hover:bg-destructive/10 border border-transparent hover:border-destructive/20 transition-all duration-150 shrink-0 lg:shrink lg:mt-3 cursor-pointer"
              >
                <LogOut size={16} />
                <span>Đăng Xuất</span>
              </button>
            </nav>
          </aside>

          {/* Main Content Area */}
          <div
            className={
              flush
                ? "min-w-0"
                : "min-w-0 rounded-xl border border-border bg-card p-6 sm:p-8 shadow-xs"
            }
          >
            {children}
          </div>
        </div>
      </div>
    </div>
  );
}
