# Phân quyền theo nhóm — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thay role `ADMIN` duy nhất bằng danh mục quyền cố định + nhóm (Keycloak Group) do admin quản lý trên backoffice, có ba nhóm mặc định và chức năng tạo tài khoản nhân viên.

**Architecture:** Quyền = client role của `fashion-api` (đã nằm trong access token, `KeycloakJwtAuthoritiesConverter` map nguyên tên). Nhóm = Keycloak Group có client-role mapping. identity-service là cổng duy nhất gọi Keycloak Admin API để quản lý nhóm, membership và tạo nhân viên. Các service chỉ kiểm tra `hasAuthority(<quyền>)`.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Security (resource server + `@PreAuthorize`), OpenFeign + Resilience4j, Keycloak 26.7 Admin REST API, `kcadm.sh`, PowerShell 5.1.

**Spec:** `docs/superpowers/specs/2026-11-03-rbac-groups-design.md`

## Global Constraints

- Agent không commit. Cuối mỗi task: chạy test, đọc `git diff`, đề xuất commit message dạng `feat(scope): ...`.
- Maven từ bash: `export JAVA_HOME="C:/Program Files/Java/jdk-21"`, chạy `./mvnw -o ...`. Sau khi đổi package/annotation hàng loạt dùng `clean`.
- Không sửa `apps/**`. Việc frontend chỉ ghi vào `docs/api-requests.md`.
- identity-service giữ layer-first (`controller/ dto/ service/ service/impl/ client/ exception/`).
- DTO mới dùng `record`. Inject qua constructor. Không magic string: tên quyền luôn tham chiếu `PermissionNames`.
- Không đổi giá trị `INTERNAL_CALLER` hay bất cứ `EventTypes` nào.
- identity-service service account chỉ thêm `view-clients`. Không cấp `manage-clients`, `manage-realm`.
- Nhóm hệ thống nhận diện theo tên cố định: `Admin`, `Customer`. Nhóm mặc định sửa được: `Nhân viên`.
- Chuỗi `"ADMIN"` làm actor/`changedBy` trong lịch sử đơn là nhãn audit, giữ nguyên.

## Review Focus

1. Token service account (`internal-caller`) hoặc khách hàng (`ROLE_USER`) không được lọt rule "có ít nhất một quyền backoffice" ở gateway và BFF → test ở Task 3.
2. Tên nhóm trùng khác hoa/thường (`nhân viên` vs `Nhân viên`) hoặc cố đặt tên `Admin`/`Customer` cho nhóm tự tạo → 409 → test ở Task 10.
3. `PUT /users/{id}/groups` có một `groupId` không tồn tại → 404 và không áp dụng phần nào → test ở Task 10.
4. Tạo nhân viên với email chỉ tồn tại ở identity_database (user cũ chưa chuyển Keycloak) → 409 `ACCOUNT_LINK_CONFLICT` trước khi gọi Keycloak → test ở Task 11.
5. Script chạy trên PowerShell 5.1 phải giữ nguyên dấu tiếng Việt của tên nhóm `Nhân viên` (stdin mặc định không phải UTF-8) → bước kiểm chứng ở Task 2.

---

## File Structure

| file | trách nhiệm |
|---|---|
| `packages/common-library/.../security/PermissionNames.java` | hằng số tên quyền (dùng trong annotation) |
| `packages/common-library/.../security/Permission.java` | enum danh mục: tên, khu vực, nhãn, `isAssignable`, `staffDefault` |
| `platform/keycloak/realm-fashion-store.json` | client role quyền, 3 nhóm, default group, admin vào `Admin`, `view-clients` |
| `scripts/keycloak/sync-rbac.ps1` | đồng bộ Keycloak đã import từ trước |
| `identity/client/KeycloakGroupFeignClient.java` + `KeycloakGroupClient.java` | Admin API cho group, role mapping, membership, tạo/xóa user |
| `identity/service/GroupService.java` + `impl/GroupServiceImpl.java` | nghiệp vụ nhóm, ràng buộc nhóm hệ thống, logout bắt buộc |
| `identity/service/StaffAccountService.java` + `impl/StaffAccountServiceImpl.java` | tạo nhân viên |
| `identity/controller/AdminGroupController.java`, `AdminStaffController.java` | API mới |
| `identity/dto/group/*`, `identity/dto/staff/*` | record request/response |

---

### Task 1: Danh mục quyền trong common-library + client role trong realm

