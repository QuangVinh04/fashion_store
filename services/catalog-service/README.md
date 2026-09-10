# Catalog Service

Gộp từ ba service cũ: `product-service`, `inventory-service` và `file-service`. Sở hữu
sản phẩm, danh mục, thương hiệu, thuộc tính, biến thể, tồn kho và thư viện media —
tất cả trong một database (`catalog_database`).

Lý do gộp: `Inventory` khoá theo `variantId`, vốn thuộc aggregate `ProductVariant`.
Tách hai thứ đó thành hai service buộc mọi bước saga phải đi một vòng HTTP để đọc lại
chính dữ liệu nằm ngay cạnh nó.

Port `8087` · DB `catalog_database` (host `5435`) · exchange `fashion.events`.

## Run locally

```powershell
docker compose up -d catalog-postgres rabbitmq minio
mvn -f services/catalog-service/pom.xml spring-boot:run
```

```bash
export JAVA_HOME="C:/Program Files/Java/jdk-21"
./mvnw -o -pl services/catalog-service clean test
```

Mọi traffic client đi qua gateway `http://localhost:8080`, không gọi thẳng `8087`.

## Chức năng

### 1. Sản phẩm & biến thể (`ProductService`)
- Đọc công khai: danh sách phân trang, chi tiết theo `id` / `slug`, biến thể của một
  sản phẩm, size chart của sản phẩm, tìm kiếm theo keyword, lọc theo category (id hoặc
  slug) và brand slug, `advance-search` bằng `ProductSpecificationsBuilder`
  (`SpecSearchCriteria` + `SearchOperation`).
- Snapshot biến thể (`/variants/batch`) cho order/cart lấy giá + thông tin tối thiểu
  mà không cần đọc DB của catalog.
- Backoffice: CRUD sản phẩm, cập nhật hàng loạt biến thể
  (`ProductVariantBatchRequest`), gán thuộc tính (`AssignProductAttributeRequest`),
  vòng đời `publish` / `unpublish` / `archive` (`ProductStatus`), xoá.
- Tạo/xoá biến thể kéo theo `InventoryService.ensureStock` / `deleteStock` nên mỗi
  biến thể luôn có đúng một dòng tồn kho.

### 2. Phân loại & thuộc tính
- `Category` — cây danh mục (`/api/v1/category`, `/all` trả tree), sản phẩm theo danh mục.
- `Brand` — đọc công khai bản `active`, admin CRUD, xoá là soft-delete (`active=false`).
- `ProductAttribute` + `ProductAttributeOption` + `ProductAttributeValue` — thuộc tính
  động theo `AttributeType`, admin quản lý cả option.
- `ColorOption` / `SizeOption` — bảng tra dùng chung cho biến thể.
- `SizeChart` + `SizeChartRow` — bảng size theo `Gender` × `ProductType`, admin CRUD
  từng dòng.

### 3. Tồn kho (`InventoryService`)
- `Inventory` giữ `quantity` + `reservedQuantity` theo `variantId`; `available` là phần
  chênh lệch. Mọi thao tác ghi đều `findByVariantIdWithLock` (pessimistic lock) và khoá
  theo thứ tự đã sắp xếp để tránh deadlock.
- HTTP: xem tồn kho một/nhiều biến thể, `PUT` chỉnh số lượng (admin), `POST /check`
  cho cart (chỉ cần đăng nhập), `reserve` / `release` / `confirm`.
- Saga qua RabbitMQ: `reserveSaga` kiểm tra đủ hàng trước rồi mới mutate (2 phase),
  `confirmSaga` trừ hẳn `quantity`, `releaseSaga` hoàn `reservedQuantity`.
  `InventoryReservation` (+ `InventoryReservationItem`) khoá `orderId` unique nên
  command lặp lại là idempotent.

### 4. Media (`MediaFileService`) — upload bằng presigned URL (MinIO)

Bytes không đi qua service. Ba bước:

1. `POST /api/v1/files/presign` — validate content type (allow-list) + kích thước khai báo,
   sinh `storageKey = yyyy/MM/<uuid>.<ext>`, INSERT row `status = PENDING`, trả `uploadUrl`
   đã ký (`PUT`, hết hạn theo `app.minio.presign-expiry-seconds`).
