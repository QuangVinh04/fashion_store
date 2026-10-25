import { customerError } from "../api/client";
import { useState, useEffect } from "react";
import { Link } from "react-router";
import { ArrowRight, Truck, ShieldCheck, RefreshCw } from "lucide-react";
import { store } from "../api/store";
import { motion } from "motion/react";
import { spring } from "../app/components/ui/animate-tokens";
import type { ProductSummary } from "../api/types";
import {
  ProductCard,
  SkeletonCard,
  Status,
  useLoad,
} from "../components/StoreUI";

export default function Home() {
  const [activeTab, setActiveTab] = useState<
    "ALL" | "MEN" | "WOMEN" | "UNISEX"
  >("ALL");
  const [tabProducts, setTabProducts] = useState<ProductSummary[]>([]);
  const [tabLoading, setTabLoading] = useState(false);
  const [tabError, setTabError] = useState("");

  const categories = useLoad(store.categories, []);

  useEffect(() => {
    let active = true;
    setTabLoading(true);
    setTabError("");
    const params: Record<string, string | number> = { page: 0, size: 8 };
    if (activeTab !== "ALL") {
      params.gender = activeTab;
    }
    store
      .products(params)
      .then((res) => {
        if (active) setTabProducts(res.items);
      })
      .catch((err) => {
        if (active) setTabError(customerError(err));
      })
      .finally(() => {
        if (active) setTabLoading(false);
      });
    return () => {
      active = false;
    };
  }, [activeTab]);

  const tabs: { key: "ALL" | "MEN" | "WOMEN" | "UNISEX"; label: string }[] = [
    { key: "ALL", label: "Tất Cả" },
    { key: "MEN", label: "Nam" },
    { key: "WOMEN", label: "Nữ" },
    { key: "UNISEX", label: "Unisex" },
  ];


  return (
    <div className="w-full">
      {/* Hero Section */}
      <section className="store-container store-section py-6 lg:py-12">
        <div className="grid lg:grid-cols-2 items-center gap-8 lg:gap-14">
          <div className="flex flex-col justify-center order-2 lg:order-1">
            <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-border-strong bg-secondary/40 px-3.5 py-1 text-xs font-medium text-foreground self-start">
              <span className="size-1.5 rounded-full bg-primary" />
              Bộ Sưu Tập Thu - Đông 2026
            </div>
            <h1 className="text-3xl sm:text-4xl lg:text-5xl font-bold tracking-tight text-foreground leading-[1.15] text-balance mb-5">
              Sống Tối Giản, <br />
              Mặc Tinh Tế.
            </h1>
            <p className="text-sm sm:text-base text-muted-foreground leading-relaxed max-w-md mb-8">
              Trang phục thiết kế hiện đại từ chất liệu Linen & Organic Cotton cao cấp — đường cắt chuẩn mực, tôn vinh phong thái tự tin mỗi ngày.
            </p>
            <div className="flex items-center gap-3.5 flex-wrap">
              <Link
                to="/products"
                className="inline-flex items-center gap-2.5 rounded-full bg-primary text-white px-7 py-3.5 text-xs font-semibold shadow-lg shadow-primary/20 hover:bg-primary-hover active:scale-[0.97] transition-all duration-200"
              >
                Khám Phá Ngay <ArrowRight size={15} />
              </Link>
              <Link
                to="/products?gender=MEN"
                className="inline-flex items-center gap-1.5 rounded-full border border-border-strong bg-card px-5 py-3.5 text-xs font-medium text-foreground hover:bg-secondary/60 active:scale-[0.97] transition-all"
              >
                Thời Trang Nam
              </Link>
              <Link
                to="/products?gender=WOMEN"
                className="inline-flex items-center gap-1.5 rounded-full border border-border-strong bg-card px-5 py-3.5 text-xs font-medium text-foreground hover:bg-secondary/60 active:scale-[0.97] transition-all"
              >
                Thời Trang Nữ
              </Link>
            </div>
          </div>

          <div className="relative overflow-hidden rounded-3xl bg-background aspect-[4/5] max-h-[540px] order-1 lg:order-2 group shadow-xl border border-border/60">
            <img
              src="https://images.unsplash.com/photo-1665832102316-9fd8e8f77c88?w=1000&h=1200&fit=crop&auto=format"
              alt="Bộ sưu tập thời trang LINO"
              className="w-full h-full object-cover transition-transform duration-700 ease-out group-hover:scale-105"
            />

            {/* Floating Lookbook Card */}
            <div className="absolute top-4 left-4 rounded-full border border-white/20 bg-black/40 backdrop-blur-md px-3.5 py-1.5 text-xs font-medium text-white shadow-lg">
              Lookbook '26 · Tự Nhiên & Tinh Tế
            </div>

            {/* Bottom Glass Card */}
            <div className="absolute bottom-4 left-4 right-4 flex items-center justify-between gap-3 rounded-2xl border border-white/20 bg-card/90 backdrop-blur-md p-4 shadow-xl">
              <div>
                <p className="text-[11px] font-semibold text-primary uppercase tracking-wider">
                  Premium Essentials
                </p>
                <p className="text-sm font-semibold text-foreground mt-0.5">
                  Bộ Sưu Tập LINO Autumn
                </p>
              </div>
              <Link
                to="/products"
                className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3.5 py-2 text-xs font-semibold text-white shadow-xs hover:bg-primary-hover active:scale-95 transition-all"
              >
                Xem Ngay <ArrowRight size={13} />
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* Value Badges */}
      <section className="border-y border-border/80 bg-background/50 py-8">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 grid grid-cols-1 md:grid-cols-3 gap-4 lg:gap-6">
          <div className="flex items-center gap-4 rounded-2xl border border-border bg-card p-4 sm:p-5 shadow-xs hover:shadow-md transition-all">
            <div className="size-12 rounded-2xl bg-primary-light flex items-center justify-center text-primary shrink-0">
              <Truck size={22} />
            </div>
            <div>
              <p className="text-sm font-semibold text-foreground">
                Miễn Phí Vận Chuyển
              </p>
              <p className="text-xs text-muted-foreground mt-0.5 leading-relaxed">
                Giao nhanh toàn quốc cho đơn hàng từ 500.000 ₫
              </p>
            </div>
          </div>

          <div className="flex items-center gap-4 rounded-2xl border border-border bg-card p-4 sm:p-5 shadow-xs hover:shadow-md transition-all">
            <div className="size-12 rounded-2xl bg-primary-light flex items-center justify-center text-primary shrink-0">
              <RefreshCw size={22} />
            </div>
            <div>
              <p className="text-sm font-semibold text-foreground">
                Đổi Trả Dễ Dàng
              </p>
              <p className="text-xs text-muted-foreground mt-0.5 leading-relaxed">
                Đổi trả miễn phí tận nơi trong vòng 7 ngày
              </p>
            </div>
          </div>

          <div className="flex items-center gap-4 rounded-2xl border border-border bg-card p-4 sm:p-5 shadow-xs hover:shadow-md transition-all">
            <div className="size-12 rounded-2xl bg-primary-light flex items-center justify-center text-primary shrink-0">
              <ShieldCheck size={22} />
            </div>
            <div>
              <p className="text-sm font-semibold text-foreground">
                Chất Lượng Chuẩn Mực
              </p>
              <p className="text-xs text-muted-foreground mt-0.5 leading-relaxed">
                100% thiết kế chuẩn phom, chất vải organic tinh tuyển
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Featured Categories */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 py-8 md:py-10">
        <div className="flex items-end justify-between mb-6">
          <div>
            <p className="text-xs text-muted-foreground font-semibold mb-2">
              Bộ Sưu Tập
            </p>
            <h2 className="text-foreground font-semibold leading-none text-balance">
              DANH MỤC SẢN PHẨM
            </h2>
          </div>
          <Link
            to="/products"
            className="text-xs text-foreground font-semibold hover:text-primary border-b border-border-strong pb-1 hover:border-primary transition-colors store-action"
          >
            Xem Tất Cả
          </Link>
        </div>

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 md:gap-6">
          {[
            {
              label: "Thời Trang Nam",
              href: "/products?gender=MEN",
              sub: "Áo sơ mi, polo & quần tây thanh lịch",
              img: "https://images.unsplash.com/photo-1617137984095-74e4e5e3613f?w=600&h=800&fit=crop&auto=format",
            },
            {
              label: "Thời Trang Nữ",
              href: "/products?gender=WOMEN",
              sub: "Váy đầm, áo kiểu & blazer sang trọng",
              img: "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=600&h=800&fit=crop&auto=format",
            },
            {
              label: "Thời Trang Unisex",
              href: "/products?gender=UNISEX",
              sub: "T-shirt, hoodie & phong cách phóng khoáng",
              img: "https://images.unsplash.com/photo-1529139574466-a303027c1d8b?w=600&h=800&fit=crop&auto=format",
            },
            {
              label: "Bộ Sưu Tập Ưu Đãi",
              href: "/products",
              sub: "Thiết kế mới tinh tuyển & giá ưu đãi",
              img: "https://images.unsplash.com/photo-1490481651871-ab68de25d43d?w=600&h=800&fit=crop&auto=format",
            },
          ].map((cat) => (
            <Link
              key={cat.label}
              to={cat.href}
              className="group flex flex-col overflow-hidden rounded-2xl border border-border/80 bg-card hover:border-primary/40 hover:shadow-md transition-all duration-300"
            >
              <div className="aspect-[4/5] overflow-hidden bg-secondary relative">
                <img
                  src={cat.img}
                  alt={cat.label}
                  loading="lazy"
                  className="size-full object-cover transition-transform duration-700 ease-out group-hover:scale-105"
                />
              </div>
              <div className="p-4 sm:p-5 flex flex-col justify-between flex-1 gap-1.5">
                <div className="flex items-center justify-between gap-2">
                  <p className="text-base font-semibold text-foreground group-hover:text-primary transition-colors">
                    {cat.label}
                  </p>
                  <div className="size-7 rounded-full bg-secondary flex items-center justify-center text-muted-foreground group-hover:bg-primary group-hover:text-white transition-all shrink-0">
                    <ArrowRight
                      size={13}
                      className="group-hover:translate-x-0.5 transition-transform"
                    />
                  </div>
                </div>
                <p className="text-xs text-muted-foreground leading-relaxed">
                  {cat.sub}
                </p>
              </div>
            </Link>
          ))}
        </div>
      </section>

      {/* Promo Banner */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 py-6 md:py-8">
        <div className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-neutral-900 via-neutral-800 to-neutral-900 text-white p-6 sm:p-10 grid lg:grid-cols-[1fr_auto] items-center gap-6 shadow-xl border border-neutral-700/50">
          <div className="space-y-2">
            <span className="inline-block rounded-full bg-white/10 px-3 py-1 text-xs font-medium text-white/90 backdrop-blur-xs">
              Đặc Quyền Thành Viên Mới
            </span>
            <h2 className="text-2xl sm:text-3xl font-bold tracking-tight text-white leading-snug">
              GIẢM 30% CHO ĐƠN HÀNG ĐẦU TIÊN
            </h2>
            <p className="text-sm text-neutral-300 max-w-lg leading-relaxed">
              Nhập mã ưu đãi ở bước thanh toán để nhận ngay đặc quyền chào đón từ LINO.
            </p>
          </div>
          <div className="flex flex-col sm:flex-row items-start sm:items-center gap-3">
            <div className="flex items-center gap-2 rounded-xl border border-dashed border-white/30 bg-white/10 px-4 py-2.5 backdrop-blur-xs">
              <span className="text-xs text-neutral-300">Mã voucher:</span>
              <span className="font-mono text-base font-bold tracking-wider text-white">
                LINOMOI
              </span>
            </div>
            <Link
              to="/products"
              className="inline-flex items-center gap-2 rounded-xl bg-primary px-5 py-3 text-xs font-semibold text-white shadow-lg shadow-primary/30 hover:bg-primary-hover active:scale-95 transition-all"
            >
              Mua Sắm Ngay <ArrowRight size={14} />
            </Link>
          </div>
        </div>
      </section>

      {/* New Arrivals with Tabs */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 py-8 md:py-10">
        <div className="flex flex-col sm:flex-row sm:items-end justify-between mb-8 gap-4">
          <div>
            <p className="text-xs text-muted-foreground font-semibold mb-2">
              Sản Phẩm Đang Mở Bán
            </p>
            <h2 className="text-foreground font-semibold leading-none text-balance">
              SẢN PHẨM MỚI NHẤT
            </h2>
          </div>

          <Link
            to="/products"
            className="text-xs text-foreground font-semibold hover:text-primary border-b border-border-strong pb-1 hover:border-primary transition-colors self-start sm:self-auto store-action"
          >
            Tất Cả Sản Phẩm
          </Link>
        </div>

        {/* Category Tabs */}
        <div className="flex gap-1 mb-6 rounded-xl bg-secondary/60 p-1 overflow-x-auto relative">
          {tabs.map((tab) => {
            const isActive = activeTab === tab.key;
            return (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                aria-pressed={isActive}
                className={`relative px-4 py-2 text-sm font-medium whitespace-nowrap transition-colors rounded-lg store-button ${
                  isActive
                    ? "text-foreground font-semibold"
                    : "text-muted-foreground hover:text-foreground"
                }`}
              >
                {isActive && (
                  <motion.span
                    layoutId="category-tab-active"
                    transition={spring.selection}
                    className="absolute inset-0 bg-white rounded-lg shadow-xs dark:bg-card"
                    style={{ zIndex: 0 }}
                  />
                )}
                <span className="relative z-10">{tab.label}</span>
              </button>
            );
          })}
        </div>

        <Status
          loading={tabLoading}
          error={tabError}
          retry={() => setActiveTab((prev) => prev)}
        />

        {tabLoading ? (
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
            {Array.from({ length: 8 }).map((_, i) => (
              <SkeletonCard key={i} />
            ))}
          </div>
        ) : tabProducts.length > 0 ? (
          <motion.div
            key={activeTab}
            initial="hidden"
            animate="visible"
            variants={{
              hidden: { opacity: 0 },
              visible: {
                opacity: 1,
                transition: {
                  staggerChildren: 0.05,
                },
              },
            }}
            className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3"
          >
            {tabProducts.map((p) => (
              <motion.div
                key={p.id}
                variants={{
                  hidden: { opacity: 0, y: 14 },
                  visible: {
                    opacity: 1,
                    y: 0,
                    transition: {
                      type: "spring",
                      stiffness: 300,
                      damping: 24,
                    },
                  },
                }}
              >
                <ProductCard product={p} />
              </motion.div>
            ))}
          </motion.div>
        ) : (
          !tabError && (
            <div className="py-8 md:py-10 text-center border border-dashed border-border-strong p-8">
              <p className="text-sm text-muted-foreground">
                Chưa có sản phẩm nào thuộc phân loại này.
              </p>
              <Link
                to="/products"
                className="mt-4 inline-block text-xs underline font-semibold text-foreground store-text-link"
              >
                Xem tất cả sản phẩm
              </Link>
            </div>
          )
        )}
      </section>

      {/* Brand Story & Values */}
      <section className="bg-background py-8 md:py-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6">
          <div className="max-w-2xl mx-auto text-center mb-6">
            <p className="text-xs text-muted-foreground font-semibold mb-3">
              Triết Lý Thương Hiệu
            </p>
            <h2 className="text-foreground font-semibold leading-tight text-balance">
              TẠI SAO CHỌN THỜI TRANG LINO?
            </h2>
          </div>

          <div className="grid md:grid-cols-3 gap-3">
            {[
              {
                num: "01",
                title: "Chất Liệu Tuyển Chọn",
                desc: "Cotton dệt mịn, sợi linen thoáng khí và len pha cao cấp mang lại sự thoải mái tối đa cho cả ngày dài vận động.",
              },
              {
                num: "02",
                title: "Thiết Kế Tối Giản Vượt Thời Gian",
                desc: "Cắt may tinh giản, phom dáng chuẩn mực dễ dàng ứng dụng và phối hợp cho nhiều hoàn cảnh từ công sở tới dạo phố.",
              },
              {
                num: "03",
                title: "Minh Bạch & Trách Nhiệm",
                desc: "Chính sách mua sắm rõ ràng, hỗ trợ đổi trả thuận tiện và quy trình sản xuất đề cao độ bền bỉ của sản phẩm.",
              },
            ].map((v) => (
              <div
                key={v.num}
                className="bg-white p-4 sm:p-5 border border-border flex flex-col justify-between rounded-2xl"
              >
                <div>
                  <span className="text-primary font-semibold text-base block mb-4">
                    {v.num}
                  </span>
                  <h3 className="text-base font-semibold text-foreground mb-3 text-balance">
                    {v.title}
                  </h3>
                  <p className="text-sm text-muted-foreground leading-relaxed">
                    {v.desc}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Newsletter */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 py-8 md:py-10 text-center">
        <h2 className="text-foreground font-semibold mb-4 text-balance">
          NHẬN THÔNG TIN BỘ SƯU TẬP MỚI
        </h2>
        <p className="text-sm text-muted-foreground mb-8 max-w-md mx-auto">
          Đăng ký nhận thông tin qua email hiện chưa khả dụng. Bạn có thể khám
          phá sản phẩm tại cửa hàng.
        </p>

        <Link to="/products" className="store-action inline-flex items-center bg-primary px-6 py-3 text-white hover:bg-primary-hover">Khám phá sản phẩm</Link>
      </section>
    </div>
  );
}
