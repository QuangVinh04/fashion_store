'use client'
import {useState,useEffect} from 'react'

import Link from 'next/link'

import {Alert,Button,Card,CardContent,CardHeader,Chip,Stack,Table,TableBody,TableCell,TableContainer,TableHead,TableRow,TextField,Typography} from '@mui/material'

import QueryState from '@/components/backoffice/QueryState'
import ConfirmActionDialog from '@/components/backoffice/ConfirmActionDialog'
import {usePayment} from '@/hooks/usePayments'
import {paymentsApi} from '@/libs/api/payments'
import type {PaymentDetails} from '@/types/payments'
import EvidenceUpload from '../returns/EvidenceUpload'
import EvidenceViewer from '../returns/EvidenceViewer'
import {financialMoney} from '../returns/return-actions'

function savedCod(key:string) {
  try{const value=JSON.parse(sessionStorage.getItem(key)||'null');

return value?.operationId&&Array.isArray(value.evidenceIds)&&value.receivedAt ? value:null}catch{return null}
}

function localInputTime(value:string) {
  if(!value)return '';const date=new Date(value);

return Number.isNaN(date.getTime())?'':new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,16)
}

function CodCollectionForm({payment,onRefresh}:{payment:PaymentDetails;onRefresh:()=>void}) {
  const key=`backoffice:cod-collection:${payment.orderId}`
  const [stored]=useState(()=>savedCod(key))
  const [photos,setPhotos]=useState<string[]>(stored?.evidenceIds||[]);const [receivedAt,setReceivedAt]=useState(()=>localInputTime(stored?.receivedAt||''));const [uploading,setUploading]=useState(false)
  const [open,setOpen]=useState(false);const [busy,setBusy]=useState(false);const [error,setError]=useState<unknown>(null)

  async function submit() {
    if(busy)return;setBusy(true);setError(null)

    try {
      const old=sessionStorage.getItem(key)
      const body=old?JSON.parse(old):{operationId:crypto.randomUUID(),receivedAmount:payment.amount,receivedAt:new Date(receivedAt).toISOString(),evidenceIds:photos}

      if(!body.operationId || !body.evidenceIds?.length || !body.receivedAt || body.receivedAmount!==payment.amount)throw new Error('Chứng từ thu tiền chưa đầy đủ. Cần rà soát yêu cầu đã lưu.')
      sessionStorage.setItem(key,JSON.stringify(body))
      await paymentsApi.confirmCodCollection(payment.orderId,body);sessionStorage.removeItem(key);setOpen(false);onRefresh()
    } catch(error){setError(error);onRefresh()}finally{setBusy(false)}
  }

  
return <Stack spacing={2} sx={{mt:3}}><Alert severity='warning'>Chưa có xác nhận thu tiền COD. Giao hàng thành công không chứng minh đã thu tiền; cần chứng từ để xác nhận trước khi hoàn ví.</Alert>
    <TextField label='Số tiền đã thu (VND)' value={payment.amount} InputProps={{readOnly:true}}/>
    {stored&&<Alert severity='info'>Có yêu cầu đã gửi trước đó. Kiểm tra lại sẽ giữ nguyên chứng từ, thời điểm và mã thao tác.</Alert>}
    <TextField label='Thời điểm thực tế đã thu tiền' type='datetime-local' value={receivedAt} onChange={event=>setReceivedAt(event.target.value)} InputLabelProps={{shrink:true}} disabled={busy||!!stored}/>
    <EvidenceUpload purpose='payments' value={photos} onChange={setPhotos} onBusy={setUploading} disabled={busy||!!stored}/>
    <Button variant='contained' disabled={busy||uploading||!receivedAt||!photos.length} onClick={()=>setOpen(true)}>{stored?'Kiểm tra chứng từ đã gửi':'Ghi nhận đã thu COD'}</Button>
    <ConfirmActionDialog open={open} title='Xác nhận chứng từ thu COD' message={`Xác nhận đã thu đủ ${financialMoney(payment.amount,payment.currency)}. Thao tác ghi nhận tiền từ khách, không xác nhận tiền đã đối soát về shop.`} pending={busy} error={error} onClose={()=>setOpen(false)} onConfirm={submit}/>
  </Stack>
}

