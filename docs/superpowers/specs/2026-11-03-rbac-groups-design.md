# Phân quyền theo nhóm (Admin / Nhân viên / Customer)

Ngày: 2026-11-03 · Trạng thái: đã triển khai (backend)

## Vì sao

Hiện backoffice chỉ có một role `ADMIN`: ai vào được backoffice là làm được mọi thứ, kể cả hoàn tiền, điều
chỉnh ví, xem doanh thu. Chủ shop muốn cho nhân viên vào làm việc vận hành mà không đụng tới tiền và phân
quyền, và muốn tự tạo thêm nhóm (ví dụ "Nhân viên kho") bằng cách tick quyền trên backoffice — không phải sửa
code hay vào Keycloak console.

## Mục tiêu

- Danh mục **quyền cố định do code định nghĩa** (bảng dưới). Code chỉ kiểm tra quyền, không kiểm tra tên role/nhóm.
- Admin tạo / sửa / xóa **nhóm** trên backoffice, tick quyền cho nhóm, gán user vào một hoặc nhiều nhóm.
  User nhận hợp quyền của mọi nhóm mình thuộc.
- Ba nhóm mặc định `Admin`, `Nhân viên`, `Customer` có sẵn khi realm được import lần đầu; Keycloak đã import từ
  trước thì có sau khi chạy script đồng bộ.
- Admin tạo tài khoản nhân viên ngay trên backoffice.
- Ai có ít nhất một quyền backoffice thì đăng nhập được backoffice; không có thì bị chặn ở BFF như hiện nay.

## Không làm

- Admin không tạo quyền mới; quyền mới chỉ thêm qua code khi có tính năng mới.
- Không phân quyền theo dữ liệu (ví dụ nhân viên chỉ thấy đơn của kho mình).
- Không dùng / không xóa bảng cũ `roles`, `permissions`, `role_permissions`, `user_roles` trong identity-service.
- Không đổi luồng storefront: khách hàng vẫn dùng realm role `USER` như hiện nay.
- UI backoffice (màn quản lý nhóm, gán nhóm cho user, ẩn menu theo quyền) do Antigravity làm; tài liệu này chỉ
  định nghĩa API.

## Anti-goal (coi là hỏng dù chạy được)

- Admin tự khóa mình ra ngoài: nhóm `Admin` bị sửa/xóa, hoặc user cuối cùng bị gỡ khỏi `Admin`.
- Nhân viên bị gỡ quyền nhưng vẫn thao tác được lâu sau đó.
- Còn sót endpoint quản trị kiểm tra `ROLE_ADMIN`, khiến nhóm tự tạo có quyền nhưng vẫn bị 403 (hoặc ngược lại,
  endpoint tiền mở cho nhân viên).

## Danh mục quyền

Mỗi quyền là một **client role của client `fashion-api`** trong Keycloak (giống `internal-caller`), nên nằm sẵn
trong access token ở `resource_access.fashion-api.roles` và `KeycloakJwtAuthoritiesConverter` đã map thành
authority nguyên tên. Service kiểm tra bằng `hasAuthority('order:manage')`.

| khu vực | quyền | Admin | Nhân viên |
|---|---|---|---|
| Dashboard | `dashboard:read` — số đơn theo trạng thái, tổng đơn, hàng sắp hết, top sản phẩm theo số lượng | ✓ | ✓ |
| | `revenue:read` — doanh thu (quyền tiền) | ✓ | |
| Đơn hàng | `order:read` · `order:manage` (cập nhật trạng thái, hủy) | ✓ | ✓ |
| Vận chuyển | `shipment:manage` (tạo vận đơn GHN, đồng bộ) | ✓ | ✓ |
| Đổi trả | `return:read` · `return:manage` | ✓ | ✓ |
| | `refund:approve` (quyền tiền) | ✓ | |
| Sản phẩm | `product:read` · `product:manage` (thay `product:write` cũ) | ✓ | ✓ |
| Tồn kho | `inventory:read` · `inventory:manage` | ✓ | ✓ |
| Review | `review:read` · `review:manage` | ✓ | ✓ |
| Khuyến mãi | `promotion:read` | ✓ | ✓ |
| | `promotion:manage` | ✓ | |
| Khách hàng | `customer:read` | ✓ | ✓ |
| | `customer:manage` (sửa, khóa tài khoản) | ✓ | |
| Payment, ví | `payment:read` · `wallet:read` | ✓ | ✓ |
| | `wallet:adjust` (nạp / điều chỉnh ví — quyền tiền) | ✓ | |
| Phân quyền | `group:manage` (tạo/sửa nhóm, gán user) | ✓ | |

