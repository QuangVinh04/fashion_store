# Phân quyền theo nhóm — Frontend (backoffice) Implementation Plan

> **Người thực hiện:** Antigravity (sở hữu `apps/backoffice/**`). Steps dùng checkbox (`- [ ]`). Mỗi task kết thúc bằng workflow `.agents/workflows/fe-check.md`.

**Goal:** Backoffice hiển thị và chặn theo quyền thay vì `ROLE_ADMIN`, và có màn quản lý nhóm, gán nhóm cho user, tạo tài khoản nhân viên.

**Architecture:** Danh sách authority lấy từ `/bff/session` (`session.roles`, đã gồm quyền như `order:read`). Một file cấu hình map route → quyền, dùng chung cho menu và route guard. Nút thao tác ẩn theo quyền qua `useAuth().hasPermission`. API nhóm/nhân viên gọi qua `src/libs/api/` như các module hiện có.

**Tech Stack:** Next.js 14 App Router, MUI 5, TanStack Query, Vitest + jsdom, pnpm.

**Spec:** `docs/superpowers/specs/2026-11-03-rbac-groups-design.md` · backend: `docs/superpowers/plans/2026-11-04-rbac-groups.md`

## Global Constraints

- Chỉ sửa `apps/backoffice/**`. Thiếu API/field thì thêm entry vào `docs/api-requests.md`, không sửa Java.
- Agent không commit. Cuối task: `pnpm lint`, `pnpm test`, screenshot qua `http://localhost:8084`, đề xuất commit `feat(backoffice): ...`.
- Không lưu token, không bỏ CSRF. Gọi API qua `src/libs/api/client.ts`.
- Không `any`; component > 250 dòng thì tách; không magic string — tên quyền luôn dùng hằng số trong `src/configs/permissions.ts`.
- Ẩn nút chỉ là UX. Backend vẫn là nơi chặn; mọi lỗi 403 phải hiện thông báo "Bạn không có quyền thực hiện thao tác này", không làm vỡ trang.
- Tên quyền (giữ nguyên chuỗi): `dashboard:read`, `revenue:read`, `order:read`, `order:manage`, `shipment:manage`, `return:read`, `return:manage`, `refund:approve`, `product:read`, `product:manage`, `inventory:read`, `inventory:manage`, `review:read`, `review:manage`, `promotion:read`, `promotion:manage`, `customer:read`, `customer:manage`, `payment:read`, `wallet:read`, `wallet:adjust`, `group:manage`.
- Nhóm hệ thống: `Admin`, `Customer` (`isEditable=false` từ API). Không cho chọn `Customer` khi gán nhóm hay tạo nhân viên.

## Phụ thuộc backend

| task frontend | cần backend task (plan backend) |
|---|---|
| 1–4 | 3 (BFF/gateway), 4–8 (service đổi sang quyền), 5 (dashboard `/operations`) |
| 5–7 | 9–11 (API nhóm, nhân viên) |

Task 1–4 làm trước được với session giả lập trong test; chạy thật cần backend đã xong.

## Review Focus

1. Nhân viên mở thẳng URL trang không có quyền (ví dụ gõ `/wallets`) → thấy màn "không có quyền" trong layout, không phải redirect vòng lặp hay trang trắng → test ở Task 2.
2. User đăng nhập được Keycloak nhưng không có quyền nào (khách hàng) → `/access-denied`, không lặp redirect login → test ở Task 1.
3. Dashboard của nhân viên không được gọi `GET /api/v1/admin/dashboard` (403) → test ở Task 4.
4. Đổi nhóm/quyền xong, response có `notLoggedOutUserIds` khác rỗng → hiện cảnh báo nêu số user chưa bị đăng xuất, không báo thành công trơn → test ở Task 6.
5. Admin đang sửa nhóm của chính mình bị backend từ chối (`CANNOT_LEAVE_OWN_ADMIN`, `LAST_ADMIN_REQUIRED`) → hiện message lỗi của backend trong dialog, giữ nguyên lựa chọn → test ở Task 7.

---

### Task 1: Quyền trong AuthContext

**Files:**
- Create: `src/configs/permissions.ts`
- Modify: `src/context/AuthContext.tsx`
- Test: `src/context/AuthContext.test.tsx`

