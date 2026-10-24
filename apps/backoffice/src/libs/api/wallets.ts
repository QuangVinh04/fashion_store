import { api } from './client'
import type { WalletAccount,WalletTransaction,WalletTopUp,FinancialPage } from '@/types/wallets'

const path=(id:string)=>`/api/v1/admin/wallets/${encodeURIComponent(id)}`

export const walletsApi={
  list:(page=0,userId='')=>api.get<FinancialPage<WalletAccount>>(`/api/v1/admin/wallets?page=${page}&size=20${userId?`&userId=${encodeURIComponent(userId)}`:''}`),
  get:(userId:string)=>api.get<WalletAccount>(path(userId)),
  getTransaction:(userId:string,entryId:string)=>api.get<WalletTransaction>(`${path(userId)}/transactions/${encodeURIComponent(entryId)}`),
  listTransactions:(userId:string,page=0)=>api.get<FinancialPage<WalletTransaction>>(`${path(userId)}/transactions?page=${page}&size=20`),
  listTopUps:(userId:string,page=0)=>api.get<FinancialPage<WalletTopUp>>(`${path(userId)}/top-ups?page=${page}&size=20`)
}
