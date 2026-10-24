import { api,ApiError } from './client'
import type { PaymentDetails,RefundDetails,CodCollection } from '@/types/payments'
import type { FinancialPage } from '@/types/wallets'

const order=(id:string)=>`/api/v1/admin/payments/order/${encodeURIComponent(id)}`

export const paymentsApi={
  getByOrderId:(id:string)=>api.get<PaymentDetails>(order(id)),
  getRefunds:(id:string,page=0)=>api.get<FinancialPage<RefundDetails>>(`${order(id)}/refunds?page=${page}&size=20`),
  evidenceUrl:(id:string,mediaId:string)=>api.get<string>(`/api/v1/admin/orders/${encodeURIComponent(id)}/cod-collection/evidence/${encodeURIComponent(mediaId)}`),
  getCodCollection:async(id:string)=>{
    try{return await api.get<CodCollection>(`/api/v1/admin/orders/${encodeURIComponent(id)}/cod-collection`)}
    catch(error){if(error instanceof ApiError && error.status===404 && error.code===4001)return null;throw error}
  },
  confirmCodCollection:(id:string,body:{operationId:string;receivedAmount:string;receivedAt:string;evidenceIds:string[]})=>api.post<CodCollection>(`/api/v1/admin/orders/${encodeURIComponent(id)}/cod-collection`,body)
}
