# Product Review Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Triển khai toàn diện tính năng Đánh giá sản phẩm chuẩn E-Commerce (Gateway routing fix, Flyway migration V53, quản lý kiểm duyệt ẩn/hiện, phản hồi của Shop, thông tin người dùng, ảnh/video media và thông số form dáng thời trang trên Backoffice UI).

**Architecture:** Sử dụng kiến trúc Microservices hiện có: Spring Cloud Gateway định tuyến chính xác đến `catalog-service`; `catalog-service` quản lý thực thể `Review` và `ReviewMedia`, đồng thời gọi Feign client sang `identity-service` để lấy thông tin khách hàng; Backoffice (Next.js/React MUI) cung cấp giao diện quản trị đầy đủ bộ lọc, tìm kiếm, kiểm duyệt và phản hồi.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring Data JPA, Flyway, PostgreSQL, OpenFeign, Resilience4j, Next.js 14, React 18, TypeScript, Material UI, TanStack Query.

**Spec:** `docs/superpowers/specs/2026-10-17-product-review-enhancements-design.md`

## Global Constraints

- Tuân thủ cấu trúc package và quy tắc đặt tên hiện có trong `catalog-service` và `apps/backoffice`.
- Không hard-delete dữ liệu review khi thực hiện ẩn vi phạm; chỉ dùng trạng thái `VISIBLE` / `HIDDEN`.
- Storefront công khai chỉ thấy các review có `status = 'VISIBLE'`.
- Tích hợp gọi `identity-service` phải có CircuitBreaker và Fallback, không để lỗi mạng từ identity làm sập API quản lý đánh giá.

## Review Focus

- Route `/api/v1/admin/reviews` phải match chính xác cả khi có query params mà không bị 404 trên Gateway.
- Tìm kiếm từ khóa `keyword` trong admin review phải tìm được theo tên sản phẩm hoặc nội dung đánh giá không phân biệt hoa thường.
- Đánh giá có trạng thái `HIDDEN` không được tính vào điểm trung bình `averageRating` công khai của sản phẩm.
- Xóa phản hồi của Shop phải dọn dẹp cả `replyComment`, `repliedAt`, `repliedBy`.
- Xóa review (Admin Delete) phải cascade xóa sạch các bản ghi ảnh liên quan trong bảng `review_media`.

---

### Task 1: API Gateway Routing Fix

**Files:**
- Modify: `platform/gateway/src/main/resources/application.yaml`

**Interfaces:**
- Produces: Định tuyến cho cả `/api/v1/admin/reviews` và `/api/v1/admin/reviews/**` về `catalog-service`.

- [ ] **Step 1: Cập nhật route catalog trong `platform/gateway/src/main/resources/application.yaml`**
Thay đổi predicate đoạn `/api/v1/admin/reviews/**` thành `/api/v1/admin/reviews,/api/v1/admin/reviews/**`.

- [ ] **Step 2: Kiểm tra cấu hình yaml hợp lệ**
Chạy test hoặc kiểm tra syntax yaml của gateway:
Run: `mvn test-compile -pl platform/gateway`

- [ ] **Step 3: Commit**
```bash
git add platform/gateway/src/main/resources/application.yaml
git commit -m "fix(gateway): support exact path for admin reviews route"
```

---

### Task 2: Database Migration (`V53__enhance_product_reviews.sql`)

**Files:**
- Create: `services/catalog-service/src/main/resources/db/migration/V53__enhance_product_reviews.sql`

**Interfaces:**
- Produces: Các cột mới trong bảng `review` và bảng mới `review_media`.

- [ ] **Step 1: Viết script SQL migration `V53__enhance_product_reviews.sql`**
```sql
ALTER TABLE review
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    ADD COLUMN IF NOT EXISTS reply_comment TEXT,
    ADD COLUMN IF NOT EXISTS replied_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS replied_by VARCHAR(36),
    ADD COLUMN IF NOT EXISTS fit_rating VARCHAR(20),
    ADD COLUMN IF NOT EXISTS customer_height INT,
    ADD COLUMN IF NOT EXISTS customer_weight INT;

CREATE TABLE IF NOT EXISTS review_media (
    id VARCHAR(36) PRIMARY KEY,
    review_id VARCHAR(36) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    media_type VARCHAR(20) NOT NULL DEFAULT 'IMAGE',
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_review_media_review FOREIGN KEY (review_id) REFERENCES review(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_review_media_review_id ON review_media(review_id);
CREATE INDEX IF NOT EXISTS idx_review_status ON review(status);
```

