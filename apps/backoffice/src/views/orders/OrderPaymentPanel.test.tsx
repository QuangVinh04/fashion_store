import {act} from 'react'

import {createRoot} from 'react-dom/client'
import {it,expect,vi,afterEach} from 'vitest'

import OrderPaymentPanel from './OrderPaymentPanel'

const state=vi.hoisted(()=>({payment:null as any,error:new Error('payment unavailable') as Error|null}))

vi.mock('@/hooks/usePayments',()=>({usePayment:()=>({payment:{data:state.payment,isLoading:false,error:state.error,refetch:vi.fn()},refunds:{data:null,isLoading:false,error:null,refetch:vi.fn()},cod:{data:null,isLoading:false,error:null,refetch:vi.fn()}})}))
afterEach(()=>{sessionStorage.clear();state.payment=null;state.error=new Error('payment unavailable')})
it('money panel error is local and preserves readable parent order',async()=>{
 ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT=true
 const container=document.createElement('div');const root=createRoot(container);

document.body.append(container)

 try {
  await act(async()=>root.render(<><p>Thông tin đơn hàng vẫn hiển thị</p><OrderPaymentPanel orderId='order'/></>))
  expect(container.textContent).toContain('Thông tin đơn hàng vẫn hiển thị');expect(container.querySelector('[role="alert"]')).toBeTruthy()
 } finally {act(()=>root.unmount());container.remove()}
})
it('saved COD operation can be retried after reload without replacing its proof or time',async()=>{
 ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT=true
 state.payment={id:'pay',orderId:'order',userId:'buyer',method:'COD',provider:'COD',status:'COD_PENDING',amount:'80000',currency:'VND'};state.error=null
 sessionStorage.setItem('backoffice:cod-collection:order',JSON.stringify({operationId:'same-op',receivedAmount:'80000',receivedAt:'2026-10-11T01:00:00Z',evidenceIds:['photo']}))
 const container=document.createElement('div');const root=createRoot(container);

document.body.append(container)

 try {
  await act(async()=>root.render(<OrderPaymentPanel orderId='order'/>))
  const button=Array.from(container.querySelectorAll('button')).find(button=>/Ghi nhận đã thu COD|Kiểm tra chứng từ đã gửi/.test(button.textContent||''))

  expect(button).toBeTruthy();expect(button?.disabled).toBe(false)
 }finally{act(()=>root.unmount());container.remove()}
})
