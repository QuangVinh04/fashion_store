# Luồng hủy đơn

## Hủy đơn `PENDING`

Đơn còn nằm trong checkout saga. Hủy qua checkout saga (`docs/flows/checkout-saga.md`): chưa giữ kho thì đóng ngay; đang chờ thanh toán thì hủy payment rồi nhả kho.

## Hủy đơn đã xác nhận — cancellation saga

Điều phối: `order-service`, `saga/service/OrderCancellationSagaService`. Saga loại `CANCELLATION`.

Điều kiện:
- Đơn ở `CONFIRMED`, `PROCESSING` hoặc `PACKED`.
- Checkout saga đã `COMPLETED`.
- Vận đơn chưa bàn giao cho carrier.
- Gọi lại với cùng `operationId` trả về saga đã có (idempotent).

Bắt đầu: đơn chuyển `CANCELLING`.

| bước | việc | command / reply |
|---|---|---|
| `STOP_SHIPMENT` | hủy vận đơn GHN. Bỏ qua nếu chưa có vận đơn hoặc vận đơn đã `CANCELLED` | gọi GHN |
| `CANCEL_ORDER_INVENTORY` | trả hàng về kho | `inventory.order.cancellation.requested` → `inventory.order.cancelled` / `.rejected` |
| `REFUND_ORDER_FUNDS` | hoàn tiền vào ví. Đơn COD chưa thu tiền: bỏ qua | `payment.order-funds-refund.requested` → `.credited` / `.rejected` |
| `DONE` | đơn `CANCELLED` | — |

Ngoại lệ:
- Carrier lấy hàng trước khi dừng được vận đơn: chưa nhả kho, chưa hoàn tiền → từ chối hủy, đơn tiếp tục giao.
- Hủy đã xong nhưng hàng vẫn đi: chỉ ghi nhận để vận hành xử lý.

Thứ tự khóa: `Order → OrderSaga → Shipment`.

## API

| ai | endpoint |
|---|---|
| khách | `POST /api/v1/orders/{id}/cancel` |
| admin | `POST /api/v1/admin/orders/{orderId}/cancel` |
| admin | `POST /api/v1/admin/orders/{orderId}/cancellation/retry` |
| admin | `POST /api/v1/admin/orders/{orderId}/cancellations/{cancellationId}/verify-carrier` |