**Files:**
- Create: `packages/common-library/src/main/java/com/fashionstore/common/security/PermissionNames.java`
- Create: `packages/common-library/src/main/java/com/fashionstore/common/security/Permission.java`
- Modify: `platform/keycloak/realm-fashion-store.json` (`roles.client.fashion-api`)
- Test: `packages/common-library/src/test/java/com/fashionstore/common/security/PermissionTest.java`

**Interfaces:**
- Produces: `PermissionNames.DASHBOARD_READ = "dashboard:read"`, `REVENUE_READ`, `ORDER_READ`, `ORDER_MANAGE`, `SHIPMENT_MANAGE`, `RETURN_READ`, `RETURN_MANAGE`, `REFUND_APPROVE`, `PRODUCT_READ`, `PRODUCT_MANAGE`, `INVENTORY_READ`, `INVENTORY_MANAGE`, `REVIEW_READ`, `REVIEW_MANAGE`, `PROMOTION_READ`, `PROMOTION_MANAGE`, `CUSTOMER_READ`, `CUSTOMER_MANAGE`, `PAYMENT_READ`, `WALLET_READ`, `WALLET_ADJUST`, `GROUP_MANAGE` (giá trị đúng như bảng trong spec).
- Produces: `enum Permission { ...; String authority(); String area(); String label(); boolean isAssignable(); boolean isStaffDefault(); static String[] authorities(); static Optional<Permission> fromAuthority(String) }`. `area`/`label` tiếng Việt cho UI. `isAssignable=false` chỉ với `GROUP_MANAGE`. `isStaffDefault` theo cột "Nhân viên" của spec.

- [ ] **Step 1: Viết test fail** — `PermissionTest`:
  - `authoritiesMatchRealmClientRoles()`: đọc `../../platform/keycloak/realm-fashion-store.json` bằng Jackson, lấy tên các role trong `roles.client.fashion-api` trừ `internal-caller`, `assertThat(...).containsExactlyInAnyOrder(Permission.authorities())`.
  - `onlyGroupManageIsNotAssignable()`: các `Permission` có `isAssignable()==false` đúng bằng `[GROUP_MANAGE]`.
  - `staffDefaultExcludesMoneyAndAdministration()`: tập không phải `isStaffDefault` đúng bằng `REVENUE_READ, REFUND_APPROVE, WALLET_ADJUST, PROMOTION_MANAGE, CUSTOMER_MANAGE, GROUP_MANAGE`.
- [ ] **Step 2:** `./mvnw -o -pl packages/common-library test -Dtest=PermissionTest` → FAIL (class chưa có).
- [ ] **Step 3:** Tạo `PermissionNames` (final class, constructor private) và `Permission` enum. Trong realm file: xóa client role `product:write`, thêm 22 client role (giữ `internal-caller`), bỏ `product:write` khỏi `clientRoles` của `admin@fashion.local`.
- [ ] **Step 4:** Chạy lại test → PASS. `./mvnw -o -pl packages/common-library install -DskipTests` để các module khác thấy class mới.
- [ ] **Step 5:** Đề xuất commit: `feat(common): add backoffice permission catalog`.

### Task 2: Nhóm mặc định trong realm + script đồng bộ

**Files:**
- Modify: `platform/keycloak/realm-fashion-store.json`
- Create: `scripts/keycloak/sync-rbac.ps1`
- Modify: `platform/keycloak/README.md` (mục Realm contents, Dev users, thêm mục "Đồng bộ quyền và nhóm")

**Interfaces:**
- Produces (realm): `groups` gồm `Admin` (clientRoles `fashion-api`: mọi quyền), `Nhân viên` (quyền `isStaffDefault`), `Customer` (realmRoles `USER`); `defaultGroups: ["/Customer"]`; `admin@fashion.local.groups: ["/Admin"]`; service account `identity-service` thêm `realm-management: view-clients`.
- Produces (script): `.\scripts\keycloak\sync-rbac.ps1 [-AdminUser admin] [-AdminPassword ...]`, idempotent, đọc quyền/nhóm từ realm file.

