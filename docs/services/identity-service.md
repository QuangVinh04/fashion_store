# identity-service

Cổng 8082 · database `identity_database` (host 5438) · layer-first.

## Trách nhiệm

- Hồ sơ user. Dòng `users` được JIT-provision ở request đã xác thực đầu tiên, `users.id` = Keycloak `sub`.
- Sổ địa chỉ giao hàng, có mã địa giới GHN.
- Quản trị user (khóa/mở, sửa) qua Keycloak Admin API.
- Phân quyền theo nhóm: nhóm = Keycloak Group có client-role mapping của `fashion-api` (mỗi quyền một client role, danh mục `Permission` trong common-library). Không lưu bảng riêng. Đổi nhóm/quyền thì logout người bị ảnh hưởng. Nhóm hệ thống `Admin`, `Customer` nhận diện theo tên, không sửa/xóa được. Tạo nhân viên: user Keycloak (mật khẩu tạm) + nhóm đã chọn + `Customer` + dòng `users`.
- Quyền được kiểm tra bằng `@PreAuthorize("hasAuthority(...)")` ở tầng service, không kiểm tra role `ADMIN`. Chi tiết: `docs/superpowers/specs/2026-11-03-rbac-groups-design.md`.
- Avatar: lưu tham chiếu media của catalog-service. Phát `profile.avatar.changed` khi đổi avatar.

Không login user, không cấp token.

## API

| method | path | ghi chú |
|---|---|---|
| GET, PUT | `/api/v1/users/profile` | hồ sơ của user hiện tại |
| GET, POST | `/api/v1/users/addresses` | |
| GET, PUT, DELETE | `/api/v1/users/addresses/{id}` | |
| PATCH | `/api/v1/users/addresses/{id}/default` | |
| GET | `/api/v1/admin/users`, `/api/v1/admin/users/{id}` | `customer:read`; chi tiết kèm `groups` (rỗng nếu Keycloak không khả dụng) |
| PUT | `/api/v1/admin/users/{id}` | `customer:manage` (đích thuộc nhóm `Admin`: thêm `group:manage`) |
| PATCH | `/api/v1/admin/users/{id}/status` | `customer:manage` (đích thuộc nhóm `Admin`: thêm `group:manage`) |
| GET | `/api/v1/admin/permissions` | `group:manage`: danh mục quyền |
| GET, POST | `/api/v1/admin/groups` | `group:manage`: danh sách, tạo nhóm |
| PUT, DELETE | `/api/v1/admin/groups/{id}` | `group:manage`: đổi tên/quyền, xóa (từ chối `Admin`, `Customer`) |
| GET | `/api/v1/admin/groups/{id}/members` | `group:manage`: thành viên, phân trang |
| PUT | `/api/v1/admin/users/{userId}/groups` | `group:manage`: đặt lại nhóm của user |
| POST | `/api/v1/admin/staff` | `group:manage`: tạo tài khoản nhân viên (201) |
| GET | `/internal/v1/users/{userId}` | nội bộ |
| GET | `/internal/v1/users/{userId}/addresses/{addressId}` | nội bộ, order-service dùng khi checkout |
| GET | `/internal/v1/users/{userId}/avatar-reference` | nội bộ |

## Messaging

- Phát: `profile.avatar.changed` (qua outbox).
- Nhận: không.

## HTTP ra ngoài

- catalog-service: xác thực media avatar.
- Keycloak Admin API.

## Bảng chính

`users`, `user_addresses`, `outbox_event`.
