# Luồng vận chuyển GHN

Xử lý: `order-service`, feature `shipment`. Provider: `GHN` (`GHTK` có trong enum, chưa làm).

## Tạo vận đơn

1. Đơn `PROCESSING` (đang đóng gói). Admin đóng gói xong rồi gọi `POST /api/v1/orders/{orderId}/shipment`, body tùy chọn `weightGram` (cân nặng thực tế, > 0). Bỏ trống thì tính từ catalog.
2. order-service gọi GHN ngoài transaction, rồi ghi kết quả trong một transaction ngắn. Thành công thì đơn chuyển `PACKED`.
3. Chỉ tạo được khi đơn `PROCESSING`. Đơn `PACKED` chưa có vận đơn (dữ liệu cũ) vẫn tạo được. Đơn `CONFIRMED` chưa được shop xác nhận nên bị từ chối.
4. Lần đầu gửi `orderCode` = mã đơn. Vận đơn tạo lại sau khi GHN hủy gửi `orderCode-<mã cũ>`.
5. GHN lỗi: đơn giữ `PROCESSING`, admin bấm lại. Cùng `client_order_code` nên không tạo trùng.

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
- Admin không chuyển tay sang `SHIPPING` hay `DELIVERED`. Chỉ webhook GHN làm việc đó.

## Đồng bộ khi mất webhook

`POST /api/v1/admin/orders/{orderId}/shipment/sync` (admin). order-service lấy trạng thái thật từ GHN rồi chạy qua đúng đường xử lý webhook ở trên. GHN detail không có giờ sự kiện nên dùng giờ thu COD của GHN, nếu không có thì giờ hiện tại. GHN không trả lời được: 502.

## Sau khi giao

- Khách xác nhận đã nhận hàng: `POST /api/v1/orders/{id}/confirm-receipt` → `COMPLETED`.
- Không xác nhận: `OrderAutoCompletionScheduler` chuyển `COMPLETED` sau 7 ngày (`RETURN_WINDOW`) nếu không có yêu cầu trả hàng mở.
- Trong 7 ngày khách có thể yêu cầu trả hàng: `docs/flows/return-refund.md`.

## Danh mục địa chỉ

`GET /api/v1/shipping/locations/provinces`, `GET /api/v1/shipping/locations/wards` (từ GHN).
