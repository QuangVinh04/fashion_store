'use client'

import {useRef,useState} from 'react'

// eslint-disable-next-line import/named
import {useQuery} from '@tanstack/react-query'
import {Alert,Box,Button,Checkbox,FormControlLabel,TextField,Typography} from '@mui/material'

import type {OrderDetail,CarrierVerificationInput} from '@/types/orders'
import {ordersApi} from '@/libs/api/orders'
import {toVietnameseError} from '@/libs/api/errors'
import ConfirmActionDialog from '@/components/backoffice/ConfirmActionDialog'
import EvidenceUpload from '@/views/returns/EvidenceUpload'
import EvidenceViewer from '@/views/returns/EvidenceViewer'
import {prepareCarrierVerification} from './paid-cancellation'

export default function CancellationCarrierVerificationPanel({order,onRefresh}:{order:OrderDetail;onRefresh:()=>Promise<unknown>}) {
  const c=order.cancellation
  const key=`cancellation-carrier-verification:${c?.id}`

  const [draft,setDraft]=useState<Omit<CarrierVerificationInput,'operationId'>>(()=>{
    try {const old=JSON.parse(sessionStorage.getItem(key)||'null');

if(old?.caseId===c?.id && old.payload)return old.payload} catch {/* The submit guard will reject malformed stored data. */}
    
return {reason:'',trackingCode:order.trackingCode||'',carrierCancelled:false,noHandoff:false,noShipmentExists:false,mediaIds:[]}
  })

  const [open,setOpen]=useState(false);const [busy,setBusy]=useState(false);const [uploading,setUploading]=useState(false);const [error,setError]=useState<unknown>(null)
  const sending=useRef(false)

  const proof=useQuery({queryKey:['cancellation-carrier-verification',order.id,c?.carrierVerifiedAt],
    queryFn:()=>ordersApi.cancellationVerification(order.id),enabled:!!c?.carrierVerifiedAt,retry:false})

  async function submit() {
    if(!c || sending.current || uploading)return
    sending.current=true;setBusy(true);setError(null)

    try {
      const body=prepareCarrierVerification(sessionStorage,c.id,{...draft,reason:draft.reason.trim()},()=>crypto.randomUUID())
      const result=await ordersApi.verifyCancellationCarrier(order.id,body)

      if(result.cancellation?.carrierVerifiedAt)sessionStorage.removeItem(key)
      setOpen(false);await Promise.all([onRefresh(),proof.refetch()])
    } catch(failure) {setError(failure);try {await onRefresh()}catch{/* Preserve the same verification until its result is known. */}}
    finally {sending.current=false;setBusy(false)}
  }

  if(!c)return null
  const allowed=!c.closedAt && !c.fulfillmentStoppedAt && c.status!=='FULFILLMENT_EXCEPTION'

  
return <Box sx={{display:'grid',gap:2}}>
    {error ? <Alert severity='error'>{toVietnameseError(error)}</Alert>:null}
    {proof.error && <Alert severity='warning' action={<Button onClick={()=>void proof.refetch()}>Thử lại</Button>}>Không tải được chứng từ xác minh carrier.</Alert>}
    {proof.data && <Box sx={{display:'grid',gap:1}}>
      <Typography variant='body2'>Đã xác minh bởi {proof.data.verifiedBy} · {new Date(proof.data.verifiedAt).toLocaleString('vi-VN')}</Typography>
      <Typography variant='body2'>{proof.data.reason} · {proof.data.noShipmentExists?'Xác minh không có vận đơn':proof.data.trackingCode}</Typography>
      <EvidenceViewer resource='cancellation' id={order.id} mediaIds={proof.data.mediaIds}/>
    </Box>}
    {allowed && <Button variant='outlined' disabled={busy} onClick={()=>setOpen(true)}>Xác minh carrier bằng chứng từ</Button>}
    <ConfirmActionDialog open={open} title='Xác minh đã dừng giao hàng' message='Chỉ xác nhận sau khi kiểm tra với carrier và kho: kiện hàng chưa bàn giao, carrier đã hủy vận đơn hoặc xác nhận không có vận đơn. Chứng từ được lưu cùng người duyệt và thời điểm xác minh.'
      pending={busy||uploading} error={error} confirmLabel='Lưu xác minh và tiếp tục hủy' onConfirm={submit} onClose={()=>setOpen(false)}>
      <Box sx={{display:'grid',gap:2,mt:2}}>
        <TextField label='Kết quả xác minh' multiline minRows={3} fullWidth value={draft.reason} inputProps={{maxLength:500}} disabled={busy} onChange={event=>setDraft({...draft,reason:event.target.value})}/>
        <FormControlLabel control={<Checkbox checked={draft.noShipmentExists} disabled={busy} onChange={event=>setDraft({...draft,noShipmentExists:event.target.checked,trackingCode:event.target.checked?'':order.trackingCode||''})}/>} label='Carrier xác nhận không có vận đơn'/>
        {!draft.noShipmentExists && <TextField label='Mã vận đơn đã xác minh hủy' value={draft.trackingCode} fullWidth disabled={busy} onChange={event=>setDraft({...draft,trackingCode:event.target.value})}/>}
        <FormControlLabel control={<Checkbox checked={draft.carrierCancelled} disabled={busy} onChange={event=>setDraft({...draft,carrierCancelled:event.target.checked})}/>} label='Đã xác minh carrier dừng giao / không có vận đơn'/>
        <FormControlLabel control={<Checkbox checked={draft.noHandoff} disabled={busy} onChange={event=>setDraft({...draft,noHandoff:event.target.checked})}/>} label='Kho xác nhận hàng chưa bàn giao vận chuyển'/>
        <EvidenceUpload purpose='payments' value={draft.mediaIds} disabled={busy} onBusy={setUploading} onChange={mediaIds=>setDraft(value=>({...value,mediaIds}))}/>
      </Box>
    </ConfirmActionDialog>
  </Box>
}
