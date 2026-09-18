# Plan — Hoàn thiện Fashion Store theo Roadmap Audit (42% → sẵn sàng vận hành)

## Context

Audit nghiệp vụ (2026-09-16) chấm hệ thống **42%** — xương sống rất chắc (variant Màu×Size, cart snapshot, saga/outbox/DLQ/retry/idempotency, pessimistic lock) nhưng lớp nghiệp vụ bề mặt chưa có nên **chưa bán được hàng thực tế**. 5 blocker P0: voucher giả (hardcode `WELCOME10` trong `CheckoutServiceImpl.calculateDiscount`), ship giả (hardcode 25k/40k trong `calculateShippingFee`), fulfillment thủ công (`PUT /orders/{id}/status` tự gõ), return không hoàn tiền/restock, không có lịch sử đơn.

**Mục tiêu:** đưa hệ thống lên đủ khả năng vận hành như website bán quần áo thực tế; mỗi phase giao độc lập và kiểm thử được.

**Ràng buộc (CLAUDE.md):** Java 21 (`JAVA_HOME="C:/Program Files/Java/jdk-21"`), `./mvnw -o -pl ... clean test` + `clean` sau đổi package, DB-per-service Flyway `V<n>__*.sql` mới (Catalog: V1–19 product, V20–39 inventory, V40+ file), layer package phẳng `controller/dto/entity/repository/service+impl/mapper/...`, cross-service = RabbitMQ (`contracts.*` + `EventTypes`) hoặc HTTP qua gateway, không `git push` khi chưa yêu cầu.

---

## Quyết định kiến trúc (đã chốt)

| Vấn đề | Quyết định | Lý do |
|---|---|---|
| Promotion đặt ở đâu | Bảng trong `order-service` (không tách service mới) | Gắn chặt checkout/order/saga, chưa cần scale độc lập |
| Shipment đặt ở đâu | Bảng `shipment` trong `order-service` | Đơn và vận đơn là 1 aggregate; GHN/GHTK là HTTP client trong `order/client` |
| Review / Wishlist | Gộp vào `catalog-service` (review + wishlist) | Wishlist gắn với product_id nên thuộc catalog; giữ số service không tăng; tách sau khi cần scale |
| Notification mở rộng | Mở rộng `notification-service` hiện có (thêm template) | Đã có outbox + Thymeleaf |
| Search nâng cao | Mở rộng `ProductSpecificationsBuilder` trước, ES để Phase 7 | Đủ với <10k SP |

---

## Phase 1 — Checkout & Promotion (P0, ưu tiên số 1, 2–3 tuần) — ✅ Hoàn thành (2026-09-16)

> **Kết quả đạt được:** Đã thay thế toàn bộ voucher hardcode `WELCOME10` trong `CheckoutServiceImpl.calculateDiscount()` bằng hệ thống Promotion & Coupon Usage đầy đủ. `V10__create_promotion_schema.sql` (promotion) + `V11__create_coupon_usage.sql` (coupon_usage, unique `(promotion_id, user_id, order_id)`). Enums `PromotionType/ScopeType/CouponUsageStatus`, entities `Promotion/CouponUsage`, repos, `OrderErrorCode` bổ sung 5 mã 4010–4014. `PromotionService` với `previewDiscount` (checkout) / `reserve`+`confirm`+`release` (saga + hủy PENDING). `CheckoutServiceImpl` preview qua service, `OrderServiceImpl.createOrder` reserve trong transaction, `OrderSagaEventListener.inventoryConfirmed` confirm + `cancelOrder()` release khi bù trừ. `AdminPromotionController` `/api/v1/admin/promotions` CRUD. 76 test order-service xanh (0 failure). File xem `services/order-service/src/main/java/com/fashionstore/order/{entity,repository,service,controller}/Promotion*|Coupon*`.

**Spec:** thay `CheckoutServiceImpl.calculateDiscount()` hardcode bằng voucher thật có validate, quota, rollback theo saga. Không còn tính sai tiền.

**DB — `services/order-service/src/main/resources/db/migration/`:**

- `V10__create_promotion_schema.sql` — `promotion(id pk, code unique, type PERCENT/FIXED, value numeric, max_discount numeric, min_order_value numeric, start_at timestamptz, end_at timestamptz, total_quota int, per_user_quota int, scope_type ALL/PRODUCT/CATEGORY, scope_ids text, active boolean, created_at/updated_at)`. Index `code`, `(active, end_at)`.
- `V11__create_coupon_usage.sql` — `coupon_usage(id pk, promotion_id fk, user_id, order_id unique, used_at, status RESERVED/CONFIRMED/RELEASED)` + unique `(promotion_id, user_id, order_id)`. Index `(promotion_id, user_id)`.

**Code:**

