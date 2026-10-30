# order-service

Cổng 8089 · database `order_database` (host 5437) · feature-first.
Gộp từ cart-service. Không tách lại.

## Feature

| feature | trách nhiệm |
|---|---|
| `cart` | giỏ hàng, snapshot tên/giá sản phẩm |
| `checkout` | phiên checkout: kiểm tra lại giỏ với catalog, chụp địa chỉ, tính phí GHN, áp mã giảm giá. Hết hạn bằng `CheckoutExpiryScheduler` |
| `order` | đặt đơn, trạng thái đơn, lịch sử, xác nhận đã nhận hàng, tự hoàn tất sau hạn đổi trả, thông báo |
| `saga` | saga checkout và saga hủy đơn, deadline từng bước, admin retry |
| `promotion` | mã giảm giá `PERCENT` / `FIXED`, phạm vi `ALL` / `PRODUCT` / `CATEGORY`, giữ chỗ lượt dùng |
| `shipment` | vận đơn GHN, webhook GHN, danh mục tỉnh/phường |
| `returns` | đổi trả do khách, hàng hoàn do carrier, thất lạc, đơn trả hàng GHN, hoàn tiền |
| `dashboard` | số liệu cho backoffice |

## Trạng thái

- Đơn (`OrderStatus`): `PENDING`, `CONFIRMED`, `PROCESSING`, `PACKED`, `SHIPPING`, `DELIVERED`, `COMPLETED`, `CANCELLING`, `CANCELLED`, `LOST`, `RETURNED`, `REFUNDED`.
- Checkout: `DRAFT`, `SUBMITTED`, `EXPIRED`, `COMPLETED`, `CANCELLED`.
- Saga: loại `CHECKOUT` / `CANCELLATION`; trạng thái `RUNNING`, `COMPENSATING`, `COMPLETED`, `COMPENSATED`, `FAILED`.
- Vận đơn: `PENDING`, `PICKED`, `SHIPPING`, `DELIVERED`, `RETURNING`, `LOST`, `RETURNED`, `CANCELLED`.

## API chính

| nhóm | path |
|---|---|
| giỏ hàng | `/api/v1/cart`, `/api/v1/cart/items/**` |
| checkout | `POST /api/v1/checkouts`, `GET/PUT /api/v1/checkouts/{id}`, `POST /api/v1/checkouts/{id}/cancel`, `GET /api/v1/checkouts/me` |
| đơn (khách) | `POST /api/v1/orders/{checkoutId}` (đặt đơn), `GET /api/v1/orders/**`, `POST /api/v1/orders/{id}/cancel`, `POST /api/v1/orders/{id}/confirm-receipt`, `/api/v1/orders/{id}/return-request/**` |
| đơn (admin) | `/api/v1/admin/orders/**`: danh sách, chi tiết, lịch sử, saga, hủy, retry saga/hủy, xác minh carrier |
| đổi trả (admin) | `/api/v1/admin/orders/returns/**` |
| vận chuyển | `POST/GET /api/v1/orders/{orderId}/shipment` (admin), `POST /api/v1/shipping/ghn/webhook`, `/api/v1/shipping/locations/**` |
| khuyến mãi | `/api/v1/promotions/validate`, `/api/v1/admin/promotions/**` |
| dashboard | `/api/v1/admin/dashboard` |
| nội bộ | `POST /internal/v1/orders/verify-purchase` |

## Messaging

- Phát command tới catalog (`inventory.*`) và payment (`payment.*`, `wallet.saga-compensation.requested`, `payment.order-funds-refund.requested`).
- Phát `order.confirmed`, `order.delivered`, `order.cancelled`, `notification.email.requested`, `notification.in-app.requested`.
- Nhận reply từ catalog và payment, và `order.confirmed` của chính mình.

Chi tiết: `docs/flows/`.

## HTTP ra ngoài

catalog-service, identity-service, GHN.

## Job định kỳ

| job | việc |
|---|---|
| `CheckoutExpiryScheduler` | hết hạn checkout |
| `OrderSagaTimeoutScanner` | thi hành deadline từng bước saga |
| `OrderAutoCompletionScheduler` | đơn `DELIVERED` quá 7 ngày (`RETURN_WINDOW`) và không có yêu cầu trả hàng mở thì chuyển `COMPLETED` |
| `OutboxEventRelay` | đẩy outbox lên RabbitMQ, dọn outbox hằng ngày lúc 02:00 |

## Bảng chính

`cart`, `cart_item`, `checkout`, `checkout_item`, `orders`, `order_item`, `order_status_history`, `order_saga`, `promotion`, `coupon_usage`, `shipment`, `return_request`, `return_shipment`, `outbox_event`, `processed_message`.