- [ ] **Step 1:** Sửa realm file như trên. Thêm vào `PermissionTest` (Task 1) test `realmDefaultGroupsMatchCatalog()`: nhóm `Admin` có đúng `Permission.authorities()`, `Nhân viên` có đúng các quyền `isStaffDefault`, `defaultGroups == ["/Customer"]`. Chạy → PASS.
- [ ] **Step 2:** Viết script. Đầu script `$OutputEncoding = [System.Text.UTF8Encoding]::new($false)` và `[Console]::OutputEncoding` tương tự. Đăng nhập `kcadm.sh config credentials` vào realm `master` qua `docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh`. Các bước đúng thứ tự trong spec mục "Khởi tạo quyền và nhóm mặc định" (tạo client role thiếu; chưa có `Admin` → tạo `Admin` + `Nhân viên` và đưa user có realm role `ADMIN` vào `Admin`; luôn đồng bộ `Admin` đủ quyền; chưa có `Customer` → tạo, map `USER`, đặt default group). Thêm `view-clients` cho `service-account-identity-service`. Body JSON tạo nhóm đưa qua stdin (`-f -`), không qua tham số dòng lệnh.
- [ ] **Step 3: Kiểm chứng trên Keycloak dev đã import realm cũ:** `docker compose up -d keycloak`, chạy script → `kcadm.sh get groups -r fashion-store` liệt kê `Admin`, `Nhân viên` (đúng dấu), `Customer`; `admin@fashion.local` thuộc `Admin`. Chạy lần hai → không lỗi, không đổi gì. Xóa `Nhân viên` rồi chạy lại → không tạo lại.
- [ ] **Step 4:** Ghi README. Đề xuất commit: `feat(keycloak): add default permission groups and sync script`.

### Task 3: Gateway + backoffice-bff kiểm tra quyền backoffice

**Files:**
- Modify: `platform/gateway/src/main/java/com/fashionstore/gateway/config/SecurityConfig.java:36`
- Modify: `platform/backoffice-bff/src/main/java/com/fashionstore/backofficebff/config/SecurityConfig.java:54`
- Test: `platform/gateway/src/test/.../config/SecurityConfigTest.java`, `platform/backoffice-bff/src/test/.../BackofficeBffSecurityTest.java`, `.../config/KeycloakAuthoritiesMapperTest.java`, `platform/storefront-bff/src/test/.../config/KeycloakAuthoritiesMapperTest.java`

**Interfaces:**
- Consumes: `Permission.authorities()`.

- [ ] **Step 1: Sửa test trước.** Gateway: token có `order:read` → không bị 403 ở `/api/v1/admin/orders`; token chỉ `ROLE_USER`/`ROLE_ADMIN` không có quyền nào → 403; token chỉ `internal-caller` → 403. BFF: session có `product:read` → vào được; chỉ `ROLE_USER` → bị chặn. Hai `KeycloakAuthoritiesMapperTest`: đổi `product:write` → `product:manage`.
- [ ] **Step 2:** Chạy `./mvnw -o -pl platform/gateway,platform/backoffice-bff test` → FAIL.
- [ ] **Step 3:** Đổi `.hasRole("ADMIN")` thành `.hasAnyAuthority(Permission.authorities())` ở cả hai chỗ. Cập nhật comment "Backoffice chỉ dành cho ADMIN".
- [ ] **Step 4:** Chạy lại → PASS (cả `platform/storefront-bff`). Đề xuất commit: `feat(platform): gate backoffice on permissions instead of ADMIN role`.

### Task 4: order-service đổi sang quyền

**Files:** (`services/order-service/src/main/java/com/fashionstore/order/...`)
- Modify: `shared/config/security/SecurityConfig.java:42` → `.hasAnyAuthority(Permission.authorities())`
- Modify `@PreAuthorize` theo bảng:

| chỗ | quyền |
|---|---|
| `OrderServiceImpl`: `searchOrders`, `getOrderById`, `getOrderSaga` (2), `getOrderHistory` | `ORDER_READ` |
| `OrderServiceImpl`: `cancelOrderForAdmin`, `updateOrderStatus` | `ORDER_MANAGE` |
| `OrderCancellationService`: `requestAdmin`, `retryAdmin`, `verifyCarrier`; `OrderSagaTimeoutService.retryFailedCheckout` | `ORDER_MANAGE` |
| `ShipmentController.createShipment`, `syncShipment`; `ShipmentSyncService.syncFromCarrier` | `SHIPMENT_MANAGE` |
| `ShipmentServiceImpl.getShipmentForAdmin` | `ORDER_READ` |
| `AdminReturnController` (bỏ annotation mức class, đặt mức method): `search`, `get`, 3 `*EvidenceUrl` → `RETURN_READ`; `refund` → `REFUND_APPROVE`; còn lại → `RETURN_MANAGE` | |
| `ReturnServiceImpl`: `getReturnRequestById`, `searchReturnRequests`, 3 `get*EvidenceReadUrl` → `RETURN_READ`; `refundReturn` → `REFUND_APPROVE`; `approveReturn`, `recreatePickup`, `rejectReturn`, `receiveReturn`, `cancelReturn`, `createFulfillmentReturn`, `closeUnpaidCarrierReturn` → `RETURN_MANAGE`; `ReturnPickupService.cancelByAdmin` → `RETURN_MANAGE` | |
| `AdminPromotionController` (mức method): GET → `PROMOTION_READ`; create/update/toggle → `PROMOTION_MANAGE` | |
| `AdminDashboardServiceImpl.getDashboardStats` | `REVENUE_READ` |

  Mẫu annotation: `@PreAuthorize("hasAuthority('" + PermissionNames.ORDER_READ + "')")`.
