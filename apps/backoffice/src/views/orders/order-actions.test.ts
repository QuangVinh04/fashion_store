import { expect, it } from 'vitest'

import { allowedOrderActions } from './order-actions'

it('admin status buttons cannot bypass receipt, return or refund workflows',()=>{
  expect(allowedOrderActions({status:'DELIVERED'})).toEqual([])
  expect(allowedOrderActions({status:'RETURNED'})).toEqual([])
})

it('follows the backend sequence and requires a shipment before shipping', () => {
  expect(allowedOrderActions({ status: 'PENDING' })).toEqual([])
  expect(allowedOrderActions({ status: 'CONFIRMED' })).toEqual(['PROCESSING'])
  expect(allowedOrderActions({ status: 'PROCESSING' })).toEqual(['PACKED'])
  expect(allowedOrderActions({ status: 'PACKED' })).toEqual([])
  expect(allowedOrderActions({ status: 'PACKED' }, { id: 's1' })).toEqual(['SHIPPING'])
  expect(allowedOrderActions({ status: 'PACKED', trackingCode: 'track' })).toEqual(['SHIPPING'])
  expect(allowedOrderActions({ status: 'RETURNED' })).toEqual([])
})
