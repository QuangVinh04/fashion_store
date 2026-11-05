# Keycloak

Realm `fashion-store` — OIDC provider for the platform. Imported on startup from
`realm-fashion-store.json` (`start-dev --import-realm`).

Keycloak is the only token issuer. Browsers log in through `storefront-bff` / `backoffice-bff`;
api-gateway and every service validate the relayed access token themselves.
Service-to-service `/internal/**` calls use `client_credentials` tokens of the service accounts
(client role `internal-caller`). identity-service creates its `users` row on the first authenticated
request with `users.id` = token `sub` (JIT provisioning).

## Run

```bash
docker compose up -d keycloak     # also starts keycloak-postgres
```

| what | URL |
|---|---|
| Admin console | http://localhost:8180/admin (`admin` / `admin`, override `KEYCLOAK_ADMIN_USERNAME`/`KEYCLOAK_ADMIN_PASSWORD`) |
| Account console | http://localhost:8180/realms/fashion-store/account |
| Discovery | http://localhost:8180/realms/fashion-store/.well-known/openid-configuration |
| JWKS from inside `fashion-network` | http://keycloak:8080/realms/fashion-store/protocol/openid-connect/certs |

Token `iss` is always `http://localhost:8180/realms/fashion-store` (`KC_HOSTNAME`),
even when the token is fetched via `http://keycloak:8080` (`KC_HOSTNAME_BACKCHANNEL_DYNAMIC`).
Resource servers must therefore configure `issuer` and `jwk-set-uri` separately
instead of relying on `issuer-uri` discovery.

## Realm contents

| client | type | purpose |
|---|---|---|
| `storefront-bff` | confidential, auth code + PKCE S256, no direct grant | BFF login at `http://localhost:8083/login/oauth2/code/keycloak` |
| `backoffice-bff` | confidential, auth code + PKCE S256, no direct grant | BFF login at `http://localhost:8084/login/oauth2/code/keycloak` |
| `swagger-ui` | public, PKCE | Swagger UI "Authorize" |
| `order-service`, `catalog-service`, `identity-service` | confidential, service account only | `client_credentials` for `/internal/**` |
| `fashion-api` | no login flow | audience `fashion-api` + client roles |

- Realm roles: `USER` (default for new users), `ADMIN` (composite of `USER`).
- `fashion-api` client roles: the 22 backoffice permissions (`order:read`, `product:manage`, `wallet:adjust`, `group:manage`, ...,
  catalog = `Permission` enum in common-library, checked by `PermissionTest`) and `internal-caller` (granted to the three service accounts).
- Groups: `Admin` (every permission, system), `Nhân viên` (the `isStaffDefault` permissions, editable), `Customer`
  (realm role `USER`, system). `Customer` is the realm default group, so new users join it automatically.
- Service account `identity-service` has `realm-management` roles `view-users`, `view-clients`, `manage-users`.
- Every client adds `fashion-api` to `aud`. The two BFF clients also put `realm_access.roles` and
  `resource_access.fashion-api.roles` into the ID token (read by the BFF `GrantedAuthoritiesMapper`).
- Access token 5 min · SSO idle 30 min · SSO max 7 days · refresh token rotation (reuse revokes the session) · brute force lockout after 5 failures · email verification required.
- Password policy: ≥ 8 ký tự, có chữ hoa, chữ thường, số, không trùng username/email, không dùng lại 3 mật khẩu gần nhất.
- Events: user event và admin event được ghi (giữ 30 ngày), xem ở Admin console → Events. `eventsListeners` mặc định `jboss-logging`.

### Back-channel logout và registry phiên trong Redis

Khi admin khoá tài khoản hoặc đăng xuất user ở Keycloak, Keycloak POST `logout_token` (có `sid`) tới BFF; BFF tra `sid` → session BFF rồi huỷ.
Bảng `sid → session` nằm trong Redis (`RedisReactiveOidcSessionRegistry`, common-library, key `<session namespace>:oidc:*`, TTL 7 ngày = SSO max),
nên BFF restart hoặc chạy nhiều instance vẫn huỷ đúng session. Bản `InMemoryReactiveOidcSessionRegistry` trước đây mất bảng này khi restart.
ID token thô không được lưu; chỉ lưu issuer, audience, subject, `sid` và header CSRF mà handler logout cần.