- [ ] **Step 2: Commit**
```bash
git add services/catalog-service/src/main/resources/db/migration/V53__enhance_product_reviews.sql
git commit -m "feat(catalog): add V53 migration for review moderation, reply, media and fit attributes"
```

---

### Task 3: Backend Entities, Enums & Repository

**Files:**
- Create: `services/catalog-service/src/main/java/com/fashionstore/catalog/entity/enumeration/ReviewStatus.java`
- Create: `services/catalog-service/src/main/java/com/fashionstore/catalog/entity/enumeration/FitRating.java`
- Create: `services/catalog-service/src/main/java/com/fashionstore/catalog/entity/ReviewMedia.java`
- Modify: `services/catalog-service/src/main/java/com/fashionstore/catalog/entity/Review.java`
- Modify: `services/catalog-service/src/main/java/com/fashionstore/catalog/repository/ReviewRepository.java`

**Interfaces:**
- Consumes: Bảng `review`, `review_media`.
- Produces:
  - `Review.getStatus()`, `Review.getReplyComment()`, `Review.getMediaList()`, `Review.getFitRating()`.
  - `ReviewRepository.findAllForAdmin(String keyword, Integer rating, ReviewStatus status, Pageable pageable)`.
  - `ReviewRepository.findPublicReviewsByProductId(String productId, Integer rating, Pageable pageable)`.

- [ ] **Step 1: Tạo các Enum `ReviewStatus` và `FitRating`**
`ReviewStatus`: `VISIBLE`, `HIDDEN`.
`FitRating`: `TRUE_TO_SIZE`, `RUNS_SMALL`, `RUNS_LARGE`.

- [ ] **Step 2: Tạo entity `ReviewMedia` và cập nhật `Review`**
- `ReviewMedia`: có `id`, `review` (`@ManyToOne`), `url`, `mediaType`, `displayOrder`.
- `Review`: thêm `status` (`@Enumerated(EnumType.STRING)`), `replyComment`, `repliedAt`, `repliedBy`, `fitRating`, `customerHeight`, `customerWeight`, `@OneToMany List<ReviewMedia> mediaList`.

- [ ] **Step 3: Cập nhật `ReviewRepository`**
Thêm query dynamic/JPQL hỗ trợ lọc `keyword`, `rating`, `status` cho Admin; đồng thời cập nhật query public chỉ lấy `status = ReviewStatus.VISIBLE`.

- [ ] **Step 4: Commit**
```bash
git add services/catalog-service/src/main/java/com/fashionstore/catalog/entity/ services/catalog-service/src/main/java/com/fashionstore/catalog/repository/
git commit -m "feat(catalog): update Review entities and repository for moderation and media"
```

---

### Task 4: Identity Client & Customer Info Enrichment

**Files:**
- Create: `services/catalog-service/src/main/java/com/fashionstore/catalog/client/IdentityUserFeignClient.java`
- Create: `services/catalog-service/src/main/java/com/fashionstore/catalog/client/IdentityUserClient.java`

**Interfaces:**
- Consumes: `GET /internal/v1/users/{userId}` từ `identity-service`.
- Produces: `IdentityUserClient.getUser(String userId) -> Optional<InternalUserResponse>`.

- [ ] **Step 1: Viết Feign Client và Service Wrapper với CircuitBreaker**
Triển khai `IdentityUserFeignClient` kết nối tới `${app.clients.identity-base-url}`, và `IdentityUserClient` có fallback trả về `Optional.empty()` khi lỗi.

- [ ] **Step 2: Commit**
```bash
git add services/catalog-service/src/main/java/com/fashionstore/catalog/client/
git commit -m "feat(catalog): add IdentityUserClient for customer profile enrichment"
```

---

