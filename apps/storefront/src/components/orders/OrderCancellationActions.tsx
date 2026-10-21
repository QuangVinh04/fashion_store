import {useEffect,useRef,useState} from 'react';
import {Link} from 'react-router';
import type {Order} from '../../api/types';
import {customerError} from '../../api/client';
import {canCancelOrder,cancelOrder,cancellationState,prepareCancellation} from '../../api/order-cancellation';
import {AlertDialog,AlertDialogContent,AlertDialogHeader,AlertDialogTitle,AlertDialogDescription,AlertDialogFooter,AlertDialogCancel} from '../../app/components/ui/alert-dialog';
import {Button} from '../../app/components/ui/button';

export default function OrderCancellationActions({order,onRefresh,onCancelRequested}:{order:Order;onRefresh:()=>Promise<unknown>;onCancelRequested?:()=>void}) {
  const [open,setOpen]=useState(false);const [busy,setBusy]=useState(false);const [error,setError]=useState('');
  // Đơn chưa thanh toán: backend huỷ payment rồi nhả kho, đơn về CANCELLED sau vài giây.
  const unpaid=order.status==='PENDING';const [unpaidCancelling,setUnpaidCancelling]=useState(false);
  const [reason,setReason]=useState(()=>{try {
    const saved=JSON.parse(sessionStorage.getItem(`order-cancellation:${order.id}`)||'null');
    return saved?.orderId===order.id && typeof saved?.reason==='string'?saved.reason:'Khách hàng yêu cầu hủy đơn';
  } catch {return 'Khách hàng yêu cầu hủy đơn';}});
  const sending=useRef(false);const refresh=useRef(onRefresh);refresh.current=onRefresh;
  const c=order.cancellation;
  useEffect(()=>{
    if(!c || c.closedAt || !['FULFILLMENT_PENDING','INVENTORY_PENDING','REFUND_PENDING'].includes(c.status))return;
    let attempts=0;const timer=setInterval(()=>{if(document.visibilityState==='visible' && attempts++<15)void refresh.current().catch(()=>{});},4000);
    return ()=>clearInterval(timer);
  },[order.id,c?.status,c?.closedAt]);
  async function submit() {
    if(sending.current)return;sending.current=true;setBusy(true);setError('');
    try {
      const body=prepareCancellation(sessionStorage,order.id,reason.trim(),()=>crypto.randomUUID());
      const result=await cancelOrder(order.id,body);
      if(result.cancellation?.operationId===body.operationId || result.status==='CANCELLED' || unpaid)sessionStorage.removeItem(`order-cancellation:${order.id}`);
      if(result.status==='PENDING'){setUnpaidCancelling(true);onCancelRequested?.();}
      setOpen(false);await onRefresh();
    } catch(failure) {
      setError(customerError(failure)||'Chưa xác định được kết quả hủy. Hãy kiểm tra trạng thái đơn trước khi thử lại.');
      try {await onRefresh();} catch { /* Keep the same operation until its result is known. */ }
    } finally {sending.current=false;setBusy(false);}
  }
  if(c) {
    const state=cancellationState(c);
    return <div className="space-y-3 border border-border rounded-xl p-4" role="status">
      <p className="font-semibold text-sm">{state.label}</p>
      <ol className="space-y-2 text-xs text-muted-foreground">
        <li>{c.fulfillmentStoppedAt?'Đã dừng giao hàng':'Đang xác minh dừng giao hàng'}</li>
        <li>{c.inventoryCancelledAt?'Đã xử lý kho':'Chờ xử lý kho'}</li>
        <li>{state.credited?'Tiền đã được hoàn vào ví':c.status==='COMPLETED'?'Đơn không có khoản tiền cần hoàn':'Tiền chưa được xác nhận hoàn vào ví'}</li>
      </ol>
      {state.credited && <Link to="/profile/wallet" className="text-primary text-sm underline">Xem số dư và giao dịch ví</Link>}
      <Button variant="outline" size="sm" onClick={()=>void onRefresh().catch(()=>setError('Không thể tải trạng thái. Hãy thử lại.'))}>Cập nhật trạng thái</Button>
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
    </div>;
  }
  if(unpaidCancelling && order.status==='PENDING')return <p role="status" className="text-sm text-muted-foreground">Đang hủy đơn hàng và hoàn trả hàng giữ chỗ…</p>;
  if(!canCancelOrder(order))return null;
  return <>
    <Button variant="outline" className="w-full" disabled={busy} onClick={()=>setOpen(true)}>Yêu cầu hủy đơn hàng</Button>
    <p className="text-xs text-muted-foreground text-center">{unpaid?'Đơn hàng chưa thanh toán sẽ được hủy ngay.':'Có thể hủy trước khi bàn giao vận chuyển. Khoản đã thanh toán sẽ được hoàn vào ví cửa hàng.'}</p>
    <AlertDialog open={open} onOpenChange={value=>{if(!busy)setOpen(value);}}><AlertDialogContent>
      <AlertDialogHeader><AlertDialogTitle>Yêu cầu hủy đơn hàng</AlertDialogTitle>
        <AlertDialogDescription>{unpaid?'Phiên thanh toán sẽ bị hủy và đơn hàng không thể thanh toán tiếp. Nếu bạn vừa thanh toán xong, tiền sẽ được hoàn vào ví.':'Shop cần xác minh dừng giao hàng trước khi hoàn tất hủy. Tiền đã thu sẽ được hoàn đầy đủ vào ví, gồm phí giao hàng đã thanh toán.'}</AlertDialogDescription></AlertDialogHeader>
      <label className="text-sm space-y-2">Lý do hủy<textarea value={reason} onChange={event=>setReason(event.target.value)} disabled={busy} maxLength={500} rows={3} className="w-full border border-border rounded-lg p-3"/></label>
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      <AlertDialogFooter><AlertDialogCancel disabled={busy}>Giữ đơn hàng</AlertDialogCancel><Button disabled={busy||!reason.trim()} onClick={()=>void submit()}>{busy?'Đang gửi…':'Gửi yêu cầu hủy'}</Button></AlertDialogFooter>
    </AlertDialogContent></AlertDialog>
  </>;
}
