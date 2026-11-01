# Yêu cầu API (frontend → backend)

Antigravity thêm yêu cầu vào mục **Open**. Claude Code implement, cập nhật Swagger, rồi chuyển yêu cầu sang mục **Done**.

Mẫu:

```markdown
### <tiêu đề ngắn>
- Người yêu cầu: Antigravity · <ngày>
- Màn hình: apps/<app>/src/... (nơi dùng)
- Cần: METHOD /api/... — trả về hoặc nhận gì (field, paging, filter)
- Lý do: một dòng
- Trạng thái: open
```

## Open

_(chưa có)_

## Done

### Ảnh thu nhỏ (thumbnail) cho danh sách sản phẩm
- Người yêu cầu: Claude Code (kèm task UI cho Antigravity) · 2026-11-01
- Màn hình: apps/backoffice (ProductList, MediaLibrary/MediaPicker), apps/storefront (thẻ sản phẩm)
- Cần:
  - Upload ảnh JPEG/PNG xong (`POST /api/v1/files/{id}/complete`), backend tự tạo bản thu nhỏ (cạnh dài 600px, JPEG). Ảnh đã nhỏ hơn 600px, WebP/GIF/AVIF/SVG hoặc ảnh cũ không có bản thu nhỏ.
  - `MediaFileResponse` có thêm `thumbnailUrl` (null khi không có bản thu nhỏ; dùng `url` làm dự phòng).
  - `GET /api/v1/files/{id}/content?variant=thumb` redirect sang bản thu nhỏ, tự quay về ảnh gốc nếu không có. Bỏ `variant` hoặc giá trị khác thì vẫn là ảnh gốc.
  - `ProductSummaryResponse.thumbnailUrl` (danh sách admin và storefront) giờ đã là URL bản thu nhỏ khi ảnh do media API phục vụ; `ProductResponse` (chi tiết) và URL gửi lên khi lưu sản phẩm không đổi.
  - Media picker/thư viện ảnh nên hiển thị `thumbnailUrl ?? url` cho ô xem trước.
- Lý do: danh sách tải ảnh gốc (hàng MB) cho ô vài chục pixel.
- Trạng thái: done

### Chống ghi đè khi hai admin sửa cùng một sản phẩm
- Người yêu cầu: Claude Code (kèm task UI cho Antigravity) · 2026-11-01
- Màn hình: apps/backoffice, form sửa sản phẩm
- Cần:
  - Response `ProductResponse` có thêm `version` (số nguyên).
  - `PUT /admin/products/{productId}`: thêm field tùy chọn `version`, gửi lại đúng giá trị form đã tải. Khác bản hiện tại thì trả HTTP 409, code `9008` (`CONCURRENT_UPDATE`). Không gửi `version` thì không kiểm tra (client cũ vẫn chạy).
  - Khi nhận 409: báo "Sản phẩm vừa được người khác sửa", tải lại dữ liệu rồi cho admin sửa lại; không tự gửi lại.
- Lý do: tránh bản lưu sau ghi đè âm thầm bản lưu trước.
- Trạng thái: done

### Luồng xử lý đơn thủ công của admin
- Người yêu cầu: Claude Code (kèm task UI cho Antigravity) · 2026-11-01
- Màn hình: apps/backoffice/src/views/orders/OrderDetail.tsx, OrderList.tsx, order-actions.ts
- Cần:
  - `CONFIRMED` không còn tự chuyển `PROCESSING`. Admin xác nhận: `PATCH` trạng thái `PROCESSING`. Từ chối: `POST /api/v1/admin/orders/{id}/cancel` (lý do bắt buộc).
  - Admin chỉ còn chuyển được `CONFIRMED → PROCESSING`. Chuyển sang `PACKED`/`SHIPPING`/`DELIVERED` trả `ORDER_STATUS_INVALID`.
  - `POST /api/v1/orders/{id}/shipment`: thêm `weightGram` (tùy chọn, > 0). Chỉ khi đơn `PROCESSING`, thành công thì đơn `PACKED`.
  - Mới: `POST /api/v1/admin/orders/{id}/shipment/sync` trả `ShipmentResponse`. Dùng khi mất webhook GHN.
- Lý do: shop xác nhận đơn trước khi đóng gói, vận đơn chỉ tạo sau khi đóng gói xong, giao nhận theo webhook GHN.
- Trạng thái: done

### Tìm và lọc danh sách đơn admin
- Người yêu cầu: Antigravity · 2026-10-31
- Màn hình: apps/backoffice/src/views/orders/OrderList.tsx
- Cần: GET /api/v1/admin/orders — thêm filter `search`, `paymentMethod`, `createdFrom`, `createdTo`
- Lý do: tìm theo mã đơn/tên/SĐT phía server, lọc ngày và phương thức thanh toán; bỏ lọc client-side trên một trang
- Trạng thái: done
- Cách dùng:
  - `search`: tìm gần đúng, không phân biệt hoa thường, theo `orderCode`, tên và SĐT người nhận.
  - `paymentMethod`: `COD` | `ONLINE` | `WALLET`.
  - `createdFrom`, `createdTo`: `yyyy-MM-dd`, gồm cả hai ngày.
  - Các filter kết hợp bằng AND cùng `userId` và `status`.
  - `GET /api/v1/admin/orders/{orderId}` vẫn chỉ nhận UUID. Từ danh sách, điều hướng bằng `order.id`, không dùng `orderCode` làm đường dẫn: các endpoint con (`/payment`, `/shipment`, `/cancel`) cũng nhận UUID.
