import { useState } from "react";
import { Link, Outlet, useLocation, useNavigate } from "react-router";
import { AdminDataProvider } from "../context/AdminDataContext";
import {
  LayoutDashboard, Package, ShoppingCart, Users, BarChart2,
  Bell, Search, Menu, X, ChevronRight, LogOut, Settings,
  TrendingUp, Tag, MessageSquare, Layers, Palette,
} from "lucide-react";

const NAV_GROUPS = [
  {
    label: "Tổng Quan",
    items: [
      { href: "/admin", label: "Dashboard", icon: LayoutDashboard, exact: true },
      { href: "/admin/analytics", label: "Phân Tích", icon: BarChart2 },
    ],
  },
  {
    label: "Danh Mục",
    items: [
      { href: "/admin/products", label: "Sản Phẩm", icon: Package },
      { href: "/admin/attributes", label: "Thuộc Tính", icon: Layers },
      { href: "/admin/colors-sizes", label: "Màu & Kích Cỡ", icon: Palette },
      { href: "/admin/orders", label: "Đơn Hàng", icon: ShoppingCart },
      { href: "/admin/customers", label: "Khách Hàng", icon: Users },
      { href: "/admin/promotions", label: "Khuyến Mãi", icon: Tag },
    ],
  },
  {
    label: "Hệ Thống",
    items: [
      { href: "/admin/reviews", label: "Đánh Giá", icon: MessageSquare },
      { href: "/admin/settings", label: "Cài Đặt", icon: Settings },
    ],
  },
];

const STATS = [
  { label: "Doanh Thu Tháng", value: "248.5M ₫", change: "+12.4%", up: true },
  { label: "Đơn Hàng Mới", value: "1,284", change: "+8.1%", up: true },
  { label: "Sản Phẩm", value: "12", change: "+2", up: true },
  { label: "Khách Hàng", value: "3,847", change: "+5.3%", up: true },
];

