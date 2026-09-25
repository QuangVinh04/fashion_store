# storefront-bff

Backend-for-Frontend for the storefront SPA (Spring Cloud Gateway, WebFlux). It is the OAuth2 client:
the browser only holds an HttpOnly session cookie, and access/refresh tokens stay in the server-side session (Redis).

```
Browser ──cookie──► storefront-bff :8083 ──TokenRelay (Bearer)──► api-gateway :8080 ──► services
                         └── oauth2Login ──► Keycloak realm fashion-store (client storefront-bff, PKCE S256)
```

## Endpoints for the SPA

| path | purpose |
|---|---|
| `GET /oauth2/authorization/keycloak` | start login (full-page navigation) |
| `GET /bff/session` | `{authenticated, userId, email, name, roles}`, never returns tokens |
| `POST /logout` | ends the BFF session and the Keycloak SSO session, then redirects to `/` |
| `/api/**` | proxied to api-gateway with `Authorization: Bearer <access token>` when logged in |
| `/**` | proxied to the SPA dev server / static host (`STOREFRONT_UI_URL`, default `http://localhost:3000`) so SPA and BFF share one origin |

Every path is `permitAll` at the BFF; api-gateway and the services authorize the relayed token. Anonymous browsing (products, guest cart) is proxied without a token.

`/api/v1/auth/**` is denied: registration, login, password reset and account management go through Keycloak.

## CSRF

Session-cookie auth requires CSRF protection. The BFF writes a readable cookie `FS_STOREFRONT_XSRF`.
The SPA must send its value in the `X-XSRF-TOKEN` header on every POST/PUT/PATCH/DELETE, including `/logout`.

## Cookies

Cookies ignore ports, so each BFF on `localhost` uses its own names:
`FS_STOREFRONT_SESSION` (HttpOnly, SameSite=Lax, `Secure` via `SESSION_COOKIE_SECURE`) and `FS_STOREFRONT_XSRF`.

## Configuration

| env | default |
|---|---|
| `SERVER_PORT` | `8083` |
| `API_GATEWAY_URL` | `http://localhost:8080` |
| `STOREFRONT_UI_URL` | `http://localhost:3000` |
| `KEYCLOAK_PUBLIC_URL` | `http://localhost:8180/realms/fashion-store` (browser redirects, token `iss`) |
| `KEYCLOAK_INTERNAL_URL` | same; in Docker `http://keycloak:8080/realms/fashion-store` (token, JWKS, userinfo) |
| `STOREFRONT_BFF_CLIENT_SECRET` | `dev-storefront-bff-secret` (dev only) |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |

Keycloak endpoints are configured explicitly in `config/OAuth2ClientConfig` instead of discovery,
because the issuer (`localhost:8180`) differs from the internal URL the BFF calls.
