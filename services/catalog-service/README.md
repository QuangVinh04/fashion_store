# Catalog Service

Gộp từ ba service cũ: `product-service`, `inventory-service` và `file-service`.
Sở hữu sản phẩm, danh mục, biến thể, tồn kho và thư viện media — tất cả trong một
database (`catalog_database`).

Lý do gộp: `Inventory` khoá theo `variantId`, vốn thuộc aggregate `ProductVariant`.
Tách hai thứ đó thành hai service buộc mọi bước saga phải đi một vòng HTTP để đọc
lại chính dữ liệu nằm ngay cạnh nó.

## Run locally

```powershell
docker compose up -d catalog-postgres rabbitmq
mvn -f services/catalog-service/pom.xml spring-boot:run
```

Cổng HTTP mặc định: `8087`.

## Package layout

Chia theo tầng, không chia theo domain con. Sản phẩm, tồn kho và media dùng chung
một `controller` / `service` / `repository` / `model`, nên đọc một tầng là thấy hết
những gì service này có ở tầng đó.

```text
com.fashionstore.catalog
|-- config            # security, RabbitMQ, FileStorageProperties
|-- controller        # sản phẩm, danh mục, thuộc tính, tồn kho, media
|-- dto
|-- event             # listener + outbox recorder/relay
|-- exception         # ProductErrorCode, InventoryErrorCode, FileErrorCode
|-- mapper
|-- model             # entity của cả ba domain
|   |-- attribute
|   |-- enumeration
|   `-- option
|-- repository        # repository + specification
|-- service
|   `-- impl
|-- util
`-- CatalogServiceApplication
```

Bảng `outbox_event` được tách theo tầng như mọi bảng khác: entity `OutboxEvent` ở
`model`, `OutboxEventRepository` ở `repository`, còn `OutboxEventRecorder` /
`OutboxEventRelay` ở `event` cùng với listener.

## Flyway

Ba lịch sử migration được nhập vào một, đánh số theo dải để còn chỗ mở rộng:

| Dải | Nguồn |
|---|---|
| `V1`–`V10` | product-service |
| `V20`–`V22` | inventory-service |
| `V40` | file-service |

`V22` không còn tạo bảng `outbox_event` như bản gốc — bảng đó đã do `V2`/`V3` tạo
trong cùng database, nên `V22` chỉ bù hai cột `created_by` / `updated_by`.

## Security

Ba service cũ có ba luật `anyRequest()` khác nhau, nên thay vì trộn thành một
chain, mỗi domain giữ một `SecurityFilterChain` riêng khoanh vùng bằng
`securityMatcher`. Thứ tự: media (`/api/v1/files/**`) → inventory
(`/api/v1/inventory/**`, `/internal/v1/**`) → catalog (bao trùm phần còn lại).

## Messaging

Publish qua outbox: kết quả giữ / từ chối tồn kho.

Chưa có consumer cho `RESERVE_INVENTORY` / `CONFIRM_INVENTORY` /
`RELEASE_INVENTORY`. `InventoryReservationService` chưa từng tồn tại; saga của
order-service phát command vào queue và hiện không ai nhận. Đây là việc còn lại
của P5, xem `docs/refactor-plan.md`.
