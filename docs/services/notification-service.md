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

Order-service phát email khi: đơn xác nhận, đang giao, đã giao, bị hủy, thay đổi trạng thái đổi trả.

## Bảng chính

`in_app_notification`, `processed_message`.
