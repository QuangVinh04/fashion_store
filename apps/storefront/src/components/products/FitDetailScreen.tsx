import { useState, useEffect, useRef } from 'react';
import { ArrowLeft, Sparkles, RefreshCw, Trash2 } from 'lucide-react';
import MeasurementField from './MeasurementField';
import BodyFitMap from './BodyFitMap';
import type { SizeRecommendationHook, MeasurementKey } from '../../hooks/useSizeRecommendation';

const DETAIL_FIELDS: MeasurementKey[] = [
  'chestCm',
  'waistCm',
  'hipCm',
  'shoulderCm',
  'lengthCm',
  'inseamCm',
];

export default function FitDetailScreen({
  hook,
  onBack,
}: {
  hook: SizeRecommendationHook;
  onBack: () => void;
}) {
  const {
    measurements,
    recommendation,
    loading,
    error,
    isNetworkError,
    submit,
    updateField,
    clear,
  } = hook;

  const headingRef = useRef<HTMLHeadingElement>(null);
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<MeasurementKey, string>>>({});

  // Focus heading on mount for keyboard navigation
  useEffect(() => {
    headingRef.current?.focus();
  }, []);

  const handleValidate = (key: MeasurementKey, err?: string) => {
    setFieldErrors((prev) => {
      const next = { ...prev };
      if (!err) delete next[key];
      else next[key] = err;
      return next;
    });
  };

  const handleUpdate = async () => {
    if (Object.keys(fieldErrors).length > 0) return;
    await submit();
  };

  const hasAnySaved = Object.keys(measurements).length > 0;
  const showHeatmap =
    recommendation?.basis === 'BODY_MEASUREMENTS' &&
    recommendation.regions &&
    recommendation.regions.length > 0;

  return (
    <div className="pd-fitdetail-screen">
      <header className="pd-fitdetail-header">
        <button
          type="button"
          onClick={onBack}
          className="pd-fitdetail-back-btn"
          aria-label="Quay lại Bảng kích cỡ"
        >
          <ArrowLeft size={16} aria-hidden="true" />
          <span>Quay lại</span>
        </button>
        <h2 ref={headingRef} tabIndex={-1} className="pd-fitdetail-title">
          Độ vừa chi tiết từng vùng
        </h2>
      </header>

      <div className="pd-fitdetail-intro">
        <p className="pd-fitdetail-desc">
          Nhập thêm số đo cơ thể để hệ thống đối chiếu chính xác với từng phần của trang phục.
        </p>
        {hasAnySaved && (
          <button
            type="button"
            className="pd-finder-clear-btn"
            onClick={clear}
            title="Xóa toàn bộ số đo trong phiên"
          >
            <Trash2 size={13} aria-hidden="true" />
            <span>Xóa số đo đã nhập</span>
          </button>
        )}
      </div>

      {/* Detail measurement fields */}
      <div className="pd-finder-grid">
        {DETAIL_FIELDS.map((key) => (
          <MeasurementField
            key={key}
            name={key}
            value={measurements[key]}
            onChange={(val) => updateField(key, val)}
            onValidate={(err) => handleValidate(key, err)}
            error={fieldErrors[key]}
          />
        ))}
      </div>

      <div className="pd-fitdetail-actions">
        <button
          type="button"
          disabled={loading || Object.keys(fieldErrors).length > 0}
          onClick={() => void handleUpdate()}
          className="pd-finder-submit-btn"
        >
          {loading ? (
            <RefreshCw size={14} className="animate-spin" />
          ) : (
            <Sparkles size={14} />
          )}
          <span>{loading ? 'Đang tính toán…' : 'Cập nhật gợi ý'}</span>
        </button>
      </div>

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

      {/* Heatmap visualization or placeholder */}
      <div className="pd-fitdetail-map-section">
        {showHeatmap ? (
          <BodyFitMap regions={recommendation.regions} />
        ) : (
          <div className="pd-fitdetail-placeholder">
            <p>Nhập số đo chi tiết và bấm "Cập nhật gợi ý" để xem độ vừa từng vùng.</p>
          </div>
        )}
      </div>
    </div>
  );
}
