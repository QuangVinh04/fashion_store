import { useState, useEffect } from 'react';
import { Link } from 'react-router';
import { ArrowRight, Truck, ShieldCheck, RefreshCw } from 'lucide-react';
import { store } from '../api/store';
import type { ProductSummary } from '../api/types';
import { ProductCard, SkeletonCard, Status, useLoad } from '../components/StoreUI';

export default function Home() {
  const [activeTab, setActiveTab] = useState<'ALL' | 'MEN' | 'WOMEN' | 'UNISEX'>('ALL');
  const [tabProducts, setTabProducts] = useState<ProductSummary[]>([]);
  const [tabLoading, setTabLoading] = useState(false);
  const [tabError, setTabError] = useState('');
  const [newsletterEmail, setNewsletterEmail] = useState('');
  const [newsletterSubmitted, setNewsletterSubmitted] = useState(false);

  const categories = useLoad(store.categories, []);

  useEffect(() => {
    let active = true;
    setTabLoading(true);
    setTabError('');
    const params: Record<string, string | number> = { page: 0, size: 8 };
    if (activeTab !== 'ALL') {
      params.gender = activeTab;
    }
    store
      .products(params)
      .then((res) => {
        if (active) setTabProducts(res.items);
      })
      .catch((err) => {
        if (active) setTabError((err as Error).message);
      })
      .finally(() => {
        if (active) setTabLoading(false);
      });
    return () => {
      active = false;
    };
  }, [activeTab]);

  const tabs: { key: 'ALL' | 'MEN' | 'WOMEN' | 'UNISEX'; label: string }[] = [
    { key: 'ALL', label: 'Tất Cả' },
    { key: 'MEN', label: 'Nam' },
    { key: 'WOMEN', label: 'Nữ' },
    { key: 'UNISEX', label: 'Unisex' },
  ];

  const handleNewsletterSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newsletterEmail) return;
    setNewsletterSubmitted(true);
  };

  return (
    <div style={{ fontFamily: "'Inter', sans-serif" }} className="w-full">
      {/* Hero Section */}
      <section className="max-w-[1400px] mx-auto px-6 py-8 md:py-16">
        <div className="grid lg:grid-cols-2 min-h-[75vh] items-center gap-10">
          <div className="flex flex-col justify-center order-2 lg:order-1">
            <p className="text-xs tracking-[0.3em] uppercase text-[#888] font-semibold mb-6">
              LINO · Bộ Sưu Tập 2025
            </p>
            <h1
              className="leading-[0.88] mb-8 text-[#111] font-black uppercase"
              style={{
                fontFamily: "'Barlow Condensed', sans-serif",
                fontSize: 'clamp(4.5rem, 10vw, 8.5rem)',
                letterSpacing: '-0.02em',
              }}
            >
              SỐNG<br />
              ĐƠN<br />
              <span className="text-[#E5001B]">GIẢN</span>
            </h1>
            <p
              className="text-base text-[#555] leading-relaxed max-w-md mb-10"
              style={{ fontWeight: 300 }}
            >
              Trang phục tối giản thiết kế cho cuộc sống hiện đại — phom dáng chuẩn xác, chất liệu tự
              nhiên được tinh tuyển kỹ lưỡng và hoàn thiện tỉ mỉ.
            </p>
            <div className="flex items-center gap-5 flex-wrap">
              <Link
                to="/products"
                className="inline-flex items-center gap-3 bg-[#111] text-white px-8 py-4 text-xs tracking-widest uppercase font-semibold hover:bg-[#E5001B] active:scale-[0.98] transition-all duration-300"
              >
                Khám Phá Ngay <ArrowRight size={14} />
              </Link>
              <Link
                to="/products?gender=MEN"
                className="inline-flex items-center gap-2 text-xs tracking-widest uppercase text-[#111] font-semibold border-b border-[#111] pb-1 hover:border-[#E5001B] hover:text-[#E5001B] transition-colors"
              >
                Thời Trang Nam
              </Link>
              <Link
                to="/products?gender=WOMEN"
                className="inline-flex items-center gap-2 text-xs tracking-widest uppercase text-[#111] font-semibold border-b border-[#111] pb-1 hover:border-[#E5001B] hover:text-[#E5001B] transition-colors"
              >
                Thời Trang Nữ
              </Link>
            </div>
          </div>

          <div className="relative overflow-hidden bg-[#f5f5f5] aspect-[4/5] order-1 lg:order-2 group">
            <img
              src="https://images.unsplash.com/photo-1665832102316-9fd8e8f77c88?w=1000&h=1200&fit=crop&auto=format"
              alt="Bộ sưu tập thời trang LINO"
              className="w-full h-full object-cover transition-transform duration-700 ease-out group-hover:scale-105"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-transparent pointer-events-none" />
            <div className="absolute bottom-6 left-6 right-6 flex items-end justify-between bg-white/95 backdrop-blur-xs p-5 border border-white/20">
              <div>
                <p className="text-[10px] uppercase tracking-widest text-[#888] font-medium">
                  Thời Trang Tối Giản
                </p>
                <p className="text-sm font-semibold text-[#111] mt-0.5">
                  Bộ Sưu Tập Xu Hướng 2025
                </p>
              </div>
              <Link
                to="/products"
                className="text-xs uppercase tracking-widest font-semibold text-[#111] hover:text-[#E5001B] flex items-center gap-1 underline underline-offset-4"
              >
                Xem Ngay <ArrowRight size={12} />
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* Value Badges */}
      <section className="border-y border-[rgba(0,0,0,0.08)] bg-[#fafafa]">
        <div className="max-w-[1400px] mx-auto px-6 py-6 grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="flex items-center gap-4">
            <Truck size={24} className="text-[#111] shrink-0" />
            <div>
              <p className="text-xs uppercase font-bold tracking-wider text-[#111]">
                Miễn Phí Vận Chuyển
              </p>
              <p className="text-xs text-[#777] mt-0.5">Áp dụng cho mọi đơn hàng từ 500.000 ₫</p>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <RefreshCw size={24} className="text-[#111] shrink-0" />
            <div>
              <p className="text-xs uppercase font-bold tracking-wider text-[#111]">
                Đổi Trả Dễ Dàng
              </p>
              <p className="text-xs text-[#777] mt-0.5">Hỗ trợ yêu cầu trả hàng trong vòng 7 ngày</p>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <ShieldCheck size={24} className="text-[#111] shrink-0" />
            <div>
              <p className="text-xs uppercase font-bold tracking-wider text-[#111]">
                Chất Lượng Đảm Bảo
              </p>
              <p className="text-xs text-[#777] mt-0.5">100% sản phẩm thiết kế & gia công chuẩn mực</p>
            </div>
          </div>
        </div>
      </section>

      {/* Featured Categories */}
      <section className="max-w-[1400px] mx-auto px-6 py-16 md:py-24">
        <div className="flex items-end justify-between mb-10">
          <div>
            <p className="text-xs uppercase tracking-[0.25em] text-[#888] font-semibold mb-2">
              Bộ Sưu Tập
            </p>
            <h2
              className="text-[#111] font-black uppercase leading-none"
              style={{
                fontFamily: "'Barlow Condensed', sans-serif",
                fontSize: 'clamp(2rem, 5vw, 3.5rem)',
                letterSpacing: '-0.01em',
              }}
            >
              DANH MỤC SẢN PHẨM
            </h2>
          </div>
          <Link
            to="/products"
            className="text-xs uppercase tracking-widest text-[#111] font-semibold hover:text-[#E5001B] border-b border-[#111] pb-1 hover:border-[#E5001B] transition-colors"
          >
            Xem Tất Cả
          </Link>
        </div>

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 md:gap-6">
          {[
            {
              label: 'Nam',
              href: '/products?gender=MEN',
              sub: 'Áo sơ mi, polo & quần tây',
              img: 'https://images.unsplash.com/photo-1617137984095-74e4e5e3613f?w=600&h=800&fit=crop&auto=format',
            },
            {
              label: 'Nữ',
              href: '/products?gender=WOMEN',
              sub: 'Váy đầm, áo kiểu & blazer',
              img: 'https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=600&h=800&fit=crop&auto=format',
            },
            {
              label: 'Unisex',
              href: '/products?gender=UNISEX',
              sub: 'T-shirt, hoodie & phong cách tự do',
              img: 'https://images.unsplash.com/photo-1529139574466-a303027c1d8b?w=600&h=800&fit=crop&auto=format',
            },
            {
              label: 'Ưu Đãi',
              href: '/products',
              sub: 'Thiết kế mới & giá ưu đãi',
              img: 'https://images.unsplash.com/photo-1490481651871-ab68de25d43d?w=600&h=800&fit=crop&auto=format',
            },
          ].map((cat) => (
            <Link
              key={cat.label}
              to={cat.href}
              className="group relative overflow-hidden bg-[#f5f5f5] aspect-[3/4] block focus-visible:outline-2 focus-visible:outline-[#111]"
            >
              <img
                src={cat.img}
                alt={cat.label}
                className="w-full h-full object-cover transition-transform duration-700 ease-out group-hover:scale-105"
              />
              <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/20 to-transparent transition-opacity group-hover:from-black/80" />
              <div className="absolute bottom-0 left-0 right-0 p-5 md:p-6 text-white">
                <p className="text-[10px] md:text-xs uppercase tracking-widest opacity-80 mb-1">
                  {cat.sub}
                </p>
                <p
                  className="font-black text-2xl md:text-3xl leading-none uppercase tracking-tight"
                  style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
                >
                  {cat.label}
                </p>
              </div>
              <div className="absolute top-4 right-4 w-9 h-9 bg-white text-[#111] flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-300">
                <ArrowRight size={14} />
              </div>
            </Link>
          ))}
        </div>
      </section>

      {/* Promo Banner */}
      <section className="bg-[#E5001B] text-white">
        <div className="max-w-[1400px] mx-auto px-6 py-16 grid lg:grid-cols-[1fr_auto] items-center gap-8">
          <div>
            <p className="text-xs uppercase tracking-[0.25em] text-white/80 font-medium mb-3">
              Ưu Đãi Thành Viên Mới
            </p>
            <h2
              className="font-black uppercase leading-[0.9]"
              style={{
                fontFamily: "'Barlow Condensed', sans-serif",
                fontSize: 'clamp(2.75rem, 6vw, 5rem)',
                letterSpacing: '-0.02em',
              }}
            >
              GIẢM 30% CHO ĐƠN HÀNG ĐẦU TIÊN
            </h2>
          </div>
          <div className="flex flex-col gap-4 max-w-sm">
            <p className="text-sm text-white/90 leading-relaxed" style={{ fontWeight: 300 }}>
              Sử dụng mã ưu đãi <strong className="text-white font-bold bg-white/20 px-2 py-0.5 tracking-wider">LINOMOI</strong> ở bước thanh toán để nhận giảm giá.
            </p>
            <Link
              to="/products"
              className="inline-flex items-center gap-3 bg-white text-[#111] px-8 py-4 text-xs tracking-widest uppercase font-bold hover:bg-[#111] hover:text-white transition-colors duration-300 self-start"
            >
              Mua Sắm Ngay <ArrowRight size={14} />
            </Link>
          </div>
        </div>
      </section>

      {/* New Arrivals with Tabs */}
      <section className="max-w-[1400px] mx-auto px-6 py-16 md:py-24">
        <div className="flex flex-col sm:flex-row sm:items-end justify-between mb-8 gap-4">
          <div>
            <p className="text-xs uppercase tracking-[0.25em] text-[#888] font-semibold mb-2">
              Sản Phẩm Đang Mở Bán
            </p>
            <h2
              className="text-[#111] font-black uppercase leading-none"
              style={{
                fontFamily: "'Barlow Condensed', sans-serif",
                fontSize: 'clamp(2rem, 5vw, 3.5rem)',
                letterSpacing: '-0.01em',
              }}
            >
              SẢN PHẨM MỚI NHẤT
            </h2>
          </div>

          <Link
            to="/products"
            className="text-xs uppercase tracking-widest text-[#111] font-semibold hover:text-[#E5001B] border-b border-[#111] pb-1 hover:border-[#E5001B] transition-colors self-start sm:self-auto"
          >
            Tất Cả Sản Phẩm
          </Link>
        </div>

        {/* Category Tabs */}
        <div className="flex gap-2 mb-10 border-b border-[rgba(0,0,0,0.1)] overflow-x-auto pb-px">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`px-5 py-3 text-xs tracking-widest uppercase font-semibold transition-all relative whitespace-nowrap ${
                activeTab === tab.key ? 'text-[#111]' : 'text-[#888] hover:text-[#111]'
              }`}
            >
              {tab.label}
              {activeTab === tab.key && (
                <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-[#111]" />
              )}
            </button>
          ))}
        </div>

        <Status loading={tabLoading} error={tabError} retry={() => setActiveTab((prev) => prev)} />

        {tabLoading ? (
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-x-5 gap-y-10">
            {Array.from({ length: 8 }).map((_, i) => (
              <SkeletonCard key={i} />
            ))}
          </div>
        ) : tabProducts.length > 0 ? (
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-x-5 gap-y-10">
            {tabProducts.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
        ) : (
          !tabError && (
            <div className="py-20 text-center border border-dashed border-[#ddd] p-8">
              <p className="text-sm text-[#888]">Chưa có sản phẩm nào thuộc phân loại này.</p>
              <Link
                to="/products"
                className="mt-4 inline-block text-xs uppercase tracking-widest underline font-semibold text-[#111]"
              >
                Xem tất cả sản phẩm
              </Link>
            </div>
          )
        )}
      </section>

      {/* Brand Story & Values */}
      <section className="bg-[#f5f5f5] py-20">
        <div className="max-w-[1400px] mx-auto px-6">
          <div className="max-w-2xl mx-auto text-center mb-16">
            <p className="text-xs uppercase tracking-[0.25em] text-[#888] font-semibold mb-3">
              Triết Lý Thương Hiệu
            </p>
            <h2
              className="text-[#111] font-black uppercase leading-tight"
              style={{
                fontFamily: "'Barlow Condensed', sans-serif",
                fontSize: 'clamp(2rem, 4vw, 3rem)',
              }}
            >
              TẠI SAO CHỌN THỜI TRANG LINO?
            </h2>
          </div>

          <div className="grid md:grid-cols-3 gap-8">
            {[
              {
                num: '01',
                title: 'Chất Liệu Tuyển Chọn',
                desc: 'Cotton dệt mịn, sợi linen thoáng khí và len pha cao cấp mang lại sự thoải mái tối đa cho cả ngày dài vận động.',
              },
              {
                num: '02',
                title: 'Thiết Kế Tối Giản Vượt Thời Gian',
                desc: 'Cắt may tinh giản, phom dáng chuẩn mực dễ dàng ứng dụng và phối hợp cho nhiều hoàn cảnh từ công sở tới dạo phố.',
              },
              {
                num: '03',
                title: 'Minh Bạch & Trách Nhiệm',
                desc: 'Chính sách mua sắm rõ ràng, hỗ trợ đổi trả thuận tiện và quy trình sản xuất đề cao độ bền bỉ của sản phẩm.',
              },
            ].map((v) => (
              <div
                key={v.num}
                className="bg-white p-8 md:p-10 border border-[rgba(0,0,0,0.06)] flex flex-col justify-between"
              >
                <div>
                  <span
                    className="text-[#E5001B] font-black text-4xl block mb-6"
                    style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
                  >
                    {v.num}
                  </span>
                  <h3 className="text-base font-bold uppercase tracking-wider text-[#111] mb-3">
                    {v.title}
                  </h3>
                  <p className="text-sm text-[#555] leading-relaxed" style={{ fontWeight: 300 }}>
                    {v.desc}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Newsletter */}
      <section className="max-w-[1400px] mx-auto px-6 py-20 text-center">
        <h2
          className="text-[#111] font-black uppercase mb-4"
          style={{
            fontFamily: "'Barlow Condensed', sans-serif",
            fontSize: 'clamp(2rem, 4vw, 3rem)',
            letterSpacing: '-0.01em',
          }}
        >
          NHẬN THÔNG TIN BỘ SƯU TẬP MỚI
        </h2>
        <p className="text-sm text-[#555] mb-8 max-w-md mx-auto" style={{ fontWeight: 300 }}>
          Đăng ký để nhận sớm nhất thông báo về sản phẩm mới và các chương trình ưu đãi đặc quyền từ LINO.
        </p>

        {newsletterSubmitted ? (
          <div className="bg-[#f0f8f0] border border-[#2D5A3D]/20 p-4 max-w-md mx-auto text-sm text-[#2D5A3D] font-medium">
            Cảm ơn bạn! LINO sẽ gửi thông tin ưu đãi mới nhất tới địa chỉ email của bạn.
          </div>
        ) : (
          <form onSubmit={handleNewsletterSubmit} className="flex max-w-md mx-auto">
            <input
              type="email"
              required
              value={newsletterEmail}
              onChange={(e) => setNewsletterEmail(e.target.value)}
              placeholder="Nhập email của bạn"
              className="flex-1 border border-[#ccc] px-4 py-3.5 text-sm text-[#111] bg-white focus:outline-none focus:border-[#111] placeholder:text-[#999]"
            />
            <button
              type="submit"
              className="bg-[#111] text-white px-7 py-3.5 text-xs tracking-widest uppercase font-semibold hover:bg-[#E5001B] active:scale-[0.98] transition-colors shrink-0"
            >
              Đăng Ký
            </button>
          </form>
        )}
      </section>
    </div>
  );
}
