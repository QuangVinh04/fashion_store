# Thiết kế Nâng cấp Hệ thống Đánh giá Sản phẩm Chuẩn E-Commerce

Tài liệu thiết kế kiến trúc nâng cấp module Quản lý đánh giá sản phẩm (Product Review Management) đạt chuẩn sàn thương mại điện tử chuyên nghiệp (Shopee, Shopify).

## 1. Mục tiêu và Phạm vi

Nâng cấp toàn diện tính năng đánh giá sản phẩm trên 3 lớp:
1. **API Gateway & Routing**: Sửa lỗi 404 cho API Admin Review.
2. **Backend & Database (`catalog-service`, tích hợp `identity-service`)**:
   - Mở rộng schema: Hỗ trợ trạng thái kiểm duyệt (Ẩn/Hiện), phản hồi từ Shop (Shop Reply), đánh giá form dáng thời trang (Fit rating, chiều cao, cân nặng), và lưu trữ ảnh/video review.
   - API Enrichment: Tích hợp lấy thông tin khách hàng (họ tên, email, sđt) qua Feign client tới `identity-service`.
   - Admin APIs: Tìm kiếm, lọc đa tiêu chí, toggle ẩn/hiện kiểm duyệt, gửi/xóa phản hồi của Shop.
   - Public APIs: Chỉ hiển thị review `VISIBLE`, tính toán rating trung bình chính xác, kèm phản hồi của Shop và ảnh/video thực tế.
3. **Frontend Quản trị (`apps/backoffice`)**:
   - Giao diện Admin quản trị chuyên nghiệp: Thanh tìm kiếm từ khóa, lọc theo số sao, lọc trạng thái (Hiển thị / Đã ẩn), lọc có ảnh.
   - Hiển thị khách hàng rõ ràng (Avatar, Tên, Email), thông tin size/fit, thumbnail ảnh (click xem phóng to).
   - Dialog phản hồi đánh giá trực tiếp cho khách hàng.
   - Thao tác nhanh Ẩn/Hiện và Xóa có xác nhận.

---

## 2. Thiết kế Cơ sở dữ liệu (Flyway Migration `V53__enhance_product_reviews.sql`)

### 2.1. Cập nhật bảng `review`
- `status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE'` (`VISIBLE` - Hiển thị, `HIDDEN` - Đã ẩn vi phạm/spam).
- `reply_comment TEXT`: Nội dung phản hồi từ phía Cửa hàng / Admin.
- `replied_at TIMESTAMP WITHOUT TIME ZONE`: Thời gian phản hồi.
- `replied_by VARCHAR(36)`: ID người phản hồi (Admin/CSKH).
- `fit_rating VARCHAR(20)`: Đánh giá độ vừa vặn (`TRUE_TO_SIZE` - Vừa vặn, `RUNS_SMALL` - Hơi chật/nhỏ, `RUNS_LARGE` - Hơi rộng/to).
- `customer_height INT`: Chiều cao người mua (cm).
- `customer_weight INT`: Cân nặng người mua (kg).

### 2.2. Tạo mới bảng `review_media`
Lưu trữ danh sách hình ảnh / video feedback:
```sql
CREATE TABLE IF NOT EXISTS review_media (
    id VARCHAR(36) PRIMARY KEY,
    review_id VARCHAR(36) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    media_type VARCHAR(20) NOT NULL DEFAULT 'IMAGE',
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_review_media_review FOREIGN KEY (review_id) REFERENCES review(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_review_media_review_id ON review_media(review_id);
```

---

## 3. Kiến trúc Backend (`services/catalog-service`)

### 3.1. Entity & Repository
- Cập nhật entity `Review`:
  - Thêm các trường: `status`, `replyComment`, `repliedAt`, `repliedBy`, `fitRating`, `customerHeight`, `customerWeight`.
  - Quan hệ `@OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)` với `ReviewMedia`.
- Tạo entity `ReviewMedia`.
- Repository `ReviewRepository`:
  - Bổ sung query tìm kiếm Admin: kết hợp lọc `rating`, `status`, và từ khóa `keyword` (tìm theo tên sản phẩm, nội dung comment).
  - Tối ưu query đếm và tính rating chỉ cho các review `status = 'VISIBLE'` trên Storefront.

### 3.2. Tích hợp lấy thông tin khách hàng (`IdentityClient`)
- Khai báo Feign Client gọi sang `identity-service`: `GET /internal/v1/users/{userId}` để lấy `fullName`, `email`, `phone`.
- Cung cấp cơ chế fallback resilient: Nếu `identity-service` bận hoặc không tìm thấy user, hệ thống fallback hiển thị `User-{userId}` mà không làm đứt gãy trang quản trị.

