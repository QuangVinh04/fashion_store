# API Gateway

Spring Cloud Gateway is the only route to the services. It preserves the existing
`/api/v1/...` routes and forwards them to the owning service.

Browsers do not call it directly: they go through `storefront-bff` / `backoffice-bff`,
which relay the Keycloak access token (`TokenRelay`). Port 8080 stays open for
Bearer calls from Swagger UI / Postman.

## Authentication

- Resource server for Keycloak tokens only (`KEYCLOAK_ISSUER`, JWKS `KEYCLOAK_JWK_SET_URI`, `aud` must contain `fashion-api`).
- The `Authorization` header is forwarded unchanged; every service validates the JWT again (zero trust).
  No `X-User-*` headers are set or trusted anywhere.
- `/internal/**` is always denied at the gateway.

## OpenAPI / Swagger UI

The gateway exposes one Swagger UI for all public HTTP APIs at:

- `http://localhost:8080/swagger-ui/index.html`

The selector contains Identity, Catalog, Order, and Payment service documents.
The gateway proxies their OpenAPI JSON at `/identity/v3/api-docs`,
`/catalog/v3/api-docs`, `/order/v3/api-docs`, and `/payment/v3/api-docs`.
Internal `/internal/**` endpoints are intentionally excluded from these public
documents.
