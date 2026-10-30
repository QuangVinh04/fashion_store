# Kiến trúc backend

## Ranh giới service

- **Database-per-service.** Mỗi service sở hữu schema và thư mục `src/main/resources/db/migration` riêng. Không đọc/ghi bảng của service khác. Không tạo FK giữa các service.
- **Giao tiếp giữa service** chỉ qua RabbitMQ event/command (`com.fashionstore.contracts.*`) hoặc HTTP client đi qua gateway.
- **`packages/api-contracts` chỉ chứa wire DTO** cho message giữa các service. Entity, repository, business service, mapper, controller luôn nằm trong service sở hữu.
- **`packages/common-library` chỉ chứa plumbing ở mức framework** (bảng bên dưới). Không có domain logic.
- Service đã gộp, coi mỗi cái là một deployable, không tách lại:
  - `catalog-service` ⊃ product + inventory + media (file)
  - `order-service` ⊃ cart

## Shared code — dùng lại, không viết lại

`com.fashionstore.common.*` (common-library):

| cần | dùng |
|---|---|
| REST envelope / paging | `dto.ApiResponse`, `dto.PageResponse` |
| lỗi | `exception.AppException`, `BaseErrorCode`, `ErrorCode`, `GlobalExceptionHandler` |
| JPA base class | `persistence.BaseEntity`, `persistence.AuditedEntity` |
| security | `security.CurrentUserProvider`, `ApiAuthenticationEntryPoint`, `ApiAccessDeniedHandler`, `KeycloakJwtAuthoritiesConverter` (bean `JwtAuthenticationConverter` auto-configured — service chỉ cần `.oauth2ResourceServer(rs -> rs.jwt(withDefaults()))`) |
| messaging | `messaging.RabbitTopology` (exchange/DLX/unrouted + quorum queue factory — mọi `RabbitMQConfig` khai báo qua đây), `messaging.outbox.ConfirmedRabbitSender` (gửi có publisher-confirm cho outbox relay), `messaging.outbox.OutboxEventStatus`, `messaging.processed.@EnableProcessedMessages` + `ProcessedMessageService` |
| khác | `util.SlugUtils`, `web.CorrelationIdFilter`, `payment.PaymentMethod/PaymentProvider` |

`com.fashionstore.contracts.*` (api-contracts): `common.EventEnvelope`, `common.EventTypes`, cùng các command/event `inventory.*`, `order.*`, `payment.*`, `notification.*`. Event mới thêm **ở đây** và tham chiếu `EventTypes` — không hardcode chuỗi routing key ở hai service.

## Package convention trong service

Feature-first (Spring Modulith), layer phẳng trong mỗi feature. Chi tiết: `docs/backend-code-structure.md`.

```text
com.fashionstore.<service>
  <feature>/   controller/  dto/  entity/  repository/  service/ + service/impl/
               mapper/  client/  event/  exception/  config/
  shared/      OPEN module — outbox, Rabbit config, security, OpenAPI, util; không phụ thuộc feature nào
  <Service>Application.java
```

- **Đã migrate:** `catalog-service` (mẫu tham chiếu — `product`, `inventory`, `media`, `review`, `wishlist`), `order-service` (`cart`, `checkout`, `order`, `saga`, `returns`, `shipment`, `promotion`, `dashboard`), `payment-service` (`payment`, `wallet`); mỗi service có `shared`. identity/notification vẫn layer-first (`controller/ dto/ entity/ …` phẳng).
- Trong một feature, mỗi layer một package; chỉ tạo layer feature cần. Listener/scheduler đặt trong `<feature>/event/`; outbox nằm trong `shared/outbox/`.
- Feature chỉ dùng feature khác qua API được expose. `ModularityTest` + `src/test/resources/modulith-known-violations.txt` đóng băng các vi phạm hiện có — không thêm dòng vào file này để build pass; xóa dòng khi đã sửa vi phạm.
- Giữ cặp `service/` interface + `service/impl/`, không thêm gì ngoài cặp này. Tuân thủ layering `controller → service/impl → repository → model`.

## Ràng buộc

Không làm nếu người dùng không yêu cầu rõ:

- ❌ Chuyển entity, repository, business service vào `packages/**`.
- ❌ Đưa thứ khác ngoài wire DTO vào `api-contracts`; đưa domain logic vào `common-library`.
- ❌ Tạo lại `product-service`, `inventory-service`, `file-service`, `cart-service` — đã gộp và xóa.

## Trap

- `packages/api-contracts` build ra artifactId **`event-contracts`** — tên thư mục và artifact khác nhau.
- RabbitMQ payload không mang FQCN (`Jackson2JsonMessageConverter`, kiểu suy ra từ signature của listener), nên di chuyển class event là an toàn. Đổi tên giá trị trong `EventTypes` là phá wire của mọi consumer.
