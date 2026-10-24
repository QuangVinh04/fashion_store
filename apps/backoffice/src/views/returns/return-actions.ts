import type { ReceiveReturnInput, ReturnAction, ReturnCase, ReturnOperation } from '@/types/returns'

export const returnStatusLabels: Record<string, string> = {
  REQUESTED: 'Chờ duyệt',
  APPROVED: 'Đã duyệt, chờ nhận hàng',
  RECEIVED: 'Đã nhận hàng',
  REFUNDED: 'Đã hoàn ví',
  REJECTED: 'Đã từ chối',
  CANCELLED: 'Đã đóng / hủy',
  PENDING: 'Chờ duyệt (hồ sơ cũ)',
  RETURN_APPROVED: 'Đã duyệt (hồ sơ cũ)',
  PICKUP_PENDING: 'Hồ sơ cũ: chờ lấy hàng',
  PICKED_UP: 'Hồ sơ cũ: đã lấy hàng',
  QC_PASSED: 'Hồ sơ cũ: QC đạt',
  DISPUTE: 'Hồ sơ cũ: tranh chấp',
  DISPOSITION_PENDING: 'Hồ sơ cũ: gửi lại hàng',
  REFUND_PENDING: 'Hồ sơ cũ: chờ hoàn tiền',
  RETURN_CANCELLED: 'Đã hủy (hồ sơ cũ)',
  LEGACY_REVIEW_REQUIRED: 'Hồ sơ cũ cần rà soát'
}
export const returnKindLabels: Record<string, string> = {
  CUSTOMER_RETURN: 'Khách trả hàng',
  CARRIER_RETURN: 'Hãng vận chuyển hoàn hàng',
  VERIFIED_LOSS: 'Thất lạc đã xác minh'
}

export const returnHistoryLabels: Record<string, string> = {
  RETURN_REQUESTED: 'Khách yêu cầu trả hàng',
  RETURN_APPROVED: 'Đã duyệt trả hàng',
  RETURN_RECEIVED: 'Đã nhận hàng và ghi nhận QC',
  RETURN_REJECTED: 'Đã từ chối trả hàng',
  RETURN_CANCELLED: 'Đã hủy yêu cầu trả hàng',
  RETURN_EXPIRED: 'Đã hủy do quá hạn gửi hàng',
  RETURN_REFUND_REQUESTED: 'Đã yêu cầu hoàn vào ví',
  RETURN_REFUNDED: 'Đã xác nhận hoàn vào ví',
  CARRIER_RETURN_VERIFIED: 'Đã xác minh hàng hãng vận chuyển hoàn về',
  VERIFIED_LOSS_VERIFIED: 'Đã xác minh hàng thất lạc'
}

export function getRefundState(request: ReturnCase) {
  if (request.status === 'REFUNDED') return 'credited'
  if (request.refundFailureReason) return 'failed'
  if (request.refundStartedAt) return 'pending'

  return 'none'
}

export function getReturnActions(request: ReturnCase): ReturnAction[] {
  if (request.closedAt || ['REFUNDED', 'REJECTED', 'CANCELLED'].includes(request.status)) return []
  if (getRefundState(request) === 'pending') return []
  if (request.kind === 'VERIFIED_LOSS') return request.status === 'APPROVED' ? ['refund'] : []
  if (!['CUSTOMER_RETURN', 'CARRIER_RETURN'].includes(request.kind)) return []
  if (['REQUESTED', 'PENDING'].includes(request.status)) return ['approve', 'reject', 'cancel']
  if (['APPROVED', 'RETURN_APPROVED'].includes(request.status)) return ['receive', 'cancel']

  if (request.status === 'RECEIVED') {
    if (request.qcPassed === true) return ['refund']
    if (request.qcPassed === false) return ['reject']
  }

  return []
}

