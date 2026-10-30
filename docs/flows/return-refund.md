# Luồng đổi trả và hoàn tiền

Xử lý: `order-service`, feature `returns`. Hoàn tiền: `payment-service`, luôn ghi có vào ví.

## Loại yêu cầu (`ReturnRequestKind`)

| loại | ai tạo |
|---|---|
| `CUSTOMER_RETURN` | khách, trong 7 ngày sau khi giao |
| `CARRIER_RETURN` | admin, khi carrier hoàn hàng: `POST /api/v1/admin/orders/returns/carrier/{orderId}` |
| `VERIFIED_LOSS` | admin, khi hàng thất lạc đã xác minh: `POST /api/v1/admin/orders/returns/lost/{orderId}` |

Chỉ yêu cầu `CUSTOMER_RETURN` mới gửi thông báo trạng thái cho khách. Mọi loại hoàn tiền đều báo khách.

## Trả hàng do khách

| bước | ai | endpoint | trạng thái |
|---|---|---|---|
| upload ảnh bằng chứng | khách | `POST /api/v1/files/evidence/returns/presign` (catalog) | — |
| tạo yêu cầu | khách | `POST /api/v1/orders/{id}/return-request` | `REQUESTED` |
| duyệt: tạo đơn trả hàng GHN (lấy tại khách, giao về kho) | admin | `POST /api/v1/admin/orders/returns/{id}/approve` | `APPROVED` |
| GHN lấy hàng (mã `RET-<id>`) | webhook GHN | `POST /api/v1/shipping/ghn/webhook` | `IN_TRANSIT` |
| nhận hàng, QC | admin | `POST /api/v1/admin/orders/returns/{id}/receive` | `RECEIVED` |
| hoàn tiền | admin | `POST /api/v1/admin/orders/returns/{id}/refund` | chờ payment |
| ví ghi có | `payment.order-funds-refund.credited` | — | `REFUNDED` |

Nhánh khác:
- Từ chối: `POST .../{id}/reject` → `REJECTED`.
- Hủy: `POST .../{id}/cancel` → `CANCELLED`. Có đơn trả GHN thì hủy bên GHN trước.
- GHN lấy hàng thất bại: `POST .../{id}/recreate-pickup`. Chỉ tạo lại khi lượt trước thất bại và hàng vẫn ở chỗ khách.
- Đóng không hoàn tiền: `POST .../{id}/close-without-refund`.
- Phiếu `IN_TRANSIT` quá hạn mà kho chưa nhận: báo admin, không tự hủy.
- Phiếu đóng không hoàn tiền (hủy, GHN hủy, quá hạn lấy hàng): đơn quay lại luồng thường và chuyển `COMPLETED` ngay.

Quy tắc kỹ thuật:
- Duyệt gọi GHN ngoài transaction. GHN lỗi thì phiếu giữ `REQUESTED` để admin bấm lại.
- `client_order_code` duy nhất theo shop nên gọi lại không tạo đơn GHN trùng.
- Thứ tự khóa: `Order → ReturnRequest`.
- `PENDING`, `RETURN_APPROVED`, `LEGACY_REVIEW_REQUIRED` và các giá trị khác trong `ReturnRequestStatus` chỉ còn cho dữ liệu cũ.

## Hoàn tiền

1. order-service phát `payment.order-funds-refund.requested`.
2. payment-service ghi có vào ví, phát `payment.order-funds-refund.credited` hoặc `.rejected`.
3. Credited → phiếu `REFUNDED`, đơn chuyển `REFUNDED`. Trước đó đơn giữ `RETURNED`.
4. Reply rejected đến muộn không đảo ngược một khoản đã xác nhận ghi có.