2. Browser `PUT <uploadUrl>` thẳng lên MinIO, kèm đúng header `Content-Type` đã ký.
3. `POST /api/v1/files/{id}/complete` — `statObject` xác minh object có thật, lấy size/etag
   thật từ storage (không tin số client khai), `width`/`height` do FE gửi kèm, chuyển
   `status = ACTIVE`. Không có bước này thì DB sẽ có row trỏ tới object không tồn tại.

- `GET /api/v1/files/{id}/content` giữ nguyên đường dẫn nhưng trả `302` sang presigned GET URL.
- Tìm kiếm bằng `MediaFileSpecifications` (owner, folder, tag, type, status), sửa
  metadata (`displayName`, `altText`, `folder`, tag), trash → restore → xoá vĩnh viễn
  (`MediaStatus`), phân quyền xem bằng `MediaVisibility`.
- Chỉ media `ACTIVE` mới gán được vào sản phẩm (`existsByIdAndStatus`) — row `PENDING`
  chưa upload xong không lọt vào `product_image` hay thumbnail của variant.

## HTTP API

| Nhóm | Public (GET) | Admin |
|---|---|---|
| Product | `/api/v1/products/**` (alias `/api/v1/product/**`): `/`, `/{id}`, `/slug/{slug}`, `/{id}/variants`, `/{id}/size-chart`, `/all`, `/search`, `/category/{id}`, `/category-slug/{slug}`, `/brand-slug/{slug}`, `/advance-search`, `/variants/batch` | `/admin/products`, `/{id}`, `/{id}/variants`, `/{id}/attributes`, `/{id}/publish|unpublish|archive` |
| Category | `/api/v1/category`, `/{id}`, `/{id}/products`, `/all` | cùng prefix (POST/PUT/DELETE) |
| Brand | `/api/v1/brands`, `/{id}` | `/admin/brands/**` |
| Attribute | `/api/v1/product-attributes` | `/admin/product-attributes/**` (+ `/options/**`) |
| Color / Size | `/api/v1/color-options`, `/api/v1/size-options` (+ `/active`) | `/admin/color-options/**`, `/admin/size-options/**` |
| Size chart | `/api/v1/size-charts`, `/{id}` | `/admin/size-charts/**` (+ `/rows/**`) |
| Inventory | — | `/api/v1/inventory/variants/{variantId}`, `/variants/batch`, `PUT /variants/{variantId}`, `/check` (authenticated), `/reserve`, `/release`, `/confirm/{orderId}` |
| Media | `GET /api/v1/files/{id}/content` (302 → presigned) | `POST /api/v1/files/presign`, `POST /{id}/complete`, `/api/v1/files/**` (authenticated) |

Response bọc trong `ApiResponse` / `PageResponse` của `common-library`; lỗi ném
`AppException` với `ProductErrorCode` / `InventoryErrorCode` / `FileErrorCode`.

## Package layout

Chia theo tầng, không chia theo domain con. Sản phẩm, tồn kho và media dùng chung một
`controller` / `service` / `repository` / `model`, nên đọc một tầng là thấy hết những gì
service này có ở tầng đó.

