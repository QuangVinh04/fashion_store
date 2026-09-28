import { Link } from "react-router";
import { ArrowRight } from "lucide-react";

export default function NotFound() {
  return (
    <div className="min-h-[80vh] flex flex-col items-center justify-center px-6 text-center" style={{ fontFamily: "'Inter', sans-serif" }}>
      <p
        className="text-[#E5001B] select-none"
        style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "clamp(8rem, 20vw, 18rem)", letterSpacing: "-0.05em", lineHeight: 1, opacity: 0.12 }}
      >
        404
      </p>
      <div className="-mt-8 relative z-10">
        <h1
          className="text-[#111] mb-4"
          style={{ fontFamily: "'Barlow Condensed', sans-serif", fontWeight: 900, fontSize: "clamp(2.5rem, 5vw, 4rem)", letterSpacing: "-0.02em", lineHeight: 1 }}
        >
          TRANG KHÔNG TỒN TẠI
        </h1>
        <p className="text-base text-[#888] mb-10 max-w-sm mx-auto" style={{ fontWeight: 300 }}>
          Trang bạn đang tìm kiếm có thể đã bị xóa, đổi tên hoặc tạm thời không khả dụng.
        </p>
        <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
          <Link
            to="/"
            className="inline-flex items-center gap-3 bg-[#111] text-white px-8 py-4 text-xs tracking-widest uppercase font-medium hover:bg-[#E5001B] transition-colors duration-300"
          >
            Về Trang Chủ <ArrowRight size={14} />
          </Link>
          <Link
            to="/products"
            className="inline-flex items-center gap-2 text-xs tracking-widest uppercase text-[#111] font-medium border-b border-[#111] pb-0.5 hover:border-[#E5001B] hover:text-[#E5001B] transition-colors"
          >
            Xem Sản Phẩm
          </Link>
        </div>
      </div>
    </div>
  );
}