export default function OrderPaymentPanel({orderId}:{orderId:string}) {
  const {payment,refunds,cod}=usePayment(orderId)
  const current=payment.data

  useEffect(()=>{if(cod.data){const key=`backoffice:cod-collection:${orderId}`;

if(savedCod(key)?.operationId===cod.data.operationId)sessionStorage.removeItem(key)}},[cod.data,orderId])
  
return <Card sx={{mb:3}}><CardHeader title='Thanh toán và hoàn tiền' action={<Button onClick={()=>{void payment.refetch();void refunds.refetch();void cod.refetch()}}>Cập nhật</Button>}/><CardContent>
    <QueryState loading={payment.isLoading} error={payment.error} onRetry={()=>void payment.refetch()}>
      {current&&<Stack spacing={2}><Stack direction='row' spacing={1} flexWrap='wrap'><Chip label={current.method==='WALLET'?'Ví cửa hàng':current.method==='COD'?'Thu tiền COD':`Thanh toán ${current.provider}`}/><Chip label={({COMPLETED:'Đã thanh toán',COD_PENDING:'Chưa xác nhận thu COD',PENDING:'Chờ thanh toán',REFUNDED:'Đã hoàn tiền',CANCELLED:'Đã hủy',FAILED:'Thanh toán lỗi'} as Record<string,string>)[current.status]||current.status}/></Stack>
        <Typography>Số tiền: <strong>{financialMoney(current.amount,current.currency)}</strong></Typography>
        {current.paidAt&&<Typography variant='body2'>Xác nhận thanh toán: {new Date(current.paidAt).toLocaleString('vi-VN')}</Typography>}
        {current.failureReason&&<Alert severity='warning'>{current.failureReason}</Alert>}
        <Button component={Link} href={`/wallets/${encodeURIComponent(current.userId)}`} sx={{alignSelf:'flex-start'}}>Xem ví khách hàng</Button>
        {current.method==='COD'&&<QueryState loading={cod.isLoading} error={cod.error} onRetry={()=>void cod.refetch()}>
          {cod.data?<Stack spacing={1}><Typography variant='body2'>Chứng từ thu COD: {cod.data.status==='CONFIRMED'?'Đã xác minh':cod.data.status==='REJECTED'?'Cần rà soát':'Đang xác minh'}</Typography><Typography variant='body2'>Người ghi nhận: {cod.data.confirmedBy}</Typography><Typography variant='body2'>Thời điểm thu: {new Date(cod.data.collectedAt).toLocaleString('vi-VN')}</Typography><Typography variant='body2'>Đối soát tiền với hãng vận chuyển: {cod.data.carrierRemittanceStatus==='UNKNOWN'?'Chưa xác nhận':cod.data.carrierRemittanceStatus}</Typography>{cod.data.failureReason&&<Alert severity='warning'>{cod.data.failureReason}</Alert>}{!!cod.data.evidenceIds?.length&&<EvidenceViewer resource='cod' id={orderId} mediaIds={cod.data.evidenceIds}/>} </Stack>
            : !cod.error&&<CodCollectionForm payment={current} onRefresh={()=>{void payment.refetch();void cod.refetch()}}/>}
        </QueryState>}
      </Stack>}
    </QueryState>
    {current&&<><Typography variant='subtitle1' sx={{mt:3,mb:2}}>Lịch sử hoàn tiền</Typography><QueryState loading={refunds.isLoading} error={refunds.error} empty={!refunds.data?.content.length} onRetry={()=>void refunds.refetch()}><TableContainer><Table size='small'><TableHead><TableRow><TableCell>Khoản hoàn</TableCell><TableCell>Đích hoàn / trạng thái</TableCell><TableCell align='right'>Số tiền</TableCell></TableRow></TableHead><TableBody>{refunds.data?.content.map(row=><TableRow key={row.id}><TableCell sx={{wordBreak:'break-all'}}>{row.businessRefundId||row.id}{row.ledgerEntryId&&<Typography component={Link} href={`/wallets/${encodeURIComponent(current.userId)}?ledgerId=${encodeURIComponent(row.ledgerEntryId)}`} variant='caption' display='block'>Sổ cái: {row.ledgerEntryId}</Typography>}</TableCell><TableCell>{row.destination==='WALLET'?'Ví cửa hàng':'Khoản hoàn cũ qua cổng'} / {row.status}{row.failureReason&&<Typography variant='caption' display='block'>{row.failureReason}</Typography>}</TableCell><TableCell align='right'>{financialMoney(row.amount,row.currency)}</TableCell></TableRow>)}</TableBody></Table></TableContainer></QueryState></>}
  </CardContent></Card>
}