```text
com.fashionstore.catalog
|-- config            # SecurityConfig, CustomJwtDecoder, RabbitMQConfig/Names, MinioConfig/Properties
|-- controller        # sản phẩm, danh mục, brand, thuộc tính, color/size, size chart, tồn kho, media
|-- dto
|-- exception         # ProductErrorCode, InventoryErrorCode, FileErrorCode
|-- mapper            # MapStruct (componentModel=spring)
|-- messaging         # InventoryCommandListener
|-- model             # entity của cả ba domain
|   |-- attribute     # ProductAttribute / Option / Value
|   |-- enumeration   # ProductStatus, InventoryStatus, MediaStatus, Gender, ...
|   `-- option        # ColorOption, SizeOption
|-- outbox            # OutboxEvent + Repository + OutboxService + OutboxPublisher
|-- repository        # repository + specification (ProductSpecification, MediaFileSpecifications)
|-- service
|   `-- impl
|-- util
`-- CatalogServiceApplication
```

Deviation còn lại: `BrandService` và `SizeChartService` là class cụ thể, chưa tách cặp
interface + `impl` như các service khác. `outbox/**` chưa flatten theo tầng.

## Flyway

Ba lịch sử migration được nhập vào một, đánh số theo dải để còn chỗ mở rộng:

| Dải | Nguồn | Nội dung |
|---|---|---|
| `V1`–`V10` | product-service | schema sản phẩm, outbox (`V2`/`V3`), domain model, vòng đời + ảnh, color/size |
| `V20`–`V23` | inventory-service | tồn kho, `processed_message`, reservation, align cột |
| `V40` | file-service | media library |
| `V41` | catalog | presigned upload: `checksum_sha256` nullable, thêm `etag` |

`V22` không còn tạo bảng `outbox_event` như bản gốc — bảng đó đã do `V2`/`V3` tạo trong
cùng database, nên `V22` chỉ bù hai cột `created_by` / `updated_by`.

## Security

Ba service cũ có ba luật `anyRequest()` khác nhau, nên thay vì trộn thành một chain, mỗi
domain giữ một `SecurityFilterChain` riêng khoanh vùng bằng `securityMatcher`:

1. `mediaFilterChain` — `/api/v1/files/**`: `GET /{id}/content` public, còn lại authenticated.
   CORS cho bước PUT nằm ở MinIO (`MINIO_API_CORS_ALLOW_ORIGIN`), không phải ở chain này —
   browser gọi thẳng `:9000`.
2. `inventoryFilterChain` — `/api/v1/inventory/**`, `/internal/v1/**`: `POST /check`
   authenticated, `/internal/v1/**` cần authority `internal`, còn lại `ROLE_ADMIN`.
3. `catalogFilterChain` — bao trùm phần còn lại: actuator health + GET catalog public,
   `anyRequest()` `ROLE_ADMIN`.

JWT decode bằng `CustomJwtDecoder` (jwk-set từ identity-service), `JwtAuthenticationConverter`
bỏ tiền tố `ROLE_` mặc định. CORS đăng ký theo path: `/api/v1/files/**` lấy bản của
file-service (expose `Content-Disposition`), `/**` lấy bản của inventory-service.

## Messaging

Exchange direct `fashion.events`, routing key = đúng giá trị `EventTypes`.

Consume (queue → command):

| Queue | EventType | Xử lý |
|---|---|---|
| `inventory.reservation-requested` | `INVENTORY_RESERVATION_REQUESTED` | `reserveSaga` |
| `inventory.confirmation-requested` | `INVENTORY_CONFIRMATION_REQUESTED` | `confirmSaga` |
| `inventory.release-requested` | `INVENTORY_RELEASE_REQUESTED` | `releaseSaga` |

Idempotent qua `ProcessedMessageService` (header `outboxEventId` làm messageId).

Publish qua outbox (`OutboxService.saveMessage` → `OutboxPublisher`, relay theo
`app.outbox.relay-delay-ms`, có dọn event cũ): `INVENTORY_RESERVED`,
`INVENTORY_REJECTED`, `INVENTORY_CONFIRMED`, `INVENTORY_RELEASED`.

## Cấu hình (env)

| Biến | Mặc định |
|---|---|
| `SERVER_PORT` | `8087` |
| `CATALOG_POSTGRES_HOST/PORT/DB/USER/PASSWORD` | `localhost` / `5435` / `catalog_database` / `root` / `root` |
| `RABBITMQ_HOST/PORT/USERNAME/PASSWORD` | `localhost` / `5672` / `guest` / `guest` |
| `JWT_ISSUER`, `JWT_JWK_SET_URI` | identity-service `:8082` |
| `FILE_PUBLIC_BASE_URL` | `http://localhost:8087` |
| `MINIO_ENDPOINT` / `MINIO_PUBLIC_ENDPOINT` | `http://localhost:9000` / `http://localhost:9000` |
| `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET` | `minioadmin` / `minioadmin` / `fashion-media` |
| `MINIO_PRESIGN_EXPIRY_SECONDS`, `MINIO_MAX_UPLOAD_BYTES` | `900`, `20971520` |
| `OUTBOX_RELAY_DELAY_MS` | `3000` |
