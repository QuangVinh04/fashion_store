import { customerError } from "../api/client";
import { useState, useEffect } from "react";
import { Link } from "react-router";
import { ArrowRight, Truck, ShieldCheck, RefreshCw } from "lucide-react";
import { store } from "../api/store";
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
      <section className="store-container store-section">
        <div className="grid lg:grid-cols-2 items-center gap-6 lg:gap-10">
          <div className="flex flex-col justify-center order-2 lg:order-1">
            <p className="text-xs text-muted-foreground font-semibold mb-6">
              LINO · Bộ Sưu Tập
            </p>
            <h1 className="store-hero mb-6 text-foreground font-semibold text-balance">
              SỐNG ĐƠN <span className="text-primary">GIẢN</span>
            </h1>
            <p className="text-base text-muted-foreground leading-relaxed max-w-md mb-6">
              Trang phục tối giản thiết kế cho cuộc sống hiện đại — phom dáng
              chuẩn xác, chất liệu tự nhiên được tinh tuyển kỹ lưỡng và hoàn
              thiện tỉ mỉ.
            </p>
            <div className="flex items-center gap-5 flex-wrap">
              <Link
                to="/products"
                className="inline-flex items-center gap-3 bg-primary text-white px-8 py-4 text-xs font-semibold hover:bg-primary-hover active:scale-[0.98] transition-all duration-300 store-action"
              >
                Khám Phá Ngay <ArrowRight size={14} />
              </Link>
              <Link
                to="/products?gender=MEN"
                className="inline-flex items-center gap-2 text-xs text-foreground font-semibold border-b border-border-strong pb-1 hover:border-primary hover:text-primary transition-colors store-action"
              >
                Thời Trang Nam
              </Link>
              <Link
                to="/products?gender=WOMEN"
                className="inline-flex items-center gap-2 text-xs text-foreground font-semibold border-b border-border-strong pb-1 hover:border-primary hover:text-primary transition-colors store-action"
              >
                Thời Trang Nữ
              </Link>
            </div>
          </div>

          <div className="relative overflow-hidden rounded-2xl bg-background aspect-[4/5] max-h-[520px] order-1 lg:order-2 group">
            <img
              src="https://images.unsplash.com/photo-1665832102316-9fd8e8f77c88?w=1000&h=1200&fit=crop&auto=format"
              alt="Bộ sưu tập thời trang LINO"
              className="w-full h-full object-cover transition-transform duration-700 ease-out group-hover:scale-105"
            />

            <div className="absolute bottom-4 left-4 right-4 flex flex-wrap items-center justify-between gap-2 bg-white p-4 rounded-xl">
              <div>
                <p className="text-xs text-muted-foreground font-medium">
                  Thời Trang Tối Giản
                </p>
                <p className="text-sm font-semibold text-foreground mt-0.5">
                  Bộ Sưu Tập LINO
                </p>
              </div>
              <Link
                to="/products"
                className="text-xs font-semibold text-foreground hover:text-primary flex items-center gap-1 underline underline-offset-4 store-text-link"
              >
                Xem Ngay <ArrowRight size={12} />
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* Value Badges */}
      <section className="border-y border-border bg-background">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="flex items-center gap-4">
            <Truck size={24} className="text-foreground shrink-0" />
            <div>
              <p className="text-xs font-semibold text-foreground">
                Miễn Phí Vận Chuyển
              </p>
              <p className="text-xs text-muted-foreground mt-0.5">
                Áp dụng cho mọi đơn hàng từ 500.000 ₫
              </p>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <RefreshCw size={24} className="text-foreground shrink-0" />
            <div>
              <p className="text-xs font-semibold text-foreground">
                Đổi Trả Dễ Dàng
              </p>
              <p className="text-xs text-muted-foreground mt-0.5">
                Hỗ trợ yêu cầu trả hàng trong vòng 7 ngày
              </p>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <ShieldCheck size={24} className="text-foreground shrink-0" />
            <div>
              <p className="text-xs font-semibold text-foreground">
                Chất Lượng Đảm Bảo
              </p>
              <p className="text-xs text-muted-foreground mt-0.5">
                100% sản phẩm thiết kế & gia công chuẩn mực
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
              label: "Nam",
              href: "/products?gender=MEN",
              sub: "Áo sơ mi, polo & quần tây",
              img: "https://images.unsplash.com/photo-1617137984095-74e4e5e3613f?w=600&h=800&fit=crop&auto=format",
            },
            {
              label: "Nữ",
              href: "/products?gender=WOMEN",
              sub: "Váy đầm, áo kiểu & blazer",
              img: "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=600&h=800&fit=crop&auto=format",
            },
            {
              label: "Unisex",
              href: "/products?gender=UNISEX",
              sub: "T-shirt, hoodie & phong cách tự do",
              img: "https://images.unsplash.com/photo-1529139574466-a303027c1d8b?w=600&h=800&fit=crop&auto=format",
            },
            {
              label: "Ưu Đãi",
              href: "/products",
              sub: "Thiết kế mới & giá ưu đãi",
              img: "https://images.unsplash.com/photo-1490481651871-ab68de25d43d?w=600&h=800&fit=crop&auto=format",
            },
          ].map((cat) => (
            <Link
              key={cat.label}
              to={cat.href}
              className="group overflow-hidden rounded-2xl border border-border bg-card block store-text-link"
            >
              <div className="aspect-[3/4] overflow-hidden">
                <img
                  src={cat.img}
                  alt={cat.label}
                  loading="lazy"
                  className="size-full object-cover transition-transform duration-500 group-hover:scale-105"
                />
              </div>
              <div className="flex items-start justify-between gap-2 p-4">
                <div className="min-w-0">
                  <p className="text-base font-medium">{cat.label}</p>
                  <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
                    {cat.sub}
                  </p>
                </div>
                <ArrowRight
                  size={16}
                  className="mt-1 shrink-0 text-muted-foreground"
                />
              </div>
            </Link>
          ))}
        </div>
      </section>

      {/* Promo Banner */}
      <section className="bg-primary-light text-foreground">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8 md:py-10 grid lg:grid-cols-[1fr_auto] items-center gap-8">
          <div>
            <p className="text-xs text-muted-foreground font-medium mb-3">
              Ưu Đãi Thành Viên Mới
            </p>
            <h2 className="text-lg font-semibold leading-snug text-balance">
              GIẢM 30% CHO ĐƠN HÀNG ĐẦU TIÊN
            </h2>
          </div>
          <div className="flex flex-col gap-4 max-w-sm">
            <p className="text-sm text-muted-foreground leading-relaxed">
              Sử dụng mã ưu đãi{" "}
              <strong className="text-foreground font-semibold bg-white rounded px-2 py-0.5">
                LINOMOI
              </strong>{" "}
              ở bước thanh toán để nhận giảm giá.
            </p>
            <Link
              to="/products"
              className="inline-flex items-center gap-3 bg-primary text-white px-5 py-2.5 text-sm font-medium hover:bg-primary-hover transition-colors duration-300 self-start store-action"
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
        <div className="flex gap-1 mb-6 rounded-xl bg-secondary/60 p-1 overflow-x-auto">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              aria-pressed={activeTab === tab.key}
              className={`px-4 py-2 text-sm font-medium transition-colors whitespace-nowrap ${
                activeTab === tab.key
                  ? "bg-white text-foreground"
                  : "text-muted-foreground hover:text-foreground"
              } store-button`}
            >
              {tab.label}
            </button>
          ))}
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
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
            {tabProducts.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
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