- `order/entity/Promotion.java`, `CouponUsage.java`, `enumeration/PromotionType.java`, `CouponUsageStatus.java` — đặt trong `entity/` (order-service dùng `entity/`).
- `order/repository/PromotionRepository.java` (`findByCode`), `CouponUsageRepository.java` (`countByPromotionIdAndUserId`, `findByOrderId`, `countByPromotionId`).
- `order/service/PromotionService.java` + `impl/PromotionServiceImpl.java` — `previewDiscount(code, userId, subtotal, items)` (chỉ tính, không giữ quota) và `reserve(code, userId, orderId, subtotal, items)` / `confirm(orderId)` / `release(orderId)`. Logic: active + trong hạn + minOrder + scope + totalQuota + perUserQuota + tính discount (percent có trần `maxDiscount`).
- `order/service/impl/CheckoutServiceImpl.java` — thay `calculateDiscount(subtotal, couponCode)` sang gọi `promotionService.previewDiscount(...)`. **Không reserve quota ở checkout** — chỉ preview. Reserve thật ở `OrderServiceImpl.createOrder` trong cùng transaction tạo đơn.
- `order/service/impl/OrderServiceImpl.java` — trong `createOrder` transactional: sau khi build `Order`, gọi `promotionService.reserve(...)` (tạo `coupon_usage RESERVED`) cùng transaction. Saga `COMPENSATED` → `release`, saga COMPLETED (khi `OrderSagaEventListener` xử lý `INVENTORY_CONFIRMED`) → `confirm`.
- `order/dto/PromotionRequest.java`, `PromotionResponse.java`, `order/controller/AdminPromotionController.java` (`/api/v1/admin/promotions` CRUD, `@PreAuthorize("hasRole('ADMIN')")`).
- `order/exception/OrderErrorCode.java` — thêm `PROMOTION_NOT_FOUND`, `PROMOTION_EXPIRED`, `PROMOTION_QUOTA_EXCEEDED`, `PROMOTION_MIN_ORDER_NOT_MET`, `PROMOTION_SCOPE_MISMATCH`.

**Test (TDD — viết trước, xem đỏ):**

- `PromotionServiceTest` — hết hạn, hết quota, vượt per-user, minOrder không đạt, percent có trần, fixed, scope product/category.
- `CheckoutServiceImplTest` — preview discount đúng, code sai trả 0.
- `OrderServiceImplTest` — reserve thành công, double-create idempotent không tạo thêm usage, saga compensated release.

**Verify:**

```bash
export JAVA_HOME="C:/Program Files/Java/jdk-21"
./mvnw -o -pl services/order-service -am clean test -Dtest=PromotionServiceTest,CheckoutServiceImplTest,OrderServiceImplTest
```

---

## Phase 2 — Shipping thực GHN/GHTK (P0, 2 tuần)

**DB:**

- Catalog `V12__add_variant_weight.sql` — `alter table product_variant add column weight_gram integer` (+ `length_mm,width_mm,height_mm` nếu cần).
- Order `V12__create_shipment.sql` — `shipment(id pk, order_id unique fk, provider GHN/GHTK, tracking_code, fee numeric, weight_gram int, status PENDING/PICKED/SHIPPING/DELIVERED/RETURNED, ghn_order_code varchar, created_at/updated_at)`. Index `tracking_code`, `status`.

**Code:**

- `catalog/entity/ProductVariant.java` — thêm `weightGram` (+ dimensions). Cập nhật `dto/ProductVariantRequest.java`, `ProductVariantResponse.java`, `mapper/ProductVariantMapper.java`.
- `order/client/GhnClient.java` + `GhnFeignClient.java` — `calculateFee(province, district, ward, weight)` và `createOrder(shipmentReq)` qua GHN API. Cấu hình `ghn.token`, `ghn.shopId` qua env.
- `order/service/impl/CheckoutServiceImpl.java` — thay `calculateShippingFee(subtotal, method)` bằng gọi `ghnClient.calculateFee(...)` khi có `addressId` + weight; fallback hardcode nếu GHN fail (trả 502, không trả giá 0). Giữ miễn phí ≥500k nếu muốn.
- `order/service/ShipmentService.java` + `impl/ShipmentServiceImpl.java` — tạo shipment khi `OrderSaga` COMPLETED (hoặc khi admin bấm "tạo vận đơn"), gọi GHN create, lưu trackingCode.
- `order/controller/ShipmentController.java` — `POST /api/v1/orders/{id}/shipment` (admin), `GET /api/v1/orders/{id}/shipment` (customer), webhook `POST /internal/shipments/ghn-callback` (gateway `denyAll`, chỉ order-service nội bộ).

**Verify:** test `GhnClient` bằng WireMock; `CheckoutServiceImplTest` với mock GHN.

---

## Phase 3 — Fulfillment & Audit (P0, 1–2 tuần)