Người dùng đã chốt: mức chia "xem / thao tác" mỗi khu vực, riêng quyền tiền tách lẻ; nhóm Nhân viên đúng như cột trên.

Mỗi quyền có nhãn tiếng Việt và tên khu vực để UI hiển thị danh sách tick theo nhóm.

### Quyền ghi kéo theo quyền đọc

Service gọi method đọc lồng trong luồng ghi (ví dụ cập nhật đơn rồi `getOrderById`), nên nhóm chỉ có quyền ghi sẽ
bị 403 sau khi thao tác đã commit. `GroupPermissionPolicy` (identity-service) kiểm tra quyền trên tập được yêu cầu
rồi tự thêm quyền đọc tương ứng; `GroupResponse` / `GroupChangeResponse` trả tập đã chuẩn hóa. Đọc không kéo theo ghi.

| quyền ghi | kéo theo |
|---|---|
| `order:manage`, `shipment:manage` | `order:read` |
| `return:manage`, `refund:approve` | `return:read` |
| `product:manage` | `product:read` |
| `inventory:manage` | `inventory:read` |
| `review:manage` | `review:read` |
| `promotion:manage` | `promotion:read` |
| `customer:manage` | `customer:read` |
| `wallet:adjust` | `wallet:read` |
| `revenue:read` | `dashboard:read` |

## Nhóm mặc định

| nhóm | quyền | sửa/xóa được | ghi chú |
|---|---|---|---|
| `Admin` | luôn đủ mọi quyền trong danh mục | không | không gỡ được thành viên cuối cùng |
| `Nhân viên` | cột "Nhân viên" ở trên | có, như nhóm tự tạo | |
| `Customer` | không quyền backoffice; realm role `USER` | không | default group của realm: user đăng ký mới tự vào |

Nhóm hệ thống nhận diện **theo tên** (`Admin`, `Customer`, `SystemGroups` trong identity-service), không đọc
group attribute (`system=true` vẫn có trong realm file nhưng code không dùng); `isSystem` / `isEditable` của API
tính từ tên. Tên `Admin` / `Customer` bị giữ chỗ (không phân biệt hoa thường) nên nhóm tự tạo không trùng được. `Nhân viên` không phải nhóm hệ thống: sửa, xóa, đổi tên được.

## Cách làm

### Keycloak là nơi lưu (quyết định của Claude, người dùng đã nghe đề xuất)

- Quyền = client role của `fashion-api`. Nhóm = Keycloak **Group** (top-level) có client-role mapping tới các
  quyền. Thành viên nhóm = group membership của Keycloak user. Không thêm bảng nào vào identity_database.
- identity-service là cổng duy nhất thao tác: mở rộng `KeycloakAdminFeignClient` / `KeycloakAdminClient` hiện có
  (cùng circuit breaker/retry `keycloakAdmin`) với các API groups, group role-mappings, group members,
  user groups, client roles, default groups.

### Khởi tạo quyền và nhóm mặc định — realm file + script (người dùng chọn)

identity-service **không** được cấp `manage-clients` / `manage-realm`. Thay vào đó:

- **`realm-fashion-store.json`** (realm import mới) khai báo: mọi client role quyền của `fashion-api`; ba nhóm
  `Admin`, `Nhân viên`, `Customer` với mapping như bảng trên (nhóm hệ thống đánh dấu bằng group attribute, ví dụ
  `system=true`); `defaultGroups: ["/Customer"]`; `admin@fashion.local` thuộc `Admin`. Bỏ client role
  `product:write` và gán `product:write` của admin. Realm role `ADMIN` giữ trong realm nhưng code không kiểm tra nữa.
