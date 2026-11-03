import { useState, useEffect, useRef, useCallback } from 'react';
import { store } from '../api/store';
import { ApiError } from '../api/client';
import type {
  SizeRecommendation,
  SizeRecommendationParams,
  SizeRecommendationPreference,
} from '../api/types';

export const SIZE_STORAGE_KEY = 'sizeFinder.body';

export type BodyMeasurements = {
  heightCm?: number;
  weightKg?: number;
  chestCm?: number;
  waistCm?: number;
  hipCm?: number;
  shoulderCm?: number;
  lengthCm?: number;
  inseamCm?: number;
  preference?: SizeRecommendationPreference;
};

export const FIELD_BOUNDS = {
  heightCm: { min: 100, max: 230, unit: 'cm', label: 'Chiều cao' },
  weightKg: { min: 25, max: 250, unit: 'kg', label: 'Cân nặng' },
  chestCm: { min: 50, max: 180, unit: 'cm', label: 'Vòng ngực' },
  waistCm: { min: 40, max: 180, unit: 'cm', label: 'Vòng eo' },
  hipCm: { min: 50, max: 180, unit: 'cm', label: 'Vòng mông' },
  shoulderCm: { min: 25, max: 70, unit: 'cm', label: 'Chiều rộng vai' },
  lengthCm: { min: 40, max: 130, unit: 'cm', label: 'Dài áo' },
  inseamCm: { min: 40, max: 110, unit: 'cm', label: 'Dài quần' },
} as const;

export type MeasurementKey = keyof typeof FIELD_BOUNDS;

export function readStoredMeasurements(): BodyMeasurements {
  try {
    const raw = sessionStorage.getItem(SIZE_STORAGE_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

export function writeStoredMeasurements(data: BodyMeasurements) {
  try {
    sessionStorage.setItem(SIZE_STORAGE_KEY, JSON.stringify(data));
  } catch {
    // Graceful degradation in restricted storage contexts
  }
}

export function clearStoredMeasurements() {
  try {
    sessionStorage.removeItem(SIZE_STORAGE_KEY);
  } catch {
    // Graceful degradation
  }
}

export function useSizeRecommendation(
  productId?: string,
  isOpen = false,
  hasSizeChart = false
) {
  const [measurements, setMeasurements] = useState<BodyMeasurements>(readStoredMeasurements);
  const measurementsRef = useRef<BodyMeasurements>(measurements);
  const [submittedMeasurements, setSubmittedMeasurements] = useState<BodyMeasurements | null>(null);
  const [recommendation, setRecommendation] = useState<SizeRecommendation | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isNetworkError, setIsNetworkError] = useState(false);

  const autoFetchedForProductRef = useRef<string | null>(null);
  const requestIdRef = useRef(0);

  // S4: Reset state when productId changes
  useEffect(() => {
    setRecommendation(null);
    setSubmittedMeasurements(null);
    setError(null);
    setIsNetworkError(false);
  }, [productId]);

  // Keep ref synchronized
  useEffect(() => {
    measurementsRef.current = measurements;
  }, [measurements]);

  const executeRecommendation = useCallback(
    async (paramsToSubmit: BodyMeasurements) => {
      if (!productId) return;
      const currentRequestId = ++requestIdRef.current;
      setLoading(true);
      setError(null);
      setIsNetworkError(false);

      const apiParams: SizeRecommendationParams = {};
      (Object.keys(FIELD_BOUNDS) as MeasurementKey[]).forEach((key) => {
        const val = paramsToSubmit[key];
        if (typeof val === 'number' && !isNaN(val)) {
          apiParams[key] = val;
        }
      });
      apiParams.preference = paramsToSubmit.preference || 'REGULAR';

      try {
        const res = await store.productSizeRecommendation(productId, apiParams);
        if (currentRequestId !== requestIdRef.current) return;
        setRecommendation(res);
        setSubmittedMeasurements(paramsToSubmit);
      } catch (err: unknown) {
        if (currentRequestId !== requestIdRef.current) return;
        if (err instanceof ApiError) {
          setError(err.message || 'Không thể lấy gợi ý kích cỡ lúc này.');
          setIsNetworkError(err.status === 0 || err.status >= 500);
        } else {
          setError('Lỗi kết nối máy chủ. Vui lòng thử lại.');
          setIsNetworkError(true);
        }
      } finally {
        if (currentRequestId === requestIdRef.current) {
          setLoading(false);
        }
      }
    },
    [productId]
  );

  // S4: Auto-run once only when the sheet is opened for this product and session has valid height/weight
  useEffect(() => {
    if (!isOpen || !productId || !hasSizeChart) return;
    if (autoFetchedForProductRef.current === productId) return;
    autoFetchedForProductRef.current = productId;

    const stored = readStoredMeasurements();
    const hasValidInput =
      (typeof stored.heightCm === 'number' && stored.heightCm >= 100 && stored.heightCm <= 230) ||
      (typeof stored.weightKg === 'number' && stored.weightKg >= 25 && stored.weightKg <= 250);

    if (hasValidInput) {
      measurementsRef.current = stored;
      setMeasurements(stored);
      void executeRecommendation(stored);
    }
  }, [isOpen, productId, hasSizeChart, executeRecommendation]);

  const submit = useCallback(
    async (overrides?: Partial<BodyMeasurements>) => {
      const next = { ...measurementsRef.current, ...overrides };
      measurementsRef.current = next;
      setMeasurements(next);
      writeStoredMeasurements(next);
      await executeRecommendation(next);
    },
    [executeRecommendation]
  );

  const changePreference = useCallback(
    async (preference: SizeRecommendationPreference) => {
      const base = submittedMeasurements || measurementsRef.current;
      const next = { ...base, preference };
      measurementsRef.current = next;
      setMeasurements(next);
      writeStoredMeasurements(next);
      await executeRecommendation(next);
    },
    [submittedMeasurements, executeRecommendation]
  );

  const updateField = useCallback((key: MeasurementKey, val?: number) => {
    // Invalidate any pending in-flight requests when shopper modifies inputs
    requestIdRef.current++;

    const next = { ...measurementsRef.current };
    if (val === undefined || isNaN(val)) {
      delete next[key];
    } else {
      next[key] = val;
    }
    measurementsRef.current = next;
    setMeasurements(next);
    writeStoredMeasurements(next);

    // Hide old recommendation when inputs are modified until re-submitted
    setRecommendation(null);
    setSubmittedMeasurements(null);
    setError(null);
  }, []);

  const clear = useCallback(() => {
    clearStoredMeasurements();
    measurementsRef.current = {};
    setMeasurements({});
    setSubmittedMeasurements(null);
    setRecommendation(null);
    setError(null);
  }, []);

  return {
    measurements,
    submittedMeasurements,
    recommendation,
    loading,
    error,
    isNetworkError,
    submit,
    changePreference,
    updateField,
    clear,
  };
}

export type SizeRecommendationHook = ReturnType<typeof useSizeRecommendation>;