**Interfaces:**
- Produces: `export const Permission = { DASHBOARD_READ: 'dashboard:read', ... GROUP_MANAGE: 'group:manage' } as const`; `export type PermissionName = (typeof Permission)[keyof typeof Permission]`; `export const BACKOFFICE_PERMISSIONS: readonly PermissionName[]`.
- Produces (`AuthContextType`): thay `isAdmin: boolean` bằng `canAccessBackoffice: boolean` (có ít nhất một quyền trong `BACKOFFICE_PERMISSIONS`), thêm `hasPermission: (permission: PermissionName) => boolean`, `hasAnyPermission: (permissions: readonly PermissionName[]) => boolean`.

- [ ] **Step 1:** Test: session `roles: ['ROLE_USER', 'order:read']` → `canAccessBackoffice=true`, `hasPermission('order:read')=true`, `hasPermission('refund:approve')=false`; `roles: ['ROLE_ADMIN', 'ROLE_USER']` (không quyền) → `canAccessBackoffice=false` và redirect `/access-denied`; `roles: ['internal-caller']` → `false`.
- [ ] **Step 2:** `pnpm test src/context/AuthContext.test.tsx` → FAIL.
- [ ] **Step 3:** Implement; effect redirect dùng `canAccessBackoffice` thay `isAdmin`. Grep `isAdmin` trong `src/` và đổi mọi chỗ dùng.
- [ ] **Step 4:** PASS + `pnpm lint`.

### Task 2: Menu và route guard theo quyền

**Files:**
- Create: `src/configs/routePermissions.ts`
- Create: `src/components/backoffice/RequirePermission.tsx`
- Modify: `src/components/layout/vertical/VerticalMenu.tsx`
- Modify: `src/app/(dashboard)/layout.tsx` (bọc children bằng guard theo pathname)
- Test: `src/configs/routePermissions.test.ts`, `src/components/backoffice/RequirePermission.test.tsx`

**Interfaces:**
- Produces: `export const ROUTE_PERMISSIONS: ReadonlyArray<{ prefix: string; anyOf: readonly PermissionName[] }>` và `requiredPermissionsFor(pathname: string): readonly PermissionName[] | null` (khớp prefix dài nhất; `null` = chỉ cần `canAccessBackoffice`).
- Bảng map:

| route | anyOf |
|---|---|
| `/` | `dashboard:read`, `revenue:read` |
| `/orders` | `order:read` |
| `/returns` | `return:read` |
| `/wallets` | `wallet:read` |
| `/promotions` | `promotion:read` |
| `/products`, `/categories`, `/sizes`, `/brands`, `/colors`, `/attributes`, `/size-charts`, `/media` | `product:read` |
| `/inventory` | `inventory:read` |
| `/customers` | `customer:read` |
| `/reviews` | `review:read` |
| `/groups` (Task 6) | `group:manage` |

- `RequirePermission({ anyOf, children })`: thiếu quyền → render `PageHeader` + thông báo "Bạn không có quyền xem trang này" (dùng component sẵn có trong `src/components/backoffice/`), không redirect.

- [ ] **Step 1:** Test `requiredPermissionsFor('/orders/abc') = ['order:read']`, `('/') = ['dashboard:read','revenue:read']`; `RequirePermission` thiếu quyền → có text "không có quyền", đủ quyền → render children.
- [ ] **Step 2:** `pnpm test` → FAIL.
- [ ] **Step 3:** Implement. `VerticalMenu`: mỗi `MenuItem` hiện khi `hasAnyPermission(requiredPermissionsFor(href))`; `SubMenu`/`MenuSection` không còn item nào thì ẩn. Thêm section "Quản trị" với item "Nhóm & phân quyền" `/groups` (route tạo ở Task 6).
- [ ] **Step 4:** PASS. Screenshot menu với tài khoản admin và tài khoản nhân viên (nhóm `Nhân viên`).

### Task 3: Ẩn thao tác theo quyền trong các màn hiện có

**Files (Modify):** các view dưới `src/views/` có nút thao tác: `orders/*` (cập nhật trạng thái, hủy, tạo vận đơn, đồng bộ GHN, `OrderPaymentPanel`), `returns/*` (duyệt, từ chối, nhận hàng, hoàn tiền), `promotions/*`, `catalog/*`, `inventory/*`, `reviews/*`, `customers/*`, `wallets/*`.
- Test: mở rộng `src/views/orders/order-actions.test.ts`, `src/views/returns/return-actions.test.ts` (hoặc test component tương ứng).

Quy tắc:

| thao tác | quyền |
|---|---|
| cập nhật trạng thái, hủy đơn, retry saga | `order:manage` |
| tạo vận đơn, đồng bộ GHN, xác minh với hãng vận chuyển | `shipment:manage` |
| `OrderPaymentPanel` (đọc payment/refund) | `payment:read`; thiếu thì ẩn cả panel |
| duyệt/từ chối/nhận/hủy/tạo lại pickup/mở yêu cầu đổi trả | `return:manage` |
| hoàn tiền, upload bằng chứng thanh toán | `refund:approve` |
| tạo/sửa/bật tắt khuyến mãi | `promotion:manage` |
| tạo/sửa/xóa/publish sản phẩm và dữ liệu danh mục | `product:manage` |
| nhập kho, sửa ngưỡng | `inventory:manage` |
| đổi trạng thái/trả lời/xóa review | `review:manage` |
| sửa, khóa/mở khóa khách hàng | `customer:manage` |

Nếu `order-actions.ts` / `return-actions.ts` đã tính danh sách thao tác khả dụng theo trạng thái, thêm tham số quyền vào chính hàm đó thay vì rải `hasPermission` trong JSX.

- [ ] **Step 1:** Test: đơn ở trạng thái cho phép hủy, không có `order:manage` → không có action hủy; return chờ hoàn tiền, có `return:manage` nhưng không `refund:approve` → không có action hoàn tiền.
- [ ] **Step 2:** `pnpm test` → FAIL.
- [ ] **Step 3:** Implement theo bảng.
- [ ] **Step 4:** PASS. Screenshot chi tiết đơn và chi tiết return với tài khoản nhân viên.

### Task 4: Dashboard theo quyền

**Files:**
- Modify: `src/libs/api/dashboard.ts`, `src/hooks/useDashboard.ts`, `src/types/dashboard.ts`, `src/app/(dashboard)/page.tsx`
- Test: `src/hooks/useDashboard.test.tsx`

**Interfaces:**
- Consumes (backend): `GET /api/v1/admin/dashboard/operations?days&lowStockThreshold` → `{ totalOrders, orderByStatus, lowStockCount, topProducts: { variantId, productName, totalQuantitySold }[] }`.
- Produces: `dashboardApi.getOperations(days, lowStockThreshold)`; type `OperationsDashboardData`; `useDashboard(days, lowStockThreshold)` tự chọn endpoint: có `revenue:read` → `getStats`, ngược lại → `getOperations`; trả thêm `hasRevenue: boolean`.

- [ ] **Step 1:** Test: không có `revenue:read` → chỉ gọi `getOperations`, không gọi `getStats`; có → gọi `getStats`.
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** Implement. Trang dashboard: `hasRevenue=false` → ẩn `SalesAndOrdersMixedChart`, ô doanh thu trong `KpiOverview`, cột doanh thu trong `TopSellingProducts`; số yêu cầu đổi trả (`returnsApi`) chỉ gọi khi có `return:read`.
- [ ] **Step 4:** PASS. Screenshot dashboard admin vs nhân viên.

### Task 5: Lớp API nhóm và nhân viên

**Files:**
- Create: `src/types/group.ts`, `src/libs/api/groups.ts`, `src/libs/api/staff.ts`, `src/hooks/useGroups.ts`
- Modify: `src/types/customer.ts` (`AdminUserDetail.groups: GroupSummary[]`)
- Test: `src/libs/api/groups.test.ts`

**Interfaces (khớp DTO backend Task 10–11):**
- `PermissionItem { authority: PermissionName; area: string; label: string; isAssignable: boolean }`
- `Group { id: string; name: string; permissions: PermissionName[]; memberCount: number | null; isSystem: boolean; isEditable: boolean }`
- `GroupChange { group: Group; notLoggedOutUserIds: string[] }`, `UserGroups { groups: Group[]; notLoggedOutUserIds: string[] }`, `GroupMember { userId: string; email: string | null; fullName: string | null }`
- `CreateStaffRequest { email; firstName; lastName; temporaryPassword; groupIds: string[] }`, `Staff { id; email; fullName; groups: Group[] }`
- `groupsApi`: `listPermissions()` `GET /api/v1/admin/permissions`; `list()` `GET /api/v1/admin/groups`; `create(body)` `POST`; `update(id, body)` `PUT /groups/{id}`; `remove(id)` `DELETE` → `string[]`; `members(id, page, size)` `GET /groups/{id}/members`; `setUserGroups(userId, groupIds)` `PUT /api/v1/admin/users/{userId}/groups`. `staffApi.create(body)` `POST /api/v1/admin/staff`.
- `useGroups`: query keys `['admin-groups']`, `['admin-permissions']`, `['admin-group-members', id, page]`; mutation invalidate `['admin-groups']` và `['customers', ...]` chi tiết user liên quan.

