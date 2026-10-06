import { Link } from "react-router";
import {
  Truck,
  RotateCcw,
  CreditCard,
  ShieldCheck,
  ArrowRight,
} from "lucide-react";

export default function Policy() {
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
            <span className="text-foreground">Chính Sách Bán Hàng</span>
          </nav>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8 md:py-10">
        <div className="max-w-2xl mb-6">
          <p className="text-xs text-muted-foreground font-semibold mb-2">
            Quy Định & Quyền Lợi
          </p>
          <h1 className="text-foreground font-semibold text-xl leading-tight mb-4 text-balance">
            CHÍNH SÁCH BÁN HÀNG LINO
          </h1>
          <p className="text-sm text-muted-foreground leading-relaxed">
            LINO cam kết mang lại sự minh bạch, bảo vệ tối đa quyền lợi khách
            hàng trong từng khâu vận chuyển, thanh toán và bảo hành đổi trả sản
            phẩm.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 lg:gap-14">
          {/* Policy 1: Shipping */}
          <section className="border border-border p-4 sm:p-5 bg-white space-y-4 rounded-2xl">
            <div className="w-10 h-10 rounded-xl bg-background flex items-center justify-center mb-4">
              <Truck size={22} className="text-foreground" />
            </div>
            <h2 className="text-lg font-semibold text-foreground text-balance">
              1. CHÍNH SÁCH VẬN CHUYỂN
            </h2>
            <div className="text-sm text-muted-foreground leading-relaxed space-y-3">
              <p>
                <strong>Đối tác vận chuyển:</strong> Toàn bộ đơn hàng của LINO
                được vận chuyển chuyên nghiệp thông qua đối tác Giao Hàng Nhanh
                (GHN).
              </p>
              <p>
                <strong>Miễn phí vận chuyển:</strong> Áp dụng tự động cho mọi
                đơn hàng có giá trị thanh toán từ <strong>500.000 ₫</strong> trở
                lên trên toàn quốc.
              </p>
              <p>
                <strong>Thời gian giao hàng:</strong>
                <br />• Khu vực nội thành Hà Nội & TP. Hồ Chí Minh: 1–2 ngày làm
                việc.
                <br />• Các tỉnh/thành phố khác: 2–4 ngày làm việc.
              </p>
              <p>
                <strong>Theo dõi hành trình:</strong> Sau khi đơn hàng được bàn
                giao cho đối tác GHN, mã vận đơn sẽ được cập nhật trong mục Chi
                tiết đơn hàng để quý khách tiện tra cứu trực tuyến.
              </p>
            </div>
          </section>

          {/* Policy 2: Return & Refund */}
          <section className="border border-border p-4 sm:p-5 bg-white space-y-4 rounded-2xl">
            <div className="w-10 h-10 rounded-xl bg-background flex items-center justify-center mb-4">
              <RotateCcw size={22} className="text-foreground" />
            </div>
            <h2 className="text-lg font-semibold text-foreground text-balance">
              2. CHÍNH SÁCH ĐỔI TRẢ & HOÀN TIỀN
            </h2>
            <div className="text-sm text-muted-foreground leading-relaxed space-y-3">
              <p>
                <strong>Thời hạn yêu cầu:</strong> Trong vòng{" "}
                <strong>7 ngày</strong> kể từ thời điểm đơn hàng được ghi nhận
                đã giao thành công.
              </p>
              <p>
                <strong>Điều kiện sản phẩm:</strong> Sản phẩm còn nguyên vẹn tem
                mác, bao bì nguyên bản, chưa qua sử dụng, chưa giặt ủi và không
                bị hư hại do tác động bên ngoài.
              </p>
              <p>
                <strong>Quy trình hoàn tiền:</strong> Việc xét duyệt yêu cầu trả
                hàng và xử lý hoàn tiền là hai công đoạn riêng biệt. Sau khi
                LINO nhận lại kiện hàng và kiểm tra hợp lệ, lệnh hoàn tiền sẽ
                được gửi sang ngân hàng/đơn vị thanh toán của quý khách trong
                vòng 3–5 ngày làm việc.
              </p>
            </div>
          </section>

          {/* Policy 3: Payment */}
          <section className="border border-border p-4 sm:p-5 bg-white space-y-4 rounded-2xl">
            <div className="w-10 h-10 rounded-xl bg-background flex items-center justify-center mb-4">
              <CreditCard size={22} className="text-foreground" />
            </div>
            <h2 className="text-lg font-semibold text-foreground text-balance">
              3. PHƯƠNG THỨC THANH TOÁN
            </h2>
            <div className="text-sm text-muted-foreground leading-relaxed space-y-3">
              <p>
                <strong>Thanh toán khi nhận hàng (COD):</strong> Quý khách thanh
                toán tiền mặt trực tiếp cho bưu tá khi nhận và kiểm tra hàng hóa
                bên ngoài.
              </p>
              <p>
                <strong>Thanh toán trực tuyến (VNPay / PayOS):</strong> Giao
                dịch được bảo vệ và xử lý qua cổng thanh toán bảo mật. Khách
                hàng quét mã VietQR hoặc sử dụng thẻ ATM nội địa / thẻ quốc tế
                Visa, Mastercard.
              </p>
              <p>
                <strong>Bảo mật giao dịch:</strong> LINO không lưu trữ thông tin thẻ ngân hàng hoặc mật khẩu tài
                khoản của người dùng.
              </p>
            </div>
          </section>

          {/* Policy 4: Privacy */}
          <section className="border border-border p-4 sm:p-5 bg-white space-y-4 rounded-2xl">
            <div className="w-10 h-10 rounded-xl bg-background flex items-center justify-center mb-4">
              <ShieldCheck size={22} className="text-foreground" />
            </div>
            <h2 className="text-lg font-semibold text-foreground text-balance">
              4. BẢO MẬT THÔNG TIN KHÁCH HÀNG
            </h2>
            <div className="text-sm text-muted-foreground leading-relaxed space-y-3">
              <p>
                <strong>Mục đích thu thập:</strong> Thông tin cá nhân (họ tên,
                số điện thoại, địa chỉ) chỉ được sử dụng cho việc xử lý đơn
                hàng, liên hệ giao nhận và dịch vụ khách hàng.
              </p>
              <p>
                <strong>Bảo vệ dữ liệu:</strong> Thông tin tài khoản được bảo vệ
                bằng cơ chế đăng nhập và kiểm soát truy cập của LINO.
              </p>
              <p>
                <strong>Quyền của người dùng:</strong> Quý khách có quyền xem,
                chỉnh sửa hoặc yêu cầu xóa dữ liệu thông tin cá nhân của mình
                bất kỳ lúc nào tại mục Hồ sơ cá nhân.
              </p>
            </div>
          </section>
        </div>

        <div className="mt-16 pt-8 border-t border-border flex flex-col sm:flex-row items-center justify-between gap-4">
          <p className="text-xs text-muted-foreground">
            Bạn cần trao đổi trực tiếp hoặc có trường hợp cần hỗ trợ khẩn cấp?
          </p>
          <Link
            to="/contact"
            className="inline-flex items-center gap-2 text-xs font-semibold text-foreground hover:text-primary underline underline-offset-4 store-text-link"
          >
            Liên Hệ Ban Quản Trị LINO <ArrowRight size={13} />
          </Link>
        </div>
      </div>
    </div>
  );
}
