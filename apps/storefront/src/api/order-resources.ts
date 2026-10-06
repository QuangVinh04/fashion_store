import { ApiError } from './client';

function isMissing(error: unknown, code: number): boolean {
  return error instanceof ApiError && error.status === 404 && error.code === code;
}

function pause(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    signal.throwIfAborted();
    const abort = () => {
      clearTimeout(timer);
      signal.removeEventListener('abort', abort);
      reject(signal.reason);
    };
    const timer = setTimeout(() => {
      signal.removeEventListener('abort', abort);
      resolve();
    }, ms);
    signal.addEventListener('abort', abort, { once: true });
  });
}

// Payment is created asynchronously after inventory reservation.
export async function loadOrderPayment<T>(
  fetchPayment: () => Promise<T>, signal: AbortSignal, retryDelayMs = 2000,
): Promise<T | null> {
  for (let attempt = 0; attempt <= 15; attempt++) {
    signal.throwIfAborted();
    try {
      const payment = await fetchPayment();
      signal.throwIfAborted();
      return payment;
    } catch (error) {
      signal.throwIfAborted();
      if (!isMissing(error, 5001)) throw error;
      if (attempt === 15) return null;
      await pause(retryDelayMs, signal);
    }
  }
  return null;
}

export async function loadOrderShipment<T>(
  trackingCode: string | undefined, fetchShipment: () => Promise<T>,
): Promise<T | null> {
  if (!trackingCode) return null;
  try {
    return await fetchShipment();
  } catch (error) {
    if (isMissing(error, 9007)) return null;
    throw error;
  }
}
