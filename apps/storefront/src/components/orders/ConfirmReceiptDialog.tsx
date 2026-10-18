import { AlertDialog,AlertDialogContent,AlertDialogHeader,AlertDialogTitle,AlertDialogDescription,AlertDialogFooter,AlertDialogCancel } from '../../app/components/ui/alert-dialog';
import { Button } from '../../app/components/ui/button';
export default function ConfirmReceiptDialog({open,busy,blocked=false,error,onOpenChange,onConfirm}:{open:boolean;busy:boolean;blocked?:boolean;error:string;onOpenChange:(open:boolean)=>void;onConfirm:()=>void}) {
  return <AlertDialog open={open} onOpenChange={value=>{if(!busy)onOpenChange(value);}}><AlertDialogContent>
    <AlertDialogHeader><AlertDialogTitle>Xác nhận đã nhận hàng</AlertDialogTitle>
      <AlertDialogDescription>Bạn chỉ nên xác nhận khi đã kiểm tra kỹ sản phẩm. Sau khi xác nhận, bạn không thể gửi yêu cầu trả hàng/hoàn tiền cho đơn này qua hệ thống.</AlertDialogDescription></AlertDialogHeader>
    {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
    <AlertDialogFooter><AlertDialogCancel disabled={busy}>Quay lại kiểm tra</AlertDialogCancel><Button variant="primary" disabled={busy||blocked} onClick={onConfirm}>{busy?'Đang xác nhận…':'Đã kiểm tra, xác nhận nhận hàng'}</Button></AlertDialogFooter>
  </AlertDialogContent></AlertDialog>;
}
