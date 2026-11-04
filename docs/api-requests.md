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

### Bổ sung totalElements trong GET /api/v1/admin/promotions
- Người yêu cầu: Antigravity · 2026-11-01
- Màn hình: apps/backoffice/src/views/promotions/PromotionList.tsx
- Cần: `GET /api/v1/admin/promotions` trả về trường `totalElements` trong `PageResponse`.
  - Cụ thể tại `PromotionServiceImpl.java:233`, builder của `PageResponse` cần gọi thêm `.totalElements(page.getTotalElements())`.
- Lý do: Hiện tại API trả thiếu `totalElements` làm `normalizeAdminPage` ở backoffice báo lỗi hợp đồng phân trang.
- Trạng thái: open

## Done

### Phân quyền theo nhóm và tạo tài khoản nhân viên
- Người yêu cầu: Claude Code (kèm task UI cho Antigravity) · 2026-11-04
- Màn hình: apps/backoffice (menu, màn "Nhóm & phân quyền", chi tiết user, form "Tạo nhân viên", dashboard)
- Backend đã xong (identity, order, catalog, payment, gateway, backoffice-bff; Swagger "Admin Groups", "Admin Staff" và các endpoint đổi quyền đã cập nhật). Frontend chưa làm.
- **Quyền (22 authority, cố định theo code).** `/bff/session` trả chúng trong `roles`, cùng `ROLE_USER` / `ROLE_ADMIN` (vd `["order:manage","order:read","ROLE_USER"]`; `ROLE_ADMIN` chỉ xuất hiện nếu user còn realm role `ADMIN`). `ROLE_ADMIN` không còn cho quyền gì: vào backoffice = có ít nhất một quyền trong bảng dưới, không có thì BFF chặn như trước. Nhãn tiếng Việt và khu vực lấy từ `GET /api/v1/admin/permissions`, không hardcode.

  | khu vực | quyền | cần cho |
  |---|---|---|
  | Dashboard | `dashboard:read` | `GET /api/v1/admin/dashboard/operations` |
  | | `revenue:read` | `GET /api/v1/admin/dashboard` (có doanh thu) |
  | Đơn hàng | `order:read` | danh sách, chi tiết, lịch sử, saga của `/api/v1/admin/orders/**`; `GET /api/v1/admin/orders/{id}/shipment` |
  | | `order:manage` | đổi trạng thái, hủy, retry saga hủy/checkout, xác minh carrier; presign bằng chứng `payments` |
  | Vận chuyển | `shipment:manage` | `POST /api/v1/orders/{id}/shipment`, `POST /api/v1/admin/orders/{id}/shipment/sync` |
  | Đổi trả | `return:read` | xem yêu cầu đổi trả, URL bằng chứng |
  | | `return:manage` | duyệt, từ chối, nhận hàng, hủy, tạo lại pickup, mở/đóng carrier return |
  | | `refund:approve` (tiền) | hoàn tiền đổi trả; presign bằng chứng `payments` |
  | Sản phẩm | `product:read` | `GET` `/admin/products`, `/admin/brands`, `/admin/color-options`, `/admin/size-options`, `/admin/product-attributes`, `/admin/size-charts` |
  | | `product:manage` | ghi các path trên và `/api/v1/category` (tạo/sửa/xóa danh mục) |
  | Tồn kho | `inventory:read` · `inventory:manage` | `GET` / ghi `/admin/inventory/**`, `/api/v1/inventory/**` |
  | Đánh giá | `review:read` · `review:manage` | `GET` / ghi `/api/v1/admin/reviews/**` |
  | Khuyến mãi | `promotion:read` · `promotion:manage` | `GET` / ghi `/api/v1/admin/promotions/**` |
  | Khách hàng | `customer:read` | `GET /api/v1/admin/users`, `GET /api/v1/admin/users/{id}` |
  | | `customer:manage` | `PUT /api/v1/admin/users/{id}`, `PATCH /api/v1/admin/users/{id}/status` (nếu đích thuộc nhóm `Admin` thì cần thêm `group:manage`, không thì 403) |
  | Thanh toán, ví | `payment:read` | `/api/v1/admin/payments/**` |
  | | `wallet:read` | `/api/v1/admin/wallets/**` |
  | | `wallet:adjust` (tiền) | hiện chưa endpoint nào kiểm tra (để dành cho điều chỉnh ví), vẫn tick được |
  | Phân quyền | `group:manage` | mọi endpoint ở mục "Endpoint mới". `isAssignable=false`: không bao giờ tick cho nhóm tự tạo, chỉ nhóm `Admin` có |

  Quyền tiền/nhạy cảm: `revenue:read`, `refund:approve`, `promotion:manage`, `customer:manage`, `wallet:adjust`, `group:manage` (nhóm `Nhân viên` mặc định không có).
