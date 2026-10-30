# Luồng vận chuyển GHN

Xử lý: `order-service`, feature `shipment`. Provider: `GHN` (`GHTK` có trong enum, chưa làm).

## Tạo vận đơn

1. Admin gọi `POST /api/v1/orders/{orderId}/shipment`.
2. order-service gọi GHN ngoài transaction, rồi ghi kết quả trong một transaction ngắn.
3. Lần đầu gửi `orderCode` = mã đơn. Vận đơn tạo lại sau khi GHN hủy gửi `orderCode-<mã cũ>`.

## Webhook GHN

`POST /api/v1/shipping/ghn/webhook`. Ánh xạ trạng thái GHN:

| GHN | `ShipmentStatus` |
|---|---|
| `ready_to_pick`, `picking`, `money_collect_picking` | `PENDING` |
| `picked` | `PICKED` |
| `storing`, `delivering`, `transporting` | `SHIPPING` |
| `delivered` | `DELIVERED` |
| `cancel` | `CANCELLED` |
| `return` | `RETURNING` |
| `returned` | `RETURNED` |

Tác động lên đơn:
- Carrier đã lấy hàng (`picked`) → đơn `SHIPPING`.
- `delivered` → đơn `DELIVERED`, phát `order.delivered` (kèm bằng chứng thu COD), gửi email.
- GHN hủy trước khi lấy hàng → hàng còn ở kho, đơn chờ admin tạo vận đơn mới.
- Trạng thái cuối không chuyển tiếp được nữa.

## Sau khi giao

- Khách xác nhận đã nhận hàng: `POST /api/v1/orders/{id}/confirm-receipt` → `COMPLETED`.
- Không xác nhận: `OrderAutoCompletionScheduler` chuyển `COMPLETED` sau 7 ngày (`RETURN_WINDOW`) nếu không có yêu cầu trả hàng mở.
- Trong 7 ngày khách có thể yêu cầu trả hàng: `docs/flows/return-refund.md`.

## Danh mục địa chỉ

`GET /api/v1/shipping/locations/provinces`, `GET /api/v1/shipping/locations/wards` (từ GHN).
