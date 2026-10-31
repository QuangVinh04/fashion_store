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
