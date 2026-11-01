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
| `promotion` | mã giảm giá `PERCENT` / `FIXED`, hai loại (`target`): `PRODUCT` giảm tiền hàng (phạm vi `ALL` / `PRODUCT` / `CATEGORY`) và `SHIPPING` giảm phí ship (chỉ phạm vi `ALL`); giữ chỗ lượt dùng. Mỗi đơn tối đa một mã mỗi loại |
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
| khuyến mãi | `GET /api/v1/promotions/validate` (mã sản phẩm, tính trên giỏ), `GET /api/v1/promotions/available?target=` (mã công khai kèm `eligible`), `/api/v1/admin/promotions/**` |
| dashboard | `/api/v1/admin/dashboard` |
| nội bộ | `POST /internal/v1/orders/verify-purchase` |

## Messaging

- Phát command tới catalog (`inventory.*`) và payment (`payment.*`, `wallet.saga-compensation.requested`, `payment.order-funds-refund.requested`).
- Phát `order.confirmed`, `order.delivered`, `order.cancelled`, `notification.email.requested`, `notification.in-app.requested`.
- Nhận reply từ catalog và payment.

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

## Tiền của checkout và đơn

`totalAmount = subtotalAmount − discountAmount + shippingFee − shippingDiscountAmount`.

- `shippingFee` luôn là phí thật của GHN (shipment dùng đúng số này). Không còn miễn phí ship tự động theo giá trị đơn; chỉ mã `SHIPPING` giảm được phí, và tiền giảm ≤ `shippingFee`.
- Checkout nhận `couponCode` (mã `PRODUCT`) và `shippingCouponCode` (mã `SHIPPING`); chuỗi rỗng là gỡ mã. Gửi mã sai loại → `4038`.
- Khi đặt đơn, hai khoản giảm được tính lại dưới khóa của promotion và phải bằng bản chụp của checkout, nếu lệch thì `4037` (409) và đơn không được tạo. `coupon_usage` có tối đa hai dòng mỗi đơn; confirm/release xử lý cả hai.
- Mã lỗi khuyến mãi: `4010` không tồn tại · `4011` hết hạn/chưa hiệu lực · `4012` hết lượt · `4013` chưa đủ đơn tối thiểu · `4014` ngoài phạm vi · `4015` trùng code · `4016` không được sửa mã/loại sau khi có lượt dùng · `4037` khuyến mãi đổi sau checkout · `4038` sai loại mã.
