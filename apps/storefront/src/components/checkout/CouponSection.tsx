import { Check, Tag, AlertCircle } from "lucide-react";
import type { AvailablePromotion } from "../../api/types";
import { money } from "../../api/client";

interface CouponSectionProps {
  title: string;
  placeholder: string;
  value: string;
  appliedCode?: string;
  onChange: (val: string) => void;
  onApply: (customCode?: string) => void;
  onRemove: () => void;
  busy: boolean;
  error?: string;
  promotions: AvailablePromotion[];
  loadingPromotions?: boolean;
}

export function CouponSection({
  title,
  placeholder,
  value,
  appliedCode,
  onChange,
  onApply,
  onRemove,
  busy,
  error,
  promotions,
  loadingPromotions,
}: CouponSectionProps) {
  const isApplied = Boolean(appliedCode && appliedCode.toUpperCase() === value.trim().toUpperCase());

  return (
    <section className="border border-border p-4 sm:p-5 bg-white rounded-2xl">
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-xs font-semibold text-foreground flex items-center gap-2 text-balance">
          <Tag size={15} /> {title}
        </h2>
        {isApplied && (
          <span className="text-[11px] font-semibold text-success bg-success-light px-2.5 py-0.5 rounded-full border border-success/20 flex items-center gap-1">
            <Check size={12} /> Đã áp dụng
          </span>
        )}
      </div>

      <div className="flex gap-2 mb-2">
        <input
          type="text"
          placeholder={placeholder}
          aria-label={title}
          value={value}
          disabled={busy}
          onChange={(e) => onChange(e.target.value.toUpperCase())}
          className="flex-1 border border-border-strong rounded-xl px-4 py-2.5 text-xs outline-none focus:border-border-strong store-input disabled:opacity-50"
        />
        {isApplied ? (
          <button
            type="button"
            disabled={busy}
            onClick={onRemove}
            className="px-4 py-2.5 border border-border text-foreground hover:bg-secondary/40 text-xs font-semibold rounded-xl transition-all disabled:opacity-50 store-button"
          >
            Gỡ
          </button>
        ) : (
          <button
            type="button"
            disabled={busy || !value.trim()}
            onClick={() => onApply()}
            className="px-4 py-2.5 bg-primary text-white text-xs font-semibold rounded-xl hover:bg-primary-hover active:scale-[0.98] transition-all disabled:opacity-50 store-button"
          >
            Áp dụng
          </button>
        )}
      </div>

      {error && (
        <div className="mt-2 p-2.5 bg-primary-light border border-destructive/20 text-xs text-destructive flex items-center gap-1.5 rounded-lg">
          <AlertCircle size={14} className="shrink-0 text-primary" />
          <span>{error}</span>
        </div>
      )}

      {/* Available suggestions */}
      <div className="space-y-2 pt-3 mt-3 border-t border-border/60">
        <p className="text-[11px] text-muted-foreground font-medium">
          Gợi ý mã ưu đãi (bấm để áp dụng ngay):
        </p>
        {loadingPromotions ? (
          <div className="text-[11px] text-muted-foreground animate-pulse">Đang tải mã khả dụng…</div>
        ) : promotions.length === 0 ? (
          <div className="text-[11px] text-muted-foreground italic">Không có mã phù hợp lúc này.</div>
        ) : (
          <div className="flex flex-wrap gap-2">
            {promotions.map((coupon) => {
              const eligible = coupon.eligible;
              const isSelected = appliedCode === coupon.code || value.trim().toUpperCase() === coupon.code;
              return (
                <button
                  key={coupon.code}
                  type="button"
                  disabled={!eligible || busy}
                  onClick={() => {
                    if (eligible) {
                      onChange(coupon.code);
                      onApply(coupon.code);
                    }
                  }}
                  title={!eligible && coupon.reason ? coupon.reason : undefined}
                  className={`flex flex-col sm:flex-row items-start sm:items-center gap-1 sm:gap-1.5 px-3 py-1.5 rounded-lg border text-xs text-left transition-all ${
                    !eligible
                      ? "opacity-50 cursor-not-allowed border-dashed border-border bg-secondary/30 text-muted-foreground"
                      : isSelected
                      ? "border-primary bg-primary/10 text-primary font-bold shadow-2xs"
                      : "border-border bg-background text-foreground hover:border-border-strong hover:bg-secondary/40 font-medium active:scale-[0.97]"
                  }`}
                >
                  <div className="flex items-center gap-1">
                    <span className="font-mono font-semibold">{coupon.code}</span>
                    {isSelected && <Check size={12} className="text-primary shrink-0" />}
                  </div>
                  {coupon.description && (
                    <span className="text-[11px] text-muted-foreground hidden sm:inline">
                      • {coupon.description}
                    </span>
                  )}
                  {coupon.estimatedDiscount !== undefined && coupon.estimatedDiscount > 0 && (
                    <span className="text-[10px] text-success font-medium hidden md:inline">
                      (giảm {money(coupon.estimatedDiscount)})
                    </span>
                  )}
                  {!eligible && coupon.reason && (
                    <span className="text-[10px] text-destructive block sm:hidden">
                      {coupon.reason}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        )}
      </div>
    </section>
  );
}