- Test: `shared/config/security/SecurityConfigTest.java`, `returns/controller/AdminReturnControllerTest.java`, `saga/service/OrderSagaAdminRetryTest.java`, `shipment/service/impl/AdminShipmentContractTest.java` + mọi test khác dùng `ROLE_ADMIN`/`jwt("ADMIN")` (grep).

- [ ] **Step 1:** Trong `SecurityConfigTest` thêm token `staff-token` (các quyền `isStaffDefault`) và `no-permission-token` (`ROLE_ADMIN` thuần). Test: `staff-token` → 403 ở `POST /api/v1/admin/orders/returns/{id}/refund`, `POST /api/v1/admin/promotions`, `GET /api/v1/admin/dashboard`; 2xx/không-403 ở `GET /api/v1/admin/orders`, `POST /api/v1/admin/orders/returns/{id}/approve`; `no-permission-token` → 403 ở `/api/v1/admin/orders`. Test nào kiểm tra method-security mà ProbeController không đủ thì đặt ở controller test tương ứng.
- [ ] **Step 2:** `./mvnw -o -pl services/order-service clean test` → các test mới FAIL.
- [ ] **Step 3:** Đổi annotation theo bảng. Đổi test cũ đang dùng `ADMIN` sang authority tương ứng.
- [ ] **Step 4:** `grep -rn "hasRole('ADMIN')\|ROLE_ADMIN" services/order-service/src/main` → rỗng. `clean test` → PASS. Đề xuất commit: `feat(order): authorize admin endpoints by permission`.

### Task 5: Dashboard vận hành

**Files:**
- Modify: `order/dashboard/controller/AdminDashboardController.java`, `dashboard/service/AdminDashboardService.java`, `dashboard/service/impl/AdminDashboardServiceImpl.java`
- Create: `order/dashboard/dto/OperationsDashboardResponse.java`, `order/dashboard/dto/TopProductQuantityResponse.java`
- Test: `dashboard/service/impl/AdminDashboardServiceImplTest.java`, `dashboard/controller/AdminDashboardControllerTest.java`

**Interfaces:**
- Produces: `GET /api/v1/admin/dashboard/operations?days=30&lowStockThreshold=10` → `ApiResponse<OperationsDashboardResponse>`.
- `record OperationsDashboardResponse(Long totalOrders, List<OrderStatusCountResponse> orderByStatus, Long lowStockCount, List<TopProductQuantityResponse> topProducts)`; `record TopProductQuantityResponse(String variantId, String productName, Long totalQuantitySold)`.
- `AdminDashboardService.getOperationsStats(int days, int lowStockThreshold)` với `@PreAuthorize` `DASHBOARD_READ`.

- [ ] **Step 1:** Test service: `getOperationsStats` trả cùng `totalOrders`, `orderByStatus`, `lowStockCount`, số lượng top product như `getDashboardStats` với cùng mock. Test controller: JSON không có key `totalRevenue` ở đâu cả.
- [ ] **Step 2:** Chạy → FAIL.
- [ ] **Step 3:** Implement bằng cách gọi lại các query/private method đang dùng trong `getDashboardStats`; không viết query mới. Swagger `@Operation` cho endpoint mới.
- [ ] **Step 4:** `clean test` → PASS. Đề xuất commit: `feat(order): add operations dashboard without revenue`.

### Task 6: catalog-service đổi sang quyền

**Files:** (`services/catalog-service/src/main/java/com/fashionstore/catalog/...`)
- Modify: `shared/config/security/SecurityConfig.java:51-73`. Catalog vốn phân quyền theo path, giữ cách đó (khác spec: không thêm `@PreAuthorize` cho từng method controller sản phẩm):
  - `GET /api/v1/inventory/**`, `GET /admin/inventory/**` → `INVENTORY_READ`; các method khác dưới `/api/v1/inventory/**`, `/admin/inventory/**` (trừ `POST /api/v1/inventory/check` giữ `authenticated()`) → `INVENTORY_MANAGE`.
  - GET dưới các path admin của sản phẩm/danh mục (`/api/v1/admin/products/**`, `brands`, `color-options`, `size-options`, `product-attributes`, `size-charts` — lấy đúng prefix trong các controller `product/controller/*`) → `PRODUCT_READ`; ghi trên các path đó và ghi trên `/api/v1/category/**` → `PRODUCT_MANAGE`.
  - `anyRequest()` → `.hasAnyAuthority(Permission.authorities())`.
