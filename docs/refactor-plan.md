# Kế hoạch tối ưu kiến trúc service

Mục tiêu: giảm chi phí vận hành và độ phức tạp phân tán xuống mức hợp lý cho
một người phát triển, **không** đánh mất các pattern cần thể hiện (gateway,
database-per-service, gọi đồng bộ, event bất đồng bộ, saga + outbox).

Đích đến: **5 service + gateway**, mỗi service vẫn giữ database riêng của mình.

| Hiện tại (8 service) | Sau khi gộp (5 service) |
|---|---|
| identity | **identity-service** — auth, user, profile |
| product + inventory + file | **catalog-service** |
| cart + order | **order-service** — cart, checkout, order, saga |
| payment | **payment-service** — giữ nguyên |
| notification | **notification-service** — bỏ DB, dùng Redis |

Thứ tự các mốc được sắp theo nguyên tắc: **việc rẻ và đảo ngược được làm
trước, việc dịch chuyển code làm sau**. Mỗi mốc đều để lại repo ở trạng thái
build xanh và chạy được.

---

## P0 — Gỡ chặn — ĐÃ XONG

`./mvnw -B verify` xanh toàn reactor, 81 test pass, 0 fail.

- [x] `product-service/.../ProductServiceImpl.java`
  - [x] Thêm record `VariantOptionsValidation` và `ProductValidation`
  - [x] `validateProduct()` trả về `ProductValidation` thay vì `void`
  - [x] `validateVariantOptions(requests, product)` trả về kết quả tra cứu option
  - [x] `validateAttributes(requests)` trả về `Map<String, ProductAttribute>`
  - [x] Khôi phục `validateBrand`, `validateRequiredCategoryIds`, `resolveSizeChartId`,
        `assignCategories` (bị xoá ở commit 80eccc0); `assignCategories` viết lại theo
        kiểu aggregate như `assignImages`, không dùng `productCategoryRepository` nữa
  - [x] Check trùng SKU đổi từ `existsBySkuAndActiveIsTrue` sang `findBySku`:
        `uk_product_variant_sku` áp cho mọi dòng nên chỉ xét variant active sẽ lọt
        validate rồi vỡ ở tầng DB
  - [x] Bỏ `request.getImageUrl()` — `ProductVariantRequest` không có trường này
- [x] `inventory-service`
  - [x] Chuẩn hoá package `com.fashionstore.product.*` → `com.fashionstore.clothes_retail_api.*`
        (10 file). Quan trọng: `InventoryServiceApplication` khai báo `com.fashionstore.product`
        nên component scan không quét được chính service của nó
  - [x] Thêm `InventoryService.upsertStock()` / `deleteStock()`
- [x] `./mvnw -B verify` xanh trên toàn reactor
- [ ] Bỏ `[skip ci]` khỏi quy trình commit thường ngày

### Nợ kỹ thuật P0 để lại (làm ở P5)

Đã gỡ khỏi build để CI xanh, khôi phục bằng `git show HEAD:<path>`:

- `inventory-service/.../event/InventoryReservationEventListener.java`
- `inventory-service/src/test/.../InventoryReservationServiceTest.java`

Lý do: `InventoryReservationService` **chưa từng tồn tại trong bất kỳ commit nào**.
File test có sẵn mô tả đặc tả cần có (gộp item trùng variant, all-or-nothing khi
thiếu hàng, publish `INVENTORY_RESERVED`/`REJECTED`/`CONFIRMED`/`RELEASED`), nhưng nó
tham chiếu tới một cấu trúc entity khác hẳn cái đang có: `Inventory.availableQuantity`
(thực tế là `quantity`), `InventoryReservationItem` (chưa có),
`findByVariantIdForUpdate` / `findWithItemsById` (repository chưa có).

Hệ quả trong lúc chờ:

- Bước `RESERVE_INVENTORY` / `CONFIRM_INVENTORY` / `RELEASE_INVENTORY` của saga
  **không có consumer** — message vào queue và nằm đó, saga sẽ timeout