export default function AdminLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();

  const isActive = (href: string, exact = false) => {
    if (exact) return location.pathname === href;
    return location.pathname.startsWith(href);
  };

  const isHome = location.pathname === "/admin";

  return (
    <AdminDataProvider>
    <div className="min-h-screen bg-[#f4f4f6] flex" style={{ fontFamily: "'Inter', sans-serif" }}>
      {/* Mobile overlay */}
      {mobileSidebarOpen && (
        <div className="fixed inset-0 bg-black/50 z-40 lg:hidden" onClick={() => setMobileSidebarOpen(false)} />
      )}

      {/* Sidebar */}
      <aside className={`fixed top-0 left-0 bottom-0 z-50 flex flex-col bg-[#111] transition-all duration-300 ${sidebarOpen ? "w-64" : "w-16"} ${mobileSidebarOpen ? "translate-x-0" : "-translate-x-full lg:translate-x-0"}`}>
        {/* Logo */}
        <div className={`flex items-center gap-3 px-4 h-16 border-b border-white/10 flex-shrink-0 ${sidebarOpen ? "justify-between" : "justify-center"}`}>
          {sidebarOpen && (
            <Link to="/" className="flex items-center gap-2" title="Về trang chủ">
              <span style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "1.5rem", letterSpacing: "-0.02em", color: "#fff" }}>LINO</span>
              <span className="text-[10px] uppercase tracking-widest text-white/40 border border-white/20 px-1.5 py-0.5">Admin</span>
            </Link>
          )}
          <button onClick={() => setSidebarOpen(!sidebarOpen)} className="hidden lg:flex w-8 h-8 items-center justify-center text-white/40 hover:text-white transition-colors flex-shrink-0">
            <Menu size={16} />
          </button>
          <button onClick={() => setMobileSidebarOpen(false)} className="lg:hidden text-white/40 hover:text-white">
            <X size={18} />
          </button>
        </div>

        {/* Nav */}
        <nav className="flex-1 overflow-y-auto py-4 space-y-6">
          {NAV_GROUPS.map((group) => (
            <div key={group.label}>
              {sidebarOpen && (
                <p className="px-4 mb-2 text-[9px] uppercase tracking-[0.2em] text-white/30 font-semibold">{group.label}</p>
              )}
              <div className="space-y-0.5 px-2">
                {group.items.map(({ href, label, icon: Icon, exact }) => {
                  const active = isActive(href, exact);
                  return (
                    <Link
                      key={href}
                      to={href}
                      title={!sidebarOpen ? label : undefined}
                      className={`flex items-center gap-3 px-3 py-2.5 text-sm transition-all duration-150 group relative ${active ? "bg-white text-[#111]" : "text-white/60 hover:text-white hover:bg-white/10"} ${!sidebarOpen ? "justify-center" : ""}`}
                    >
                      <Icon size={16} className="flex-shrink-0" />
                      {sidebarOpen && <span className="font-medium">{label}</span>}
                      {active && sidebarOpen && <ChevronRight size={13} className="ml-auto opacity-40" />}
                    </Link>
                  );
                })}
              </div>
            </div>
          ))}
        </nav>

        {/* Bottom */}
        <div className="flex-shrink-0 border-t border-white/10 p-3">
          <Link
            to="/"
            className={`flex items-center gap-3 px-3 py-2.5 text-sm text-white/50 hover:text-white hover:bg-white/10 transition-colors ${!sidebarOpen ? "justify-center" : ""}`}
            title={!sidebarOpen ? "Về trang chủ" : undefined}
          >
            <LogOut size={15} className="flex-shrink-0" />
            {sidebarOpen && <span>Về Trang Chủ</span>}
          </Link>
        </div>
      </aside>

      {/* Main */}
      <div className={`flex-1 flex flex-col min-w-0 transition-all duration-300 ${sidebarOpen ? "lg:ml-64" : "lg:ml-16"}`}>
        {/* Topbar */}
        <header className="sticky top-0 z-30 bg-white border-b border-[rgba(0,0,0,0.08)] h-16 flex items-center px-6 gap-4">
          <button onClick={() => setMobileSidebarOpen(true)} className="lg:hidden text-[#888] hover:text-[#111]">
            <Menu size={20} />
          </button>

          {/* Search */}
          <div className="flex-1 max-w-md">
            <div className="relative">
              <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-[#aaa]" />
              <input
                type="text"
                placeholder="Tìm kiếm sản phẩm, đơn hàng..."
                className="w-full pl-9 pr-4 py-2 text-sm border border-[rgba(0,0,0,0.1)] bg-[#f8f8f8] text-[#111] placeholder:text-[#bbb] focus:outline-none focus:border-[#111] transition-colors"
              />
            </div>
          </div>

          <div className="ml-auto flex items-center gap-3">
            <button className="relative w-9 h-9 flex items-center justify-center text-[#888] hover:text-[#111] transition-colors">
              <Bell size={18} />
              <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-[#E5001B] rounded-full" />
            </button>
            <button onClick={() => navigate("/profile")} className="w-8 h-8 bg-[#111] flex items-center justify-center">
              <span className="text-white text-xs font-bold">A</span>
            </button>
          </div>
        </header>

        {/* Page Content */}
        <main className="flex-1 p-6">
          {isHome ? (
            /* Mini Dashboard */
            <div>
              <div className="mb-8">
                <p className="text-xs uppercase tracking-widest text-[#888] mb-1">Tổng Quan</p>
                <h1 style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "2rem", letterSpacing: "-0.01em", color: "#111" }}>
                  DASHBOARD
                </h1>
              </div>

              {/* Stats */}
              <div className="grid grid-cols-2 xl:grid-cols-4 gap-4 mb-8">
                {STATS.map((s) => (
                  <div key={s.label} className="bg-white border border-[rgba(0,0,0,0.08)] p-5">
                    <p className="text-xs uppercase tracking-widest text-[#888] mb-3">{s.label}</p>
                    <p className="text-2xl font-bold text-[#111] mb-2" style={{ fontFamily: "'Barlow Condensed', sans-serif", letterSpacing: "-0.02em" }}>{s.value}</p>
                    <div className={`flex items-center gap-1 text-xs font-medium ${s.up ? "text-[#2D5A3D]" : "text-[#E5001B]"}`}>
                      <TrendingUp size={12} />
                      {s.change} so với tháng trước
                    </div>
                  </div>
                ))}
              </div>

              {/* Quick Access */}
              <div className="grid md:grid-cols-3 gap-4">
                {[
                  { href: "/admin/products", label: "Quản Lý Sản Phẩm", desc: "12 sản phẩm đang hiển thị", icon: Package },
                  { href: "/admin/orders", label: "Quản Lý Đơn Hàng", desc: "8 đơn hàng chờ xử lý", icon: ShoppingCart },
                  { href: "/admin/customers", label: "Quản Lý Khách Hàng", desc: "3.847 thành viên", icon: Users },
                ].map(({ href, label, desc, icon: Icon }) => (
                  <Link key={href} to={href} className="bg-white border border-[rgba(0,0,0,0.08)] p-6 flex items-center gap-4 hover:border-[#111] transition-colors group">
                    <div className="w-10 h-10 bg-[#f5f5f5] flex items-center justify-center flex-shrink-0 group-hover:bg-[#111] transition-colors">
                      <Icon size={18} className="text-[#888] group-hover:text-white transition-colors" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-[#111]">{label}</p>
                      <p className="text-xs text-[#888] mt-0.5">{desc}</p>
                    </div>
                    <ChevronRight size={16} className="ml-auto text-[#ccc] group-hover:text-[#111] transition-colors" />
                  </Link>
                ))}
              </div>
            </div>
          ) : (
            <Outlet />
          )}
        </main>
      </div>
    </div>
    </AdminDataProvider>
  );
}
