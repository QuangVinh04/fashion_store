# Luồng xác thực (Keycloak)

Tài liệu ôn tập: bốn luồng và bảng claim. Chi tiết cấu hình realm, vận hành và production: [`platform/keycloak/README.md`](../platform/keycloak/README.md).
Quy tắc bảo mật cho code: [`.claude/rules/security.md`](../.claude/rules/security.md).

Ý chính:

- **Keycloak là nơi duy nhất cấp token.** Trình duyệt không bao giờ cầm token: BFF giữ token trong session (Redis), trình duyệt chỉ có cookie session `HttpOnly`.
- **Mỗi hop tự kiểm tra lại JWT** (chữ ký bằng JWKS, `iss`, `aud=fashion-api`). Gateway chuyển tiếp token nguyên vẹn, service không tin gateway.
- Phân quyền = role trong token: `realm_access.roles` thành `ROLE_*`, `resource_access.fashion-api.roles` thành permission (`order:read`, `group:manage`…).

## 1. Đăng nhập qua BFF (Authorization Code + PKCE)

```mermaid
sequenceDiagram
    autonumber
    actor U as Trình duyệt
    participant B as BFF (storefront / backoffice)
    participant K as Keycloak
    participant G as API Gateway
    participant S as Service (vd order-service)

    U->>B: GET /oauth2/authorization/keycloak
    B-->>U: 302 tới Keycloak (code_challenge S256, state)
    U->>K: đăng nhập (backoffice: mật khẩu + OTP bắt buộc)
    K-->>U: 302 về BFF /login/oauth2/code/keycloak?code=...
    U->>B: code
    B->>K: đổi code lấy token (code_verifier + client_secret)
    K-->>B: access token + ID token + refresh token
    Note over B: Lưu token vào session Redis.<br/>Quyền lấy từ ID token (realm_access, resource_access).
    B-->>U: Set-Cookie session (HttpOnly, SameSite=Lax, Secure ở prod)

    U->>B: /api/... (cookie + header X-XSRF-TOKEN)
    B->>G: TokenRelay: thêm Authorization Bearer access token
    G->>G: kiểm JWT, /admin/** cần ít nhất một permission, /internal/** bị chặn
    G->>S: chuyển tiếp nguyên token
    S->>S: kiểm JWT lần nữa, @PreAuthorize theo permission
    S-->>U: dữ liệu (qua G và B)
```

- PKCE bật cho cả client bí mật (realm bắt buộc `S256`); không có direct grant, không có implicit.
- CSRF: BFF ghi cookie XSRF (tên riêng mỗi BFF vì cookie không phân biệt cổng); SPA gửi lại giá trị qua header.
- Access token sống 5 phút, refresh token xoay vòng (dùng lại token cũ thì cả phiên bị huỷ).

## 2. Service gọi service (`client_credentials`)

```mermaid
sequenceDiagram
    autonumber
    participant A as order-service
    participant K as Keycloak
    participant C as catalog-service

    A->>K: client_credentials (client order-service + secret)
    K-->>A: access token của service account (client role internal-caller)
    Note over A: Token được cache, tự lấy lại khi hết hạn.<br/>Dùng được cả ngoài request (listener, scheduler).
    A->>C: GET /internal/... (InternalServiceTokenInterceptor gắn Bearer)
    C->>C: kiểm JWT, /internal/** cần authority internal-caller
    C-->>A: dữ liệu
```

- Gateway chặn hẳn `/internal/**` từ ngoài; chỉ service nội bộ gọi nhau trực tiếp.
- Token user (kể cả admin) gọi `/internal/**` bị 403; token service account không có realm role nên không vào được API của user.
- Interceptor chỉ gắn cho Feign client nội bộ, không gắn cho client ra ngoài (GHN, payOS). Lấy token thất bại là sự cố hạ tầng: 502, không phải 500.

## 3. Back-channel logout (admin khoá tài khoản, đăng xuất ở nơi khác)