export function canApproveRefund(
  request: ReturnCase,
  payment: { method: string; status: string; codCollectedAt?: string | null } | null | undefined
) {
  const verified =
    request.kind !== 'VERIFIED_LOSS' || (!!request.verificationBy && !!request.verificationEvidenceIds?.length)

  const reopenCarrier =
    request.kind === 'CARRIER_RETURN' &&
    request.status === 'CANCELLED' &&
    request.qcPassed === true &&
    !!request.receivedAt &&
    !request.refundStartedAt &&
    /^0(?:\.0+)?$/.test(request.approvedRefundAmount || '') &&
    payment?.method === 'COD' &&
    !!payment.codCollectedAt

  return (
    verified &&
    (getReturnActions(request).includes('refund') || reopenCarrier) &&
    !!payment &&
    payment.status === 'COMPLETED' &&
    (payment.method !== 'COD' || !!payment.codCollectedAt)
  )
}

export function validateReceive(input: ReceiveReturnInput) {
  if (typeof input.qcPassed !== 'boolean') return 'Cần chọn kết quả kiểm tra.'
  if (!input.qcNote.trim() || input.qcNote.trim().length > 500) return 'Ghi chú kiểm tra cần từ 1 đến 500 ký tự.'
  if (input.mediaIds.length > 5 || new Set(input.mediaIds).size !== input.mediaIds.length)
    return 'Chỉ gửi tối đa 5 ảnh khác nhau.'
  if (!input.qcPassed && !input.mediaIds.length) return 'QC không đạt cần ảnh bằng chứng.'

  return ''
}

type Storage = { getItem: (key: string) => string | null; setItem: (key: string, value: string) => unknown }

export function readOperation(storage: Pick<Storage, 'getItem'>, key: string): ReturnOperation | null {
  const raw = storage.getItem(key)

  if (!raw) return null
  const value = JSON.parse(raw)

  if (
    !value ||
    !['approve', 'reject', 'receive', 'refund', 'cancel', 'close-without-refund'].includes(value.action) ||
    !value.body ||
    typeof value.body !== 'object' ||
    Array.isArray(value.body) ||
    'expectedVersion' in value.body
  ) {
    throw new Error(
      'Yêu cầu lưu trên trình duyệt thuộc luồng cũ hoặc không hợp lệ. Đối chiếu hồ sơ trước khi bỏ yêu cầu này.'
    )
  }

  return value
}

export function prepareOperation(
  storage: Storage,
  key: string,
  action: ReturnAction,
  body: Record<string, unknown>
): ReturnOperation {
  const previous = readOperation(storage, key)

  if (previous) {
    if (previous.action !== action || JSON.stringify(previous.body) !== JSON.stringify(body)) {
      throw new Error('Có yêu cầu chưa xác định kết quả. Hãy đối chiếu hoặc thử lại cùng yêu cầu.')
    }

    return previous
  }

  const operation = { action, body }

  storage.setItem(key, JSON.stringify(operation))

  return operation
}

export function operationConfirmed(operation: ReturnOperation, request: ReturnCase) {
  switch (operation.action) {
    case 'approve':
      return ['APPROVED', 'RETURN_APPROVED'].includes(request.status)
    case 'receive':
      return (
        request.status === 'RECEIVED' &&
        request.qcPassed === operation.body.qcPassed &&
        request.qcNote === operation.body.qcNote &&
        JSON.stringify(request.qcEvidenceIds || []) === JSON.stringify(operation.body.mediaIds || [])
      )
    case 'refund':
      return !!request.refundStartedAt || request.status === 'REFUNDED'
    case 'reject':
      return request.status === 'REJECTED' && request.rejectReason === operation.body.rejectReason
    case 'cancel':
      return request.status === 'CANCELLED' && request.rejectReason === operation.body.reason
    case 'close-without-refund':
      return (
        request.kind === 'CARRIER_RETURN' &&
        request.status === 'CANCELLED' &&
        !!request.closedAt &&
        /^0(?:\.0+)?$/.test(request.approvedRefundAmount || '') &&
        request.rejectReason ===
          `No COD collected; no refund due. ${String(operation.body.reason).trim()}`.slice(0, 500)
      )
  }
}

export { financialMoney } from '@/utils/format'