**DB — order-service:**

- `V13__create_order_status_history.sql` — `order_status_history(id pk, order_id fk, from_status, to_status, changed_by, reason, created_at)` index `(order_id, created_at)`.

**Code:**

- `order/entity/OrderStatusHistory.java`, `repository/OrderStatusHistoryRepository.java`.
- `order/service/impl/OrderServiceImpl.java` — mỗi `validateTransition` thành công → `save(history)` + publish event nếu cần. Guard: `SHIPPING` chỉ khi `shipment != null`, `DELIVERED` qua GHN webhook hoặc admin có quyền.
- `order/saga/ShipmentEventListener.java` — GHN webhook → `order.setStatus(SHIPPING/DELIVERED)` + history.
- `order/controller/AdminOrderController.java` — thêm `GET /{id}/history`.

---

## Phase 4 — Return/Refund hoàn chỉnh (P0, 2 tuần)

**DB — order-service:**

- `V15__create_return_request.sql` — `return_request(id pk, order_id unique, user_id, reason text, images text, status PENDING/APPROVED/REJECTED, reviewed_by, reviewed_at, created_at)`.

**Code:**

- `order/entity/ReturnRequest.java`, `repository/ReturnRequestRepository.java`.
- `order/service/ReturnService.java` + `impl/ReturnServiceImpl.java` — `requestReturn` tạo `PENDING` (không đổi Order ngay), `approve` → `order.setStatus(RETURNED)` + restock (`InventoryService.releaseStock` qua saga hoặc trực tiếp) + `sagaOutbox.emit(refundPayment)`, `reject` → lý do.
- `order/controller/OrderController.java` — `POST /{id}/return-request` tạo `PENDING`; `AdminOrderController` — `POST /returns/{id}/approve` | `/reject`.
- `payment/event/PaymentRequestedEventListener.java` — `refundPayment` hiện đổi status, bổ sung gọi VNPay refund API thật nếu có config merchant; nếu không giữ đổi status + log để kế toán xử lý tay.
- `order/saga/RefundEventListener.java` — đã có, mở rộng xử lý `PAYMENT_REFUND_REJECTED`.

**Verify:** test approve → restock + refund emitted; reject → không đổi gì.

---

## Phase 5 — Review & Wishlist (P1, 2 tuần)

**DB:**

- Catalog `V14__create_review.sql` — `review(id pk, product_id fk, user_id, order_id, rating 1-5, comment text, verified_purchase boolean, created_at)` unique `(product_id, user_id, order_id)`, index `(product_id, rating)`.
- Catalog `V15__create_wishlist.sql` — `wishlist(user_id, product_id, created_at)` pk `(user_id, product_id)` (thuộc catalog vì gắn với product_id; nằm trong dải V1–19 product của catalog).

**Code:**

- `catalog/entity/Review.java`, `repository/ReviewRepository.java`, `service/ReviewService.java`, `controller/ReviewController.java` — `POST /api/v1/products/{id}/reviews` (chỉ khi đã `DELIVERED` — check qua `OrderRepository` hoặc gọi order-service), `GET /api/v1/products/{id}/reviews`.
- `catalog/entity/WishlistItem.java`, `repository/WishlistItemRepository.java`, `service/WishlistService.java`, `controller/WishlistController.java` — `POST/DELETE /api/v1/wishlist/{productId}`, `GET /api/v1/wishlist` (catalog-service, DB `catalog_database`).
- Gateway: thêm route `/api/v1/wishlist/**`, `/api/v1/products/*/reviews` nếu cần.

**Verify:** test `verifiedPurchase` guard, rating aggregate.

---

## Phase 6 — Inventory ledger & low-stock (P1, 1 tuần)

**DB — catalog:**

- `V24__create_inventory_ledger.sql` — `inventory_ledger(id pk, variant_id, type IN/OUT/RESERVE/CONFIRM/RELEASE/ADJUST, quantity int, ref_order_id, created_by, created_at)` index `(variant_id, created_at)`.

**Code:**

- `catalog/entity/InventoryLedger.java`, `repository/InventoryLedgerRepository.java`.
- `catalog/service/impl/InventoryServiceImpl.java` — ghi ledger trong `reserveInternal`, `confirmSaga`, `releaseSaga`, `updateStock` (ADJUST).
- `catalog/controller/InventoryController.java` — `GET /admin/inventory/low-stock?threshold=10`, `GET /admin/inventory/ledger?variantId=`.

---

## Phase 7 — Search & Admin Dashboard (P1, 1–2 tuần)