- `ProductVariantStockEventListener` giữ lại nhưng hiện là code chết: không service
  nào publish `ProductVariantStockEvent`
- `upsertStock` chỉ cập nhật dòng đã có, không tạo mới được vì
  `ProductVariantStockEvent` không mang `productId` mà `Inventory.productId` là NOT NULL

Viết lại phần này ở P5 là hợp lý vì lúc đó inventory gộp vào `catalog-service`,
reserve/confirm nằm chung transaction với truy vấn variant — viết bây giờ sẽ phải
viết lại lần nữa.

## P1 — Dọn dẹp, không đụng logic — ĐÃ XONG

`./mvnw -B verify` vẫn xanh, 81 test pass.

- [x] Xoá 3 thư mục chỉ có `README.md`, không nằm trong reactor:
      `services/customer-service`, `services/search-service`, `services/recommendation-service`
      → nội dung 3 README chuyển vào mục "Next service boundaries" của
      `docs/microservices-roadmap.md`
- [x] Đổi package sót lại từ thời monolith sang chuẩn `com.fashionstore.<tên>`:
  - [x] `inventory-service`: `com.fashionstore.clothes_retail_api` → `com.fashionstore.inventory`
  - [x] `payment-service`: → `com.fashionstore.payment`, đồng thời bỏ tầng
        `modules/payment/` thừa cho khớp quy ước thư mục trong README.
        Xoá 6 thư mục rỗng sót lại từ lần tách monolith
        (`common/{dto,entity,messaging,payment,security,web}`)
- [x] Toàn bộ repo: mọi khai báo `package` đã khớp thư mục chứa nó
- [x] Xoá khối SMTP đã comment trong `identity-service/.../EmailServiceImpl.java`
- [x] Cập nhật `README.md` cho khớp danh sách module thực tế
- [x] Sửa link hỏng: cả `README.md` lẫn `docs/microservices-roadmap.md` đều trỏ tới
      `docs/project-progress.md` — file không tồn tại
- [x] Sửa hai mô tả sai trong `microservices-roadmap.md`: product-service được ghi là
      "publishes `product.variant.stock`" (không có producer nào) và inventory-service
      được ghi là có consumer idempotent cho saga (consumer đó chưa tồn tại)

## P2 — Gộp hạ tầng database — ĐÃ BỎ (quyết định ngày 2026-09-04)

**Không làm.** Chủ dự án chọn giữ mỗi service một container Postgres riêng, đúng
mô hình database-per-service ở cả mức tiến trình lẫn mức logic.

Phương án đã thử rồi revert: một Postgres instance, 8 schema (`identity`,
`product`, `orders`, …), mỗi service ghim `currentSchema` trong JDBC URL.
Đã chạy được: Flyway tạo bảng lịch sử riêng cho từng schema
(`"product"."flyway_schema_history"`, `"orders"."flyway_schema_history"`),
product-service áp 10 migration và order-service áp 5 migration đúng schema
của mình, cả hai khởi động bình thường.

Lý do vẫn bỏ: đánh đổi không đáng.

- một Postgres chết là cả hệ thống chết, thay vì chỉ một service
- không tách được tài nguyên CPU/disk theo service
- với đồ án về kiến trúc microservice, việc *thể hiện đúng* nguyên tắc
  database-per-service có giá trị hơn khoản tiết kiệm 7 container

Nếu sau này máy dev không tải nổi 19 container, phương án trung gian đáng cân
nhắc trước khi quay lại schema: **8 database riêng trong 1 Postgres instance**.
Trong Postgres hai database cùng cluster cô lập tuyệt đối — không câu SQL nào
query chéo được, chặt hơn schema — mà vẫn chỉ tốn 1 container.

Không có migration hay entity nào ghi cứng tên schema, nên mọi phương án ở trên
đều chỉ là đổi cấu hình, không đụng code.

### Việc P2 để lại (đã giữ)