- Modify: `AdminReviewController` (mức method): `list` → `REVIEW_READ`; còn lại → `REVIEW_MANAGE`. `ReviewServiceImpl:256` đổi `isAdmin` thành có authority `REVIEW_MANAGE` (đổi tên biến `canManageReviews`).
- Modify: `MediaFileServiceImpl:322` `ROLE_ADMIN` → authority `RETURN_READ`. `EvidenceUploadController.payments` → `REFUND_APPROVE`. `ProductServiceImpl.getProductByIdForBackoffice` → `PRODUCT_READ` (đổi luôn FQCN annotation thành import).
- Test: `shared/config/security/SecurityConfigTest.java`, `inventory/controller/AdminInventoryControllerTest.java`, `InventoryControllerTest.java`, `inventory/service/impl/InventoryServiceImplTest.java`, `media/service/impl/MediaFileServiceImplTest.java`, `review/controller/AdminReviewControllerTest.java`.

- [ ] **Step 1:** `SecurityConfigTest`: token chỉ `inventory:read` → được `GET /admin/inventory`, 403 ở `POST /admin/inventory/variants/x/receipts` và `POST /api/v1/admin/products`; token `product:manage` → được `POST /api/v1/admin/products`; token `ROLE_ADMIN` thuần → 403 ở `POST /api/v1/admin/products`; khách `ROLE_USER` vẫn `POST /api/v1/inventory/check` được. `MediaFileServiceImplTest`: user có `return:read` đọc được evidence `returns`, `ROLE_ADMIN` thuần thì không. Review: user có `review:manage` xóa review người khác được.
- [ ] **Step 2:** `./mvnw -o -pl services/catalog-service clean test` → FAIL.
- [ ] **Step 3:** Implement. Đổi test cũ dùng `ADMIN`.
- [ ] **Step 4:** grep `hasRole("ADMIN")\|hasRole('ADMIN')\|ROLE_ADMIN` trong `src/main` → rỗng. `clean test` → PASS. Cập nhật dòng "builder rà và thêm `@PreAuthorize`" trong spec thành "catalog dùng rule theo path". Đề xuất commit: `feat(catalog): authorize admin endpoints by permission`.

### Task 7: payment-service đổi sang quyền

**Files:**
- Modify: `services/payment-service/.../shared/config/security/SecurityConfig.java:53` → `.hasAnyAuthority(Permission.authorities())`
- Modify: `AdminPaymentController` → `PAYMENT_READ`; `AdminWalletController`, `AdminWalletTopUpController` → `WALLET_READ` (mức class giữ được vì cả class là đọc).
- Test: `shared/config/security/SecurityConfigTest.java`, `wallet/controller/AdminMoneyControllerTest.java`

`WALLET_ADJUST` chưa có endpoint dùng — không tạo endpoint.

- [ ] **Step 1:** Test: token `wallet:read` → 200 ở `GET /api/v1/admin/wallets`, 403 ở `GET /api/v1/admin/payments/order/x`; `ROLE_ADMIN` thuần → 403.
- [ ] **Step 2:** `./mvnw -o -pl services/payment-service clean test` → FAIL.
- [ ] **Step 3:** Implement, đổi test cũ.
- [ ] **Step 4:** `clean test` → PASS. Đề xuất commit: `feat(payment): authorize admin endpoints by permission`.

### Task 8: identity-service quản lý khách hàng theo quyền

**Files:**
- Modify: `services/identity-service/.../service/impl/AdminUserServiceImpl.java:33` — bỏ annotation mức class; `searchUsers`, `getUser` → `CUSTOMER_READ`; `updateUser`, `updateStatus` → `CUSTOMER_MANAGE`. Sửa Javadoc `AdminUserController:32`.
- Test: `service/impl/AdminUserServiceSecurityTest.java`, `config/security/SecurityConfigTest.java`

- [ ] **Step 1:** `AdminUserServiceSecurityTest`: `customer:read` → `searchUsers` OK, `updateStatus` → `AccessDeniedException`; `ROLE_ADMIN` thuần → `AccessDeniedException` ở `searchUsers`.
- [ ] **Step 2:** `./mvnw -o -pl services/identity-service clean test` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** PASS. Đề xuất commit: `feat(identity): authorize customer admin by permission`.

### Task 9: Keycloak Admin client cho group và user

