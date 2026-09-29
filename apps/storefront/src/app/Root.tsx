import { useEffect, useState } from "react";
import { Link, Outlet, useLocation, useNavigate } from "react-router";
import { Search, ShoppingBag, User, Menu, X, ChevronRight, LogOut } from "lucide-react";
import { useCart } from "../context/CartContext";
import { useAuth } from "../context/AuthContext";

const NAV_LINKS = [
  { label: "Nam", href: "/products?gender=MEN" },
  { label: "Nữ", href: "/products?gender=WOMEN" },
  { label: "Unisex", href: "/products?gender=UNISEX" },
  { label: "Sản Phẩm", href: "/products" },
];

export default function Root() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const { count } = useCart();
  const { user, isLoggedIn, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    if (!isLoggedIn || location.pathname !== '/') return;
    const destination = sessionStorage.getItem('lino:returnTo');
    if (destination?.startsWith('/') && !destination.startsWith('//')) {
      sessionStorage.removeItem('lino:returnTo');
      navigate(destination, { replace: true });
    }
  }, [isLoggedIn, location.pathname, navigate]);

  const handleLogout = () => {
    void logout();
    setUserMenuOpen(false);
  };

  return (
    <div className="min-h-screen bg-white text-[#111]" style={{ fontFamily: "'Inter', sans-serif" }}>
      {/* Announcement Bar */}
      <div className="bg-[#E5001B] text-white text-center py-2.5 text-xs tracking-widest uppercase" style={{ fontWeight: 500 }}>
        MIỄN PHÍ VẬN CHUYỂN CHO ĐƠN HÀNG TỪ 500.000 ₫
      </div>

      {/* Header */}
      <header className="sticky top-0 z-50 bg-white border-b border-[rgba(0,0,0,0.08)]">
        <div className="max-w-[1400px] mx-auto px-6 h-16 flex items-center justify-between">
          <Link
            to="/"
            className="flex-shrink-0 text-[#111] hover:text-[#E5001B] transition-colors"
            style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "1.75rem", letterSpacing: "-0.02em" }}
          >
            LINO
          </Link>

          <nav className="hidden lg:flex items-center gap-8">
            {NAV_LINKS.map((link) => (
              <Link
                key={link.label}
                to={link.href}
                className="text-[13px] font-medium tracking-wide uppercase transition-colors duration-150 hover:text-[#E5001B]"
                style={{ color: location.pathname.startsWith("/products") && link.href.startsWith("/products") ? "#E5001B" : "#111" }}
              >
                {link.label}
              </Link>
            ))}
          </nav>

          <div className="flex items-center gap-4">
            <Link to="/products" className="flex text-[#888] hover:text-[#111] transition-colors" aria-label="Tìm kiếm">
              <Search size={18} />
            </Link>
            <div className="hidden sm:block relative">
              {isLoggedIn ? (
                <>
                  <button
                    onClick={() => setUserMenuOpen(!userMenuOpen)}
                    className="flex items-center gap-1.5 text-[#888] hover:text-[#111] transition-colors"
                    aria-label="Tài khoản"
                  >
                    <div className="w-7 h-7 bg-[#111] flex items-center justify-center">
                      <span className="text-white text-xs font-bold">{user?.fullName?.[0]?.toUpperCase() || 'U'}</span>
                    </div>
                  </button>
                  {userMenuOpen && (
                    <div className="absolute right-0 top-10 w-52 bg-white border border-[rgba(0,0,0,0.12)] shadow-lg z-50 py-2">
                      <div className="px-4 py-3 border-b border-[rgba(0,0,0,0.08)]">
                        <p className="text-xs font-semibold text-[#111] truncate">{user?.fullName}</p>
                        <p className="text-[10px] text-[#888] truncate mt-0.5">{user?.email}</p>
                      </div>
                      {[
                        { label: "Hồ Sơ", href: "/profile" },
                        { label: "Đơn Hàng", href: "/profile/orders" },
                        { label: "Địa Chỉ", href: "/profile/addresses" },
                        { label: "Yêu Thích", href: "/wishlist" },
                      ].map(({ label, href }) => (
                        <Link key={href} to={href} onClick={() => setUserMenuOpen(false)}
                          className="flex items-center px-4 py-2.5 text-xs uppercase tracking-widest text-[#555] hover:text-[#111] hover:bg-[#f5f5f5] transition-colors">
                          {label}
                        </Link>
                      ))}
                      <div className="border-t border-[rgba(0,0,0,0.08)] mt-1 pt-1">
                        <button onClick={handleLogout}
                          className="w-full flex items-center gap-2 px-4 py-2.5 text-xs uppercase tracking-widest text-[#888] hover:text-[#E5001B] hover:bg-red-50 transition-colors">
                          <LogOut size={13} /> Đăng Xuất
                        </button>
                      </div>
                    </div>
                  )}
                </>
              ) : (
                <Link to="/login" className="text-[#888] hover:text-[#111] transition-colors" aria-label="Đăng nhập">
                  <User size={18} />
                </Link>
              )}
            </div>
            <Link to="/cart" className="relative text-[#111] hover:text-[#E5001B] transition-colors" aria-label="Giỏ hàng">
              <ShoppingBag size={20} />
              {count > 0 && (
                <span className="absolute -top-1.5 -right-1.5 w-4 h-4 bg-[#E5001B] text-white text-[9px] font-bold flex items-center justify-center rounded-full">
                  {count}
                </span>
              )}
            </Link>
            <button className="lg:hidden text-[#111]" onClick={() => setMenuOpen(!menuOpen)} aria-label="Menu">
              {menuOpen ? <X size={22} /> : <Menu size={22} />}
            </button>
          </div>
        </div>

        {menuOpen && (
          <div className="lg:hidden bg-white border-t border-[rgba(0,0,0,0.08)] px-6 py-6 flex flex-col gap-4">
            <div className="flex flex-col gap-3">
              {NAV_LINKS.map((link) => (
                <Link
                  key={link.label}
                  to={link.href}
                  onClick={() => setMenuOpen(false)}
                  className="text-sm font-semibold uppercase tracking-wider text-[#111] hover:text-[#E5001B] transition-colors flex items-center justify-between py-2 border-b border-[rgba(0,0,0,0.05)]"
                >
                  {link.label}
                  <ChevronRight size={15} className="text-[#888]" />
                </Link>
              ))}
            </div>

            <div className="pt-2">
              {isLoggedIn ? (
                <div className="space-y-3">
                  <div className="flex items-center gap-3 p-3 bg-gray-50 border border-gray-100">
                    <div className="w-8 h-8 bg-[#111] flex items-center justify-center text-white text-xs font-bold">
                      {user?.fullName?.[0]?.toUpperCase() || 'U'}
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="text-xs font-semibold text-[#111] truncate">{user?.fullName}</p>
                      <p className="text-[10px] text-[#888] truncate">{user?.email}</p>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-2 text-xs font-semibold uppercase tracking-wider text-center">
                    <Link to="/profile" onClick={() => setMenuOpen(false)} className="p-2.5 bg-gray-50 hover:bg-gray-100 text-[#111]">Hồ Sơ</Link>
                    <Link to="/profile/orders" onClick={() => setMenuOpen(false)} className="p-2.5 bg-gray-50 hover:bg-gray-100 text-[#111]">Đơn Hàng</Link>
                    <Link to="/profile/addresses" onClick={() => setMenuOpen(false)} className="p-2.5 bg-gray-50 hover:bg-gray-100 text-[#111]">Địa Chỉ</Link>
                    <Link to="/wishlist" onClick={() => setMenuOpen(false)} className="p-2.5 bg-gray-50 hover:bg-gray-100 text-[#111]">Yêu Thích</Link>
                  </div>
                  <button onClick={() => { handleLogout(); setMenuOpen(false); }} className="w-full flex items-center justify-center gap-2 py-2.5 text-xs uppercase tracking-widest text-[#E5001B] border border-[#E5001B]/20 hover:bg-red-50">
                    <LogOut size={13} /> Đăng Xuất
                  </button>
                </div>
              ) : (
                <div className="flex gap-2">
                  <Link to="/login" onClick={() => setMenuOpen(false)} className="flex-1 py-3 text-center text-xs uppercase tracking-widest font-semibold bg-[#111] text-white hover:bg-black">
                    Đăng Nhập
                  </Link>
                  <Link to="/register" onClick={() => setMenuOpen(false)} className="flex-1 py-3 text-center text-xs uppercase tracking-widest font-semibold border border-[#111] text-[#111] hover:bg-gray-50">
                    Đăng Ký
                  </Link>
                </div>
              )}
            </div>
          </div>
        )}
      </header>

      {/* Page content */}
      <main>
        <Outlet />
      </main>

      {/* Footer */}
      <footer className="bg-[#111] text-white">
        <div className="max-w-[1400px] mx-auto px-6 py-16">
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-5 gap-10 mb-12">
            <div className="col-span-2 md:col-span-4 lg:col-span-1">
              <p style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "2rem", letterSpacing: "-0.02em" }} className="mb-4">
                LINO
              </p>
              <p className="text-sm text-white/50 leading-relaxed" style={{ fontWeight: 300 }}>
                Thời trang tối giản. Chất lượng vượt trội. Thiết kế cho cuộc sống hiện đại.
              </p>
            </div>
            {[
              { title: "Sản Phẩm", links: [{ label: "Tất cả", href: "/products" }, { label: "Nam", href: "/products?gender=MEN" }, { label: "Nữ", href: "/products?gender=WOMEN" }, { label: "Unisex", href: "/products?gender=UNISEX" }] },
              { title: "Tài Khoản", links: [{ label: "Hồ sơ", href: "/profile" }, { label: "Địa chỉ", href: "/profile/addresses" }, { label: "Yêu thích", href: "/wishlist" }] },
              { title: "Hỗ Trợ", links: [{ label: "Theo dõi đơn", href: "/profile/orders" }, { label: "Thông tin mua hàng", href: "/policy" }, { label: "Liên hệ", href: "/contact" }] },
              { title: "Liên Hệ", links: [{ label: "support@lino.vn", href: "mailto:support@lino.vn" }, { label: "1800 1234", href: "tel:18001234" }] },
            ].map((col) => (
              <div key={col.title}>
                <p className="text-xs tracking-[0.2em] uppercase text-white/40 mb-5 font-medium">{col.title}</p>
                <ul className="space-y-3">
                  {col.links.map((link) => (
                    <li key={link.label}>
                      <a href={link.href} className="text-sm text-white/60 hover:text-white transition-colors" style={{ fontWeight: 300 }}>
                        {link.label}
                      </a>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
          <div className="border-t border-white/10 pt-8 flex flex-col sm:flex-row items-center justify-between gap-4">
            <p className="text-xs text-white/30">© 2025 LINO Vietnam. Bảo lưu mọi quyền.</p>
            <div className="flex items-center gap-6">
              <Link to="/policy" className="text-xs text-white/30 hover:text-white/70 transition-colors">Chính Sách</Link>
              <Link to="/contact" className="text-xs text-white/30 hover:text-white/70 transition-colors">Liên Hệ</Link>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
