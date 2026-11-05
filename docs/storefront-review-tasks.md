# Task "Đánh giá sản phẩm" — cho Antigravity

- Người tạo: Claude Code · 2026-11-06. Backend đã xong. Hợp đồng API nằm ở mục Done "Hoàn thiện đánh giá sản phẩm: ảnh, sửa, đánh giá của tôi, điểm sao trên sản phẩm" trong `docs/api-requests.md`.
- Phạm vi: `apps/storefront/**` (R1–R6), `apps/backoffice/**` (B1). Không sửa Java. Cần field hay hành vi API khác: thêm entry vào `docs/api-requests.md`.
- Làm theo thứ tự. Mỗi task xong: chạy `.agents/workflows/fe-check.md` (typecheck + screenshot), ghi `Trạng thái: done`.
- Giữ nguyên design system hiện có (`StarRating`, `Status`, class `pd-*`, `store-button`). Không thêm dependency.

## Hợp đồng API (tóm tắt)

| method | path | ghi chú |
|---|---|---|
| POST | `/api/v1/files/reviews/presign` | body `{ filename, contentType, sizeBytes }`. Chỉ JPEG/PNG/WebP ≤ 5 MiB. Trả `{ mediaId, uploadUrl, contentType, uploadHeaders? }` |
| PUT | `uploadUrl` | gửi file thẳng lên kho; header lấy từ `uploadHeaders`, không có thì `Content-Type: contentType` |
| POST | `/api/v1/files/{mediaId}/complete` | body `{}`. Trả `{ id, url, purpose: 'REVIEW_IMAGE', status: 'ACTIVE', visibility: 'PUBLIC' }` |
| POST | `/api/v1/products/{id}/reviews` | body `{ orderId, rating, comment?, mediaIds?, fitRating?, customerHeight?, customerWeight? }` (201) |
| PUT | `/api/v1/products/{id}/reviews/{reviewId}` | cùng body, **không** có `orderId`. Thay nguyên: field bỏ trống là xoá |
| DELETE | `/api/v1/products/{id}/reviews/{reviewId}` | chỉ chủ đánh giá |
| GET | `/api/v1/reviews/me?orderId=&page=&size=` | đánh giá của tôi, kể cả đang ẩn |
| GET | `/api/v1/products/{id}/reviews?rating=&page=&size=` | công khai, như cũ + field mới bên dưới |

`mediaUrls` trong body **không còn được nhận**. Ảnh luôn đi qua 3 bước upload rồi gửi `mediaIds` (tối đa 10).

```ts
type FitRating = 'TRUE_TO_SIZE' | 'RUNS_SMALL' | 'RUNS_LARGE';
type Review = {
  id: string; productId: string; productName?: string;   // productName chỉ có ở /reviews/me
  userId: string;                                         // công khai: đã che, đừng dùng để so "của tôi"
  customerName: string;                                   // công khai: "N***h" hoặc "Khách hàng"
  orderId?: string;                                       // chỉ có ở /reviews/me
  rating: number; comment?: string; verifiedPurchase: boolean;
  status: 'VISIBLE' | 'HIDDEN';
  replyComment?: string; repliedAt?: string;
  fitRating?: FitRating; customerHeight?: number; customerWeight?: number;
  mediaUrls: string[]; createdAt: string; updatedAt?: string;
  editedAt?: string;                                      // chỉ có khi khách đã sửa (PUT)
};
// ProductSummary và Product có thêm:
ratingAverage: number;  // 1 chữ số thập phân, 0 khi chưa có đánh giá
reviewCount: number;
```

Mã lỗi cần xử lý: `1036` (409) đã đánh giá sản phẩm này trong đơn, `1037` (400) chưa nhận hàng, `1038` (403) không phải đánh giá của mình, `1035` (404) đánh giá không còn, `6001` / `6002` ảnh không hợp lệ hoặc chưa upload xong.

## Quy tắc chung

- Upload ảnh khi khách chọn file (không đợi bấm gửi), hiện thumbnail + nút xoá từng ảnh, khoá nút gửi khi còn ảnh đang upload. Kiểm tra loại/kích thước ở client trước khi presign (cùng giới hạn với `uploadReturnEvidence` trong `api/return-actions.ts`).
- Ảnh đã upload nhưng khách bỏ khỏi form thì chỉ bỏ khỏi `mediaIds`, không cần gọi API xoá.
- Đánh giá đang ẩn (`status: 'HIDDEN'`) vẫn hiện cho chính khách ở chi tiết đơn, kèm nhãn "Đang bị ẩn".
- Không suy "đã đánh giá" từ lỗi `1036` nữa. Dùng `/reviews/me?orderId=`.

## R1 — API client và type