**Files:**
- Create: `identity/client/KeycloakGroupFeignClient.java` (cùng `url = "${app.keycloak.admin-url}"`, configuration `FeignGlobalConfig` + `KeycloakAdminFeignClientConfig`)
- Create: `identity/client/KeycloakGroupClient.java` (wrapper, `@CircuitBreaker/@Retry(name = "keycloakAdmin")` như `KeycloakAdminClient`)
- Create: `identity/dto/group/KeycloakGroupRepresentation.java`, `KeycloakRoleRepresentation.java`, `identity/dto/staff/KeycloakNewUserRepresentation.java` (record, chỉ các field cần)
- Modify: `identity/exception/IdentityErrorCode.java` — thêm `GROUP_NOT_FOUND(2030, NOT_FOUND)`, `GROUP_NAME_TAKEN(2031, CONFLICT)`, `SYSTEM_GROUP_READ_ONLY(2032, CONFLICT)`, `PERMISSION_NOT_ASSIGNABLE(2033, BAD_REQUEST)`, `LAST_ADMIN_REQUIRED(2034, CONFLICT)`, `CANNOT_LEAVE_OWN_ADMIN(2035, CONFLICT)`, `CUSTOMER_GROUP_NOT_ASSIGNABLE(2036, BAD_REQUEST)`, `STAFF_EMAIL_TAKEN(2037, CONFLICT)`, `STAFF_PASSWORD_REJECTED(2038, BAD_REQUEST)`, `STAFF_GROUP_REQUIRED(2039, BAD_REQUEST)` (message tiếng Việt; kiểm tra mã chưa dùng).
- Test: `client/KeycloakGroupClientTest.java` (theo mẫu `KeycloakAdminClientTest`), `client/ResilienceWiringTest.java`

**Interfaces (Produces, `KeycloakGroupClient`):**
- `List<KeycloakGroupRepresentation> listGroups()` — `GET /groups?briefRepresentation=false` (có `clientRoles`, `realmRoles`)
- `KeycloakGroupRepresentation getGroup(String groupId)`; `String createGroup(String name)` — trả id lấy từ header `Location`; `void renameGroup(String groupId, String name)`; `void deleteGroup(String groupId)`
- `void setGroupPermissions(String groupId, Set<String> authorities)` — đọc mapping hiện tại `GET /groups/{id}/role-mappings/clients/{clientUuid}`, `POST` phần thiếu, `DELETE` phần thừa; `clientUuid` của `fashion-api` tra một lần qua `GET /clients?clientId=fashion-api` rồi giữ trong field
- `List<String> listMemberIds(String groupId)` — `GET /groups/{id}/members?briefRepresentation=true&max=1000` (shortcut: tối đa 1000 thành viên mỗi nhóm, đủ cho nhân viên; cần hơn thì phân trang)
- `List<KeycloakGroupRepresentation> listUserGroups(String userId)`; `void addUserToGroup(String userId, String groupId)`; `void removeUserFromGroup(String userId, String groupId)`
- `String createUser(KeycloakNewUserRepresentation user)` — 409 → `AppException(STAFF_EMAIL_TAKEN)`, 400 → `AppException(STAFF_PASSWORD_REJECTED)`; `void deleteUser(String userId)`
- 404 trên group → `AppException(GROUP_NOT_FOUND)`. Lỗi 5xx/mất kết nối → `UpstreamFailure.translate` như `KeycloakAdminClient`.

- [ ] **Step 1:** Test với Feign mock: `createGroup` đọc id từ `Location: .../groups/abc` → `"abc"`; `setGroupPermissions(id, {order:read})` khi đang có `{product:read}` → POST `[order:read]`, DELETE `[product:read]`; `createUser` 409 → `STAFF_EMAIL_TAKEN`; `getGroup` 404 → `GROUP_NOT_FOUND`. `ResilienceWiringTest`: client mới có breaker `keycloakAdmin`.
- [ ] **Step 2:** `./mvnw -o -pl services/identity-service test -Dtest='KeycloakGroupClientTest,ResilienceWiringTest'` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** PASS. Đề xuất commit: `feat(identity): add Keycloak group admin client`.

### Task 10: API quản lý nhóm

**Files:**
- Create: `identity/service/GroupService.java`, `identity/service/impl/GroupServiceImpl.java`, `identity/controller/AdminGroupController.java`
- Create: `identity/dto/group/PermissionResponse.java`, `GroupResponse.java`, `GroupRequest.java`, `GroupChangeResponse.java`, `GroupMemberResponse.java`, `UpdateUserGroupsRequest.java`, `UserGroupsResponse.java`
- Modify: `identity/dto/user/AdminUserDetailResponse.java` (thêm `List<GroupResponse> groups`), `AdminUserServiceImpl.getUser`
- Test: `service/impl/GroupServiceImplTest.java`, `controller/AdminGroupControllerTest.java`

