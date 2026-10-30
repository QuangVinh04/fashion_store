# catalog-service

Cổng 8087 · database `catalog_database` (host 5435) · feature-first.
Gộp từ product-service, inventory-service, file-service. Không tách lại.

## Feature

| feature | trách nhiệm |
|---|---|
| `product` | sản phẩm, biến thể, danh mục, thương hiệu, màu, size, thuộc tính, size chart. Trạng thái `DRAFT → PUBLISHED → ARCHIVED` |
| `inventory` | tồn kho theo biến thể, giữ kho (reservation) cho đơn, sổ cái tồn kho, ngưỡng sắp hết hàng |
| `media` | upload qua presigned URL lên MinIO. Mục đích: `GENERAL`, `AVATAR`, `RETURN_EVIDENCE`, `PAYMENT_EVIDENCE` |
| `review` | đánh giá sản phẩm. Chỉ người đã mua (hỏi order-service) |
| `wishlist` | danh sách yêu thích |

## API chính

| nhóm | path |
|---|---|
| sản phẩm (public) | `/api/v1/products/**`, `/api/v1/category`, `/api/v1/brands`, `/api/v1/size-charts` |
| sản phẩm (admin) | `/admin/products/**`, `/admin/brands`, `/admin/color-options`, `/admin/size-options`, `/admin/product-attributes/**`, `/admin/size-charts/**` |
| tồn kho | `/api/v1/inventory/**`, `/admin/inventory/**`, `/internal/inventory/low-stock/count` |
| media | `/api/v1/files/**`, `/api/v1/files/avatars/**`, `/api/v1/files/evidence/**`, `/internal/v1/media/**` |
| review | `/api/v1/products/{id}/reviews`, `/api/v1/admin/reviews/**` |
| wishlist | `/api/v1/wishlist/**` |

Lưu ý: API admin của product và inventory dùng prefix `/admin/...`, không có `/api/v1`.

## Messaging

Nhận (command từ order-service), trả lời bằng event:

| nhận | trả lời |
|---|---|
| `inventory.reservation.requested` | `inventory.reserved` / `inventory.rejected` |
| `inventory.confirmation.requested` | `inventory.confirmed` / `inventory.confirmation.rejected` |
| `inventory.release.requested` | `inventory.released` |
| `inventory.order.cancellation.requested` | `inventory.order.cancelled` / `inventory.order.cancellation.rejected` |
| `profile.avatar.changed` (từ identity) | — |

Trạng thái reservation: `RESERVED → CONFIRMED` hoặc `REJECTED` / `RELEASED`.
Loại bút toán sổ cái: `RESERVE`, `CONFIRM`, `RELEASE`, `CANCEL_UNSHIPPED`, `RESTOCK`, `ADJUST`, `IN`, `OUT`.

## HTTP ra ngoài

- identity-service: thông tin user cho review, avatar.
- order-service: `/internal/v1/orders/verify-purchase`.
- MinIO.

## Bảng chính

- product: `product`, `product_variant`, `product_image`, `product_category`, `category`, `brand`, `color_option`, `size_option`, `product_attribute`, `product_attribute_option`, `product_attribute_value`, `size_chart`, `size_chart_row`.
- inventory: `inventory`, `inventory_reservation`, `inventory_reservation_item`, `inventory_ledger`.
- media: `media_file`, `media_file_tag`.
- review: `review`, `review_media`. wishlist: `wishlist_item`.
- hạ tầng: `outbox_event`, `processed_message`.

Migration hiện chỉ có một baseline: `V1__create_catalog_schema.sql`.
