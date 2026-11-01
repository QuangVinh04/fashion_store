import { Link, useRouteError } from "react-router";
import { ArrowRight, RefreshCw } from "lucide-react";

export default function ErrorPage() {
  const error = useRouteError();
  const errorMessage =
    error instanceof Error
      ? error.message
      : "Đã có lỗi không mong muốn xảy ra trong quá trình hiển thị.";

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
        500
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
          HỆ THỐNG ĐANG BẬN
        </h1>
        <p className="text-base text-muted-foreground mb-10 max-w-sm mx-auto">
          {errorMessage}
        </p>
        <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="inline-flex items-center gap-3 bg-primary text-white px-8 py-4 text-xs font-medium hover:bg-primary-hover transition-colors duration-300 store-action cursor-pointer"
          >
            Thử Lại <RefreshCw size={14} />
          </button>
          <Link
            to="/"
            className="inline-flex items-center gap-2 text-xs text-foreground font-medium border-b border-border-strong pb-0.5 hover:border-primary hover:text-primary transition-colors store-action"
          >
            Về Trang Chủ <ArrowRight size={14} />
          </Link>
        </div>
      </div>
    </div>
  );
}
