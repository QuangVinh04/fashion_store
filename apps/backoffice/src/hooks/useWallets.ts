'use client'
// eslint-disable-next-line import/named
import { useQuery } from '@tanstack/react-query'

import { walletsApi } from '@/libs/api/wallets'

export function useWallet(userId:string,ledgerPage=0,topUpPage=0) {
  const wallet=useQuery({queryKey:['wallet',userId],queryFn:()=>walletsApi.get(userId),retry:false})
  const ledger=useQuery({queryKey:['wallet-transactions',userId,ledgerPage],queryFn:()=>walletsApi.listTransactions(userId,ledgerPage),retry:false})

  const topUps=useQuery({queryKey:['wallet-top-ups',userId,topUpPage],queryFn:()=>walletsApi.listTopUps(userId,topUpPage),retry:false,
    refetchInterval:query=>query.state.data?.content.some(row=>['PENDING','INITIATING','UNKNOWN'].includes(row.status))?5000:false,refetchIntervalInBackground:false})

  
return {wallet,ledger,topUps}
}
