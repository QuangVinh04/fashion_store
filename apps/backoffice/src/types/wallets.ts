export type WalletAccount = {userId:string;currency:string;balance:string}
export type WalletTransaction = {id:string;direction:string;amount:string;balanceAfter:string;sourceType:string;sourceId:string;createdAt:string}
export type WalletTopUp = {id:string;amount:string;currency:string;status:string;expiresAt:string;creditedAt?:string;reconciliationReason?:string}
export type FinancialPage<T> = {content:T[];number:number;totalPages:number;totalElements:number;size:number}
