import {api} from './client';
export type ReturnResolution={orderId:string;paymentId:string;attemptGeneration:number;status:string;captureStatus:string};
export function resolvePaymentReturn(provider:'PAYOS'|'VNPAY',params:URLSearchParams,signal?:AbortSignal):Promise<ReturnResolution> {
  const reference=params.get(provider==='PAYOS'?'orderCode':'vnp_TxnRef');
  if(!reference)return Promise.reject(new Error('Thiếu mã tham chiếu thanh toán. Hãy xem kết quả tại đơn hàng của bạn.'));
  return api<ReturnResolution>(`/api/v1/payments/resolve-return?provider=${provider}&reference=${encodeURIComponent(reference)}`,{signal});
}
export function paymentReturnState(payment:{status:string;captureStatus?:string}|null|undefined,order:{status:string}|null|undefined) {
  if(payment?.status==='REFUNDED')return 'REFUNDED';
  if(payment?.captureStatus==='REVIEW_REQUIRED')return 'REVIEW_REQUIRED';
  if(payment?.status==='COMPLETED')return order && !['PENDING','CANCELLED'].includes(order.status)?'COMPLETED':'ORDER_PENDING';
  if(payment?.status==='FAILED'||payment?.status==='CANCELLED')return 'FAILED';
  return 'UNKNOWN';
}