Smoke test khi thử P2 phát hiện hai lỗi runtime có sẵn, đều làm service chết
ngay lúc khởi động dù build xanh. Đã sửa và giữ lại:

- `ProductImageRepository.existsByProductIdAndColorIsNullAndPrimaryTrue` —
  entity `ProductImage` có trường `isPrimary`, không có `primary`
- `OutboxPublisher.handleOutboxCreated` — `@TransactionalEventListener`
  ở phase `AFTER_COMMIT` kèm `@Transactional` mặc định; Spring từ chối trừ khi
  `REQUIRES_NEW` hoặc `NOT_SUPPORTED`
- `docker-compose.yml` khai báo `redis` dùng volume `redis_data` nhưng không
  định nghĩa volume đó — file compose không hợp lệ, và CI có bước
  `docker compose config --quiet` nên sẽ đỏ ngay khi build Maven xanh

**Bài học cho các mốc sau: build xanh không có nghĩa là service chạy được.**
P3 nên bổ sung một smoke test khởi động thật cho từng service.

## P3 — Lưới an toàn trước khi gộp — ĐÃ BỎ (quyết định ngày 2026-09-04)

**Không viết test.** Chủ dự án chọn bỏ qua phần viết characterization test.

Hệ quả cần biết khi làm P4/P5: repo có 81 unit test cho ~19.000 LOC, và không
test nào chạm tới luồng checkout hay saga đầu-cuối. Gộp cart vào order rồi gộp
inventory vào catalog sẽ dịch chuyển đúng những phần đó mà không có gì báo động
nếu hành vi đổi. Cụ thể là bốn luồng không được bảo vệ:

- checkout: giỏ hàng → tạo đơn → saga chạy hết → đơn `CONFIRMED`
- bồi hoàn: thanh toán fail → `RELEASE_INVENTORY` → đơn `CANCELLED`
- timeout: `OrderSagaTimeoutScanner` phát lại command
- tạo sản phẩm có variant → khởi tạo tồn kho

Thay thế bằng **kiểm tra thủ công** (không phải viết test, không tốn công bảo trì):

- [ ] Trước và sau mỗi mốc gộp, chạy từng service bằng jar đã build và xác nhận
      Spring context lên được — chính cách này đã bắt ra 3 lỗi chết-lúc-khởi-động
      mà 81 unit test không thấy
- [ ] Ghi lại response JSON của các endpoint public trước khi gộp, so sánh lại
      sau khi gộp — hợp đồng API với `apps/storefront` và `apps/backoffice`
      phải giữ nguyên

## P4 — Gộp cart vào order-service — ĐÃ XONG

`./mvnw -B verify` xanh trên 12 module, 81 test pass. order-service khởi động
thật và áp đủ 7 migration; `cart`, `cart_item` nằm cùng database với `orders`,
`checkout`, `order_saga`.

- [x] Chuyển toàn bộ `com.fashionstore.cart` sang `com.fashionstore.order.cart`,
      giữ nguyên cấu trúc bên trong (model, repository, service, controller,
      mapper, dto, client, exception)
- [x] Xoá `CartFeignClient`, `CartServiceClient`, `CartServiceResponse`,
      `CartItemServiceResponse` — `CheckoutServiceImpl` gọi thẳng `CartService`
- [x] Bỏ `CartItemsRemovalRequested` khỏi saga: `OrderSagaEventListener` xoá
      dòng giỏ hàng trực tiếp trong transaction xác nhận đơn.
      Xoá luôn contract và hằng số `EventTypes.CART_ITEMS_REMOVAL_REQUESTED`
- [x] Chuyển `ProductFeignClient` và `InventoryFeignClient` sang order-service
      (P5 sẽ gộp hai cái này làm một khi product + inventory thành catalog)
- [x] Migration: cart V1 → order V6, cart V3 → order V7. **Bỏ cart V2** vì nó
      tạo `processed_message`, bảng order đã tạo ở `V2__add_saga_support.sql`
