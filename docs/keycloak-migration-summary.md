# Tổng kết: chuyển cơ chế xác thực sang Keycloak + BFF

Ngày: 2026-09-25

Mục tiêu: bỏ cơ chế xác thực tự viết trong identity-service (tự ký JWT, tin header `X-User-*`,
secret nội bộ `X-Internal-Token`). Thay bằng mô hình giống dự án tham chiếu [YAS](https://github.com/nashtech-garage/yas):
Keycloak cấp token, BFF giữ token phía server, mọi service tự kiểm tra JWT.

## Kiến trúc sau thay đổi

```text
Browser ──cookie HttpOnly──► storefront-bff :8083 / backoffice-bff :8084
                                  │  oauth2Login (Authorization Code + PKCE), token lưu trong session Redis
                                  │  TokenRelay → Authorization: Bearer <JWT Keycloak>
                                  ▼
                             api-gateway :8080 (kiểm tra JWT, chuyển tiếp nguyên token)
                                  ▼
                   identity / catalog / order / payment (mỗi service tự kiểm tra lại JWT)

Service ↔ Service /internal/**: token service account (client_credentials, role internal-caller)
Keycloak :8180 (realm fashion-store) là nơi DUY NHẤT cấp token
```

## Trước và sau

| | Trước | Sau |
|---|---|---|
| Nơi cấp token | identity-service tự ký RSA | Keycloak |
| Token ở trình duyệt | SPA giữ access token, refresh token nằm trong cookie | không có, chỉ có cookie session của BFF |
| Nơi kiểm tra token | chỉ gateway | gateway và từng service |
| Cách service biết user | tin header `X-User-Id` / `X-User-Roles` | JWT gốc (`sub`, `realm_access`, `resource_access`) |
| Gọi nội bộ | secret tĩnh `X-Internal-Token` | `client_credentials` + role `internal-caller` |
| Đăng xuất / thu hồi | blacklist trong Redis (không chạy khi đi qua gateway) | OIDC RP-initiated logout; xoay vòng refresh token |
| Đăng ký, đổi/quên mật khẩu, MFA, chống dò mật khẩu | tự viết một phần | Keycloak |

## Các bước đã hoàn thành

### Bước 1: Hạ tầng Keycloak
- Thêm container `keycloak` (26.7.4, port `8180`) và `keycloak-postgres` vào `docker-compose.yml`.
- Realm `platform/keycloak/realm-fashion-store.json` được import tự động khi khởi động:
  - Clients: `storefront-bff`, `backoffice-bff` (confidential + PKCE S256), `swagger-ui` (public),
    `order-service` / `catalog-service` / `identity-service` (chỉ service account), `fashion-api` (audience + client roles).
  - Roles: realm `USER` (mặc định), `ADMIN`; client role của `fashion-api`: `product:write`, `internal-caller`.
  - Chính sách: access token 5 phút, SSO idle 30 phút / max 7 ngày, xoay vòng refresh token (dùng lại token cũ thì cả session bị huỷ),
    khoá tài khoản sau 5 lần sai mật khẩu, bắt buộc xác minh email, ngôn ngữ mặc định `vi`.
  - User dev: `admin@fashion.local` / `Admin@12345`, `customer@fashion.local` / `Customer@12345`.
- Về issuer: `iss = http://localhost:8180/realms/fashion-store` (URL trình duyệt thấy), còn JWKS lấy qua đường nội bộ `http://keycloak:8080`
  (`KC_HOSTNAME_BACKCHANNEL_DYNAMIC`). Vì vậy mọi nơi đều cấu hình `issuer-uri` và `jwk-set-uri` riêng, không dùng discovery.

### Bước 2: Hai BFF làm OAuth2 Client (giống YAS)
- Module mới `platform/storefront-bff` (:8083) và `platform/backoffice-bff` (:8084), chạy Spring Cloud Gateway (WebFlux):
  - `oauth2Login` có PKCE; token lưu trong WebSession trên Redis (`WebSessionServerOAuth2AuthorizedClientRepository`).
  - `TokenRelay` + `SaveSession` khi chuyển tiếp `/api/**` tới api-gateway; `/**` chuyển tới dev server của UI (SPA chạy cùng origin với BFF).
  - CSRF bằng cookie (`FS_*_XSRF`, SPA gửi lại qua header `X-XSRF-TOKEN`); cookie session HttpOnly, SameSite=Lax.
    Mỗi BFF dùng tên cookie riêng vì cookie không phân biệt port.
  - Đăng xuất qua OIDC RP-initiated logout (`end_session` của Keycloak).
  - `GET /bff/session` trả về `{authenticated, userId, email, name, roles}`, không bao giờ trả token.
  - `ClientRegistration` khai báo endpoint thủ công, có `issuerUri` (để kiểm tra `iss` của ID token) và `end_session_endpoint`.
  - backoffice-bff chặn ngay tại BFF những ai không có `ROLE_ADMIN`: gọi `/api/**` chưa đăng nhập → 401, mở trang UI → chuyển sang Keycloak.
- common-library: `KeycloakJwtAuthoritiesConverter` chuyển `realm_access.roles` thành `ROLE_*` và giữ nguyên role của `fashion-api`.

### Bước 3: Mọi service là Resource Server
- common-library tự tạo bean `JwtAuthenticationConverter` dùng converter Keycloak. Các service chỉ cần
  `.oauth2ResourceServer(rs -> rs.jwt(withDefaults()))`.
- identity, catalog, order, payment: cấu hình `spring.security.oauth2.resourceserver.jwt.{issuer-uri, jwk-set-uri, audiences=fashion-api}`.
  Các luật `hasRole` / `@PreAuthorize` giữ nguyên.
- api-gateway: chỉ tin issuer Keycloak, chuyển tiếp nguyên header `Authorization`, không còn gắn hay tin `X-User-*`.

### Gỡ bỏ cơ chế xác thực cũ
- identity-service:
  - Controller: `AuthController` (register, login, refresh, logout, verify-email, resend), `JwkController`,
    endpoint `PUT /api/v1/users/change-password`.
  - Service và client: `AuthService(Impl)`, `JwtService`, `EmailService(Impl)`, `CustomUserDetails(Service)`, `OrderServiceClient`.
  - Khác: `RsaKeyPairStore` / `RsaKeyMaterial`, `CookieUtils`, các DTO `dto/auth/*`, bean `JwtEncoder` / `JwtDecoder` / `PasswordEncoder` /
    `AuthenticationManager`, cấu hình `security.jwt.*`.
- api-gateway: `GatewayUserContextFilter`, bộ kiểm tra token cũ, `RedisConfig`, route `/api/v1/auth/**`.
- common-library: `GatewayHeaderAuthenticationFilter`, `JwtBlacklistValidator`.
- `docker-compose.yml`: các biến `JWT_*`, volume `identity-key`.

### Bước 4: Gọi nội bộ bằng `client_credentials`
- common-library:
  - `InternalServiceTokenInterceptor` (Feign): lấy token service account, không lấy được token thì báo lỗi luôn (fail closed).
  - Auto-config tạo `AuthorizedClientServiceOAuth2AuthorizedClientManager`, chạy được cả ngoài HTTP request (listener, scheduler).
- order và catalog: thêm `spring-boot-starter-oauth2-client` và registration `client_credentials`. Interceptor chỉ gắn vào Feign client nội bộ
  (order→catalog, order→identity, catalog→order); GHN và VNPay không nhận token.
- identity, catalog, order: `/internal/**` yêu cầu `hasAuthority("internal-caller")`.
- Đã gỡ: `InternalTokenAuthFilter`, `app.internal.secret-token`, biến `INTERNAL_SERVICE_TOKEN`.

### Bước 5: JIT provisioning và ghép dữ liệu user cũ
- Quy ước: `users.id` trong identity bằng `sub` của token Keycloak.
- `UserProvisioningService(Impl)`: request đầu tiên của user tạo bản ghi từ JWT (transaction `REQUIRES_NEW`,
  insert bằng câu SQL native vì `@GeneratedValue(UUID)` của Hibernate 6.6 không nhận id gán sẵn).
  - Email đồng bộ theo Keycloak; họ tên sửa trong hồ sơ thì không bị ghi đè.
  - Hai request đầu tiên chạy song song vẫn chỉ tạo một bản ghi.
  - Email trùng với user cũ khác id → `409 ACCOUNT_LINK_CONFLICT`.
  - Token service account (không có email) không bao giờ được tạo user.
- Migration `V5__keycloak_managed_credentials.sql`: bỏ `NOT NULL` ở cột `users.password`.
- `platform/keycloak/migrate-identity-users.sql`: xuất user cũ thành payload `partialImport` cho Keycloak, **giữ nguyên id**.
  Nhờ vậy `userId` đã lưu ở order, catalog, payment và dữ liệu hồ sơ, địa chỉ vẫn khớp. Cách chạy: `platform/keycloak/README.md`.

## Kiểm thử

- TDD cho từng bước. Build toàn bộ reactor `./mvnw -o clean install`: **12 module, 423 test, tất cả xanh**.
- Test mới chính:
  - common-library: `KeycloakJwtAuthoritiesConverterTest`, `CommonSecurityAutoConfigurationTest`, `InternalServiceTokenInterceptorTest`.
  - api-gateway: `SecurityConfigTest`, test tích hợp có JWKS giả và backend giả; kiểm tra token được chuyển tiếp nguyên vẹn.
  - Hai BFF: `*BffSecurityTest` (PKCE, TokenRelay, CSRF, logout, phân quyền), `KeycloakAuthoritiesMapperTest`, `SessionControllerTest`.
  - identity, catalog, order, payment: `SecurityConfigTest` chạy qua chuỗi security thật, gồm các trường hợp: không có token, header giả mạo,
    token sai, sai role, token có `internal-caller`, `X-Internal-Token` cũ bị từ chối.
  - identity: `UserProvisioningServiceImplTest`, `CurrentUserProviderTest`.
- Kiểm tra thật bằng Docker (giả lập trình duyệt bằng curl):
  - Đăng nhập qua cả hai BFF; relay token tới order, catalog, identity; phân quyền ADMIN / USER; CSRF; đăng xuất kết thúc session Keycloak.
  - Header `X-User-*` giả mạo và `X-Internal-Token` cũ đều bị 401, kể cả khi gọi thẳng vào service trong mạng Docker.
  - Import một user cũ sang Keycloak giữ nguyên id: đăng nhập thấy đủ hồ sơ và địa chỉ cũ. User mới được tạo tự động; trùng email → 409.

## Tài liệu đã cập nhật

`CLAUDE.md` (bảng port, cách client đi vào hệ thống, mô tả auth), `platform/keycloak/README.md`, `platform/gateway/README.md`,
`platform/storefront-bff/README.md`, `platform/backoffice-bff/README.md`, `services/catalog-service/README.md`.

## Việc còn lại / lưu ý

- **Mật khẩu BCrypt không chuyển được sang Keycloak:** user cũ phải đặt lại mật khẩu. Cần cấu hình SMTP thật (`MAIL_*`)
  để có email xác minh và email quên mật khẩu.
- **SPA phải tự làm hai việc:**
  - Gửi `X-XSRF-TOKEN` kèm mọi request thay đổi dữ liệu.
  - Gọi `POST /api/v1/cart/merge` sau khi đăng nhập (identity không còn tự gộp giỏ hàng khi login).
- **Một số lời gọi Feign trỏ vào API công khai chứ không phải `/internal`** (lỗi có từ trước):
  - `/api/v1/inventory/restock/{id}` cần `ADMIN`.
  - `/api/v1/users/addresses/{id}` kiểm tra quyền sở hữu theo user hiện tại.

  Cần endpoint `/internal` tương ứng.
- **Client `identity-service` trong realm vẫn có role `internal-caller`** dù không còn dùng. Nên gỡ đi (quyền tối thiểu).
- **Chưa gỡ, có chủ đích:**
  - Bảng `roles` / `permissions` và cột `password` trong `identity_database`: chờ migrate xong user cũ.
  - Redis config và `NotificationOutbox` trong identity: giờ không còn dùng.
  - Consumer email xác minh ở notification-service.
- **Hai BFF lặp lại khoảng 150 dòng cấu hình security** (giống YAS). Tách thành thư viện chung nếu thấy cần.
- **Lỗi có từ trước, không liên quan task này:**
  - payment-service crash khi khởi động (JPQL trong `PaymentRefundRepository`).
  - Webhook `/api/v1/payments/payos/webhook` chưa có trong danh sách `permitAll` của gateway.
  - Không pull được image MinIO từ `quay.io`.
