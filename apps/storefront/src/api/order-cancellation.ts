import {api,json} from './client';
import type {Order,OrderCancellation} from './types';

export type CancellationBody={operationId:string;reason:string};
export function canCancelOrder(order:Pick<Order,'canCancel'|'cancellation'>) {return order.canCancel===true && !order.cancellation;}
export const cancelOrder=(id:string,body:CancellationBody)=>api<Order>(`/api/v1/orders/${encodeURIComponent(id)}/cancel`,json('POST',body));
export function prepareCancellation(storage:Pick<Storage,'getItem'|'setItem'>,orderId:string,reason:string,newId:()=>string):CancellationBody {
  const key=`order-cancellation:${orderId}`;const raw=storage.getItem(key);const value=raw?JSON.parse(raw):{orderId,operationId:newId(),reason};
  if(value?.orderId!==orderId || typeof value?.operationId!=='string' || !value.operationId.trim() || value.operationId.length>160
      || typeof value?.reason!=='string' || !value.reason.trim() || value.reason.length>500 || value.reason!==reason) {
    throw new Error('Yêu cầu hủy đã lưu cần được kiểm tra. Hãy tải lại đơn hàng trước khi thử lại.');
  }
  storage.setItem(key,JSON.stringify(value));return {operationId:value.operationId,reason:value.reason};
}
export function cancellationState(c:Pick<OrderCancellation,'status'|'refundLedgerEntryId'>) {
  const labels:Record<string,string>={FULFILLMENT_PENDING:'Đang xác minh dừng giao hàng',INVENTORY_PENDING:'Đang xử lý kho',REFUND_PENDING:'Đang hoàn tiền vào ví',
    REVIEW_REQUIRED:'Shop đang kiểm tra yêu cầu hủy',FULFILLMENT_EXCEPTION:'Shop đang xử lý sự cố giao hàng',COMPLETED:'Đã hủy đơn hàng'};
  return {label:labels[c.status]||'Đang xử lý yêu cầu hủy',credited:!!c.refundLedgerEntryId};
}