### Task 5: DTOs & Service Layer Enhancements

**Files:**
- Modify: `services/catalog-service/src/main/java/com/fashionstore/catalog/dto/review/ReviewResponse.java`
- Modify: `services/catalog-service/src/main/java/com/fashionstore/catalog/dto/review/CreateReviewRequest.java`
- Create: `services/catalog-service/src/main/java/com/fashionstore/catalog/dto/review/UpdateReviewStatusRequest.java`
- Create: `services/catalog-service/src/main/java/com/fashionstore/catalog/dto/review/ReplyReviewRequest.java`
- Modify: `services/catalog-service/src/main/java/com/fashionstore/catalog/service/ReviewService.java`
- Modify: `services/catalog-service/src/main/java/com/fashionstore/catalog/service/impl/ReviewServiceImpl.java`
- Test: `services/catalog-service/src/test/java/com/fashionstore/catalog/service/impl/ReviewServiceImplTest.java`

**Interfaces:**
- Produces:
  - `ReviewService.getAdminReviews(String keyword, Integer rating, ReviewStatus status, Pageable pageable)`
  - `ReviewService.updateReviewStatus(String reviewId, ReviewStatus status)`
  - `ReviewService.replyReview(String reviewId, String replyComment)`
  - `ReviewService.deleteReply(String reviewId)`

- [ ] **Step 1: Viết failing test trong `ReviewServiceImplTest`**
Test cập nhật status thành HIDDEN, test thêm reply và test enrich tên người dùng.

- [ ] **Step 2: Chạy test để đảm bảo fail**
Run: `mvn test -pl services/catalog-service -Dtest=ReviewServiceImplTest`

- [ ] **Step 3: Mở rộng DTOs và cài đặt logic trong `ReviewServiceImpl`**
- Map `customerName`, `customerEmail`, `customerPhone` từ `IdentityUserClient`.
- Map `status`, `replyComment`, `repliedAt`, `mediaUrls`, `fitRating`, `customerHeight`, `customerWeight`.
- Triển khai `updateReviewStatus`, `replyReview`, `deleteReply`.

- [ ] **Step 4: Chạy test để đảm bảo pass**
Run: `mvn test -pl services/catalog-service -Dtest=ReviewServiceImplTest`

- [ ] **Step 5: Commit**
```bash
git add services/catalog-service/src/main/java/com/fashionstore/catalog/dto/ services/catalog-service/src/main/java/com/fashionstore/catalog/service/ services/catalog-service/src/test/
git commit -m "feat(catalog): implement review moderation, replies, and enrichment in ReviewService"
```

---

### Task 6: Controllers & Endpoints

**Files:**
- Modify: `services/catalog-service/src/main/java/com/fashionstore/catalog/controller/AdminReviewController.java`
- Modify: `services/catalog-service/src/main/java/com/fashionstore/catalog/controller/ReviewController.java`
- Test: `services/catalog-service/src/test/java/com/fashionstore/catalog/controller/AdminReviewControllerTest.java`

**Interfaces:**
- Produces:
  - `GET /api/v1/admin/reviews?keyword=&rating=&status=`
  - `PATCH /api/v1/admin/reviews/{id}/status`
  - `POST /api/v1/admin/reviews/{id}/reply`
  - `DELETE /api/v1/admin/reviews/{id}/reply`
  - `DELETE /api/v1/admin/reviews/{id}`

- [ ] **Step 1: Viết test controller trong `AdminReviewControllerTest`**
Test các endpoint PATCH status và POST reply trả về 200 OK.

- [ ] **Step 2: Cập nhật `AdminReviewController` và `ReviewController`**
- Khai báo các endpoints quản trị mới trong `AdminReviewController`.
- Cập nhật `ReviewController` hỗ trợ nhận `mediaUrls` và thông số fit khi tạo review.

- [ ] **Step 3: Chạy test**
Run: `mvn test -pl services/catalog-service -Dtest=AdminReviewControllerTest,ReviewControllerTest`

- [ ] **Step 4: Commit**
```bash
git add services/catalog-service/src/main/java/com/fashionstore/catalog/controller/ services/catalog-service/src/test/
git commit -m "feat(catalog): expose admin review moderation and reply endpoints"
```

