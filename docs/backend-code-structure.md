# Cấu trúc code backend

## Feature-first (Spring Modulith)

Áp dụng cho `catalog-service`, `order-service`, `payment-service`.

```text
com.fashionstore.<service>
  <feature>/
    controller/        REST controller
    dto/               request/response
    entity/            JPA entity, enum
    repository/        Spring Data repository
    service/           interface + service phối hợp
    service/impl/      implementation
    mapper/            MapStruct
    client/            Feign client, client bên ngoài
    event/             RabbitMQ listener, @Scheduled job
    exception/         ErrorCode của feature
    config/            cấu hình riêng của feature
  shared/              OPEN module: outbox, Rabbit config, security, OpenAPI, util
  <Service>Application.java
```

| service | feature |
|---|---|
| `catalog-service` | `product`, `inventory`, `media`, `review`, `wishlist` |
| `order-service` | `cart`, `checkout`, `order`, `saga`, `promotion`, `shipment`, `returns`, `dashboard` |
| `payment-service` | `payment`, `wallet` |

Quy tắc:

- Mỗi feature là một application module. `shared/package-info.java` khai báo `@ApplicationModule(type = ApplicationModule.Type.OPEN)`.
- `shared` không phụ thuộc feature nào.
- Feature chỉ dùng feature khác qua API được expose.
- Chỉ tạo layer mà feature cần.
- Listener và scheduler đặt trong `<feature>/event/`. Outbox đặt trong `shared/outbox/`.

## Kiểm tra ranh giới module

- `src/test/java/com/fashionstore/<service>/ModularityTest.java` verify cấu trúc module.
- `src/test/resources/modulith-known-violations.txt` liệt kê vi phạm đã có từ trước. Không thêm dòng để build pass. Sửa vi phạm thì xóa dòng tương ứng.

## Layer-first

`identity-service` và `notification-service` vẫn dùng layer phẳng:

```text
com.fashionstore.identity       client/ config/ controller/ dto/ entity/ exception/ mapper/ repository/ service/ service/impl/
com.fashionstore.notification   config/ controller/ dto/ event/ messaging/ model/ repository/ service/ service/impl/ websocket/
```

## Layering trong mọi service

`controller → service/impl → repository → entity`. Giữ cặp `service/` interface + `service/impl/`.

## Pattern dùng chung

| pattern | ở đâu |
|---|---|
| outbox | bảng `outbox_event`, `shared/outbox/OutboxEventRelay` gửi qua `ConfirmedRabbitSender` |
| idempotent consumer | bảng `processed_message`, `ProcessedMessageService.processOnce(messageId, consumerName, ...)` |
| khai báo exchange/queue | `RabbitTopology` (common-library), mỗi service có `RabbitMQConfig` riêng |
| gọi HTTP ra ngoài | gọi ngoài transaction, transaction DB ngắn chỉ để đọc/ghi |
| khóa | pessimistic lock theo thứ tự cố định, ví dụ order-service: `Order → OrderSaga → Shipment` / `Order → ReturnRequest` |