- `api/types.ts`: cập nhật `Review` như trên, thêm `ratingAverage`, `reviewCount` vào `ProductSummary`.
- `api/store.ts`:
  - `addReview(productId, body)`: nhận object (orderId, rating, comment, mediaIds, fitRating, customerHeight, customerWeight) thay vì 4 tham số rời.
  - Thêm `updateReview(productId, reviewId, body)`, `myReviews(orderId?)`, `uploadReviewImage(file)` (theo mẫu `uploadReturnEvidence`; kiểm tra `purpose === 'REVIEW_IMAGE'` và `status === 'ACTIVE'`, trả `{ mediaId, url }`).
- Trạng thái: done

## R2 — Form đánh giá có ảnh, độ vừa, số đo

- Tách `OrderItemReview` khỏi `pages/OrderDetail.tsx` thành `components/reviews/ReviewForm.tsx` (dùng chung cho tạo và sửa).
- Trường: số sao (bắt buộc), nội dung (≤ 1000 ký tự, có bộ đếm), ảnh (≤ 10), độ vừa (3 lựa chọn: "Vừa", "Chật", "Rộng"), chiều cao 50–250 cm, cân nặng 20–300 kg (tùy chọn).
- Bỏ điều kiện bắt buộc nhập nội dung (backend cho phép chỉ chấm sao).
- Trạng thái: done

## R3 — Chi tiết đơn biết sản phẩm nào đã đánh giá

- `pages/OrderDetail.tsx`: khi đơn đã giao, gọi `store.myReviews(orderId)` một lần, map theo `productId`.
- Chưa đánh giá: nút "Đánh giá sản phẩm này" như cũ. Đã đánh giá: hiện sao + nội dung tóm tắt, nút "Sửa đánh giá" (mở `ReviewForm` điền sẵn, gọi `updateReview`) và "Xoá" (có xác nhận).
- Sau khi tạo/sửa/xoá: tải lại `myReviews`, không reload trang.
- Trạng thái: done

## R4 — Danh sách đánh giá hiện tên, ảnh, độ vừa, phản hồi của shop

- `pages/ProductReviews.tsx` và phần xem trước trong `pages/ProductDetail.tsx`: hiện `customerName`, nhãn "Đã mua hàng" khi `verifiedPurchase`, ảnh (`mediaUrls`, bấm để xem lớn), độ vừa + chiều cao/cân nặng nếu có, `replyComment` dạng "Phản hồi từ shop".
- Có `editedAt` thì ghi "(đã sửa)". Không dùng `updatedAt`: nó cũng đổi khi shop trả lời hoặc ẩn/hiện.
- Trạng thái: done

## R5 — Sao trên thẻ sản phẩm

- `ProductCard` trong `components/StoreUI.tsx`: hiện `StarRating` + `ratingAverage` + `(reviewCount)` khi `reviewCount > 0`; không hiện gì khi bằng 0.
- `pages/ProductDetail.tsx`: phần đầu trang lấy `ratingAverage`/`reviewCount` từ product, không cần đợi `store.reviews(id)` để hiện sao.
- Trạng thái: done

## R6 — Kiểm tra bộ lọc số sao

- `pages/Products.tsx` đã gửi `minRating` (3/4/5). Backend giờ lọc theo `ratingAverage` lưu sẵn, kết quả không đổi. Không cần sửa code: chỉ kiểm tra lọc "Từ 4 sao" ra đúng sản phẩm có sao hiện trên thẻ (R5) ≥ 4.
- Trạng thái: done

## R7 — Sửa sau review (2026-11-06)

- `pages/ProductReviews.tsx:124`: "(đã sửa)" đang so `updatedAt !== createdAt`, nên hiện sai khi shop trả lời hoặc ẩn/hiện. Đổi thành `Boolean(review.editedAt)`; thêm `editedAt?: string` vào type `Review`.
- `pages/OrderDetail.tsx:213`: không nuốt lỗi `myReviews`. Hiện lỗi + nút thử lại thay vì để mọi sản phẩm hiện lại nút "Đánh giá".
- `pages/OrderDetail.tsx` đang 464 dòng (> 400): tách `OrderItemReview` sang `components/reviews/`.
- Chạy lại fe-check có screenshot khi stack đã chạy.
- Trạng thái: done

## B1 — Backoffice: dọn hàm không dùng

- `apps/backoffice/src/libs/api/reviews.ts`: `reviewsApi.remove` gọi endpoint của khách, nhân viên giờ bị 403 ở đó. UI đang dùng `removeAdmin`, nên xoá `remove` (và test của nó nếu có).
- Màn danh sách đánh giá: hiện thumbnail từ `mediaUrls` nếu chưa có.
- Trạng thái: done
