import { api, json } from './client';
export type Wallet = { currency: string; balance: string; minTopUpAmount: string; maxTopUpAmount: string; topUpEnabled: boolean };
export type WalletTransaction = { id: string; direction: string; amount: string; balanceAfter: string; sourceType: string; sourceId: string; createdAt: string };
export type FinancialPage<T> = { content: T[]; totalElements: number; totalPages: number; number: number };
export type WalletTopUp = { id: string; amount: string; currency: string; status: string; expiresAt: string; paymentUrl?: string; creditedAt?: string; reconciliationReason?: string };
export const getWallet = (signal?: AbortSignal) => api<Wallet>('/api/v1/wallet', { signal });
export const listTransactions = (page = 0, signal?: AbortSignal) => api<FinancialPage<WalletTransaction>>(`/api/v1/wallet/transactions?page=${page}&size=20`, { signal });
export const createTopUp = (amount: string, operationId: string) => api<WalletTopUp>('/api/v1/wallet/top-ups', json('POST', { amount, operationId }));
export const getTopUp = (id: string, signal?: AbortSignal) => api<WalletTopUp>(`/api/v1/wallet/top-ups/${encodeURIComponent(id)}`, { signal });
export function walletMoney(value: string) {
  const integer=/^(-?\d+)(?:\.0+)?$/.exec(value);
  if (!integer) throw new Error('Invalid whole VND amount');
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(BigInt(integer[1]));
}