### Backoffice bắt buộc OTP

Client `backoffice-bff` dùng flow đăng nhập riêng `backoffice-browser` (mật khẩu + OTP, cả hai REQUIRED, **không có bước cookie SSO**).
Cả flow (`authenticationFlows`) lẫn việc gán cho client (`authenticationFlowBindingOverrides` của `backoffice-bff`, tham chiếu flow bằng **id**) nằm trong `realm-fashion-store.json`.
Lần đầu vào backoffice, tài khoản chưa có OTP bị buộc quét QR (Google Authenticator, Authy…); dev user `admin@fashion.local` cũng vậy.

Đã kiểm trên Keycloak 26.7.4 thật bằng cách đăng nhập từng bước: admin vào **storefront** chỉ cần mật khẩu; vào **backoffice** bị buộc OTP; đăng nhập storefront trước rồi vào backoffice **vẫn bị hỏi OTP** (SSO không bỏ qua được).

- Realm chỉ được import khi chưa tồn tại: muốn áp dụng cho Keycloak đã chạy thì xóa volume `keycloak_postgres_data` (dev) hoặc sửa trong Admin console (prod).
- OTP được bắt ở bước đăng nhập của client `backoffice-bff`, **không gắn vào token**. Token lấy qua client khác (storefront-bff, swagger-ui ở dev) không qua OTP.
- Ghi override bằng **alias** của flow thì Keycloak không khởi động (`Unable to resolve auth flow binding override`); phải dùng id.
- Kiểm bằng kcadm: `--fields authenticationFlowBindingOverrides` luôn in `{}` (kcadm cắt nội dung map); đọc cả client (không `--fields`) mới thấy giá trị.

Client secrets and SMTP come from env (placeholders `${VAR:default}` in the realm file),
defaults are dev-only: `STOREFRONT_BFF_CLIENT_SECRET`, `BACKOFFICE_BFF_CLIENT_SECRET`, `ORDER_SERVICE_CLIENT_SECRET`,
`CATALOG_SERVICE_CLIENT_SECRET`, `IDENTITY_SERVICE_CLIENT_SECRET`, `PAYMENT_SERVICE_CLIENT_SECRET`, `MAIL_*`.
Without SMTP credentials, self-registered users cannot receive the verification email.

## Event Listener SPI: Keycloak báo cho hệ thống (`event-listener/`)

Keycloak là nơi tạo user, nên identity-service chỉ biết user ở request đầu tiên (JIT). Provider `fashion-rabbit`
(`platform/keycloak/event-listener`, jar chạy bên trong Keycloak) đẩy event sang RabbitMQ để identity tạo / đồng bộ dòng `users` ngay.

| Keycloak | Routing key (`EventTypes`) | identity-service |
|---|---|---|
| user event `REGISTER` | `keycloak.user.registered` | tạo `users` (id = `sub`, tên = họ + tên, email chưa xác minh) |
| admin sửa user (admin event `USER`/`UPDATE`), user event `UPDATE_EMAIL` | `keycloak.user.email-updated` | đồng bộ email nếu khác |

Vì sao đổi email chỉ đến từ admin: realm đặt `registrationEmailAsUsername` và `editUsernameAllowed=false`, user không tự sửa email được
(Account API trả 204 nhưng email không đổi; đã thử trên Keycloak thật). `UPDATE_PROFILE` không mang email nên bị bỏ qua.
Admin event cũng không mang email: provider đọc email hiện tại từ user, nên event này có thể phát cả khi email không đổi (consumer idempotent).

```bash
./mvnw -pl platform/keycloak/event-listener -am package -DskipTests   # tạo target/keycloak-rabbit-event-listener.jar
docker compose up -d --build keycloak                                 # platform/keycloak/Dockerfile copy jar vào /opt/keycloak/providers
```

