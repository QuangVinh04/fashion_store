# notification-service

Cổng 8090 · database `notification_database` (host 5439) · layer-first.

## Trách nhiệm

- Gửi email qua SMTP.
- Lưu thông báo in-app và đẩy tới browser qua WebSocket.

## API

| method | path |
|---|---|
| GET | `/api/v1/notifications` |
| PATCH | `/api/v1/notifications/{id}/read` |
| PATCH | `/api/v1/notifications/read-all` |

## Messaging

| nhận | từ | xử lý |
|---|---|---|
| `notification.email.requested` | order | `EmailNotificationListener` gửi email |
| `notification.in-app.requested` | order | `InAppNotificationListener` lưu và đẩy WebSocket |

Order-service phát email khi: đơn xác nhận, đang giao, đã giao, bị hủy; đổi trả được duyệt, bị từ chối, QC không đạt, bị hủy, đã hoàn tiền. Các bước trung gian của đổi trả (lấy hàng, đặt lại lượt lấy, nhận hàng đạt QC) chỉ báo in-app.

## Bảng chính

`in_app_notification`, `processed_message`.
