import { useEffect, useState } from "react";
import { Link, Outlet, useLocation, useNavigate } from "react-router";
import {
  Search,
  ShoppingBag,
  User,
  Menu,
  ChevronRight,
  LogOut,
} from "lucide-react";
import { useCart } from "../context/CartContext";
import { useAuth } from "../context/AuthContext";
import { Button } from "./components/ui/button";
import { Avatar, AvatarImage, AvatarFallback } from "./components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
} from "./components/ui/dropdown-menu";
import {
  Sheet,
  SheetContent,
  SheetTitle,
  SheetDescription,
} from "./components/ui/sheet";

const NAV_LINKS = [
  { label: "Nam", href: "/products?gender=MEN" },
  { label: "Nữ", href: "/products?gender=WOMEN" },
  { label: "Unisex", href: "/products?gender=UNISEX" },
  { label: "Sản Phẩm", href: "/products" },
];
const ACCOUNT_LINKS = [
  { label: "Hồ Sơ", href: "/profile" },
  { label: "Đơn Hàng", href: "/profile/orders" },
  { label: "Địa Chỉ", href: "/profile/addresses" },
  { label: "Yêu Thích", href: "/wishlist" },
];

export default function Root() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const { count } = useCart();
  const { user, isLoggedIn, login, register, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  useEffect(() => {
    if (!isLoggedIn || location.pathname !== "/") return;
    const destination = sessionStorage.getItem("lino:returnTo");
    if (destination?.startsWith("/") && !destination.startsWith("//")) {
      sessionStorage.removeItem("lino:returnTo");
      navigate(destination, { replace: true });
    }
  }, [isLoggedIn, location.pathname, navigate]);
  const handleLogout = () => {
    void logout();
    setUserMenuOpen(false);
  };
  const returnTo = location.pathname + location.search;
  const activeLink = (href: string) =>
    location.pathname.startsWith("/products") &&
    location.search === (href.split("?")[1] ? `?${href.split("?")[1]}` : "");

  return (
    <div className="min-h-screen bg-background text-foreground">
      <div className="bg-primary-light px-4 py-2 text-center text-xs font-medium text-destructive">
        MIỄN PHÍ VẬN CHUYỂN CHO ĐƠN HÀNG TỪ 500.000 ₫
      </div>
      <header className="sticky top-0 z-40 border-b border-border bg-card">
        <div className="store-container flex h-18 items-center justify-between gap-4">
          <Link
            to="/"
            className="store-logo shrink-0"
            aria-label="LINO — Trang chủ"
          >
            LINO
          </Link>
          <nav
            className="hidden items-center gap-2 lg:flex"
            aria-label="Danh mục sản phẩm"
          >
            {NAV_LINKS.map((link) => (
              <Link
                key={link.href}
                to={link.href}
                aria-current={activeLink(link.href) ? "page" : undefined}
                className={`rounded-xl px-4 py-2.5 text-sm transition-colors ${activeLink(link.href) ? "bg-secondary font-medium" : "text-muted-foreground hover:bg-background hover:text-foreground"}`}
              >
                {link.label}
              </Link>
            ))}
          </nav>
          <div className="flex items-center gap-1 sm:gap-2">
            <Link
              to="/products"
              className="store-icon-button"
              aria-label="Tìm kiếm"
            >
              <Search size={19} />
            </Link>
            {isLoggedIn ? (
              <DropdownMenu open={userMenuOpen} onOpenChange={setUserMenuOpen}>
                <DropdownMenuTrigger asChild>
                  <button className="store-icon-button" aria-label="Tài khoản">
                    <Avatar className="size-8">
                      <AvatarImage
                        src={user?.avatarUrl || user?.avatar || undefined}
                        alt=""
                        className="object-cover"
                      />
                      <AvatarFallback className="bg-secondary text-xs font-medium text-foreground">
                        {user?.fullName?.[0]?.toUpperCase() || "U"}
                      </AvatarFallback>
                    </Avatar>
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent
                  align="end"
                  className="w-60 rounded-xl border-border p-1.5"
                >
                  <div className="px-3 py-2.5">
                    <p className="truncate text-sm font-medium">
                      {user?.fullName}
                    </p>
                    <p className="mt-1 truncate text-xs text-muted-foreground">
                      {user?.email}
                    </p>
                  </div>
                  <DropdownMenuSeparator />
                  {ACCOUNT_LINKS.map((link) => (
                    <DropdownMenuItem asChild key={link.href}>
                      <Link to={link.href}>{link.label}</Link>
                    </DropdownMenuItem>
                  ))}
                  <DropdownMenuSeparator />
                  <DropdownMenuItem
                    onClick={handleLogout}
                    className="text-destructive focus:bg-primary-light focus:text-destructive"
                  >
                    <LogOut size={16} />
                    Đăng Xuất
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            ) : (
              <button
                className="store-icon-button"
                onClick={() =>
                  login(location.pathname !== "/login" ? returnTo : "/profile")
                }
                aria-label="Đăng nhập"
              >
                <User size={19} />
              </button>
            )}
            <Link
              to="/cart"
              className="store-icon-button relative"
              aria-label="Giỏ hàng"
            >
              <ShoppingBag size={20} />
              {count > 0 && (
                <span className="absolute right-0 top-0 flex min-w-4 items-center justify-center rounded-full bg-primary-light px-1 text-xs font-medium text-destructive">
                  {count}
                </span>
              )}
            </Link>
            <button
              className="store-icon-button lg:hidden"
              onClick={() => setMenuOpen(true)}
              aria-label="Menu"
              aria-expanded={menuOpen}
            >
              <Menu size={20} />
            </button>
          </div>
        </div>
      </header>
      <Sheet open={menuOpen} onOpenChange={setMenuOpen}>
        <SheetContent className="w-[min(360px,90vw)] gap-0 bg-card p-0">
          <div className="border-b border-border p-5">
            <SheetTitle className="store-logo">LINO</SheetTitle>
            <SheetDescription className="sr-only">
              Sản phẩm và tài khoản
            </SheetDescription>
          </div>
          <nav
            className="flex flex-1 flex-col gap-1 overflow-y-auto p-4"
            aria-label="Điều hướng mobile"
          >
            {NAV_LINKS.map((link) => (
              <Link
                key={link.href}
                to={link.href}
                onClick={() => setMenuOpen(false)}
                className="flex min-h-11 items-center justify-between rounded-xl px-3 text-sm hover:bg-background"
              >
                {link.label}
                <ChevronRight size={16} className="text-muted-foreground" />
              </Link>
            ))}
            {isLoggedIn && (
              <>
                <div className="my-3 border-t border-border" />
                {ACCOUNT_LINKS.map((link) => (
                  <Link
                    key={link.href}
                    to={link.href}
                    onClick={() => setMenuOpen(false)}
                    className="flex min-h-11 items-center rounded-xl px-3 text-sm hover:bg-background"
                  >
                    {link.label}
                  </Link>
                ))}
              </>
            )}
          </nav>
          <div className="border-t border-border p-4">
            {isLoggedIn ? (
              <Button
                variant="destructive"
                className="w-full"
                onClick={() => {
                  handleLogout();
                  setMenuOpen(false);
                }}
              >
                <LogOut size={16} />
                Đăng Xuất
              </Button>
            ) : (
              <div className="flex gap-3">
                <Button
                  variant="primary"
                  className="flex-1"
                  onClick={() => {
                    setMenuOpen(false);
                    login(
                      location.pathname !== "/login" ? returnTo : "/profile",
                    );
                  }}
                >
                  Đăng Nhập
                </Button>
                <Button
                  className="flex-1"
                  onClick={() => {
                    setMenuOpen(false);
                    register(
                      location.pathname !== "/register" ? returnTo : "/profile",
                    );
                  }}
                >
                  Đăng Ký
                </Button>
              </div>
            )}
          </div>
        </SheetContent>
      </Sheet>
      <main>
        <Outlet />
      </main>
      <footer className="mt-8 border-t border-border bg-card">
        <div className="store-container py-10">
          <div className="mb-8 grid grid-cols-2 gap-6 md:grid-cols-4 lg:grid-cols-5">
            <div className="col-span-2 md:col-span-4 lg:col-span-1">
              <p className="store-logo mb-3">LINO</p>
              <p className="max-w-sm text-sm leading-relaxed text-muted-foreground">
                Thời trang tối giản. Chất lượng vượt trội. Thiết kế cho cuộc
                sống hiện đại.
              </p>
            </div>
            {[
              {
                title: "Sản Phẩm",
                links: [
                  { label: "Tất cả", href: "/products" },
                  { label: "Nam", href: "/products?gender=MEN" },
                  { label: "Nữ", href: "/products?gender=WOMEN" },
                  { label: "Unisex", href: "/products?gender=UNISEX" },
                ],
              },
              {
                title: "Tài Khoản",
                links: [
                  { label: "Hồ sơ", href: "/profile" },
                  { label: "Địa chỉ", href: "/profile/addresses" },
                  { label: "Yêu thích", href: "/wishlist" },
                ],
              },
              {
                title: "Hỗ Trợ",
                links: [
                  { label: "Theo dõi đơn", href: "/profile/orders" },
                  { label: "Thông tin mua hàng", href: "/policy" },
                  { label: "Liên hệ", href: "/contact" },
                ],
              },
              {
                title: "Liên Hệ",
                links: [
                  { label: "support@lino.vn", href: "mailto:support@lino.vn" },
                  { label: "1800 1234", href: "tel:18001234" },
                ],
              },
            ].map((col) => (
              <div key={col.title}>
                <p className="mb-3 text-sm font-medium">{col.title}</p>
                <ul>
                  {col.links.map((link) => (
                    <li key={link.href}>
                      <a
                        href={link.href}
                        className="inline-flex min-h-9 min-w-8 items-center text-sm text-muted-foreground hover:text-foreground"
                      >
                        {link.label}
                      </a>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
          <div className="flex flex-col items-start justify-between gap-3 border-t border-border pt-5 sm:flex-row sm:items-center">
            <p className="text-xs text-muted-foreground">
              © 2025 LINO Vietnam. Bảo lưu mọi quyền.
            </p>
            <div className="flex gap-5">
              <Link
                to="/policy"
                className="store-link text-xs text-muted-foreground"
              >
                Chính Sách
              </Link>
              <Link
                to="/contact"
                className="store-link text-xs text-muted-foreground"
              >
                Liên Hệ
              </Link>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
