import { it, expect } from 'vitest'

import {
  canApproveRefund,
  getReturnActions,
  getRefundState,
  validateReceive,
  prepareOperation,
  readOperation,
  operationConfirmed
} from './return-actions'
import type { ReturnCase } from '@/types/returns'

const received = {
  id: 'case',
  orderId: 'order',
  userId: 'buyer',
  reason: 'Return',
  createdAt: '2026-10-14',
  kind: 'CUSTOMER_RETURN',
  status: 'RECEIVED',
  qcPassed: true,
  images: [],
  qcEvidenceIds: []
} as ReturnCase

const paid = { method: 'WALLET', status: 'COMPLETED', amount: '80000', currency: 'VND' }

it('derives permitted actions from canonical statuses without actions or version fields', () => {
  expect(getReturnActions({ ...received, status: 'REQUESTED' })).toEqual(['approve', 'reject', 'cancel'])
  expect(getReturnActions({ ...received, status: 'APPROVED' })).toEqual(['receive', 'cancel'])
  expect(getReturnActions({ ...received, qcPassed: false })).toEqual(['reject'])
  expect(getReturnActions({ ...received, status: 'REFUNDED' })).toEqual([])
  expect(getReturnActions({ ...received, status: 'DISPUTE' })).toEqual([])
})
it('requires QC and captured funds, including actual COD proof from payment', () => {
  expect(canApproveRefund(received, paid)).toBe(true)
  expect(canApproveRefund({ ...received, qcPassed: false }, paid)).toBe(false)
  expect(canApproveRefund(received, { ...paid, method: 'COD', status: 'COD_PENDING' })).toBe(false)
  expect(canApproveRefund(received, { ...paid, method: 'COD' })).toBe(false)
  expect(canApproveRefund(received, { ...paid, method: 'COD', codCollectedAt: '2026-10-14T01:00:00Z' })).toBe(true)
})
it('verified loss bypasses receiving only with verified evidence', () => {
  const loss = {
    ...received,
    kind: 'VERIFIED_LOSS',
    status: 'APPROVED',
    qcPassed: undefined,
    verificationBy: 'admin',
    verificationEvidenceIds: ['photo']
  } as ReturnCase

  expect(getReturnActions(loss)).toEqual(['refund'])
  expect(canApproveRefund(loss, paid)).toBe(true)
  expect(canApproveRefund({ ...loss, verificationEvidenceIds: [] }, paid)).toBe(false)
})

it('allows a zero-funds carrier closure to refund after verified late COD collection', () => {
  const closed = {
    ...received,
    kind: 'CARRIER_RETURN',
    status: 'CANCELLED',
    qcPassed: true,
    receivedAt: '2026-10-14T01:00:00Z',
    closedAt: '2026-10-14T02:00:00Z',
    approvedRefundAmount: '0'
  } as ReturnCase

  expect(canApproveRefund(closed, { ...paid, method: 'COD', codCollectedAt: '2026-10-14T03:00:00Z' })).toBe(true)
  expect(canApproveRefund(closed, { ...paid, method: 'COD', status: 'COD_PENDING' })).toBe(false)
  expect(
    canApproveRefund(
      { ...closed, approvedRefundAmount: '80000' },
      { ...paid, method: 'COD', codCollectedAt: '2026-10-14T03:00:00Z' }
    )
  ).toBe(false)
})
it('202 stays RECEIVED and refundStartedAt means pending until confirmed credit', () => {
  const pending = { ...received, refundStartedAt: '2026-10-14T01:00:00Z' }

  expect(getRefundState(pending)).toBe('pending')
  expect(getReturnActions(pending)).toEqual([])
  expect(getRefundState({ ...pending, refundFailureReason: 'Rejected' })).toBe('failed')
  expect(getReturnActions({ ...pending, refundFailureReason: 'Rejected' })).toEqual(['refund'])
  expect(getRefundState({ ...pending, status: 'REFUNDED', refundLedgerEntryId: 'ledger' })).toBe('credited')
})
it('validates canonical QC fields and the five-image limit', () => {
  expect(validateReceive({ qcPassed: true, qcNote: 'Checked', mediaIds: [] })).toBe('')
  expect(validateReceive({ qcPassed: false, qcNote: 'Damaged', mediaIds: [] })).not.toBe('')
  expect(validateReceive({ qcPassed: false, qcNote: 'Damaged', mediaIds: ['proof'] })).toBe('')
  expect(validateReceive({ qcPassed: true, qcNote: 'x'.repeat(501), mediaIds: [] })).not.toBe('')
  expect(validateReceive({ qcPassed: true, qcNote: 'Checked', mediaIds: ['1', '2', '3', '4', '5', '6'] })).not.toBe('')
})
it('keeps the exact uncertain request across reload without a fictitious version', () => {
  const memory = new Map<string, string>()

  const storage = {
    getItem: (key: string) => memory.get(key) || null,
    setItem: (key: string, value: string) => memory.set(key, value)
  }

  const operation = prepareOperation(storage, 'case', 'receive', {
    qcPassed: false,
    qcNote: 'Damaged',
    mediaIds: ['photo']
  })

  expect(readOperation(storage, 'case')).toEqual(operation)
  expect(() =>
    prepareOperation(storage, 'case', 'receive', { qcPassed: true, qcNote: 'Changed', mediaIds: [] })
  ).toThrow()
  expect(() => readOperation({ getItem: () => '{invalid' }, 'case')).toThrow()
})

it('reconciles an unpaid COD closure using the backend prefixed reason', () => {
  const closed = {
    ...received,
    kind: 'CARRIER_RETURN',
    status: 'CANCELLED',
    approvedRefundAmount: '0',
    closedAt: '2026-10-14T03:00:00Z',
    rejectReason: 'No COD collected; no refund due. Carrier returned parcel'
  } as ReturnCase

  expect(
    operationConfirmed({ action: 'close-without-refund', body: { reason: 'Carrier returned parcel' } }, closed)
  ).toBe(true)
  expect(operationConfirmed({ action: 'close-without-refund', body: { reason: 'Different evidence' } }, closed)).toBe(
    false
  )
})