- Cấu hình RabbitMQ qua biến môi trường Keycloak `KC_SPI_EVENTS_LISTENER__FASHION_RABBIT__{HOST,PORT,USERNAME,PASSWORD,VHOST}` (đã có trong compose; prod bắt buộc `RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD`).
- Listener được bật trong realm (`eventsListeners` của `realm-fashion-store.json`). Chưa build jar thì Keycloak chỉ log cảnh báo, vẫn chạy.
- Event chỉ được gửi **sau khi transaction của Keycloak commit** và ở thread riêng: RabbitMQ chậm hay chết không làm chậm / lỗi đăng nhập.
- **At-most-once**: Keycloak không có outbox, event mất nếu RabbitMQ chết lúc đó. JIT ở request đầu tiên của user là đường dự phòng.
- `emailVerified` từ event luôn là `false` (event không mang cờ này). Identity chỉ ghi cờ khi tạo user hoặc khi email đổi.
- Sai tham số exchange làm RabbitMQ trả `PRECONDITION_FAILED` cho mọi service, nên provider khai báo `fashion.events` y hệt `RabbitTopology`; test đối chiếu hai bên.
- API của SPI (`eventsListener` là SPI nội bộ) có thể đổi giữa các bản Keycloak: nâng cấp image thì đổi `keycloak.version` trong pom của module và build lại.

## Dev users (dev only)