- **Quyền ghi kéo theo quyền đọc.** Server tự thêm quyền đọc khi lưu nhóm và trả danh sách đã chuẩn hóa (tick `order:manage` thì response có thêm `order:read`). UI nên tự tick và hiển thị quyền đọc kéo theo để khớp response, không để admin thấy quyền "tự nhiên xuất hiện": `order:manage`, `shipment:manage` → `order:read`; `return:manage`, `refund:approve` → `return:read`; `product:manage` → `product:read`; `inventory:manage` → `inventory:read`; `review:manage` → `review:read`; `promotion:manage` → `promotion:read`; `customer:manage` → `customer:read`; `wallet:adjust` → `wallet:read`; `revenue:read` → `dashboard:read`. Gỡ quyền đọc kéo theo thì server tự thêm lại. Không có chiều ngược (đọc không kéo theo ghi).
- **Endpoint mới (identity-service; các endpoint dưới đều cần `group:manage`, trừ `groups` trong `GET /api/v1/admin/users/{id}` cần `customer:read`; response bọc `ApiResponse`).**
  - `GET /api/v1/admin/permissions` → `PermissionResponse[]` = `{ authority, area, label, isAssignable }`.
  - `GET /api/v1/admin/groups` → `GroupResponse[]` = `{ id, name, permissions: string[], memberCount, isSystem, isEditable }`. `Admin`, `Customer` là nhóm hệ thống (`isSystem=true`, `isEditable=false`: chỉ xem, không sửa/xóa). Nhóm `Nhân viên` có sẵn nhưng sửa/xóa được như nhóm tự tạo. `memberCount` đếm đủ mọi thành viên (kể cả `Customer`); chỉ là `null` khi nhóm nằm trong `groups` của một user (`GET /users/{id}`, `PUT /users/{id}/groups`); `POST /staff` vẫn trả số đếm. Số đếm được cache 5 phút và xóa ngay khi thêm/gỡ thành viên qua API này; riêng `Customer` có thể chậm tối đa 5 phút vì khách tự đăng ký không đi qua identity-service.
  - `POST /api/v1/admin/groups` body `GroupRequest` = `{ name (bắt buộc, ≤ 100 ký tự), permissions: string[] (bắt buộc) }` → `GroupChangeResponse` = `{ group: GroupResponse, notLoggedOutUserIds: string[] }`. Tên được trim; trùng nhóm khác hoặc `Admin`/`Customer` (không phân biệt hoa thường) → `2031`. `permissions` chứa quyền lạ hoặc `group:manage` → `2033`.
  - `PUT /api/v1/admin/groups/{id}` cùng body, cùng response. Đổi quyền thì mọi thành viên bị đăng xuất; chỉ đổi tên thì không.
  - `DELETE /api/v1/admin/groups/{id}` → `data` là `string[]` = `notLoggedOutUserIds` (mọi thành viên bị đăng xuất).
  - `GET /api/v1/admin/groups/{id}/members?keyword=&page=&size=` (mặc định `size=20`; `keyword` tuỳ chọn, khớp một phần email / username / họ tên, không phân biệt hoa thường) → `PageResponse` của `GroupMemberResponse` = `{ userId, email, fullName, isActive }`. Lấy từ identity DB, người chưa từng đăng nhập lấy từ Keycloak nên thường không còn `null`.
  - `GET /api/v1/admin/groups/{id}/candidates?keyword=` → `GroupMemberResponse[]`: tối đa 20 người trong Keycloak (kể cả chưa từng đăng nhập) khớp `keyword`, đã bỏ người đang thuộc nhóm. `keyword` rỗng trả `[]`. Chỉ cần `group:manage` (không cần `customer:read`). Nhóm `Customer` → `2036`.
  - `POST /api/v1/admin/groups/{id}/members` và `DELETE /api/v1/admin/groups/{id}/members`, body `GroupMembersRequest` = `{ userIds: string[] }` (1–100 id) → `GroupMembersChangeResponse` = `{ succeededUserIds: string[], failures: { userId, code, message }[], notLoggedOutUserIds: string[] }`. Kết quả theo từng người, một người lỗi không chặn người khác: UI phải hiện cả phần thành công lẫn từng lỗi. Người đã trong nhóm (khi thêm) / không có trong nhóm (khi gỡ) tính thành công, không bị đăng xuất. Nhóm `Customer` bị từ chối cả request (`2036`, HTTP 400). Gỡ khỏi `Admin` có thể lỗi từng người: `2035` (tự rời), `2034` (còn một admin). `2001`: người dùng không có trong Keycloak.
  - `PUT /api/v1/admin/users/{userId}/groups` body `UpdateUserGroupsRequest` = `{ groupIds: string[] }` (bắt buộc; đặt lại toàn bộ nhóm) → `UserGroupsResponse` = `{ groups: GroupResponse[], notLoggedOutUserIds: string[] }`. `Customer` luôn có và không chọn được: UI ẩn `Customer` khỏi danh sách chọn; gửi `Customer` bị `2036`; membership `Customer` hiện có được giữ (response vẫn có nhóm `Customer`). Gỡ thành viên cuối khỏi `Admin` → `2034`; admin tự gỡ mình khỏi `Admin` → `2035`. Thêm/gỡ người khác của `Admin` thì được. Admin duy nhất tự gỡ mình nhận `2035` (kiểm tra tự rời trước); gỡ người khác khi `Admin` chỉ còn một thành viên nhận `2034`.
  - `POST /api/v1/admin/staff` (HTTP **201**) body `CreateStaffRequest` = `{ email (hợp lệ), firstName, lastName, temporaryPassword, groupIds (≥ 1, không chứa Customer) }`, tất cả bắt buộc → `StaffResponse` = `{ id, email, fullName, groups: GroupResponse[] }` (không bao giờ trả mật khẩu; `fullName` = "firstName lastName"). Nhân viên bị bắt đổi mật khẩu ở lần đăng nhập đầu, email coi như đã xác minh, vẫn thuộc `Customer` (mua hàng được). Form dùng hai ô họ và tên riêng.
  - `GET /api/v1/admin/users/{id}` (cần `customer:read`) có thêm `groups: GroupResponse[]`. Keycloak không khả dụng hoặc user chưa có tài khoản Keycloak thì `groups` là `[]`, phần còn lại của chi tiết vẫn trả bình thường.