**Interfaces:**
- `record PermissionResponse(String authority, String area, String label, boolean isAssignable)`
- `record GroupResponse(String id, String name, List<String> permissions, Integer memberCount, boolean isSystem, boolean isEditable)` — `isSystem` = tên là `Admin`/`Customer`; `isEditable = !isSystem`
- `record GroupRequest(@NotBlank @Size(max = 100) String name, @NotNull Set<String> permissions)`
- `record GroupChangeResponse(GroupResponse group, List<String> notLoggedOutUserIds)`
- `record GroupMemberResponse(String userId, String email, String fullName)` — lấy từ `UserRepository` theo id; id không có trong identity DB thì vẫn trả với email/fullName null
- `record UpdateUserGroupsRequest(@NotNull Set<String> groupIds)`; `record UserGroupsResponse(List<GroupResponse> groups, List<String> notLoggedOutUserIds)`
- `GroupService`: `List<PermissionResponse> listPermissions()`, `List<GroupResponse> listGroups()`, `GroupChangeResponse createGroup(GroupRequest)`, `GroupChangeResponse updateGroup(String groupId, GroupRequest)`, `List<String> deleteGroup(String groupId)` (trả `notLoggedOutUserIds`), `PageResponse<List<GroupMemberResponse>> listMembers(String groupId, Pageable)`, `UserGroupsResponse updateUserGroups(String userId, UpdateUserGroupsRequest)`, `List<GroupResponse> getUserGroups(String userId)`, `void addToGroupByName(String userId, String groupName)` (Task 11 dùng để thêm `Customer`). Mọi method public `@PreAuthorize` `GROUP_MANAGE`, trừ `getUserGroups` (`CUSTOMER_READ`, vì gọi từ `getUser`) và `addToGroupByName` (không annotation, chỉ gọi nội bộ).
- Endpoint (class `@RequestMapping("/api/v1/admin")`): `GET /permissions`, `GET /groups`, `POST /groups`, `PUT /groups/{id}`, `DELETE /groups/{id}`, `GET /groups/{id}/members`, `PUT /users/{userId}/groups`. Bọc `ApiResponse`, Swagger `@Tag`/`@Operation` tiếng Việt như controller hiện có.

Quy tắc (mỗi quy tắc một test trong `GroupServiceImplTest`):
- `createGroup`/`updateGroup`: tên trùng không phân biệt hoa thường với nhóm khác, hoặc bằng `Admin`/`Customer` → `GROUP_NAME_TAKEN`; quyền ngoài danh mục → `PERMISSION_NOT_ASSIGNABLE`; chứa `group:manage` → `PERMISSION_NOT_ASSIGNABLE`.
- `updateGroup`/`deleteGroup` trên `Admin` hoặc `Customer` → `SYSTEM_GROUP_READ_ONLY`.
- `updateGroup` đổi quyền → `logout` mọi thành viên; chỉ đổi tên → không logout. `deleteGroup` → logout mọi thành viên trước khi xóa danh sách bị mất.
- `updateUserGroups`: mọi `groupId` phải tồn tại, nếu không → `GROUP_NOT_FOUND` và không gọi add/remove nào; chứa `Customer` → `CUSTOMER_GROUP_NOT_ASSIGNABLE`; membership `Customer` hiện có giữ nguyên; gỡ `Admin` khỏi chính người gọi → `CANNOT_LEAVE_OWN_ADMIN`; gỡ `Admin` khi nhóm chỉ còn 1 thành viên → `LAST_ADMIN_REQUIRED`; có thay đổi → logout user đó.
- Logout lỗi: bắt `AppException` từ `KeycloakAdminClient.logout`, log warn kèm userId (comment lý do: thay đổi đã áp dụng, token hết hạn sau 5 phút), thêm id vào `notLoggedOutUserIds`.

- [ ] **Step 1:** Viết `GroupServiceImplTest` (mock `KeycloakGroupClient`, `KeycloakAdminClient`, `UserRepository`, `CurrentUserProvider`) cho từng quy tắc trên, và `AdminGroupControllerTest`: không có `group:manage` → 403; có → 200; `GroupRequest` thiếu tên → 400.
- [ ] **Step 2:** `./mvnw -o -pl services/identity-service clean test` → FAIL.
- [ ] **Step 3:** Implement. `AdminUserServiceImpl.getUser` gọi `groupService.getUserGroups(userId)` để điền `groups`.
- [ ] **Step 4:** PASS. Đề xuất commit: `feat(identity): add group and permission management API`.