- [ ] **Step 1:** Test URL + method + body của từng hàm (mock `api` như các `*.test.ts` hiện có trong `src/libs/api/`).
- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS.

### Task 6: Màn "Nhóm & phân quyền"

**Files:**
- Create: `src/app/(dashboard)/groups/page.tsx`, `src/views/groups/GroupList.tsx`, `src/views/groups/GroupFormDialog.tsx`, `src/views/groups/PermissionChecklist.tsx`, `src/views/groups/GroupMembersDialog.tsx`, `src/views/groups/group-form.ts`
- Test: `src/views/groups/group-form.test.ts`, `src/views/groups/PermissionChecklist.test.tsx`

**Interfaces:**
- `group-form.ts`: `groupPermissionsByArea(items: PermissionItem[]): { area: string; items: PermissionItem[] }[]` (giữ thứ tự API); `toggleArea(selected: Set<PermissionName>, area: PermissionItem[], checked: boolean): Set<PermissionName>`; `changeWarning(change: { notLoggedOutUserIds: string[] }): string | null`.

Hành vi:
- Bảng: tên, số quyền, số thành viên, nhãn "Hệ thống" khi `isSystem`. Nhóm `isEditable=false`: chỉ nút "Xem", dialog ở chế độ đọc.
- Dialog tạo/sửa: tên + checklist gom theo khu vực, checkbox khu vực chọn/bỏ cả nhóm, quyền `isAssignable=false` không hiện. Cảnh báo dưới form: "Thành viên của nhóm sẽ bị đăng xuất để nhận quyền mới" khi sửa quyền nhóm đã có thành viên.
- Xóa: `ConfirmActionDialog` sẵn có, nêu số thành viên sẽ bị đăng xuất.
- Sau mutation: `changeWarning` khác null → toast cảnh báo "N người dùng chưa bị đăng xuất, quyền mới có hiệu lực trong tối đa 5 phút"; null → toast thành công. Lỗi backend (tên trùng 409…) → hiện message trong dialog.
- Nút "Tạo nhân viên" trên page header mở dialog Task 7.

- [ ] **Step 1:** Test `groupPermissionsByArea`, `toggleArea`, `changeWarning(['a','b'])` chứa "2"; `PermissionChecklist` không render `group:manage`.
- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS. Screenshot list, dialog sửa nhóm `Nhân viên`, dialog xem nhóm `Admin`.

### Task 7: Gán nhóm cho user và tạo nhân viên

**Files:**
- Create: `src/views/groups/UserGroupsEditor.tsx`, `src/views/groups/CreateStaffDialog.tsx`, `src/views/groups/staff-form.ts`
- Modify: `src/views/customers/CustomerDetailDialog.tsx` (thêm mục "Nhóm")
- Test: `src/views/groups/staff-form.test.ts`, `src/views/groups/UserGroupsEditor.test.tsx`

**Interfaces:**
- `staff-form.ts`: `validateStaffForm(form): Partial<Record<keyof CreateStaffRequest, string>>` — email hợp lệ, họ, tên, mật khẩu tạm không rỗng, ít nhất một nhóm. Lỗi field từ backend map bằng `mapFieldErrors` sẵn có.
- `UserGroupsEditor({ userId, groups })`: multi-select nhóm (loại `Customer`), chỉ hiện khi có `group:manage`; người chỉ có `customer:read` thấy danh sách nhóm dạng chip chỉ đọc.

Hành vi:
- Lưu nhóm → `setUserGroups`; lỗi 409 (`CANNOT_LEAVE_OWN_ADMIN`, `LAST_ADMIN_REQUIRED`) → hiện message backend, giữ lựa chọn đang sửa.
- Tạo nhân viên thành công → toast kèm nhắc "Gửi email và mật khẩu tạm cho nhân viên; lần đầu đăng nhập họ phải đổi mật khẩu", đóng dialog, invalidate danh sách nhóm.

- [ ] **Step 1:** Test `validateStaffForm` (thiếu nhóm → lỗi `groupIds`); `UserGroupsEditor` không có `Customer` trong option, giữ lựa chọn khi API trả 409.
- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS.
- [ ] **Step 5: Chạy thật end-to-end:** admin tạo nhóm "Nhân viên kho" (`inventory:read`, `inventory:manage`), tạo nhân viên vào nhóm đó, đăng nhập bằng nhân viên ở cửa sổ ẩn danh → menu chỉ có "Kho hàng", mở `/orders` thấy màn "không có quyền". Screenshot từng bước.