- **`notLoggedOutUserIds`.** Đổi nhóm/quyền có hiệu lực ngay vì backend đăng xuất người bị ảnh hưởng (họ phải đăng nhập lại). Id nào chưa đăng xuất được (Keycloak lỗi) nằm trong `notLoggedOutUserIds`; thay đổi vẫn được lưu. UI cảnh báo "N người chưa bị đăng xuất, quyền cũ còn hiệu lực tối đa vài phút" khi mảng không rỗng; mảng rỗng là bình thường. Nếu admin sửa nhóm chứa chính mình thì phiên của họ cũng bị đăng xuất.
- **Dashboard.** `GET /api/v1/admin/dashboard` (response giữ nguyên) giờ cần `revenue:read`. Mới: `GET /api/v1/admin/dashboard/operations?lowStockThreshold=10` cần `dashboard:read` → `{ totalOrders, orderByStatus, lowStockCount, topProducts }`, `topProducts[]` = `{ variantId, productName, totalQuantitySold }` theo số lượng, **không có doanh thu**. Tham số `days` (mặc định 30) hiện chưa được dùng. Gọi `/operations` khi user không có `revenue:read` và ẩn thẻ/biểu đồ doanh thu.
- **Evidence presign.** `POST /api/v1/files/evidence/payments/presign` giờ nhận `order:manage` hoặc `refund:approve` (trước đây cần `ROLE_ADMIN`), vì bằng chứng thu tiền COD / xác minh carrier là việc của nhân viên xử lý đơn.
- **Mã lỗi mới (identity-service, `2030`–`2039`).**

  | mã | HTTP | message |
  |---|---|---|
  | `2030` | 404 | Nhóm không tồn tại |
  | `2031` | 409 | Tên nhóm đã được dùng |
  | `2032` | 409 | Nhóm hệ thống không thể sửa hoặc xoá |
  | `2033` | 400 | Quyền không hợp lệ hoặc không được gán cho nhóm |
  | `2034` | 409 | Phải còn ít nhất một quản trị viên |
  | `2035` | 409 | Không thể tự rời nhóm Admin của chính mình |
  | `2036` | 400 | Không thể gán nhóm Customer cho nhân viên |
  | `2037` | 409 | Email đã được dùng cho một tài khoản khác |
  | `2038` | 400 | Keycloak từ chối thông tin tài khoản (mật khẩu không đạt chính sách hoặc dữ liệu không hợp lệ) |
  | `2039` | 400 | Nhân viên phải thuộc ít nhất một nhóm |

  Mã có sẵn liên quan: `2020` (409, `ACCOUNT_LINK_CONFLICT`) khi tạo nhân viên mà email còn thuộc một dòng identity cũ; `2001` (404) khi gán nhóm cho user chưa có tài khoản Keycloak.
