import { useState } from 'react';
import { Link } from 'react-router';
import { Mail, Phone, MapPin, Clock, ChevronDown, ChevronUp, Package, ShieldCheck } from 'lucide-react';

export default function Contact() {
  const [openFaq, setOpenFaq] = useState<number | null>(0);

  const faqs = [
    {
      q: 'Thời gian giao hàng tiêu chuẩn của LINO là bao lâu?',
      a: 'LINO phối hợp cùng đối tác vận chuyển Giao Hàng Nhanh (GHN) để giao hàng trên toàn quốc. Đơn hàng tại nội thành Hà Nội & TP. Hồ Chí Minh thường được giao trong 1–2 ngày làm việc; các tỉnh thành khác từ 2–4 ngày làm việc.',
    },
    {
      q: 'Chính sách đổi trả sản phẩm được áp dụng như thế nào?',
      a: 'Khách hàng có thể gửi yêu cầu trả hàng trong vòng 7 ngày kể từ khi đơn hàng được ghi nhận đã giao thành công (DELIVERED). Sản phẩm đổi trả cần giữ nguyên tem mác, chưa qua giặt ủi và không có mùi lạ. Quý khách có thể gửi yêu cầu trực tiếp tại trang Chi tiết đơn hàng trong tài khoản.',
    },
    {
      q: 'Tôi có thể thanh toán bằng những hình thức nào?',
      a: 'LINO hỗ trợ 3 phương thức thanh toán an toàn: Thanh toán tiền mặt khi nhận hàng (COD), Thanh toán trực tuyến qua cổng VNPay (ATM / Visa / Mastercard / VNPAY-QR) và Chuyển khoản tức thì qua cổng PayOS (VietQR).',
    },
    {
      q: 'Làm thế nào để tôi theo dõi tiến trình đơn hàng?',
      a: 'Sau khi đặt hàng, quý khách có thể vào mục "Tài Khoản > Đơn Hàng" để xem toàn bộ lịch trình cập nhật từ hệ thống và mã vận đơn GHN tương ứng.',
    },
  ];

  return (
    <div style={{ fontFamily: "'Inter', sans-serif" }} className="w-full">
      {/* Breadcrumb */}
      <div className="border-b border-[rgba(0,0,0,0.08)] bg-[#fafafa]">
        <div className="max-w-[1400px] mx-auto px-6 py-4">
          <nav className="flex items-center gap-2 text-xs text-[#888] uppercase tracking-widest font-medium">
            <Link to="/" className="hover:text-[#111]">
              Trang Chủ
            </Link>
            <span>/</span>
            <span className="text-[#111]">Liên Hệ & Hỗ Trợ</span>
          </nav>
        </div>
      </div>

      <div className="max-w-[1400px] mx-auto px-6 py-12 md:py-20">
        <div className="max-w-2xl mb-14">
          <p className="text-xs uppercase tracking-[0.25em] text-[#888] font-bold mb-2">
            Chăm Sóc Khách Hàng
          </p>
          <h1
            className="text-[#111] font-black uppercase text-4xl md:text-5xl leading-tight mb-4"
            style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
          >
            LIÊN HỆ VỚI LINO
          </h1>
          <p className="text-sm text-[#555] leading-relaxed" style={{ fontWeight: 300 }}>
            Đội ngũ tư vấn và hỗ trợ khách hàng của LINO luôn sẵn sàng đồng hành cùng bạn để mang đến trải nghiệm mua sắm tối ưu nhất.
          </p>
        </div>

        {/* Contact Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-20">
          <div className="border border-[rgba(0,0,0,0.08)] p-8 bg-white flex flex-col justify-between">
            <div>
              <div className="w-12 h-12 bg-[#f5f5f5] flex items-center justify-center mb-6">
                <Mail size={22} className="text-[#111]" />
              </div>
              <p className="text-xs uppercase tracking-widest text-[#888] font-bold mb-1">
                Gửi Thư Điện Tử
              </p>
              <h2 className="text-lg font-bold text-[#111] mb-2">Hỗ Trợ & Khiếu Nại</h2>
              <p className="text-xs text-[#666] leading-relaxed mb-4">
                Phản hồi thắc mắc về đơn hàng, chính sách đổi trả hoặc hợp tác.
              </p>
            </div>
            <a
              href="mailto:support@lino.vn"
              className="text-sm font-semibold text-[#111] hover:text-[#E5001B] underline underline-offset-4"
            >
              support@lino.vn
            </a>
          </div>

          <div className="border border-[rgba(0,0,0,0.08)] p-8 bg-white flex flex-col justify-between">
            <div>
              <div className="w-12 h-12 bg-[#f5f5f5] flex items-center justify-center mb-6">
                <Phone size={22} className="text-[#111]" />
              </div>
              <p className="text-xs uppercase tracking-widest text-[#888] font-bold mb-1">
                Tổng Đài Miễn Phí
              </p>
              <h2 className="text-lg font-bold text-[#111] mb-2">1800 1234</h2>
              <p className="text-xs text-[#666] leading-relaxed mb-4">
                Hoạt động từ 8:30 – 21:30 tất cả các ngày trong tuần (kể cả Thứ 7 & Chủ Nhật).
              </p>
            </div>
            <a
              href="tel:18001234"
              className="text-sm font-semibold text-[#111] hover:text-[#E5001B] underline underline-offset-4"
            >
              Gọi ngay: 1800 1234
            </a>
          </div>

          <div className="border border-[rgba(0,0,0,0.08)] p-8 bg-white flex flex-col justify-between">
            <div>
              <div className="w-12 h-12 bg-[#f5f5f5] flex items-center justify-center mb-6">
                <Package size={22} className="text-[#111]" />
              </div>
              <p className="text-xs uppercase tracking-widest text-[#888] font-bold mb-1">
                Theo Dõi Đơn Hàng
              </p>
              <h2 className="text-lg font-bold text-[#111] mb-2">Tra Cứu Trực Tuyến</h2>
              <p className="text-xs text-[#666] leading-relaxed mb-4">
                Kiểm tra trạng thái đóng gói, mã vận đơn GHN và lịch trình vận chuyển.
              </p>
            </div>
            <Link
              to="/profile/orders"
              className="text-sm font-semibold text-[#111] hover:text-[#E5001B] underline underline-offset-4"
            >
              Xem đơn hàng của bạn →
            </Link>
          </div>
        </div>

        {/* FAQs */}
        <div className="border-t border-[rgba(0,0,0,0.08)] pt-16">
          <div className="max-w-2xl mb-10">
            <p className="text-xs uppercase tracking-[0.25em] text-[#888] font-bold mb-2">
              Giải Đáp Thắc Mắc
            </p>
            <h2
              className="text-[#111] font-black uppercase text-3xl"
              style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
            >
              CÂU HỎI THƯỜNG GẶP
            </h2>
          </div>

          <div className="max-w-3xl divide-y divide-[rgba(0,0,0,0.08)] border-y border-[rgba(0,0,0,0.08)]">
            {faqs.map((faq, idx) => {
              const isOpen = openFaq === idx;
              return (
                <div key={idx} className="py-5">
                  <button
                    type="button"
                    onClick={() => setOpenFaq(isOpen ? null : idx)}
                    className="w-full flex items-center justify-between text-left font-semibold text-sm text-[#111] hover:text-[#E5001B] transition-colors"
                  >
                    <span>{faq.q}</span>
                    {isOpen ? (
                      <ChevronUp size={16} className="text-[#888] shrink-0" />
                    ) : (
                      <ChevronDown size={16} className="text-[#888] shrink-0" />
                    )}
                  </button>
                  {isOpen && (
                    <p className="pt-3 text-xs text-[#555] leading-relaxed pr-6" style={{ fontWeight: 300 }}>
                      {faq.a}
                    </p>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