- **`scripts/keycloak/sync-rbac.ps1`** cho Keycloak đã import từ trước (và chạy lại mỗi khi code thêm quyền):
  đọc danh sách quyền và nhóm từ chính `realm-fashion-store.json`, chạy `kcadm.sh` trong container keycloak
  (`docker compose exec`), idempotent:
  1. tạo client role còn thiếu, không xóa role thừa;
  2. chưa có `Admin` → tạo `Admin` + `Nhân viên`, đưa mọi user có realm role `ADMIN` vào `Admin`
     (`Nhân viên` chỉ tạo cùng `Admin`, nên admin đã xóa thì không tạo lại);
  3. luôn đồng bộ `Admin` về đủ mọi quyền;
  4. chưa có `Customer` → tạo, mapping realm role `USER`, đặt làm default group; không backfill user cũ.
  Ghi cách chạy vào `platform/keycloak/README.md`.
- **Một nguồn sự thật cho danh sách quyền:** enum `Permission` trong code và client role trong realm file phải
  khớp. Thêm test (common-library hoặc identity-service) đọc realm file và fail nếu lệch, để không quên chạy script
  với quyền mới.

Service account `identity-service` chỉ cần thêm `view-clients` (đọc client role của `fashion-api` khi gán quyền
cho nhóm); đã có `manage-users` (đủ cho group, membership, tạo user, logout). Thêm vào realm file và README.

### Tạo tài khoản nhân viên (người dùng chọn: admin tạo trên backoffice)

`POST /api/v1/admin/staff` (yêu cầu `group:manage`, trả 201): `email`, `firstName`, `lastName`, `temporaryPassword`,
`groupIds` (không chứa `Customer`, ít nhất một nhóm). Tách họ/tên vì Keycloak 26 (user profile) bắt buộc cả hai.

- identity-service tạo Keycloak user qua Admin API (`POST /users`): `enabled=true`, `emailVerified=true`
  (admin tạo nên không cần xác minh), credential mật khẩu tạm `temporary=true` → lần đầu đăng nhập Keycloak bắt
  đổi mật khẩu. Sau đó gán nhóm và thêm `Customer`.
- Tạo luôn dòng `users` trong identity_database với id = Keycloak id, để nhân viên hiện trong danh sách user
  trước khi đăng nhập lần đầu (dùng lại logic JIT-provision hiện có, không viết lại).
- Email đã tồn tại trong Keycloak → lỗi 409 với error code mới. Gán nhóm lỗi sau khi đã tạo user → xóa user vừa tạo
  để không để lại tài khoản dở dang.
- Mật khẩu tạm phải đạt password policy của realm; Keycloak từ chối thì trả lỗi 400 kèm thông báo.

Quyết định của Claude: dùng mật khẩu tạm thay vì email mời, vì không phụ thuộc SMTP (dev chưa có SMTP). Email mời
có thể làm sau.

### Danh mục quyền trong code

Hằng số tên quyền dùng chung cho mọi service và hai BFF → đặt ở common-library, ví dụ
`com.fashionstore.common.security.Permission` (enum: tên wire, khu vực, nhãn tiếng Việt). Đây là hằng số
authorization, không phải domain logic. `@PreAuthorize` tham chiếu hằng số này, không viết chuỗi lẻ.

### Quyền có hiệu lực ngay

Access token sống 5 phút và session BFF giữ authority từ lúc login, nên đổi quyền xong không tự có hiệu lực ngay.
identity-service gọi `KeycloakAdminClient.logout(userId)` (đã có, bắn back-channel logout sang BFF):

- thêm/gỡ user khỏi nhóm → logout user đó;
- đổi quyền của nhóm hoặc xóa nhóm → logout mọi thành viên nhóm.

User phải đăng nhập lại và nhận quyền mới ngay. Chấp nhận được vì chỉ ảnh hưởng nhân viên backoffice.

### API mới (identity-service, layer-first, `/api/v1/admin/...`)

Mọi endpoint yêu cầu `group:manage`.

