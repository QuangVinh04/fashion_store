import {act} from 'react'

import {createRoot} from 'react-dom/client'
import {it,expect,vi} from 'vitest'

import WalletDetail from './WalletDetail'

vi.mock('@tanstack/react-query',()=>({useQuery:()=>({data:{id:'old-refund-entry',direction:'CREDIT',amount:'120000',balanceAfter:'220000',sourceType:'RETURN_REFUND',sourceId:'case',createdAt:'2026-10-11T00:00:00Z'},isLoading:false,error:null,refetch:vi.fn()})}))
vi.mock('@/hooks/useWallets',()=>({useWallet:()=>({wallet:{data:{userId:'buyer',currency:'VND',balance:'9007199254740993'},isLoading:false,error:null,refetch:vi.fn()},ledger:{data:{content:[],totalElements:0},isLoading:false,error:null,refetch:vi.fn()},topUps:{data:{content:[],totalElements:0},isLoading:false,error:null,refetch:vi.fn()}})}))
it('admin wallet displays exact balance and no balance adjustment or withdrawal control',async()=>{
 ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT=true
 const container=document.createElement('div');const root=createRoot(container);

document.body.append(container)

 try {
  await act(async()=>root.render(<WalletDetail userId='buyer'/>))
  expect(container.textContent).toMatch(/9.007.199.254.740.993/)
  expect(container.querySelector('input[type="number"]')).toBeNull()
  expect(Array.from(container.querySelectorAll('button')).some(button=>/Rút tiền|Cộng số dư|Trừ số dư|Nạp tiền/.test(button.textContent||''))).toBe(false)
 } finally {act(()=>root.unmount());container.remove()}
})
it('deep link displays the requested ledger entry independently of the paginated list',async()=>{
 ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT=true
 const container=document.createElement('div');const root=createRoot(container);

document.body.append(container)

 try {
  await act(async()=>root.render(<WalletDetail userId='buyer' ledgerId='old-refund-entry'/>))
  expect(container.textContent).toContain('old-refund-entry')
  expect(container.textContent).toContain('RETURN_REFUND: case')
 } finally {act(()=>root.unmount());container.remove()}
})
