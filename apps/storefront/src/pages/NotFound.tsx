import { Link } from "react-router";
import { ArrowRight } from "lucide-react";

export default function NotFound() {
  return (
    <div className="min-h-[80vh] flex flex-col items-center justify-center px-4 sm:px-6 text-center">
      <p
        className="text-primary select-none"
        style={{
          fontFamily: "var(--app-font)",
          fontWeight: 600,
          letterSpacing: "-0.05em",
          lineHeight: 1,
          opacity: 0.12,
        }}
      >
        404
      </p>
      <div className="-mt-8 relative z-10">
        <h1
          className="text-foreground mb-4 text-balance"
          style={{
            fontFamily: "var(--app-font)",
            fontWeight: 600,
            lineHeight: 1,
          }}
        >
          TRANG KHÔNG TỒN TẠI
        </h1>
        <p className="text-base text-muted-foreground mb-10 max-w-sm mx-auto">
          Trang bạn đang tìm kiếm có thể đã bị xóa, đổi tên hoặc tạm thời không
          khả dụng.
        </p>
        <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
          <Link
            to="/"
            className="inline-flex items-center gap-3 bg-primary text-white px-8 py-4 text-xs font-medium hover:bg-primary-hover transition-colors duration-300 store-action"
          >
            Về Trang Chủ <ArrowRight size={14} />
          </Link>
          <Link
            to="/products"
            className="inline-flex items-center gap-2 text-xs text-foreground font-medium border-b border-border-strong pb-0.5 hover:border-primary hover:text-primary transition-colors store-action"
          >
            Xem Sản Phẩm
          </Link>
        </div>
      </div>
    </div>
  );
}
