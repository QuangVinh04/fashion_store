export type ReturnKind = 'CUSTOMER_RETURN' | 'CARRIER_RETURN' | 'VERIFIED_LOSS'
export type ReturnCase = {
  id: string
  orderId: string
  orderCode?: string
  recipientName?: string
  userId: string
  kind: ReturnKind
  reason: string
  images: string[]
  status: string
  createdAt: string
  updatedAt?: string
  verificationBy?: string
  verificationEvidenceIds?: string[]
  reviewedBy?: string
  reviewedAt?: string
  rejectReason?: string
  approvedAt?: string
  receivedAt?: string
  receivedBy?: string
  closedAt?: string
  sendBackBy?: string
  qcPassed?: boolean
  qcNote?: string
  qcEvidenceIds?: string[]
  approvedRefundAmount?: string
  refundLedgerEntryId?: string
  refundStartedAt?: string
  refundFailureReason?: string
}
export type ReceiveReturnInput = { qcPassed: boolean; qcNote: string; mediaIds: string[] }
export type ReturnAction = 'approve' | 'reject' | 'receive' | 'refund' | 'cancel' | 'close-without-refund'
export type ReturnOperation = { action: ReturnAction; body: Record<string, unknown> }
export type EvidenceSource = 'customer' | 'qc' | 'verification'
