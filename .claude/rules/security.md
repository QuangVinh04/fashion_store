# Security backend

- Keycloak realm `fashion-store` (`http://localhost:8180`, `platform/keycloak/README.md`) là nơi duy nhất cấp token.
- api-gateway và mọi service tự validate JWT Keycloak được relay (resource server, `aud=fashion-api`).
- identity-service không login user, không cấp token. Service này JIT-provision dòng `users` ở request đã xác thực đầu tiên (`users.id` = Keycloak `sub`).
- `/internal/**` yêu cầu token service-account có client role `internal-caller`. Feign dùng `InternalServiceTokenInterceptor` (`client_credentials`). Chỉ gắn interceptor này vào Feign client nội bộ, không gắn vào client bên ngoài (GHN, VNPay…).
- Không nới lỏng security để code chạy được. `SecurityConfig` chain, JWT decoding, rule `hasRole`/`hasAuthority` là behaviour, không phải boilerplate.
