# Kế hoạch tối ưu kiến trúc service

Mục tiêu: giảm chi phí vận hành và độ phức tạp phân tán xuống mức hợp lý cho
một người phát triển, **không** đánh mất các pattern cần thể hiện (gateway,
database-per-service, gọi đồng bộ, event bất đồng bộ, saga + outbox).

Đích đến: **5 service + gateway**, ~6 container thay vì 11.

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

## P2 — Cắt chi phí hạ tầng (1 ngày, đổi lấy nhiều nhất)

**Đây là mốc có tỉ lệ lợi ích/công sức cao nhất.** Không dịch chuyển một dòng
code nghiệp vụ nào, nhưng bỏ được 7 container. Làm trước khi gộp service để
bước gộp sau đó dễ hơn (các schema đã nằm chung một instance).

- [ ] Gộp 8 container Postgres thành 1, mỗi service một schema riêng
  - [ ] Sửa `docker-compose.yml`: xoá `identity-postgres`, `notification-postgres`,
        `product-postgres`, `file-postgres`, `inventory-postgres`, `cart-postgres`,
        `order-postgres`, `payment-postgres` → thay bằng một `postgres` duy nhất
  - [ ] Thêm init script tạo 8 schema
  - [ ] Mỗi service: thêm `?currentSchema=<tên>` vào JDBC URL và đặt
        `spring.flyway.schemas` / `default-schema` tương ứng
  - [ ] Xoá 8 named volume, thay bằng 1
- [ ] `notification-service` bỏ Postgres hoàn toàn
  - [ ] Thay bảng `ProcessedMessage` bằng khoá idempotency trên Redis (SETNX + TTL)
  - [ ] Xoá `ProcessedMessageRepository`, `ProcessedMessage`, migration `V1`
  - [ ] Gỡ dependency Flyway + JPA khỏi `pom.xml`
- [ ] `docker compose up -d` chạy được, smoke test luồng đăng ký → đặt hàng

> Nguyên tắc database-per-service vẫn được giữ về mặt logic: không service nào
> đọc schema của service khác. Chỉ có process Postgres là dùng chung.

## P3 — Lưới an toàn trước khi gộp (1–2 ngày)

Toàn repo hiện chỉ có **17 file test cho ~19.000 LOC**. Gộp service mà không
có test là refactor mù.

- [ ] Viết characterization test cho các luồng sẽ bị đụng vào ở P4/P5:
  - [ ] Checkout: giỏ hàng → tạo đơn → saga chạy hết → đơn `CONFIRMED`
  - [ ] Saga bồi hoàn: thanh toán fail → `RELEASE_INVENTORY` → đơn `CANCELLED`
  - [ ] Saga timeout: `OrderSagaTimeoutScanner` phát lại command
  - [ ] Product: tạo sản phẩm có variant → tồn kho được khởi tạo
- [ ] Ghi lại response JSON hiện tại của các endpoint public làm mốc so sánh
      (sau khi gộp, hợp đồng API với `apps/storefront` và `apps/backoffice` phải giữ nguyên)

## P4 — Gộp cart vào order-service (2–3 ngày)

Xoá được mắt xích đồng bộ `order → cart` trong luồng checkout.

- [ ] Chuyển `Cart`, `CartItem`, `CartStatus`, repository, service, controller
      sang `com.fashionstore.order.cart`
- [ ] Xoá `order-service/.../client/CartFeignClient.java`,
      `CartServiceClient.java`, `client/dto/CartServiceResponse.java`,
      `CartItemServiceResponse.java` → `CheckoutServiceImpl` gọi thẳng `CartService`
- [ ] Gộp migration: `cart-service` V1–V3 → đánh số tiếp vào order-service
      (V6, V7, V8) hoặc giữ schema `cart` riêng trong DB của order
- [ ] Chuyển 2 Feign client của cart (`ProductFeignClient`, `InventoryFeignClient`)
      sang order-service — tạm thời, P5 sẽ gộp chúng làm một
- [ ] Bỏ sự kiện `CartItemsRemovalRequested` khỏi luồng saga: xoá giỏ hàng giờ
      nằm trong cùng transaction với tạo đơn
- [ ] Gateway: gộp route `cart` vào route `order`
- [ ] Xoá module `services/cart-service` khỏi `pom.xml` và `docker-compose.yml`
- [ ] Test ở P3 vẫn xanh

## P5 — Gộp inventory + file vào catalog-service (3–4 ngày)

Mốc lớn nhất. Đây là cặp coupling chặt nhất: `Inventory` khoá theo `variantId`
vốn thuộc aggregate `ProductVariant`.

- [ ] Đổi tên `product-service` → `catalog-service` (artifact, package gốc giữ
      `com.fashionstore.catalog`, thư mục, container, biến môi trường)
- [ ] Chuyển `Inventory`, `InventoryReservation`, listener, service sang
      `com.fashionstore.catalog.inventory`
- [ ] Chuyển `MediaFile` + logic upload sang `com.fashionstore.catalog.media`
- [ ] Gộp lịch sử Flyway — product đang ở V1–V10, inventory V1–V3, file V1:
      đánh số lại inventory thành V20–V22, file thành V40
      (DB dev là dữ liệu bỏ đi được, cứ drop và tạo lại)
- [ ] Đặt `reserve`/`confirm`/`release` tồn kho vào cùng transaction với truy
      vấn variant — bỏ được một vòng round-trip mỗi bước saga
- [ ] Cập nhật consumer saga: command `RESERVE_INVENTORY`, `CONFIRM_INVENTORY`,
      `RELEASE_INVENTORY` giờ do catalog-service nhận
      (`RabbitMQNames`, `SagaConsumers` trong order-service)
- [ ] Gộp 2 Feign client còn lại của order-service thành 1 `CatalogFeignClient`
- [ ] Gateway: gộp route `product` + `file` thành route `catalog`
- [ ] Xoá `services/inventory-service`, `services/file-service` khỏi reactor và compose
- [ ] Test ở P3 vẫn xanh

**Kết quả sau P4 + P5:** 3 Feign client → 1. Saga còn 2 participant
(catalog + payment) thay vì 3.

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

Nếu đề tài yêu cầu giữ đúng 8 service, chỉ làm **P0 + P1 + P2**. Riêng P2 đã
đưa 11 container xuống 4 mà không dịch chuyển dòng code nghiệp vụ nào.
