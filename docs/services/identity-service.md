# identity-service

Cổng 8082 · database `identity_database` (host 5438) · layer-first.

## Trách nhiệm

- Hồ sơ user. Dòng `users` được JIT-provision ở request đã xác thực đầu tiên, `users.id` = Keycloak `sub`.
- Sổ địa chỉ giao hàng, có mã địa giới GHN.
- Quản trị user (khóa/mở, sửa) qua Keycloak Admin API.
- Avatar: lưu tham chiếu media của catalog-service. Phát `profile.avatar.changed` khi đổi avatar.

Không login user, không cấp token.

## API

| method | path | ghi chú |
|---|---|---|
| GET, PUT | `/api/v1/users/profile` | hồ sơ của user hiện tại |
| GET, POST | `/api/v1/users/addresses` | |
| GET, PUT, DELETE | `/api/v1/users/addresses/{id}` | |
| PATCH | `/api/v1/users/addresses/{id}/default` | |
| GET | `/api/v1/admin/users`, `/api/v1/admin/users/{id}` | admin |
| PUT | `/api/v1/admin/users/{id}` | admin |
| PATCH | `/api/v1/admin/users/{id}/status` | admin |
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

`users`, `user_addresses`, `roles`, `permissions`, `user_roles`, `role_permissions`, `outbox_event`.
