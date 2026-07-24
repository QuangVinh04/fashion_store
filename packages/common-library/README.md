# Common Library

Stable technical primitives shared by Spring Boot services:

- `dto`: standard API and pagination response wrappers.
- `persistence`: UUID base entities with optional audit columns.
- `exception`: standard error payload and handler; each service still owns its
  business error codes.
- `security`: JWT current-user and access-token providers.
- `web`: correlation ID servlet filter, enabled through auto-configuration.
- `messaging.processed`: idempotent consumer support, enabled explicitly with
  `@EnableProcessedMessages`.
- `messaging.outbox`: common outbox status vocabulary.
- `payment`: payment method/provider vocabulary shared by order and payment.
- `util`: slug and verification-code helpers.

Add the dependency to a service:

```xml
<dependency>
    <groupId>com.fashionstore</groupId>
    <artifactId>common-library</artifactId>
</dependency>
```

Domain entities, business repositories, service-specific DTOs, error-code
catalogs, and outbox publishing policy must remain owned by their service.

Service-specific error codes implement the shared contract:

```java
public enum ErrorCode implements ErrorDescriptor {
    CART_EMPTY(3002, "Cart is empty", HttpStatus.BAD_REQUEST);
}

throw new AppException(ErrorCode.CART_EMPTY);
```

All servlet services return the same error shape:

```json
{
  "code": 3002,
  "status": 400,
  "message": "Cart is empty",
  "path": "/api/v1/cart/checkout",
  "correlationId": "5f5bf4e8-05c8-43f6-bccb-1182cbcf81ee",
  "timestamp": "2026-06-07T10:00:00Z"
}
```