### 3.3. DTO & API Endpoints
- **Admin Endpoints (`AdminReviewController` - `/api/v1/admin/reviews`):**
  - `GET /api/v1/admin/reviews`: Params `keyword`, `rating`, `status`, `page`, `size`. Trả về PageResponse gồm `customerName`, `customerEmail`, `customerPhone`, `status`, `replyComment`, `repliedAt`, `mediaUrls`, `fitRating`, `customerHeight`, `customerWeight`.
  - `PATCH /api/v1/admin/reviews/{id}/status`: Body `{ "status": "VISIBLE" | "HIDDEN" }`.
  - `POST /api/v1/admin/reviews/{id}/reply`: Body `{ "reply": "Nội dung phản hồi" }`.
  - `DELETE /api/v1/admin/reviews/{id}/reply`: Xóa phản hồi.
- **Storefront Endpoints (`ReviewController` - `/api/v1/products/{id}/reviews`):**
  - Cập nhật `CreateReviewRequest`: Cho phép gửi kèm `mediaUrls` (List<String>), `fitRating`, `customerHeight`, `customerWeight`.
  - Cập nhật `getProductReviews`: Chỉ lấy các review `VISIBLE`, trả về kèm `replyComment`, `repliedAt`, `mediaUrls`, `fitRating`.

---

## 4. API Gateway (`platform/gateway`)

Cập nhật route `catalog` trong `platform/gateway/src/main/resources/application.yaml`:
```yaml
- id: catalog
  uri: ${CATALOG_BASE_URL:http://catalog-service:8087}
  predicates:
    - Path=...,/api/v1/admin/reviews,/api/v1/admin/reviews/**
```

---

## 5. Giao diện Quản trị (`apps/backoffice`)

### 5.1. DTO & API Client (`reviews.ts`, `types/reviews.ts`)
- Mở rộng kiểu dữ liệu `Review` trong TypeScript.
- Bổ sung hàm API: `updateStatus(reviewId, status)`, `reply(reviewId, replyComment)`, `deleteReply(reviewId)`.

### 5.2. View Quản trị (`ReviewList.tsx`)
- **Bộ lọc:**
  - `TextField` tìm kiếm từ khóa với debounce.
  - `Select` số sao: Tất cả, 5 sao, 4 sao, 3 sao, 2 sao, 1 sao.
  - `Select` trạng thái: Tất cả, Đang hiển thị, Đã ẩn.
- **Bảng dữ liệu:**
  - Khách hàng: Tên khách hàng in đậm, email bên dưới, avatar viết tắt.
  - Sản phẩm: Tên sản phẩm có link chuyển trang.
  - Đánh giá: Rating sao, nhãn Fit ("Vừa vặn", "Hơi chật",...), chiều cao/cân nặng, nội dung comment.
  - Hình ảnh: Hiển thị các thumbnail ảnh đính kèm (click xem to).
  - Phản hồi: Hiển thị box phản hồi của Shop nếu có, nút "Trả lời / Sửa" nếu chưa có.
  - Trạng thái: Chip xanh lá "Hiển thị", Chip xám "Đã ẩn".
  - Thao tác: Nút Ẩn/Hiện nhanh, Nút Trả lời, Nút Xóa.
- **Hộp thoại Phản hồi (`ReplyReviewDialog`):**
  - Textarea nhập câu trả lời của Shop gửi khách hàng.

---

## 6. Kế hoạch Kiểm chứng & Tiêu chí Nghiệm thu (Verification)
1. **Kiểm tra Gateway Routing**: Gọi `GET /api/v1/admin/reviews` qua Gateway trả về 200 OK thay vì 404.
2. **Kiểm tra Database Migration**: Chạy Flyway migration `V53` thành công, thêm các cột mới và bảng `review_media`.
3. **Kiểm tra Backend Unit / Integration Tests**:
   - `AdminReviewControllerTest`, `ReviewServiceImplTest`: Test phân trang, tìm kiếm keyword, cập nhật trạng thái, phản hồi shop.
   - Maven build & test thành công cho `catalog-service`.
4. **Kiểm tra Frontend Build & UI Tests**:
   - Build TypeScript không lỗi (`pnpm build` hoặc `pnpm type-check`).
   - Màn hình Quản lý Đánh giá tải dữ liệu đầy đủ, lọc được theo sao, từ khóa, trạng thái; thao tác Ẩn/Hiện và Phản hồi hoạt động mượt mà.
