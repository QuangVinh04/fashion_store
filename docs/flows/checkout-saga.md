# Luồng đặt hàng — checkout saga

Điều phối: `order-service`, `saga/service/OrderSagaService`. Saga loại `CHECKOUT`, một saga cho mỗi đơn. Mọi command và reply mang `correlationId` = `sagaId`.

## Trước saga

1. Khách thêm hàng vào giỏ: `/api/v1/cart/items`.
2. `POST /api/v1/checkouts` tạo phiên checkout. order-service kiểm tra lại giỏ với catalog (còn bán, giá, tồn kho), đọc địa chỉ từ identity, tính phí GHN. Các lời gọi HTTP chạy ngoài transaction. Ghi kết quả có kiểm tra `version`.
3. `POST /api/v1/orders/{checkoutId}` tạo đơn `PENDING`, chụp địa chỉ từ checkout, khởi tạo saga và phát `inventory.reservation.requested`.

## Các bước

| bước | deadline | command | reply thành công | reply thất bại |
|---|---|---|---|---|
| `RESERVE_INVENTORY` | 60s | `inventory.reservation.requested` | `inventory.reserved` | `inventory.rejected` → hủy đơn |
| `AUTHORIZE_PAYMENT` | 900s | `payment.requested` | `payment.completed` | `payment.failed` → `RELEASE_INVENTORY` |
| `CONFIRM_INVENTORY` | 60s | `inventory.confirmation.requested` | `inventory.confirmed` → đơn `CONFIRMED`, saga `COMPLETED`, phát `order.confirmed` | `inventory.confirmation.rejected` → bù trừ theo phương thức thanh toán |

`AUTHORIZE_PAYMENT` có deadline dài vì khách có thể đang thao tác trên cổng thanh toán. `payment.initiated` ở bước này chỉ lưu `paymentUrl` vào đơn. Chi tiết thanh toán: `docs/flows/payment.md`.

Sau khi nhận `order.confirmed`, order-service chuyển đơn `CONFIRMED → PROCESSING`.

## Bù trừ

| tình huống | bước bù trừ | command |
|---|---|---|
| thanh toán thất bại | `RELEASE_INVENTORY` | `inventory.release.requested` |
| xác nhận kho bị từ chối, trả bằng ví | `REFUND_WALLET`, rồi `RELEASE_INVENTORY` | `wallet.saga-compensation.requested` |
| xác nhận kho bị từ chối, COD | `RELEASE_INVENTORY` | `inventory.release.requested` |
| xác nhận kho bị từ chối, online | `REFUND_ORDER_FUNDS`, rồi `RELEASE_INVENTORY` | `payment.order-funds-refund.requested` |
| khách hủy đơn `PENDING` đang chờ thanh toán | `CANCEL_PAYMENT`, rồi `RELEASE_INVENTORY` | `payment.cancellation.requested` |

- `inventory.released` → đơn `CANCELLED`, saga `COMPENSATED`, phát `order.cancelled`.
- `payment.cancellation.rejected` (khách đã trả tiền) → saga đi tiếp sang `CONFIRM_INVENTORY`.
- Tiền online về muộn sau khi saga đã bù trừ: hoàn vào ví bằng `REFUND_ORDER_FUNDS`.
- Khách hủy đơn `PENDING` chưa giữ được kho: đóng đơn ngay.

## Deadline và retry

- `OrderSagaTimeoutScanner` quét mỗi 15s (`app.saga.timeout-scan-delay-ms`). `OrderSagaTimeoutService` thi hành deadline.
- `RESERVE_INVENTORY` quá hạn: chưa giữ gì, hủy đơn, saga `COMPENSATED`.
- `AUTHORIZE_PAYMENT` quá hạn: bù trừ bằng `CANCEL_PAYMENT`.
- Các bước còn lại quá hạn: phát lại command, tối đa `OrderSaga.MAX_RETRIES` lần. Hết lượt: saga `FAILED`, tăng metric `order.saga.failed`, cần người xử lý.
- Admin thử lại: `POST /api/v1/admin/orders/{orderId}/saga/retry`. Hệ thống phát lại command của bước đang kẹt.
- Thứ tự khóa: `Order → OrderSaga`.
