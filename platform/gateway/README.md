# API Gateway

Spring Cloud Gateway is the only backend component exposed to clients. It
preserves the existing `/api/v1/...` routes and forwards them to the owning
service.

## OpenAPI / Swagger UI

The gateway exposes one Swagger UI for all public HTTP APIs at:

- `http://localhost:8080/swagger-ui/index.html`

The selector contains Identity, Catalog, Order, and Payment service documents.
The gateway proxies their OpenAPI JSON at `/identity/v3/api-docs`,
`/catalog/v3/api-docs`, `/order/v3/api-docs`, and `/payment/v3/api-docs`.
Internal `/internal/**` endpoints are intentionally excluded from these public
documents.
