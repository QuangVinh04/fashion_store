import { useState } from 'react';
import { Sparkles, RefreshCw, Check } from 'lucide-react';
import MeasurementField from './MeasurementField';
import type { SizeRecommendationHook } from '../../hooks/useSizeRecommendation';
import type { SizeChartRow } from '../../api/types';

export default function SizeFinderPanel({
  hook,
  rows,
  onGoDetail,
}: {
  hook: SizeRecommendationHook;
  rows: SizeChartRow[];
  onGoDetail: () => void;
}) {
  const {
    measurements,
    submittedMeasurements,
    recommendation,
    loading,
    error,
    isNetworkError,
    submit,
    changePreference,
    updateField,
  } = hook;

  const [fieldErrors, setFieldErrors] = useState<{ heightCm?: string; weightKg?: string }>({});

  const handleValidate = (key: 'heightCm' | 'weightKg', err?: string) => {
    setFieldErrors((prev) => {
      const next = { ...prev };
      if (!err) delete next[key];
      else next[key] = err;
      return next;
    });
  };

  const handleSubmit = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (fieldErrors.heightCm || fieldErrors.weightKg) return;

    if (!measurements.heightCm && !measurements.weightKg) {
      setFieldErrors({ heightCm: 'Vui lòng nhập chiều cao hoặc cân nặng' });
      return;
    }

    await submit();
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      void handleSubmit();
    }
  };

  const currentPref = submittedMeasurements?.preference || measurements.preference || 'REGULAR';
  const recSize = recommendation?.recommendedSize?.trim().toUpperCase();
  const altSize = recommendation?.alternativeSize?.trim().toUpperCase();

  // Determine relative order of recommended vs alternative size
  let isAltLarger = true;
  if (recSize && altSize) {
    const rIdx = rows.findIndex((r) => (r.sizeCode || '').trim().toUpperCase() === recSize);
    const aIdx = rows.findIndex((r) => (r.sizeCode || '').trim().toUpperCase() === altSize);
    if (rIdx !== -1 && aIdx !== -1) {
      isAltLarger = aIdx > rIdx;
    }
  }

  return (
    <section className="pd-finder-box" aria-label="Tìm size cho tôi">
      <div className="pd-finder-box-header">
        <h3 className="pd-finder-box-title">Chưa biết chọn size nào?</h3>
      </div>

      <form onSubmit={handleSubmit} noValidate className="pd-finder-box-form">
        <div className="pd-finder-box-inputs">
          <MeasurementField
            name="heightCm"
            value={measurements.heightCm}
            onChange={(val) => updateField('heightCm', val)}
            onValidate={(err) => handleValidate('heightCm', err)}
            onKeyDown={handleKeyDown}
            error={fieldErrors.heightCm}
          />

          <MeasurementField
            name="weightKg"
            value={measurements.weightKg}
            onChange={(val) => updateField('weightKg', val)}
            onValidate={(err) => handleValidate('weightKg', err)}
            onKeyDown={handleKeyDown}
            error={fieldErrors.weightKg}
          />

          <div className="pd-finder-pref-group" role="radiogroup" aria-label="Ưu tiên form dáng">
            <span className="pd-finder-pref-label">Form:</span>
            {(
              [
                { value: 'SNUG', label: 'Ôm' },
                { value: 'REGULAR', label: 'Vừa' },
                { value: 'LOOSE', label: 'Rộng' },
              ] as const
            ).map((item) => (
              <label
                key={item.value}
                className={`pd-finder-pref-btn ${currentPref === item.value ? 'is-active' : ''}`}
              >
                <input
                  type="radio"
                  name="panel-pref"
                  value={item.value}
                  checked={currentPref === item.value}
                  onChange={() => void changePreference(item.value)}
                />
                <span>{item.label}</span>
              </label>
            ))}
          </div>

          <button
            type="submit"
            disabled={loading || Boolean(fieldErrors.heightCm || fieldErrors.weightKg)}
            className="pd-finder-submit-inline"
          >
            {loading ? (
              <RefreshCw size={13} className="animate-spin" />
            ) : (
              <Sparkles size={13} />
            )}
            <span>{loading ? 'Đang tính…' : 'Gợi ý'}</span>
          </button>
        </div>
      </form>

      {/* Network / Server error */}
      {error && (
        <div className="pd-finder-error-bar" role="alert">
          <span>{isNetworkError ? 'Chưa lấy được gợi ý · ' : error}</span>
          {isNetworkError && (
            <button type="button" className="pd-text-link" onClick={() => void submit()}>
              Thử lại
            </button>
          )}
        </div>
      )}

      {/* Not enough data state */}
      {recommendation?.status === 'NOT_ENOUGH_DATA' && (
        <p className="pd-finder-neutral-msg">
          Chưa đủ dữ liệu để gợi ý cho sản phẩm này, vui lòng tham khảo bảng kích cỡ bên dưới.
        </p>
      )}

      {/* R1: Result row inside Panel - purely advisory, no add-to-cart or stock buttons */}
      {recommendation?.status === 'RECOMMENDED' && recSize && (
        <div className="pd-finder-result">
          <div className="pd-finder-result-summary">
            <span className="pd-finder-rec-badge">
              <Check size={14} strokeWidth={2.5} />
              Gợi ý: <strong>size {recSize}</strong>
              {recommendation.confidence === 'LOW' && (
                <span className="pd-finder-dim"> (tham khảo)</span>
              )}
            </span>

            {altSize && (
              <span className="pd-finder-alt-hint">
                · {isAltLarger ? 'Thích rộng hơn: ' : 'Thích ôm hơn: '}
                <strong>size {altSize}</strong>
              </span>
            )}
          </div>

          <div className="pd-finder-result-footer">
            <button
              type="button"
              className="pd-finder-detail-link"
              onClick={onGoDetail}
            >
              Xem độ vừa chi tiết từng vùng ›
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
