# Kiến trúc tổng quan

Tài liệu mô tả hệ thống theo code hiện tại. Chi tiết từng service: `docs/services/`. Luồng nghiệp vụ: `docs/flows/`.

## Thành phần

```text
Browser
  ├─ storefront (Vite, :3000) ──┐
  └─ backoffice (Next.js, :5173)┤  (UI dev server được BFF proxy)
                                ▼
  storefront-bff :8083 / backoffice-bff :8084   OAuth2 client, session trong Redis, TokenRelay, CSRF
                                ▼
  api-gateway :8080                              resource server, validate JWT Keycloak
                                ▼
  identity :8082 · payment :8085 · catalog :8087 · order :8089 · notification :8090
       │                │              │              │                 │
     Postgres riêng mỗi service (database-per-service)
       └──────── RabbitMQ (event/command qua outbox) ────────┘

  Keycloak :8180   realm fashion-store, nơi duy nhất cấp token
  MinIO            lưu file media (catalog-service)
  GHN              vận chuyển (order-service)
  VNPay, PayOS     cổng thanh toán online (payment-service)
  SMTP             email (notification-service)
```

| service | trách nhiệm |
|---|---|
| `identity-service` | hồ sơ user (JIT-provision từ Keycloak `sub`), sổ địa chỉ, quản trị user qua Keycloak Admin API |
| `catalog-service` | sản phẩm, biến thể, thuộc tính, size chart, tồn kho, media (MinIO), review, wishlist |
| `order-service` | giỏ hàng, checkout, đơn hàng, saga checkout và saga hủy đơn, khuyến mãi, vận chuyển GHN, đổi trả, dashboard admin |
| `payment-service` | thanh toán COD / online (VNPay, PayOS) / ví, ví nội bộ và sổ cái, hoàn tiền vào ví |
| `notification-service` | email, thông báo in-app (lưu DB, đẩy qua WebSocket) |

## Xác thực

- Browser không giữ token. BFF giữ session (Redis), gắn access token khi chuyển request xuống gateway (`TokenRelay`).
- Gateway và từng service đều validate JWT (`aud=fashion-api`).
- Gọi nội bộ `/internal/**` dùng token service-account (`client_credentials`) có client role `internal-caller`.

## Giao tiếp giữa service

**Bất đồng bộ — RabbitMQ.** Service ghi event vào bảng `outbox_event` cùng transaction nghiệp vụ. `OutboxEventRelay` đẩy lên RabbitMQ có publisher-confirm. Consumer chống xử lý lặp bằng `processed_message`. Tên event nằm trong `com.fashionstore.contracts.common.EventTypes`.

| event / command | phát | nhận |
|---|---|---|
| `inventory.reservation.requested`, `inventory.confirmation.requested`, `inventory.release.requested` | order | catalog |
| `inventory.reserved`, `inventory.rejected`, `inventory.confirmed`, `inventory.confirmation.rejected`, `inventory.released` | catalog | order |
| `inventory.order.cancellation.requested` | order | catalog |
| `inventory.order.cancelled`, `inventory.order.cancellation.rejected` | catalog | order |
| `payment.requested`, `payment.cancellation.requested`, `wallet.saga-compensation.requested`, `payment.order-funds-refund.requested` | order | payment |
| `payment.initiated`, `payment.completed`, `payment.failed`, `payment.cancelled`, `payment.cancellation.rejected` | payment | order |
| `wallet.saga-compensation.completed` / `.rejected`, `payment.order-funds-refund.credited` / `.rejected` | payment | order |
| `order.confirmed` | order | order (chuyển đơn sang `PROCESSING`) |
| `order.delivered` | order | payment (ghi nhận thu tiền COD) |
| `order.cancelled` | order | — (chưa có consumer) |
| `notification.email.requested`, `notification.in-app.requested` | order | notification |
| `profile.avatar.changed` | identity | catalog |

`inventory.restock.requested` có trong `EventTypes` nhưng hiện không service nào phát hoặc nhận.

**Đồng bộ — HTTP (Feign).**

| gọi từ | tới | mục đích |
|---|---|---|
| order | catalog | kiểm tra biến thể, giá, tồn kho khi checkout; kiểm tra media bằng chứng đổi trả |
| order | identity | đọc địa chỉ giao hàng (`/internal/v1/users/...`) |
| order | GHN | phí ship, tạo/hủy vận đơn, đơn trả hàng, danh mục tỉnh/phường |
| catalog | identity | thông tin user cho review, avatar |
| catalog | order | `/internal/v1/orders/verify-purchase` — chỉ người đã mua mới review |
| identity | catalog | xác thực media avatar |
| identity | Keycloak Admin API | quản trị user |
| payment | VNPay, PayOS | tạo link, tra cứu giao dịch |

## Luồng chính

| luồng | tài liệu |
|---|---|
| đặt hàng (checkout saga) | `docs/flows/checkout-saga.md` |
| thanh toán COD / online / ví | `docs/flows/payment.md` |
| hủy đơn sau xác nhận | `docs/flows/order-cancellation.md` |
| vận chuyển GHN | `docs/flows/shipment-ghn.md` |
| đổi trả và hoàn tiền | `docs/flows/return-refund.md` |
