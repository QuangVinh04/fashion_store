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
- `fashion-api` client roles: `product:write`, `internal-caller` (granted to the three service accounts).
- Every client adds `fashion-api` to `aud`. The two BFF clients also put `realm_access.roles` and
  `resource_access.fashion-api.roles` into the ID token (read by the BFF `GrantedAuthoritiesMapper`).
- Access token 5 min · SSO idle 30 min · SSO max 7 days · refresh token rotation (reuse revokes the session) · brute force lockout after 5 failures · email verification required.

Client secrets and SMTP come from env (placeholders `${VAR:default}` in the realm file),
defaults are dev-only: `STOREFRONT_BFF_CLIENT_SECRET`, `BACKOFFICE_BFF_CLIENT_SECRET`, `ORDER_SERVICE_CLIENT_SECRET`,
`CATALOG_SERVICE_CLIENT_SECRET`, `IDENTITY_SERVICE_CLIENT_SECRET`, `MAIL_*`.
Without SMTP credentials, self-registered users cannot receive the verification email.

## Dev users (dev only)

| email | password | roles |
|---|---|---|
| `admin@fashion.local` | `Admin@12345` | `ADMIN`, `fashion-api/product:write` |
| `customer@fashion.local` | `Customer@12345` | `USER` |

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
- Realm roles are copied from `user_roles`. Permissions become `fashion-api` client roles only if they
  are listed in the SQL (currently `product:write`).
- A Keycloak account created **before** migration with the same email but a different id gets
  `409 ACCOUNT_LINK_CONFLICT` from identity. Delete that Keycloak account and import the legacy user instead.

## Changing the realm

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