| method | path | việc |
|---|---|---|
| GET | `/api/v1/admin/permissions` | danh mục quyền (tên, khu vực, nhãn) |
| GET | `/api/v1/admin/groups` | danh sách nhóm: id, tên, quyền, số thành viên, `isSystem`, `isEditable` |
| POST | `/api/v1/admin/groups` | tạo nhóm: tên + danh sách quyền |
| PUT | `/api/v1/admin/groups/{id}` | đổi tên / quyền (từ chối với `Admin`, `Customer`) |
| DELETE | `/api/v1/admin/groups/{id}` | xóa (từ chối với `Admin`, `Customer`) |
| GET | `/api/v1/admin/groups/{id}/members` | thành viên (phân trang) |
| PUT | `/api/v1/admin/users/{userId}/groups` | đặt lại toàn bộ nhóm của user |

Ràng buộc: tên nhóm duy nhất; quyền phải nằm trong danh mục; không gỡ thành viên cuối cùng khỏi `Admin`;
admin không tự gỡ mình khỏi `Admin`. Thêm/gỡ thành viên khác của `Admin` thì được.

Quyết định của Claude:
- `group:manage` **không tick được** cho nhóm tự tạo, chỉ `Admin` có. Nếu không, người trong nhóm tự tạo có thể
  tự thêm mình vào `Admin`. API tạo/sửa nhóm từ chối `group:manage`; `GET /permissions` đánh dấu nó `isAssignable=false`.
- `Customer` không đặt/gỡ qua `PUT /users/{userId}/groups`: request chứa `Customer` bị từ chối, membership
  `Customer` hiện có được giữ nguyên khi đặt lại nhóm. Nhân viên vẫn ở `Customer` (vẫn mua hàng được).
- Logout bắt buộc thất bại một phần (Keycloak lỗi, circuit mở): thay đổi nhóm vẫn được giữ, log từng user chưa
  logout, response trả `notLoggedOutUserIds`. Chấp nhận được vì access token hết hạn sau 5 phút và lúc refresh
  token mới đã mang quyền mới, nên service tự chặn; chỉ menu trên backoffice còn hiển thị cũ đến khi hết session.
- `logout(userId)` hủy mọi phiên SSO, kể cả storefront. Chấp nhận: chỉ xảy ra khi đổi quyền nhân viên. Lỗi dùng `AppException` + error code mới trong `IdentityErrorCode`.
`GET /api/v1/admin/users/{id}` trả thêm danh sách nhóm của user (`groups`). Nếu Keycloak không khả dụng hoặc user
chưa có tài khoản Keycloak thì `groups` là rỗng và phần còn lại vẫn trả bình thường (chi tiết user không phụ thuộc
cứng vào Keycloak). Đã cập nhật Swagger.

### Đổi kiểm tra quyền ở các service

Thay mọi kiểm tra admin bằng quyền tương ứng. Danh sách đã tìm thấy (builder rà lại bằng grep `hasRole`,
`ROLE_ADMIN`, `isAdmin`):

- `SecurityConfig` theo path: order (`/api/v1/admin/**`), payment (`/api/v1/admin/payments/**`, `/wallets/**`),
  catalog (`GET /api/v1/inventory/**`, `anyRequest()`). Order và payment: rule theo path đổi thành "có ít nhất một
  quyền backoffice" (`hasAnyAuthority(Permission.authorities())`); quyền chi tiết nằm ở `@PreAuthorize` của từng method.
- **Catalog dùng rule theo path** (không `@PreAuthorize` cho sản phẩm/tồn kho), `@PreAuthorize` chỉ ở review/media.
  Path thật (không có `/api/v1`, trừ category và review): `GET` `/admin/{products,brands,color-options,size-options,
  product-attributes,size-charts}/**` → `product:read`, ghi các path đó và `/api/v1/category/**` → `product:manage`;
  `/admin/inventory/**` và `/api/v1/inventory/**` → `inventory:read` (GET) / `inventory:manage` (còn lại), trừ
  `POST /api/v1/inventory/check` chỉ cần đăng nhập; review ở `/api/v1/admin/reviews/**` (`review:read` / `review:manage`,
  `@PreAuthorize`); `anyRequest()` → có ít nhất một quyền backoffice.
- **Gateway và backoffice-bff** cùng dùng cổng "có ít nhất một quyền trong `Permission`" (`hasAnyAuthority`) thay cho
  `hasRole("ADMIN")`.