- [x] Gộp route `cart` vào route `order` ở gateway
- [x] `pom.xml`: bỏ module cart-service, thêm mapstruct + resilience4j + aop
      vào order-service
- [x] `docker-compose.yml`: bỏ `cart-service` và `cart-postgres`;
      order-service nhận thêm `PRODUCT_BASE_URL` và depends_on product/inventory
- [x] Smoke test khởi động: order-service lên được context

### Thay đổi ngữ nghĩa cần biết

Trước đây xoá giỏ hàng là một message phát ra sau khi saga đóng, kèm comment
"hỏng cũng không rollback đơn". Giờ nó là một lệnh xoá trong cùng transaction
với `order.confirm()`. Đổi lại: **xoá giỏ hàng hỏng thì việc xác nhận đơn
rollback theo**. Đây là hệ quả trực tiếp và có chủ đích của việc gộp — mất tính
"việc phụ hỏng không ảnh hưởng đơn", đổi lấy tính nguyên tử.

### Lỗi có sẵn phát hiện khi smoke test

`cart-service` khai báo `resilience4j-spring-boot3` ghim cứng version 2.4.0,
trong khi Spring Boot BOM kéo mọi module resilience4j khác về 2.2.0. Starter
2.4.0 gọi `RxJava3FallbackDecorator` — class chỉ có ở 2.2.0 trở lên của
`resilience4j-spring6` phiên bản tương ứng — nên context chết ngay lúc khởi
động. cart-service cũ chắc chắn cũng chết y hệt nếu từng được chạy thật; build
và unit test không phát hiện được vì lỗi chỉ xảy ra lúc Spring quét
autoconfiguration. Đã bỏ version ghim cứng, để BOM quản lý.

## P5 — Gộp inventory + file vào catalog-service

Mốc lớn nhất. Đây là cặp coupling chặt nhất: `Inventory` khoá theo `variantId`
vốn thuộc aggregate `ProductVariant`.

### P5a — Dịch chuyển cấu trúc — ĐÃ XONG

Chủ dự án yêu cầu tách riêng phần **không đụng tới code nghiệp vụ** và làm trước.
`./mvnw -B verify` xanh trên 10 module, `docker compose config` hợp lệ.

- [x] Đổi tên `product-service` → `catalog-service` (artifact, package gốc thành
      `com.fashionstore.catalog`, thư mục, container, biến môi trường
      `PRODUCT_POSTGRES_*` → `CATALOG_POSTGRES_*`, DB `catalog_database`)
- [x] Chuyển `Inventory`, `InventoryReservation`, listener, service sang
      `com.fashionstore.catalog.inventory` — nguyên văn, không sửa logic
- [x] Chuyển `MediaFile` + logic upload sang `com.fashionstore.catalog.media`
- [x] Gộp lịch sử Flyway: inventory V1–V3 → V20–V22, file V1 → V40
- [x] Gateway: gộp route `product` + `file` thành route `catalog`
- [x] Xoá `services/inventory-service`, `services/file-service` khỏi reactor và compose
- [x] Hai Feign client của order-service trỏ chung `app.clients.catalog-base-url`
- [x] Smoke test khởi động: context lên được, Tomcat 8087, không xung đột bean

Bổ sung sau P5a: hai sub-package `com.fashionstore.catalog.inventory` và
`...catalog.media` đã được làm phẳng vào các package theo tầng
(`controller` / `service` / `repository` / `model` / `dto` / `mapper` /
`config` / `event` / `exception`) — chỉ đổi vị trí package, không sửa logic.
Xem `services/catalog-service/README.md`.

### P5b — Phần nghiệp vụ — CHƯA LÀM

- [ ] Viết `InventoryReservationService` + consumer cho `RESERVE_INVENTORY`,
      `CONFIRM_INVENTORY`, `RELEASE_INVENTORY` (đặc tả lấy lại từ file test đã
      xoá ở P0: `git show f94e7d7^:services/inventory-service/src/test/java/...`)
