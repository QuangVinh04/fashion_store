import { useState, useEffect } from 'react';
import { FIELD_BOUNDS, type MeasurementKey } from '../../hooks/useSizeRecommendation';

export default function MeasurementField({
  name,
  value,
  onChange,
  onValidate,
  onKeyDown,
  error: externalError,
}: {
  name: MeasurementKey;
  value?: number;
  onChange: (val?: number) => void;
  onValidate?: (err?: string) => void;
  onKeyDown?: (e: React.KeyboardEvent<HTMLInputElement>) => void;
  error?: string;
}) {
  const meta = FIELD_BOUNDS[name];
  const [text, setText] = useState<string>(value != null ? String(value) : '');
  const [error, setError] = useState<string | undefined>(externalError);

  useEffect(() => {
    setText(value != null ? String(value) : '');
    if (value == null) {
      setError(undefined);
      onValidate?.(undefined);
    }
  }, [value, onValidate]);

  useEffect(() => {
    setError(externalError);
  }, [externalError]);

  const validate = (valStr: string) => {
    const trimmed = valStr.trim();
    if (!trimmed) {
      setError(undefined);
      onValidate?.(undefined);
      return;
    }
    const num = Number(trimmed);
    if (isNaN(num) || num < meta.min || num > meta.max) {
      const msg = `${meta.label} phải từ ${meta.min} đến ${meta.max} ${meta.unit}`;
      setError(msg);
      onValidate?.(msg);
    } else {
      setError(undefined);
      onValidate?.(undefined);
    }
  };

  const handleBlur = () => {
    validate(text);
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const nextStr = e.target.value;
    setText(nextStr);
    const trimmed = nextStr.trim();
    if (!trimmed) {
      onChange(undefined);
    } else {
      const num = Number(trimmed);
      onChange(isNaN(num) ? undefined : num);
    }
  };

  return (
    <div className="pd-finder-field">
      <label htmlFor={`sf-${name}`} className="pd-finder-label">
        {meta.label} <span className="pd-finder-unit">({meta.unit})</span>
      </label>
      <div className="pd-input-wrap">
        <input
          id={`sf-${name}`}
          type="number"
          inputMode="decimal"
          placeholder={`${meta.min}–${meta.max}`}
          value={text}
          onChange={handleChange}
          onBlur={handleBlur}
          onKeyDown={onKeyDown}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `sf-${name}-error` : undefined}
          className={`pd-finder-input ${error ? 'is-invalid' : ''}`}
        />
        <span className="pd-input-suffix">{meta.unit}</span>
      </div>
      {error && (
        <span id={`sf-${name}-error`} className="pd-field-error" role="alert">
          {error}
        </span>
      )}
    </div>
  );
}
