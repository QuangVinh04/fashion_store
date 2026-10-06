import { useState } from "react";
import { Link } from "react-router";
import {
  Mail,
  Phone,
  MapPin,
  Clock,
  ChevronDown,
  ChevronUp,
  Package,
  ShieldCheck,
} from "lucide-react";

export default function Contact() {
  const [openFaq, setOpenFaq] = useState<number | null>(0);

  const faqs = [
    {
      q: "Thời gian giao hàng tiêu chuẩn của LINO là bao lâu?",
      a: "LINO phối hợp cùng đối tác vận chuyển Giao Hàng Nhanh (GHN) để giao hàng trên toàn quốc. Đơn hàng tại nội thành Hà Nội & TP. Hồ Chí Minh thường được giao trong 1–2 ngày làm việc; các tỉnh thành khác từ 2–4 ngày làm việc.",
    },
    {
      q: "Chính sách đổi trả sản phẩm được áp dụng như thế nào?",
      a: "Khách hàng có thể gửi yêu cầu trả hàng trong vòng 7 ngày kể từ khi đơn hàng được ghi nhận đã giao thành công. Sản phẩm đổi trả cần giữ nguyên tem mác, chưa qua giặt ủi và không có mùi lạ. Quý khách có thể gửi yêu cầu trực tiếp tại trang Chi tiết đơn hàng trong tài khoản.",
    },
    {
      q: "Tôi có thể thanh toán bằng những hình thức nào?",
      a: "LINO hỗ trợ 3 phương thức thanh toán an toàn: Thanh toán tiền mặt khi nhận hàng (COD), Thanh toán trực tuyến qua cổng VNPay (ATM / Visa / Mastercard / VNPAY-QR) và Chuyển khoản tức thì qua cổng PayOS (VietQR).",
    },
    {
      q: "Làm thế nào để tôi theo dõi tiến trình đơn hàng?",
      a: 'Sau khi đặt hàng, quý khách có thể vào mục "Tài Khoản > Đơn Hàng" để xem toàn bộ lịch trình cập nhật từ hệ thống và mã vận đơn GHN tương ứng.',
    },
  ];

  return (
    <div className="w-full">
      {/* Breadcrumb */}
      <div className="border-b border-border bg-background">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-4">
          <nav className="flex items-center gap-2 text-xs text-muted-foreground font-medium">
            <Link to="/" className="hover:text-foreground store-text-link">
              Trang Chủ
            </Link>
            <span>/</span>
            <span className="text-foreground">Liên Hệ & Hỗ Trợ</span>
          </nav>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8 md:py-10">
        <div className="max-w-2xl mb-6">
          <p className="text-xs text-muted-foreground font-semibold mb-2">
            Chăm Sóc Khách Hàng
          </p>
          <h1 className="text-foreground font-semibold text-xl leading-tight mb-4 text-balance">
            LIÊN HỆ VỚI LINO
          </h1>
          <p className="text-sm text-muted-foreground leading-relaxed">
            Đội ngũ tư vấn và hỗ trợ khách hàng của LINO luôn sẵn sàng đồng hành
            cùng bạn để mang đến trải nghiệm mua sắm tối ưu nhất.
          </p>
        </div>

        {/* Contact Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          <div className="border border-border p-4 sm:p-5 bg-white flex flex-col justify-between rounded-2xl">
            <div>
              <div className="w-10 h-10 rounded-xl bg-background flex items-center justify-center mb-6">
                <Mail size={22} className="text-foreground" />
              </div>
              <p className="text-xs text-muted-foreground font-semibold mb-1">
                Gửi Thư Điện Tử
              </p>
              <h2 className="text-lg font-semibold text-foreground mb-2 text-balance">
                Hỗ Trợ & Khiếu Nại
              </h2>
              <p className="text-xs text-muted-foreground leading-relaxed mb-4">
                Phản hồi thắc mắc về đơn hàng, chính sách đổi trả hoặc hợp tác.
              </p>
            </div>
            <a
              href="mailto:support@lino.vn"
              className="text-sm font-semibold text-foreground hover:text-primary underline underline-offset-4 store-text-link"
            >
              support@lino.vn
            </a>
          </div>

          <div className="border border-border p-4 sm:p-5 bg-white flex flex-col justify-between rounded-2xl">
            <div>
              <div className="w-10 h-10 rounded-xl bg-background flex items-center justify-center mb-6">
                <Phone size={22} className="text-foreground" />
              </div>
              <p className="text-xs text-muted-foreground font-semibold mb-1">
                Tổng Đài Miễn Phí
              </p>
              <h2 className="text-lg font-semibold text-foreground mb-2 text-balance">
                1800 1234
              </h2>
              <p className="text-xs text-muted-foreground leading-relaxed mb-4">
                Hoạt động từ 8:30 – 21:30 tất cả các ngày trong tuần (kể cả Thứ
                7 & Chủ Nhật).
              </p>
            </div>
            <a
              href="tel:18001234"
              className="text-sm font-semibold text-foreground hover:text-primary underline underline-offset-4 store-text-link"
            >
              Gọi ngay: 1800 1234
            </a>
          </div>

          <div className="border border-border p-4 sm:p-5 bg-white flex flex-col justify-between rounded-2xl">
            <div>
              <div className="w-10 h-10 rounded-xl bg-background flex items-center justify-center mb-6">
                <Package size={22} className="text-foreground" />
              </div>
              <p className="text-xs text-muted-foreground font-semibold mb-1">
                Theo Dõi Đơn Hàng
              </p>
              <h2 className="text-lg font-semibold text-foreground mb-2 text-balance">
                Tra Cứu Trực Tuyến
              </h2>
              <p className="text-xs text-muted-foreground leading-relaxed mb-4">
                Kiểm tra trạng thái đóng gói, mã vận đơn GHN và lịch trình vận
                chuyển.
              </p>
            </div>
            <Link
              to="/profile/orders"
              className="text-sm font-semibold text-foreground hover:text-primary underline underline-offset-4 store-text-link"
            >
              Xem đơn hàng của bạn →
            </Link>
          </div>
        </div>

        {/* FAQs */}
        <div className="border-t border-border pt-16">
          <div className="max-w-2xl mb-10">
            <p className="text-xs text-muted-foreground font-semibold mb-2">
              Giải Đáp Thắc Mắc
            </p>
            <h2 className="text-foreground font-semibold text-lg text-balance">
              CÂU HỎI THƯỜNG GẶP
            </h2>
          </div>

          <div className="max-w-3xl divide-y divide-border border-y border-border">
            {faqs.map((faq, idx) => {
              const isOpen = openFaq === idx;
              return (
                <div key={idx} className="py-5">
                  <button
                    type="button"
                    onClick={() => setOpenFaq(isOpen ? null : idx)}
                    aria-expanded={isOpen}
                    className="w-full flex items-center justify-between text-left font-semibold text-sm text-foreground hover:text-primary transition-colors store-button"
                  >
                    <span>{faq.q}</span>
                    {isOpen ? (
                      <ChevronUp
                        size={16}
                        className="text-muted-foreground shrink-0"
                      />
                    ) : (
                      <ChevronDown
                        size={16}
                        className="text-muted-foreground shrink-0"
                      />
                    )}
                  </button>
                  <div
                    className={`grid transition-[grid-template-rows] duration-200 ${isOpen ? "grid-rows-[1fr]" : "grid-rows-[0fr]"}`}
                  >
                    <div className="overflow-hidden">
                      <p className="pt-3 text-sm text-muted-foreground leading-relaxed pr-6">
                        {faq.a}
                      </p>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