- **Gợi ý ẩn UI theo quyền** (ẩn hẳn, không chỉ disable; backend vẫn trả 403 nếu gọi thẳng):

  | khu vực UI | hiện khi có | nút/hành động cần thêm |
  |---|---|---|
  | Dashboard | `dashboard:read` hoặc `revenue:read` | có `revenue:read` → `/dashboard`, ngược lại `/dashboard/operations` |
  | Đơn hàng | `order:read` | đổi trạng thái, hủy, retry, xác minh carrier: `order:manage`; tạo/đồng bộ vận đơn: `shipment:manage` |
  | Đổi trả | `return:read` | duyệt/từ chối/nhận/hủy: `return:manage`; hoàn tiền: `refund:approve` |
  | Sản phẩm, danh mục, thương hiệu, size, màu, thuộc tính | `product:read` | tạo/sửa/xóa: `product:manage` |
  | Tồn kho | `inventory:read` | điều chỉnh: `inventory:manage` |
  | Đánh giá | `review:read` | duyệt/ẩn/trả lời/xóa: `review:manage` |
  | Khuyến mãi | `promotion:read` | tạo/sửa/bật tắt: `promotion:manage` |
  | Khách hàng | `customer:read` | sửa/khóa: `customer:manage`; chọn nhóm: `group:manage` |
  | Thanh toán, ví | `payment:read` / `wallet:read` | |
  | Nhóm & phân quyền, Tạo nhân viên | `group:manage` | |
- **Việc cho Antigravity:** (1) màn "Nhóm & phân quyền": danh sách nhóm, tạo/sửa với checkbox quyền gom theo `area`, quyền `isAssignable=false` không tick được, tự tick quyền đọc kéo theo, nhóm `isEditable=false` chỉ xem; (2) chi tiết user: chọn nhóm (bỏ `Customer`); (3) form "Tạo nhân viên"; (4) ẩn menu/nút theo `roles` của `/bff/session`; (5) dashboard chọn endpoint theo `revenue:read`; (6) xử lý `notLoggedOutUserIds` và mã lỗi `2030`–`2039`.
- Trạng thái: done (backend), chờ frontend