- `catalog/repository/ProductSpecificationsBuilder.java` — mở rộng filter: price range, sizeOption, colorOption, brand, gender, material (`ProductAttributeValue`), chỉ `PUBLISHED` + variant `active`.
- `catalog/controller/ProductController.java` — `GET /api/v1/products/advance-search` thêm param `minPrice,maxPrice,size,color,brandId,gender`.
- `order/controller/AdminDashboardController.java` — `GET /api/v1/admin/dashboard` trả `revenueByDay, orderByStatus, topProducts, lowStockCount` (query aggregation, không thêm bảng).
- Cân nhắc Elasticsearch khi >10k SP — để sau, không block.

---

## Phase 8 — Polish (P2/P3)

- Guest cart merge: `CartServiceImpl` + `AuthServiceImpl.login` — lưu `anonymousId` cookie, merge khi login.
- Notification mở rộng: thêm template `order-confirmed/shipped/delivered` trong `notification-service`, trigger từ `OrderSagaEventListener` / GHN webhook.
- Recommendation: để sau khi có review + order data.

---

## Thứ tự phụ thuộc

```
Phase 1 (Promotion) -+-> Phase 2 (Shipping) --> Phase 3 (Fulfillment/Audit)
                     |                              |
                     +-> Phase 6 (Ledger)           v
                                                Phase 4 (Return/Refund) --> Phase 5 (Review/Wishlist) --> Phase 7 (Search/Dashboard) --> Phase 8 (Polish)
```

Phase 1 bắt buộc đầu tiên (chặn tiền). Phase 2 và 6 có thể song song sau Phase 1. Phase 3 cần Phase 2 (shipment). Phase 4 cần Phase 3 (history).

---

## File chính sẽ chạm (tổng hợp)

- `services/order-service/src/main/resources/db/migration/V10..V15__*.sql` (promotion, shipment, history, return — wishlist đã chuyển sang catalog V15)
- `services/catalog-service/src/main/resources/db/migration/V12__add_variant_weight.sql`, `V14__create_review.sql`, `V15__create_wishlist.sql`, `V24__create_inventory_ledger.sql`
- `services/order-service/src/main/java/com/fashionstore/order/{entity,repository,service/impl,dto,controller}/Promotion*|Shipment*|Return*|OrderStatusHistory*`
- `services/catalog-service/src/main/java/com/fashionstore/catalog/{entity,repository,service,controller}/Review*|Wishlist*` + `ProductVariant.weightGram` + `ProductSpecificationsBuilder`
- `services/order-service/src/main/java/com/fashionstore/order/service/impl/CheckoutServiceImpl.java` (thay 2 hàm hardcode)
- `services/order-service/src/main/java/com/fashionstore/order/service/impl/OrderServiceImpl.java` (promotion reserve/confirm/release + history)
- `services/order-service/src/main/java/com/fashionstore/order/client/GhnClient.java` (mới)
- `platform/gateway` + `docker-compose.yml` (route/env GHN nếu cần)
- `packages/api-contracts` — thêm `EventTypes`/DTO nếu cần cross-service (không bắt buộc Phase 1)

---

## Verification (tổng)

Mỗi phase: TDD — viết test trước, xem đỏ, implement, `clean test` xanh.

```bash
export JAVA_HOME="C:/Program Files/Java/jdk-21"
./mvnw -o -pl services/order-service -am clean test
./mvnw -o -pl services/catalog-service -am clean test
./mvnw -o -pl services/order-service -am clean test -Dtest=PromotionServiceTest
docker compose config
docker compose up -d
```

E2E qua gateway `http://localhost:8080`:
1. Tạo promotion `WELCOME10` (admin) → checkout với `couponCode=WELCOME10` → discount đúng → tạo đơn → saga COMPLETED → `coupon_usage=CONFIRMED`.
2. Hủy đơn PENDING → `coupon_usage=RELEASED`, inventory released.
3. Đơn nặng → checkout gọi GHN fee → shipment tạo → GHN webhook → DELIVERED.
4. Review chỉ khi DELIVERED, wishlist toggle.

---

## Rủi ro & lưu ý

- Promotion quota: áp dụng pessimistic lock `SELECT FOR UPDATE` (`findForUpdateByCodeIgnoreCase`) khi reserve đơn hàng để triệt tiêu race condition trên `totalQuota`, kết hợp unique constraint `(promotion_id, user_id, order_id)` chống duplicate.
- CheckoutItem snapshot: bổ sung `product_id` và `category_id` (V12 migration) từ `CartItem` để bảo đảm promotion scope `PRODUCT` / `CATEGORY` hoạt động chính xác khi update checkout và create order.
- GHN API mock bằng WireMock ở dev để không phụ thuộc mạng.
- Không thêm service mới ở Phase 1–4 để giữ vận hành đơn giản; tách sau khi cần scale.
- Mọi `EventTypes` mới thêm trong `packages/api-contracts`, không hardcode routing key string ở 2 service.
- Code style: terse, minimal, layer `controller → service/impl → repository → entity`, không thêm abstraction thừa.