```mermaid
sequenceDiagram
    autonumber
    actor Ad as Admin
    participant K as Keycloak
    participant B as BFF
    participant R as Redis
    actor U as Trình duyệt của user

    Note over B,R: Lúc đăng nhập: BFF lưu sid (phiên SSO Keycloak) -> session BFF<br/>(RedisReactiveOidcSessionRegistry), kèm index theo sub
    Ad->>K: khoá user / đăng xuất phiên
    K->>B: POST /logout/connect/back-channel/keycloak (logout_token JWT có sid)
    B->>K: lấy JWKS, kiểm chữ ký, iss, aud của logout_token
    B->>R: tra sid (không có sid thì tra sub), kiểm issuer + audience
    R-->>B: session BFF cần huỷ
    B->>B: xoá session
    U->>B: request tiếp theo
    B-->>U: 401, phải đăng nhập lại
```

- Registry nằm trong Redis nên còn nguyên sau khi BFF restart và dùng chung giữa nhiều instance. Bản in-memory trước đây mất bảng này, user bị khoá vẫn dùng tiếp tới khi session hết hạn.
- Token giả mạo (ký bằng khoá khác) bị 400 và không xoá session nào.

## 4. Keycloak báo hệ thống: tạo user và đổi email

```mermaid
sequenceDiagram
    autonumber
    actor U as Người dùng
    participant K as Keycloak (+ Event Listener SPI)
    participant Q as RabbitMQ
    participant I as identity-service

    U->>K: đăng ký tài khoản
    K->>K: tạo user, commit transaction
    K-)Q: keycloak.user.registered (thread riêng, sau commit)
    Q->>I: identity.keycloak-user-events
    I->>I: provision(sub, email, tên): tạo users, id = sub

    Note over I: Nếu event mất (at-most-once) thì request đầu tiên của user<br/>vẫn tạo dòng users (JIT từ token).
    Note over K,I: Admin sửa user trong Keycloak -> keycloak.user.email-updated -><br/>identity đồng bộ email. Lỗi vĩnh viễn (email thuộc user khác) -> identity.dlq.
```

## Bảng claim: claim nào dùng ở đâu

| Claim | Nguồn | Dùng để |
|---|---|---|
| `iss` | Keycloak (URL browser thấy, `KC_HOSTNAME`) | Mọi resource server kiểm `issuer`; JWKS lấy qua đường nội bộ nên `issuer-uri` và `jwk-set-uri` cấu hình riêng |
| `aud` = `fashion-api` | mapper `fashion-api-audience` ở mọi client | Resource server từ chối token cấp cho đối tượng khác |
| `sub` | Keycloak | = `users.id` của identity; khoá để service khác tham chiếu user |
| `email`, `email_verified`, `name` | scope `email` / `profile` | JIT provisioning tạo dòng `users`; service account không có `email` nên không bao giờ thành user |
| `realm_access.roles` | realm role | `ROLE_USER`, `ROLE_ADMIN` (`KeycloakJwtAuthoritiesConverter`) |
| `resource_access.fashion-api.roles` | client role, kể cả role từ group | Permission (`order:read`, `group:manage`…) cho `@PreAuthorize` và gateway; `internal-caller` cho `/internal/**` |
| `sid` | Keycloak (phiên SSO) | Khoá của registry back-channel logout |
| `exp` (5 phút) | Keycloak | Access token hết hạn nhanh; resource server từ chối token quá hạn |

## Thử lại các luồng này

| Muốn xem | Cách |
|---|---|
| Nội dung token | Lấy token (Swagger UI Authorize, hoặc `client_credentials` của service account) rồi giải mã ở jwt.io, đối chiếu bảng trên |
| Luồng 2 và kiểm tra `iss`/`aud`/chữ ký | `KeycloakRealmIntegrationTest` (identity-service): dùng Keycloak thật qua Testcontainers |
| Luồng 3 | `RedisReactiveOidcSessionRegistryRedisIntegrationTest` (common-library) và `*BackChannelLogoutTest` (hai BFF) |
| Luồng 4 | `docker compose up -d --build keycloak rabbitmq identity-service`, đăng ký user, xem bảng `users` |
| Event / lịch sử đăng nhập | Admin console của Keycloak, mục Events (đã bật, giữ 30 ngày) |
