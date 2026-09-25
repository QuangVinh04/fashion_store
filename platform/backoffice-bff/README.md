# backoffice-bff

Backend-for-Frontend for the backoffice SPA (Spring Cloud Gateway, WebFlux). It is the OAuth2 client:
the browser only holds an HttpOnly session cookie, and access/refresh tokens stay in the server-side session (Redis).

```
Browser ──cookie──► backoffice-bff :8084 ──TokenRelay (Bearer)──► api-gateway :8080 ──► services
                         └── oauth2Login ──► Keycloak realm fashion-store (client backoffice-bff, PKCE S256)
```

## Endpoints for the SPA

| path | purpose |
|---|---|
| `GET /oauth2/authorization/keycloak` | start login (full-page navigation) |
| `GET /bff/session` | `{authenticated, userId, email, name, roles}`, never returns tokens |
| `POST /logout` | ends the BFF session and the Keycloak SSO session, then redirects to `/` |
| `/api/**` | proxied to api-gateway with `Authorization: Bearer <access token>` when logged in |
| `/**` | proxied to the SPA dev server / static host (`BACKOFFICE_UI_URL`, default `http://localhost:5173`) so SPA and BFF share one origin |

Everything except `/bff/session` and `/actuator/health/**` requires `ROLE_ADMIN` **at the BFF**, so a non-admin token is never relayed. Unauthenticated `/api/**` returns 401; other paths redirect to Keycloak.

`/api/v1/auth/**` is denied: registration, login, password reset and account management go through Keycloak.

## CSRF

Session-cookie auth requires CSRF protection. The BFF writes a readable cookie `FS_BACKOFFICE_XSRF`.
The SPA must send its value in the `X-XSRF-TOKEN` header on every POST/PUT/PATCH/DELETE, including `/logout`.

## Cookies

Cookies ignore ports, so each BFF on `localhost` uses its own names:
`FS_BACKOFFICE_SESSION` (HttpOnly, SameSite=Lax, `Secure` via `SESSION_COOKIE_SECURE`) and `FS_BACKOFFICE_XSRF`.

## Configuration

| env | default |
|---|---|
| `SERVER_PORT` | `8084` |
| `API_GATEWAY_URL` | `http://localhost:8080` |
| `BACKOFFICE_UI_URL` | `http://localhost:5173` |
| `KEYCLOAK_PUBLIC_URL` | `http://localhost:8180/realms/fashion-store` (browser redirects, token `iss`) |
| `KEYCLOAK_INTERNAL_URL` | same; in Docker `http://keycloak:8080/realms/fashion-store` (token, JWKS, userinfo) |
| `BACKOFFICE_BFF_CLIENT_SECRET` | `dev-backoffice-bff-secret` (dev only) |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |

Keycloak endpoints are configured explicitly in `config/OAuth2ClientConfig` instead of discovery,
because the issuer (`localhost:8180`) differs from the internal URL the BFF calls.
