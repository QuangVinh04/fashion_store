import {expect,it} from 'vitest'

import {allowedOrderActions} from './order-actions'
import {prepareCancellation,cancellationState,prepareCarrierVerification} from './paid-cancellation'

it('pending cancellation blocks fulfillment actions and does not claim wallet credit',()=>{
 expect(allowedOrderActions({status:'CONFIRMED',cancellation:{status:'REFUND_PENDING'}})).toEqual([])
 expect(cancellationState({status:'REFUND_PENDING'}).credited).toBe(false)
 expect(cancellationState({status:'COMPLETED',refundLedgerEntryId:'ledger'}).credited).toBe(true)
})
it('reload preserves operation and changed payload is rejected',()=>{
 const values=new Map<string,string>();const storage={getItem:(key:string)=>values.get(key)||null,setItem:(key:string,value:string)=>{values.set(key,value)}}

 expect(prepareCancellation(storage,'order','reason',()=> 'first').operationId).toBe('first')
 expect(prepareCancellation(storage,'order','reason',()=> 'second').operationId).toBe('first')
 expect(()=>prepareCancellation(storage,'order','changed',()=> 'third')).toThrow()
})
it('carrier verification requires no-handoff proof and preserves the complete payload across reload',()=>{
 const values=new Map<string,string>();const storage={getItem:(key:string)=>values.get(key)||null,setItem:(key:string,value:string)=>{values.set(key,value)}}
 const body={reason:'Carrier verified',carrierCancelled:true,noHandoff:true,noShipmentExists:false,trackingCode:'tracking',mediaIds:['photo']}

 expect(prepareCarrierVerification(storage,'case',body,()=> 'first').operationId).toBe('first')
 expect(prepareCarrierVerification(storage,'case',body,()=> 'second').operationId).toBe('first')
 expect(()=>prepareCarrierVerification(storage,'case',{...body,trackingCode:'foreign'},()=> 'third')).toThrow()
 expect(()=>prepareCarrierVerification(storage,'other',{...body,noHandoff:false},()=> 'fourth')).toThrow()
})
