'use client'
// eslint-disable-next-line import/named
import { useQuery } from '@tanstack/react-query'

import { paymentsApi } from '@/libs/api/payments'

export function usePayment(orderId:string) {
  const payment=useQuery({queryKey:['order-payment',orderId],queryFn:()=>paymentsApi.getByOrderId(orderId),retry:false,enabled:!!orderId,
    refetchInterval:query=>['PENDING','COD_PENDING','REFUND_PENDING'].includes(query.state.data?.status||'')?5000:false,refetchIntervalInBackground:false})

  const refunds=useQuery({queryKey:['order-refunds',orderId],queryFn:()=>paymentsApi.getRefunds(orderId),retry:false,enabled:!!payment.data,
    refetchInterval:query=>query.state.data?.content.some(row=>row.status==='PENDING')?5000:false,refetchIntervalInBackground:false})

  const cod=useQuery({queryKey:['order-cod',orderId],queryFn:()=>paymentsApi.getCodCollection(orderId),retry:false,enabled:payment.data?.method==='COD',
    refetchInterval:query=>query.state.data?.status==='PENDING'?5000:false,refetchIntervalInBackground:false})

  
return {payment,refunds,cod}
}
