import { api, json } from './client';
export type WalletSession = { paymentId: string; orderId: string; attemptGeneration: number; status: string; currency: string; amount: string; balance: string; expiresAt: string; canConfirm: boolean; canChangeMethod: boolean };
export type SwitchOrder = { paymentMethod: string; currentPaymentGeneration: number; pendingSwitchOperationId?: string; paymentSwitchFailureReason?: string };
export const getWalletSession = (id: string, signal?: AbortSignal) => api<WalletSession>(`/api/v1/payments/${encodeURIComponent(id)}/wallet-session`, { signal });
export const confirmWallet = (id: string, attemptGeneration: number) => api<WalletSession>(`/api/v1/payments/${encodeURIComponent(id)}/wallet-confirm`, json('POST', { attemptGeneration }));
/**
 * Theo quy định thiết kế: Không hỗ trợ đổi phương thức thanh toán trực tiếp khi đơn đã khởi tạo.
 * Khách hàng đổi ý cần hủy đơn hàng cũ và đặt lại đơn mới.
 */
export const requestPaymentMethodSwitch = (_orderId: string, _body?: unknown) =>
  Promise.reject(new Error('Hệ thống không hỗ trợ đổi phương thức thanh toán trực tiếp. Vui lòng hủy đơn và đặt lại đơn mới.'));
export function walletPaymentState(session: WalletSession, now = Date.now()) {
  const amount = BigInt(session.amount), balance = BigInt(session.balance);
  const missing = amount > balance ? amount - balance : 0n;
  const expired = !session.expiresAt || Date.parse(session.expiresAt) <= now;
  return { missing: missing.toString(), expired, canConfirm: session.canConfirm && session.status === 'PENDING' && !expired && missing === 0n };
}
export async function confirmWalletOnce(confirm: () => Promise<WalletSession>, read: () => Promise<WalletSession>) {
  try { return { session: await confirm(), uncertain: false }; }
  catch (error) {
    const status = (error as { status?: number }).status;
    if (status !== 0 && status !== 409 && !(status && status >= 500)) throw error;
    let session: WalletSession;
    try { session = await read(); }
    catch (readError) { throw Object.assign(new Error('Confirmation result is unknown'), { uncertain: true, cause: readError }); }
    return { session, uncertain: session.status === 'PENDING' };
  }
}
export const methodSwitchCompleted = (order: SwitchOrder, method: string, generation: number) => order.paymentMethod === method && order.currentPaymentGeneration > generation && !order.pendingSwitchOperationId;
function abortError() { return new DOMException('Aborted', 'AbortError'); }
export async function pollFinancial<T>(read: (signal: AbortSignal) => Promise<T>, onValue: (value: T) => void, signal: AbortSignal, pending: (value: T) => boolean, visible = () => document.visibilityState === 'visible', interval = 5000) {
  while (!signal.aborted) {
    if (visible()) {
      const value = await read(signal);
      if (signal.aborted) throw abortError();
      onValue(value);
      if (!pending(value)) return value;
    }
    await new Promise<void>((resolve, reject) => {
      if (signal.aborted) { reject(abortError()); return; }
      const stop = () => { clearTimeout(timer); reject(abortError()); };
      const timer = setTimeout(() => { signal.removeEventListener('abort', stop); resolve(); }, interval);
      signal.addEventListener('abort', stop, { once: true });
    });
  }
  throw abortError();
}