### Task 11: Tạo tài khoản nhân viên

**Files:**
- Create: `identity/service/StaffAccountService.java`, `identity/service/impl/StaffAccountServiceImpl.java`, `identity/controller/AdminStaffController.java`, `identity/dto/staff/CreateStaffRequest.java`, `identity/dto/staff/StaffResponse.java`
- Test: `service/impl/StaffAccountServiceImplTest.java`, `controller/AdminStaffControllerTest.java`

**Interfaces:**
- `record CreateStaffRequest(@NotBlank @Email String email, @NotBlank String firstName, @NotBlank String lastName, @NotBlank String temporaryPassword, @NotEmpty Set<String> groupIds)` — tách first/last name vì Keycloak 26 user profile yêu cầu cả hai (khác spec ghi "họ tên"; cập nhật spec)
- `record StaffResponse(String id, String email, String fullName, List<GroupResponse> groups)`
- `StaffAccountService.createStaff(CreateStaffRequest) -> StaffResponse`, `@PreAuthorize` `GROUP_MANAGE`
- `POST /api/v1/admin/staff` → `ApiResponse<StaffResponse>`, HTTP 201

Luồng `createStaff`:
1. `groupIds` chứa `Customer` → `CUSTOMER_GROUP_NOT_ASSIGNABLE`; nhóm không tồn tại → `GROUP_NOT_FOUND` (kiểm tra trước khi tạo gì).
2. `userRepository.existsByEmail(email)` → `ACCOUNT_LINK_CONFLICT` (trước khi gọi Keycloak).
3. `KeycloakGroupClient.createUser(username=email, email, firstName, lastName, enabled=true, emailVerified=true, credentials=[password, temporary=true])` → id.
4. Thêm user vào từng nhóm + `groupService.addToGroupByName(id, "Customer")`; `userRepository.insertProvisionedUser(id, email, firstName + " " + lastName, true)` (cùng thứ tự với claim `name` mà JIT-provision dùng).
5. Bước 4 lỗi bất kỳ → `deleteUser(id)` rồi ném lại lỗi gốc; `deleteUser` cũng lỗi thì log error với id để dọn tay.

- [ ] **Step 1:** Test: thành công → gọi `createUser` với `temporary=true`, `emailVerified=true`, thêm đủ nhóm + `Customer`, insert dòng `users`; email có trong identity DB → `ACCOUNT_LINK_CONFLICT` và không gọi `createUser`; Keycloak 409 → `STAFF_EMAIL_TAKEN`; `addUserToGroup` ném lỗi → `deleteUser(id)` được gọi và lỗi gốc được ném; `groupIds` rỗng → 400 ở controller; không có `group:manage` → 403.
- [ ] **Step 2:** `./mvnw -o -pl services/identity-service clean test` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** PASS. Đề xuất commit: `feat(identity): let admins create staff accounts`.

### Task 12: Tài liệu, yêu cầu frontend, kiểm chứng toàn bộ

**Files:**
- Modify: `docs/api-requests.md` — mục mới (Done phía backend, chờ frontend): màn "Nhóm & phân quyền", chọn nhóm ở chi tiết user, form "Tạo nhân viên", ẩn menu theo authority từ `/bff/session`, dashboard dùng `/operations` khi không có `revenue:read`; liệt kê endpoint và DTO ở Task 5, 10, 11.
- Modify: `docs/services/identity-service.md`, `docs/services/order-service.md` (nếu có mục endpoint admin), `docs/superpowers/specs/2026-11-03-rbac-groups-design.md` (gateway, catalog theo path, first/last name, nhận diện nhóm hệ thống theo tên).

- [ ] **Step 1:** `grep -rn "hasRole(\"ADMIN\")\|hasRole('ADMIN')\|ROLE_ADMIN" services/*/src/main platform/*/src/main packages/*/src/main` → rỗng.
- [ ] **Step 2:** `./scripts/test.ps1` → toàn bộ PASS (dán số test/lỗi thật vào báo cáo).
- [ ] **Step 3: Chạy thật:** `docker compose up -d`, chạy `sync-rbac.ps1`. Với token admin (Swagger): tạo nhóm "Nhân viên kho" chỉ `inventory:read`, `inventory:manage`; `POST /api/v1/admin/staff` vào nhóm đó; đăng nhập backoffice bằng nhân viên (bị bắt đổi mật khẩu) → `GET /admin/inventory` 200, `GET /api/v1/admin/orders` 403.
- [ ] **Step 4:** Đề xuất commit: `docs: document permission groups and frontend requests`.
