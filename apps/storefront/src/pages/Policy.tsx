import { Link } from 'react-router';
import { Truck, RotateCcw, CreditCard, ShieldCheck, ArrowRight } from 'lucide-react';

export default function Policy() {
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
            <span className="text-[#111]">Chính Sách Bán Hàng</span>
          </nav>
        </div>
      </div>

      <div className="max-w-[1400px] mx-auto px-6 py-12 md:py-20">
        <div className="max-w-2xl mb-14">
          <p className="text-xs uppercase tracking-[0.25em] text-[#888] font-bold mb-2">
            Quy Định & Quyền Lợi
          </p>
          <h1
            className="text-[#111] font-black uppercase text-4xl md:text-5xl leading-tight mb-4"
            style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
          >
            CHÍNH SÁCH BÁN HÀNG LINO
          </h1>
          <p className="text-sm text-[#555] leading-relaxed" style={{ fontWeight: 300 }}>
            LINO cam kết mang lại sự minh bạch, bảo vệ tối đa quyền lợi khách hàng trong từng khâu vận chuyển, thanh toán và bảo hành đổi trả sản phẩm.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-10 lg:gap-14">
          {/* Policy 1: Shipping */}
          <section className="border border-[rgba(0,0,0,0.08)] p-8 bg-white space-y-4">
            <div className="w-12 h-12 bg-[#f5f5f5] flex items-center justify-center mb-4">
              <Truck size={22} className="text-[#111]" />
            </div>
            <h2
              className="text-2xl font-bold uppercase text-[#111]"
              style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
            >
              1. CHÍNH SÁCH VẬN CHUYỂN
            </h2>
            <div className="text-xs text-[#555] leading-relaxed space-y-2.5">
              <p>
                <strong>Đối tác vận chuyển:</strong> Toàn bộ đơn hàng của LINO được vận chuyển chuyên nghiệp thông qua đối tác Giao Hàng Nhanh (GHN).
              </p>
              <p>
                <strong>Miễn phí vận chuyển:</strong> Áp dụng tự động cho mọi đơn hàng có giá trị thanh toán từ <strong>500.000 ₫</strong> trở lên trên toàn quốc.
              </p>
              <p>
                <strong>Thời gian giao hàng:</strong>
                <br />• Khu vực nội thành Hà Nội & TP. Hồ Chí Minh: 1–2 ngày làm việc.
                <br />• Các tỉnh/thành phố khác: 2–4 ngày làm việc.
              </p>
              <p>
                <strong>Theo dõi hành trình:</strong> Sau khi đơn hàng được bàn giao cho đối tác GHN, mã vận đơn sẽ được cập nhật trong mục Chi tiết đơn hàng để quý khách tiện tra cứu trực tuyến.
              </p>
            </div>
          </section>

          {/* Policy 2: Return & Refund */}
          <section className="border border-[rgba(0,0,0,0.08)] p-8 bg-white space-y-4">
            <div className="w-12 h-12 bg-[#f5f5f5] flex items-center justify-center mb-4">
              <RotateCcw size={22} className="text-[#111]" />
            </div>
            <h2
              className="text-2xl font-bold uppercase text-[#111]"
              style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
            >
              2. CHÍNH SÁCH ĐỔI TRẢ & HOÀN TIỀN
            </h2>
            <div className="text-xs text-[#555] leading-relaxed space-y-2.5">
              <p>
                <strong>Thời hạn yêu cầu:</strong> Trong vòng <strong>7 ngày</strong> kể từ thời điểm đơn hàng được ghi nhận đã giao thành công (DELIVERED).
              </p>
              <p>
                <strong>Điều kiện sản phẩm:</strong> Sản phẩm còn nguyên vẹn tem mác, bao bì nguyên bản, chưa qua sử dụng, chưa giặt ủi và không bị hư hại do tác động bên ngoài.
              </p>
              <p>
                <strong>Quy trình hoàn tiền:</strong> Việc xét duyệt yêu cầu trả hàng và xử lý hoàn tiền là hai công đoạn riêng biệt. Sau khi LINO nhận lại kiện hàng và kiểm tra hợp lệ, lệnh hoàn tiền sẽ được gửi sang ngân hàng/đơn vị thanh toán của quý khách trong vòng 3–5 ngày làm việc.
              </p>
            </div>
          </section>

          {/* Policy 3: Payment */}
          <section className="border border-[rgba(0,0,0,0.08)] p-8 bg-white space-y-4">
            <div className="w-12 h-12 bg-[#f5f5f5] flex items-center justify-center mb-4">
              <CreditCard size={22} className="text-[#111]" />
            </div>
            <h2
              className="text-2xl font-bold uppercase text-[#111]"
              style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
            >
              3. PHƯƠNG THỨC THANH TOÁN
            </h2>
            <div className="text-xs text-[#555] leading-relaxed space-y-2.5">
              <p>
                <strong>Thanh toán khi nhận hàng (COD):</strong> Quý khách thanh toán tiền mặt trực tiếp cho bưu tá khi nhận và kiểm tra hàng hóa bên ngoài.
              </p>
              <p>
                <strong>Thanh toán trực tuyến (VNPay / PayOS):</strong> Giao dịch được bảo vệ và xử lý qua cổng thanh toán bảo mật. Khách hàng quét mã VietQR hoặc sử dụng thẻ ATM nội địa / thẻ quốc tế Visa, Mastercard.
              </p>
              <p>
                <strong>Bảo mật giao dịch:</strong> Hệ thống BFF và Gateway của LINO không lưu trữ thông tin thẻ ngân hàng hoặc mật khẩu tài khoản của người dùng.
              </p>
            </div>
          </section>

          {/* Policy 4: Privacy */}
          <section className="border border-[rgba(0,0,0,0.08)] p-8 bg-white space-y-4">
            <div className="w-12 h-12 bg-[#f5f5f5] flex items-center justify-center mb-4">
              <ShieldCheck size={22} className="text-[#111]" />
            </div>
            <h2
              className="text-2xl font-bold uppercase text-[#111]"
              style={{ fontFamily: "'Barlow Condensed', sans-serif" }}
            >
              4. BẢO MẬT THÔNG TIN KHÁCH HÀNG
            </h2>
            <div className="text-xs text-[#555] leading-relaxed space-y-2.5">
              <p>
                <strong>Mục đích thu thập:</strong> Thông tin cá nhân (họ tên, số điện thoại, địa chỉ) chỉ được sử dụng cho việc xử lý đơn hàng, liên hệ giao nhận và dịch vụ khách hàng.
              </p>
              <p>
                <strong>Bảo vệ dữ liệu:</strong> Toàn bộ dữ liệu tài khoản được quản lý độc lập qua máy chủ xác thực Keycloak đạt tiêu chuẩn an ninh cao cấp.
              </p>
              <p>
                <strong>Quyền của người dùng:</strong> Quý khách có quyền xem, chỉnh sửa hoặc yêu cầu xóa dữ liệu thông tin cá nhân của mình bất kỳ lúc nào tại mục Hồ sơ cá nhân.
              </p>
            </div>
          </section>
        </div>

        <div className="mt-16 pt-8 border-t border-[rgba(0,0,0,0.08)] flex flex-col sm:flex-row items-center justify-between gap-4">
          <p className="text-xs text-[#777]">
            Bạn cần trao đổi trực tiếp hoặc có trường hợp cần hỗ trợ khẩn cấp?
          </p>
          <Link
            to="/contact"
            className="inline-flex items-center gap-2 text-xs uppercase tracking-widest font-bold text-[#111] hover:text-[#E5001B] underline underline-offset-4"
          >
            Liên Hệ Ban Quản Trị LINO <ArrowRight size={13} />
          </Link>
        </div>
      </div>
    </div>
  );
}
