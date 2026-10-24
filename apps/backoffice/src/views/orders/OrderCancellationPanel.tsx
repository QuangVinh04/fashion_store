'use client'

import {useEffect,useRef,useState} from 'react'

import Link from 'next/link'

import {Alert,Box,Button,Card,CardContent,CardHeader,TextField,Typography} from '@mui/material'

import type {OrderDetail} from '@/types/orders'
import {ordersApi} from '@/libs/api/orders'
import {toVietnameseError} from '@/libs/api/errors'
import ConfirmActionDialog from '@/components/backoffice/ConfirmActionDialog'
import {cancellationState,prepareCancellation} from './paid-cancellation'
import CancellationCarrierVerificationPanel from './CancellationCarrierVerificationPanel'

export default function OrderCancellationPanel({order,onRefresh}:{order:OrderDetail;onRefresh:()=>Promise<unknown>}) {
  const [open,setOpen]=useState(false);const [busy,setBusy]=useState(false);const [error,setError]=useState<unknown>(null)

  const [reason,setReason]=useState(()=>{try {const value=JSON.parse(sessionStorage.getItem(`order-cancellation:${order.id}`)||'null')

    
return value?.orderId===order.id && typeof value?.reason==='string'?value.reason:'Shop tiếp nhận yêu cầu hủy đơn'
  } catch{return 'Shop tiếp nhận yêu cầu hủy đơn'}})

  const sending=useRef(false);const refresh=useRef(onRefresh);

refresh.current=onRefresh
  const c=order.cancellation
  const polling=!!c && !c.closedAt && ['FULFILLMENT_PENDING','INVENTORY_PENDING','REFUND_PENDING'].includes(c.status)

  useEffect(()=>{
    if(!polling)return
    let attempts=0;const timer=setInterval(()=>{if(document.visibilityState==='visible' && attempts++<15)void refresh.current().catch(()=>{})},4000)

    
return ()=>clearInterval(timer)
  },[order.id,polling])

  async function submit(retry=false) {
    if(sending.current)return
    sending.current=true;setBusy(true);setError(null)

    try {
      const body=retry && c?{operationId:c.operationId,reason:c.reason}:prepareCancellation(sessionStorage,order.id,reason.trim(),()=>crypto.randomUUID())
      const result=retry?await ordersApi.retryCancellation(order.id,body):await ordersApi.cancel(order.id,body)

      if(result.cancellation?.operationId===body.operationId || result.status==='CANCELLED')sessionStorage.removeItem(`order-cancellation:${order.id}`)
      setOpen(false);await onRefresh()
    } catch(failure) {setError(failure);try {await onRefresh()} catch { /* Retain the operation for reconciliation. */ }}
    finally {sending.current=false;setBusy(false)}
  }

  if(!c && !order.canCancel)return null
  const state=c?cancellationState(c):null

  
return <Card sx={{mb:3,minWidth:0}}><CardHeader title='Hủy đơn và hoàn tiền'/><CardContent>
    <Box sx={{display:'grid',gap:2}}>
      {error ? <Alert severity='error'>{toVietnameseError(error)}</Alert>:null}
      {c ? <>
        <Typography fontWeight={600}>{state?.label}</Typography><Typography>{c.reason}</Typography>
        <Typography variant='body2'>{c.fulfillmentStoppedAt?'Đã dừng giao hàng':'Chờ xác minh dừng giao hàng'} · {c.inventoryCancelledAt?'Đã xử lý kho':'Chờ xác nhận kho'}</Typography>
        <Typography variant='body2'>{state?.credited?'Tiền đã được ghi vào ví khách hàng':c.status==='COMPLETED'?'Không có khoản tiền cần hoàn':'Chưa có xác nhận tiền đã vào ví'}</Typography>
        {c.failureReason && <Alert severity='warning'>{c.failureReason}</Alert>}
        {c.reviewOwner && <Typography variant='body2'>Bộ phận phụ trách: {c.reviewOwner}{c.reviewDueAt?` · Hạn xử lý: ${new Date(c.reviewDueAt).toLocaleString('vi-VN')}`:''}</Typography>}
        <Typography variant='body2'>Mã yêu cầu: {c.id}</Typography>
        <CancellationCarrierVerificationPanel key={c.id} order={order} onRefresh={onRefresh}/>
        {c.refundLedgerEntryId && <Typography variant='body2'>Bút toán ví: {c.refundLedgerEntryId}</Typography>}
        <Box sx={{display:'flex',gap:2,flexWrap:'wrap'}}><Button disabled={busy} onClick={()=>void onRefresh()}>Cập nhật trạng thái</Button>
          {!c.closedAt && c.status!=='FULFILLMENT_EXCEPTION' && <Button disabled={busy} onClick={()=>void submit(true)}>Đối soát / tiếp tục cùng yêu cầu</Button>}
          <Button component={Link} href='/inventory'>Xem tồn kho</Button></Box>
      </>:<>
        <Typography variant='body2'>Hủy trước khi bàn giao vận chuyển. Khoản tiền đã thu được hoàn đầy đủ vào ví sau khi dừng giao hàng và xử lý kho.</Typography>
        <Button color='error' variant='outlined' onClick={()=>setOpen(true)}>Yêu cầu hủy đơn</Button>
      </>}
    </Box>
    <ConfirmActionDialog open={open} title='Hủy đơn trước bàn giao' message='Xác minh dừng giao hàng, xử lý kho và hoàn khoản tiền đã thu vào ví khách hàng. Đơn chỉ đóng khi các bước này hoàn tất.'
      pending={busy} error={error} confirmLabel='Gửi yêu cầu hủy' onConfirm={()=>submit()} onClose={()=>setOpen(false)}>
      <TextField label='Lý do hủy' value={reason} onChange={event=>setReason(event.target.value)} fullWidth multiline minRows={3} required inputProps={{maxLength:500}} sx={{mt:2}}/>
    </ConfirmActionDialog>
  </CardContent></Card>
}