### Gợi ý size theo chiều cao/cân nặng hoặc số đo cơ thể
- Người yêu cầu: Claude Code (kèm task UI cho Antigravity) · 2026-11-02
- Màn hình: apps/storefront (`ProductDetail`, cạnh `SizeGuide`)
- Đã có (catalog-service, công khai, không cần đăng nhập, không lưu số đo):
  - `GET /api/v1/products/{id}/size-recommendation` với query tùy chọn (cm/kg): `heightCm`, `weightKg`, `chestCm`, `waistCm`, `hipCm`, `shoulderCm`, `lengthCm`, `inseamCm`, `preference` = `SNUG | REGULAR | LOOSE` (mặc định `REGULAR`). Phải có ít nhất một giá trị, nếu không trả 400 `SIZE_RECOMMENDATION_INPUT_REQUIRED`. Giá trị ngoài khoảng hợp lệ trả 400.
  - `data.status`: `RECOMMENDED` hoặc `NOT_ENOUGH_DATA` (sản phẩm chưa có bảng size, hoặc dữ liệu nhập không đủ để so) → ẩn widget, không báo lỗi.
  - Khi `RECOMMENDED`: `recommendedSize`, `alternativeSize` (có thể không có), `recommendedSizeAvailable` (false = size gợi ý đang hết hàng), `basis` (`BODY_MEASUREMENTS` | `HEIGHT_WEIGHT`), `confidence` (`HIGH` | `MEDIUM` | `LOW`), `regions[]` = `{region, fit, differenceCm}` (chỉ có khi `BODY_MEASUREMENTS`; `differenceCm` = số đo của size trừ số đo của người, âm là chật).
  - Màu heatmap theo `fit`: `GOOD` xanh lá, `SNUG` vàng, `TIGHT` đỏ, `LOOSE` xám.
- Task UI chi tiết cho Antigravity: `docs/storefront-size-finder-tasks.md` (F1–F6).
- Gợi ý UI: form tối thiểu chiều cao + cân nặng, mục "Chính xác hơn" mở thêm số đo và ưu tiên form; nhớ số đo trong phiên; `confidence = LOW` hiển thị là gợi ý tham khảo. Đây là gợi ý theo bảng size, không phải kết quả của try-on.
- Trạng thái: done

### Tối ưu NFR storefront: kiểm tra wishlist theo lô, cache ảnh, cache danh mục
- Người yêu cầu: Claude Code (kèm task UI cho Antigravity) · 2026-11-01
- Màn hình: apps/storefront (StoreUI `ProductCard`, Products, Home)
- Đã có (catalog-service, xem Swagger "Wishlist"):
  - `GET /api/v1/wishlist/check?productIds=a&productIds=b` → `data: string[]` là các `productId` đang trong wishlist của người dùng (tối đa 50 id; quá 50 trả lỗi `INVALID_SEARCH_CRITERIA`; chưa đăng nhập trả `[]`). Endpoint đơn lẻ `/check/{productId}` giữ nguyên.
  - `GET /api/v1/files/{id}/content` (302) thêm `Cache-Control: max-age=300, private`.
  - `GET /api/v1/category` được cache Redis 60 giây, tự xóa khi tạo/sửa/xóa danh mục; `productCount` có thể trễ tối đa 60 giây.
- Trạng thái: done

### Mã giảm giá hai loại (sản phẩm + vận chuyển)
- Người yêu cầu: Claude Code (kèm task UI cho Antigravity) · 2026-11-01
- Màn hình: apps/storefront (Checkout, chi tiết đơn, Home), apps/backoffice (views/promotions)
- Đã có (order-service, xem Swagger "Customer - Promotions", "Checkout", "Admin - Promotions"):
  - `POST /api/v1/checkouts`, `PUT /api/v1/checkouts/{id}` nhận thêm `shippingCouponCode`; response thêm `shippingCouponCode`, `shippingDiscountAmount`. `shippingFee` là phí thật, `totalAmount` đã trừ cả hai khoản giảm. Hết freeship tự động cho đơn từ 500.000.
  - `OrderResponse` thêm `shippingDiscountAmount`.
  - `GET /api/v1/promotions/validate?code=` tính trên giỏ active (bỏ qua `subtotal`), chỉ cho mã sản phẩm.
  - `GET /api/v1/promotions/available?target=PRODUCT|SHIPPING` → `{ code, description, target, type, value, maxDiscount, minOrderValue, endAt, scopeType, eligible, reasonCode, reason, estimatedDiscount }`.
  - Admin promotion request/response thêm `target`, `description`, `isPublic`; response thêm `reservedCount`, `confirmedCount`. Không đổi được `code`/`target` khi đã có lượt dùng (`4016`).
  - Mã lỗi mới: `4037` (409) khuyến mãi đổi sau khi tạo checkout, tải lại checkout; `4038` (400) mã sai loại. Mã `4010`–`4014` giữ nguyên.
- Trạng thái: done

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
