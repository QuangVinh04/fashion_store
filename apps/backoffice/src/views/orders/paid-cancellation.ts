import type {CarrierVerificationInput} from '@/types/orders'

export type CancellationBody={operationId:string;reason:string}

export function prepareCarrierVerification(storage:Pick<Storage,'getItem'|'setItem'>,caseId:string,
  input:Omit<CarrierVerificationInput,'operationId'>,newId:()=>string):CarrierVerificationInput {
  if(!input.reason.trim() || input.reason.length>500 || input.noHandoff!==true || input.carrierCancelled!==true
      || typeof input.noShipmentExists!=='boolean' || !input.mediaIds.length || input.mediaIds.length>10
      || new Set(input.mediaIds).size!==input.mediaIds.length || input.mediaIds.some(id=>!id || id.length>36)
      || !input.noShipmentExists && !input.trackingCode?.trim()
      || input.noShipmentExists && !!input.trackingCode?.trim())throw new Error('Cần xác minh dừng giao hàng, chưa bàn giao và cung cấp ảnh bằng chứng.')
  const payload={reason:input.reason,carrierCancelled:true,noHandoff:true,noShipmentExists:input.noShipmentExists,trackingCode:input.trackingCode||'',mediaIds:input.mediaIds}
  const key=`cancellation-carrier-verification:${caseId}`;const raw=storage.getItem(key)
  const value=raw?JSON.parse(raw):{caseId,operationId:newId(),payload}

  if(value?.caseId!==caseId || typeof value?.operationId!=='string' || !value.operationId.trim() || value.operationId.length>160
      || JSON.stringify(value.payload)!==JSON.stringify(payload))throw new Error('Yêu cầu xác minh đã lưu cần được đối soát trước khi thay đổi dữ liệu.')
  storage.setItem(key,JSON.stringify(value))
  
return {operationId:value.operationId,...payload}
}

export function prepareCancellation(storage:Pick<Storage,'getItem'|'setItem'>,orderId:string,reason:string,newId:()=>string):CancellationBody {
  const key=`order-cancellation:${orderId}`;const raw=storage.getItem(key)
  const value=raw?JSON.parse(raw):{orderId,operationId:newId(),reason}

  if(value?.orderId!==orderId || typeof value?.operationId!=='string' || !value.operationId.trim() || value.operationId.length>160
      || typeof value?.reason!=='string' || !value.reason.trim() || value.reason.length>500 || value.reason!==reason)
    throw new Error('Yêu cầu hủy đã lưu cần được kiểm tra. Hãy tải lại đơn hàng trước khi thử lại.')
  storage.setItem(key,JSON.stringify(value))
  
return {operationId:value.operationId,reason:value.reason}
}

export function cancellationState(c:{status:string;refundLedgerEntryId?:string}) {
  const labels:Record<string,string>={FULFILLMENT_PENDING:'Đang xác minh dừng giao hàng',INVENTORY_PENDING:'Đang xử lý kho',REFUND_PENDING:'Đang hoàn tiền vào ví',
    REVIEW_REQUIRED:'Cần kiểm tra',FULFILLMENT_EXCEPTION:'Sự cố bàn giao vận chuyển',COMPLETED:'Đã hủy đơn hàng'}

  
return {label:labels[c.status]||'Đang xử lý yêu cầu hủy',credited:!!c.refundLedgerEntryId}
}