- [ ] Đặt `reserve`/`confirm`/`release` tồn kho vào cùng transaction với truy
      vấn variant — bỏ được một vòng round-trip mỗi bước saga
- [ ] Gộp 2 Feign client còn lại của order-service thành 1 `CatalogFeignClient`
      (hiện vẫn là hai interface, chỉ chung base URL)
- [ ] So lại response JSON đã ghi của các endpoint public

### Ba xung đột mà kế hoạch gốc không lường

Phát hiện trong lúc làm P5a, đều là lỗi có sẵn hoặc va chạm do gộp:

1. **`outbox_event` tồn tại ở cả hai bên.** product V2/V3 tạo bảng này nhưng
   product-service **không có một dòng code outbox nào** — schema chết. inventory
   V3 cũng tạo bảng đó, kèm `created_by`/`updated_by` mà bản của product thiếu.
   Xử lý: V22 không tạo bảng nữa, chỉ `alter table ... add column if not exists`
   hai cột kiểm toán để `OutboxEvent extends AuditedEntity` chạy được.

2. **Bảng `inventory` không khớp entity `Inventory`.** `V20` (nguyên bản
   inventory V1) tạo `available_quantity` và **không có** `product_id`, `status`;
   entity thì đòi `quantity`, `product_id NOT NULL`, `status NOT NULL`, cộng
   unique `(product_id, variant_id)`. Với `ddl-auto: none` Hibernate không kiểm
   tra lúc khởi động nên context vẫn lên, nhưng mọi câu truy vấn tồn kho sẽ chết
   ở tầng SQL. **Đã cố tình giữ nguyên** vì sửa là đổi schema nghiệp vụ; phải làm
   ở P5b cùng lúc với `InventoryReservationService`.

3. **Ba `SecurityConfig` có ba luật `anyRequest()` khác nhau** (product: ADMIN,
   inventory: ADMIN, file: chỉ cần đăng nhập) và hai bean trùng tên
   (`jwtConverter`, `corsConfigurationSource`) — trùng tên bean là lỗi chết lúc
   khởi động. Xử lý: mỗi domain một `SecurityFilterChain` khoanh vùng bằng
   `securityMatcher`, luật bên trong sao nguyên văn; một `CorsConfigurationSource`
   đăng ký CORS theo đường dẫn. Hai `CustomJwtDecoder` giống nhau từng byte nên
   giữ một bản.

**Kết quả sau P4 + P5a:** 3 Feign client → 2 (chung 1 base URL), 12 module → 10,
17 container → 13.

## P6 — Chốt lại (nửa ngày)

- [ ] Cập nhật `docs/microservices-roadmap.md` và `README.md` theo cấu trúc mới
- [ ] Vẽ lại sơ đồ kiến trúc (5 service + gateway, luồng saga rút gọn)
- [ ] CI: cân nhắc `push` chỉ chạy trên `main`, `pull_request` chạy đầy đủ
- [ ] Viết một đoạn trong báo cáo đồ án giải thích **lý do** gộp — đây là nội
      dung có giá trị học thuật: biết khi nào *không* nên tách service cũng là
      một kết luận kiến trúc

---

## Cái gì KHÔNG nên gộp, và tại sao

- **payment-service** — tích hợp PayPal bên ngoài, tần suất thay đổi và mô hình
  lỗi khác hẳn phần còn lại. Giữ riêng là đúng.
- **identity-service** — ranh giới bảo mật rõ ràng, phát hành JWT/JWKS cho cả hệ thống.
- **notification-service** — tuy chỉ 168 LOC, đây là consumer thuần đúng chuẩn
  và là chỗ thể hiện luồng event bất đồng bộ. Giữ lại, chỉ bỏ database.
- **Saga + outbox** — vẫn cần cho cặp order ↔ payment. Không xoá, chỉ rút gọn.

## Phương án tối thiểu

Nếu đề tài yêu cầu giữ đúng 8 service thì P0 + P1 đã xong là đủ để repo sạch và
CI xanh; phần còn lại có thể dừng.