**Production:** `docker-compose.prod.yml` sets `DEV_USERS_ENABLED=false` (all dev users are imported disabled);
see [Production](#production). Dev passwords can be changed with `DEV_ADMIN_PASSWORD` / `DEV_CUSTOMER_PASSWORD` /
`DEV_USER_PASSWORD` / `DEV_STAFF_PASSWORD` (the 10 seeded `*@fashion.local` customers).
Create real admins in Keycloak afterwards. A realm is imported only once, so change an existing realm in the admin console.

## Production

```bash
cp .env.prod.example .env.prod   # fill in; never commit
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml up -d
```

`docker-compose.prod.yml` covers authentication only (Keycloak, its database, the two BFFs, the token issuer of
every resource server). Every value it needs is `${VAR:?}`: a missing variable stops compose instead of
falling back to a dev default.

- **Keycloak** runs `start` (not `start-dev`) behind a TLS-terminating reverse proxy on the same host:
  `KC_HOSTNAME=$KEYCLOAK_HOSTNAME` (public `https://` URL, also the token `iss`), `KC_HTTP_ENABLED=true`,
  `KC_PROXY_HEADERS=xforwarded`, port published on `127.0.0.1:8180` only. The proxy must send
  `X-Forwarded-For/Proto/Host/Port` and must not expose `/admin` publicly unless intended.
- **BFFs** run profile `prod`: session cookie `Secure`, `server.forward-headers-strategy=framework` (so the
  OAuth2 `redirect_uri` is the public `https://` URL), ports on `127.0.0.1` for the proxy.
- **Realm placeholders** (only applied on first import): `STOREFRONT_BFF_PUBLIC_URL` / `BACKOFFICE_BFF_PUBLIC_URL`
  build the BFF redirect and post-logout URIs; `SWAGGER_UI_CLIENT_ENABLED=false` disables the public
  `swagger-ui` client; `DEV_USERS_ENABLED=false`; client secrets and `MAIL_*` come from `.env.prod`.
- **SMTP** is required: without it self-registration is stuck at email verification. Use a transactional
  provider and a `MAIL_FROM` domain with SPF/DKIM/DMARC.

**Existing realm** (imported earlier with dev values): the JSON is not re-applied. In the admin console update
Clients → `storefront-bff` / `backoffice-bff` → Valid redirect URIs, Valid post logout redirect URIs, Root/Home URL;
Clients → `swagger-ui` → Enabled off; Clients → each confidential client → Credentials → regenerate the secret;
disable or delete every `*@fashion.local` user; Realm settings → Email → SMTP.

| email | password | roles |
|---|---|---|
| `admin@fashion.local` | `Admin@12345` | `ADMIN`, group `Admin` |
| `nhanvien@fashion.local` | `Staff@12345` | group `Nhân viên` |
| `staff.nogroup@fashion.local` | `Staff@12345` | no group (backoffice shows no permission) |
| `customer@fashion.local` | `Customer@12345` | `USER` |
| 10 seeded customers (`nguyen.van.an@fashion.local`, …) | `User@12345` | `USER` |
| 10 more customers from `scripts/seed/keycloak-users.json` (`truong.minh.khoa@fashion.local`, …; `luong.van.son@fashion.local` is disabled) | `User@12345` | `USER` |

## Migrating legacy identity users

Users that registered with the old identity-service login still live in `identity_database`.
Import them into Keycloak **with the same id**: the token `sub` then equals the old `users.id`, so
their identity profile/addresses and every `userId` already stored in order/catalog/payment stay valid.

```bash
# 1. export (only rows that still have a legacy password hash)
docker exec -i fashion-store-identity-postgres-1 psql -U root -d identity_database -At \
  < platform/keycloak/migrate-identity-users.sql > legacy-users.json

# 2. import into the realm (existing usernames are skipped)
ADMIN_TOKEN=$(curl -s -d grant_type=password -d client_id=admin-cli -d username=admin -d password=admin \
  http://localhost:8180/realms/master/protocol/openid-connect/token | jq -r .access_token)
curl -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  --data @legacy-users.json http://localhost:8180/admin/realms/fashion-store/partialImport
```

- BCrypt hashes are **not** migrated (Keycloak has no bcrypt hash provider). Imported users get the
  `UPDATE_PASSWORD` required action. They set a new password through "Forgot password" (needs SMTP),
  or an admin sends "Update Password" from the console.
- Legacy roles are not copied: identity dropped `roles`/`user_roles` (Flyway `V2`). Imported users only get the
  default `Customer` group. Put former admins into `/Admin` and assign staff groups afterwards.
- A Keycloak account created **before** migration with the same email but a different id gets
  `409 ACCOUNT_LINK_CONFLICT` from identity. Delete that Keycloak account and import the legacy user instead.

## Syncing permissions and groups

A realm is imported only once, so a Keycloak imported before the RBAC groups existed has neither the
permission client roles nor the `Admin` / `Nhân viên` / `Customer` groups. Run from the repo root with
`docker compose up -d keycloak` already running:

```powershell
.\scripts\keycloak\sync-rbac.ps1                                  # admin / admin
.\scripts\keycloak\sync-rbac.ps1 -AdminUser admin -AdminPassword <password>
```

Run it:

- once on an existing Keycloak that was imported before this change;
- again whenever code adds a permission to `Permission` (after adding the client role to
  `realm-fashion-store.json`) so the running realm gets the role and `Admin` gets it.

It reads permissions and groups from `realm-fashion-store.json` and is idempotent: it creates missing client
roles (never deletes extra ones); if `Admin` does not exist it creates `Admin` + `Nhân viên` and puts every user with realm role
`ADMIN` into `Admin` (`Nhân viên` is only created together with `Admin`, so a deleted `Nhân viên` stays deleted);
it always re-adds every permission to `Admin`; if `Customer` does not exist it creates it, maps `USER` and
makes it the default group (existing users are not backfilled).

It also grants `realm-management` `view-clients` to `service-account-identity-service` (identity-service reads the
`fashion-api` client roles with it). On a realm imported earlier this grant is otherwise manual: Admin console → Users →
`service-account-identity-service` → Role mapping → Assign role → `realm-management` → `view-clients`.

### Manual verification checklist

Đã chạy trên Keycloak 26.7.4 thật (2026-11-05): `sync-rbac.ps1` chạy hai lần không lỗi, lần hai không đổi gì; realm có 22 permission + `internal-caller`
và 3 group; qua identity-service với token admin: `GET /permissions` (22, `group:manage` không gán được), tạo nhóm `Nhân viên kho` (tên tiếng Việt giữ nguyên dấu),
thêm rồi bớt permission (`DELETE` kèm body hoạt động), tạo nhân viên, liệt kê thành viên và quyền của nhóm; token customer nhận 403.
**Chưa chạy**: bước 4–6 (đăng nhập qua backoffice-bff, đổi quyền thì staff bị logout), vì cần gateway + BFF + UI.
Các bước cũ bên dưới giữ nguyên để chạy lại khi cần.

The scripts and the Keycloak client were only verified statically and with mocks. Run this once on a dev stack:

1. Start Docker Desktop, then `docker compose config` and `docker compose up -d` (rebuild the services changed by
   the RBAC work: `identity-service`, `order-service`, `catalog-service`, `payment-service`, `api-gateway`,
   `backoffice-bff`). Wait until Keycloak is healthy.
2. `.\scripts\keycloak\sync-rbac.ps1`. Expect no error; run it a second time and expect no changes. In the Admin
   console (realm `fashion-store`) check the `fashion-api` client has the 22 permission roles and the groups
   `Admin`, `Nhân viên`, `Customer` exist (Customer is a default group).
3. In Swagger (gateway, `http://localhost:8080`) authorize as `admin@fashion.local` / `Admin@12345`, then:
   - `GET /api/v1/admin/permissions` returns 22 items, `group:manage` has `isAssignable=false`.
   - `POST /api/v1/admin/groups` with name `Nhân viên kho` and permissions `inventory:read`, `inventory:manage`
     returns 200; `GET /api/v1/admin/groups` lists it with that exact Vietnamese name (check the
     diacritics round-trip, also in the Admin console).
   - `PUT /api/v1/admin/groups/{id}` that adds then removes a permission: the Admin console shows the role mapping
     change both ways. Removal goes through `DELETE` with a body on the group role-mappings endpoint, so confirm
     the permission really disappears (this is only mock-tested).
   - `POST /api/v1/admin/staff` with a new email, `firstName`, `lastName`, a temporary password and that group
     returns 201. `GET /api/v1/admin/groups/{id}/members` and `GET /api/v1/admin/users/{userId}` (its `groups`
     must list the group with its permissions, i.e. `listUserGroups` returns the role data) both show the user.
4. Log in at `http://localhost:8084` (backoffice-bff) as that staff user. Expect Keycloak to force a password
   change on first login. After login `/bff/session` lists `inventory:read`, `inventory:manage`, `ROLE_USER`.
5. With that staff session/token: `GET /admin/inventory` → 200; `GET /api/v1/admin/orders` → 403;
   `GET /api/v1/admin/dashboard` → 403; `GET /api/v1/admin/dashboard/operations` → 403 (no `dashboard:read`).
6. Back as admin: change the group's permissions and confirm the staff user is logged out (session gone) and the
   response has an empty `notLoggedOutUserIds`; try `PUT /api/v1/admin/users/{admin id}/groups` without `Admin`
   (expect 409 `2035`) and delete the `Nhân viên kho` group (staff user logged out again).

## Changing the realm

### Login themes

Docker Compose mounts both `lino` (the realm default for the storefront) and
`backoffice`. The `backoffice-bff` client selects `backoffice` using its
`attributes.login_theme` setting.

After adding a theme mount, recreate only Keycloak:

```bash
docker compose up -d --no-deps --force-recreate keycloak
```

For an existing realm, also update the running configuration in the Admin Console:
select realm `fashion-store`, then Clients → `backoffice-bff` → Settings →
Login theme → `backoffice`, and save. To apply the storefront theme, set
Realm settings → Themes → Login theme → `lino`, and save.
Restarting with `--import-realm` does not update an existing realm; changing these
theme settings does not require deleting the database or users.

Import uses `IGNORE_EXISTING`: once the realm exists, edits to the JSON are **not** applied.
To re-import after editing:

```bash
docker compose rm -sf keycloak keycloak-postgres
docker volume rm fashion-store_keycloak_postgres_data
docker compose up -d keycloak
```

Changes made in the admin console should be exported back into `realm-fashion-store.json`
(Realm settings → Action → Partial export, then strip generated ids/secrets).

Note: when a realm file declares `roles.realm`, Keycloak does not create the built-in
`offline_access` / `uma_authorization` roles — they are declared explicitly.