- `@PreAuthorize("hasRole('ADMIN')")`: `OrderServiceImpl`, `ReturnServiceImpl` (13 chỗ — phân loại read / manage /
  `refund:approve`), `OrderCancellationService`, `OrderSagaTimeoutService`, `ShipmentController`,
  `ShipmentSyncService`, `ShipmentServiceImpl`, `ReturnPickupService`, `AdminReturnController`,
  `AdminPromotionController`, `AdminDashboardServiceImpl`, `ProductServiceImpl`, `AdminReviewController`,
  `EvidenceUploadController`, `AdminUserServiceImpl`, `AdminPaymentController`, `AdminWalletController`,
  `AdminWalletTopUpController`.
- Kiểm tra lúc chạy: `MediaFileServiceImpl:322` (`ROLE_ADMIN`), `ReviewServiceImpl:258` (`isAdmin` → `review:manage`),
  `OrderCancellationSagaService` (`isAdmin` — "người hủy không phải chủ đơn" → cần `order:manage`).
- Chuỗi `"ADMIN"` dùng làm actor / `changedBy` trong lịch sử đơn (`OrderServiceImpl`, `OrderSagaTimeoutService`,
  `ShipmentCreationTransactionService`, `OrderReceiptService`) là nhãn audit, **không** phải kiểm tra quyền — giữ nguyên.

### Dashboard

- `GET /api/v1/admin/dashboard` giữ nguyên response, yêu cầu `revenue:read`.
- Thêm `GET /api/v1/admin/dashboard/operations` yêu cầu `dashboard:read`: `totalOrders`, `orderByStatus`,
  `lowStockCount`, `topProducts` (theo số lượng) không có `totalRevenue`. Dùng lại query của `AdminDashboardServiceImpl`.
  Tham số `days` được nhận (mặc định 30, `@Min(1)`) nhưng **chưa dùng**: số liệu vận hành không có cửa sổ thời gian.
- `POST /api/v1/files/evidence/payments/presign` nhận `order:manage` **hoặc** `refund:approve`: bằng chứng thu tiền COD
  và xác minh carrier là việc của nhân viên xử lý đơn, không chỉ người duyệt hoàn tiền.

### BFF

- backoffice-bff: `.anyExchange().hasRole("ADMIN")` → `.hasAnyAuthority(Permission.authorities())`: có ít nhất một
  quyền trong danh mục `Permission`. Gateway làm tương tự.
- `/bff/session` đã trả danh sách authority → frontend dùng để ẩn menu/nút; không cần đổi.

## Kiểm chứng

- Test enum `Permission` khớp client role trong `realm-fashion-store.json`.
- Script: chạy trên Keycloak dev đã import realm cũ → có đủ role + 3 nhóm, admin vào `Admin`; chạy lần hai không
  đổi gì; xóa `Nhân viên` rồi chạy lại thì không tạo lại.
- Unit test tạo nhân viên (Keycloak client mock): thành công; email trùng → 409; gán nhóm lỗi → user bị xóa.
- Unit test service nhóm: chặn sửa/xóa nhóm hệ thống, chặn gỡ admin cuối, logout đúng user khi đổi.
- Test security mỗi service: token chỉ có quyền Nhân viên → 403 ở refund, wallet adjust, promotion manage,
  customer manage, dashboard doanh thu; 200 ở order/return/product/inventory. Token không quyền → 403 ở `/admin/**`.
- `./scripts/test.ps1` xanh. Chạy thật: tạo nhóm "Nhân viên kho" chỉ có `inventory:*`, gán user, đăng nhập lại
  backoffice và thấy chỉ gọi được API tồn kho.

## Việc cho frontend (Antigravity) — thêm vào `docs/api-requests.md` khi API xong

- Màn "Nhóm & phân quyền": danh sách nhóm, tạo/sửa với checkbox quyền gom theo khu vực, nhóm hệ thống chỉ xem.
- Màn chi tiết user: chọn nhóm.
- Form "Tạo nhân viên": email, họ tên, mật khẩu tạm, chọn nhóm.
- Ẩn menu/nút theo authority trong `/bff/session`; dashboard gọi `/operations` khi không có `revenue:read`.

## Để builder quyết

Tên class/package trong identity-service (giữ layer-first), DTO dạng `record`, phân loại từng method của
`ReturnServiceImpl`/`OrderServiceImpl` vào read/manage/refund, cách script parse realm file, thứ tự triển khai.