---

### Task 7: Backoffice API Client & Types

**Files:**
- Modify: `apps/backoffice/src/types/reviews.ts`
- Modify: `apps/backoffice/src/libs/api/reviews.ts`
- Test: `apps/backoffice/src/libs/api/reviews.test.ts`

**Interfaces:**
- Produces:
  - TypeScript type `Review` mở rộng: `status`, `customerName`, `customerEmail`, `replyComment`, `repliedAt`, `mediaUrls`, `fitRating`, `customerHeight`, `customerWeight`.
  - `reviewsApi.list(params: { page: number; size: number; rating?: number; status?: string; keyword?: string })`
  - `reviewsApi.updateStatus(reviewId: string, status: 'VISIBLE' | 'HIDDEN')`
  - `reviewsApi.reply(reviewId: string, reply: string)`
  - `reviewsApi.deleteReply(reviewId: string)`
  - `reviewsApi.removeAdmin(reviewId: string)`

- [ ] **Step 1: Viết test cho `reviews.ts` trong `reviews.test.ts`**
- [ ] **Step 2: Cập nhật `types/reviews.ts` và `libs/api/reviews.ts`**
- [ ] **Step 3: Chạy test**
Run: `pnpm --filter backoffice test src/libs/api/reviews.test.ts`
- [ ] **Step 4: Commit**
```bash
git add apps/backoffice/src/types/reviews.ts apps/backoffice/src/libs/api/reviews.ts apps/backoffice/src/libs/api/reviews.test.ts
git commit -m "feat(backoffice): add review moderation and reply API client methods"
```

---

### Task 8: Backoffice UI Components (ReviewList & Reply Dialog)

**Files:**
- Create: `apps/backoffice/src/views/reviews/ReplyReviewDialog.tsx`
- Modify: `apps/backoffice/src/views/reviews/ReviewList.tsx`

**Interfaces:**
- Produces: Màn hình Quản lý Đánh giá sản phẩm Backoffice chuyên nghiệp.

- [ ] **Step 1: Tạo `ReplyReviewDialog.tsx`**
Dialog có trường nhập `replyComment`, nút gửi và thông báo lỗi/thành công.

- [ ] **Step 2: Nâng cấp `ReviewList.tsx`**
- Thêm thanh tìm kiếm từ khóa với debounce.
- Thêm dropdown lọc trạng thái (Tất cả / Đang hiển thị / Đã ẩn).
- Cột Khách hàng: Hiển thị Avatar tròn, Họ tên, Email.
- Cột Đánh giá: Sao, Nhãn Fit (Vừa vặn/Chật/Rộng), Chiều cao/Cân nặng, Nội dung.
- Cột Media: Thumbnail ảnh đính kèm (click xem ảnh lớn qua lightbox/modal).
- Cột Phản hồi của Shop: Thẻ phản hồi có màu nền phân biệt, hiển thị thời gian và nội dung phản hồi.
- Cột Trạng thái: Chip xanh lá / cam.
- Cột Thao tác: Nút Ẩn/Hiện nhanh, Nút Trả lời/Sửa câu trả lời, Nút Xóa.

- [ ] **Step 3: Kiểm tra TypeScript và build frontend**
Run: `pnpm --filter backoffice type-check`

- [ ] **Step 4: Commit**
```bash
git add apps/backoffice/src/views/reviews/
git commit -m "feat(backoffice): comprehensive review management UI with search, moderation, and shop replies"
```

---

### Task 9: Toàn diện Kiểm thử & Xác minh (Verification)

**Files:** Tất cả các file đã cập nhật trong các task trên.

- [ ] **Step 1: Chạy toàn bộ unit test của Catalog Service**
Run: `mvn clean test -pl services/catalog-service`
Expected: Tất cả tests pass 100%.

- [ ] **Step 2: Chạy kiểm tra build của Backoffice**
Run: `pnpm --filter backoffice build`
Expected: Build production thành công không có lỗi type hay compile.

- [ ] **Step 3: Commit hoàn tất**
```bash
git commit -m "chore: verify product review enhancement implementation"
```
