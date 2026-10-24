export const fulfillmentStorageKey = 'backoffice:fulfillment-return-request'
export type FulfillmentRequest = { orderId: string; kind: 'carrier' | 'lost'; reason: string; mediaIds: string[] }
type Storage = { getItem: (key: string) => string | null; setItem: (key: string, value: string) => unknown }

export function readFulfillmentRequest(storage: Pick<Storage, 'getItem'>): FulfillmentRequest | null {
  const raw = storage.getItem(fulfillmentStorageKey)

  if (!raw) return null
  const value = JSON.parse(raw)

  if (
    !value ||
    !['carrier', 'lost'].includes(value.kind) ||
    typeof value.orderId !== 'string' ||
    !value.orderId.trim() ||
    typeof value.reason !== 'string' ||
    !value.reason.trim() ||
    value.reason.length > 500 ||
    !Array.isArray(value.mediaIds) ||
    !value.mediaIds.length ||
    value.mediaIds.length > 5 ||
    value.mediaIds.some((id: unknown) => typeof id !== 'string' || !id)
  ) {
    throw new Error('Không đọc được hồ sơ vận chuyển đang gửi. Cần đối chiếu hồ sơ trước khi gửi yêu cầu mới.')
  }

  return value
}

export function saveFulfillmentRequest(storage: Storage, input: FulfillmentRequest) {
  const pending = readFulfillmentRequest(storage)

  if (pending && JSON.stringify(pending) !== JSON.stringify(input))
    throw new Error('Cần xử lý hồ sơ vận chuyển đã gửi trước khi thay đổi yêu cầu.')
  storage.setItem(fulfillmentStorageKey, JSON.stringify(input))
}
