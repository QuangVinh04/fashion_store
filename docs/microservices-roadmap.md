# Fashion Store Microservices Roadmap

The active backlog is tracked in [refactor-plan.md](refactor-plan.md). This file
describes architecture, service ownership, and required patterns.

## Target services

- `identity-service`: authentication, users, roles, permissions, and self-issued JWT.
- `product-service`: products, variants, categories, product search.
- `inventory-service`: stock, reservation, stock deduction, stock release.
- `cart-service`: user carts and cart items.
- `order-service`: checkout, orders, order status workflow.
- `payment-service`: payment records, VNPAY, PayPal, COD, callbacks.
- `notification-service`: email verification and order/payment notifications.

## Extracted services

- `platform/gateway`: Spring Cloud Gateway and the only public backend entry point.
- `services/identity-service`: users, credentials, JWT signing with a private RSA key, and a public JWKS endpoint.
- `services/notification-service`: asynchronous email delivery.
- `services/payment-service`: owns payment tables and serializes payment create/cancel commands for each order through RabbitMQ.
- `services/inventory-service`: standalone service scaffold. It owns inventory tables. The saga reservation consumer is currently missing — see the P0 debt note in [refactor-plan.md](refactor-plan.md).
- `services/product-service`: standalone service scaffold. It owns product/category/variant tables. Note: it does **not** currently publish `product.variant.stock` — the inventory-side consumer exists but has no producer.
- `services/cart-service`: standalone service scaffold. It owns cart tables, calls product/inventory through HTTP clients, and uses JWT `userId` for ownership.
- `services/order-service`: owns checkout/order tables and orchestrates inventory, payment, compensation, and cart cleanup through saga events.

## Current architecture

The backend runs as a Maven multi-module microservice platform:

- Product publishes `ProductVariantStockEvent`; `OutboxEventRecorder` stores it in `outbox_event`.
- `OutboxEventRelay` publishes pending outbox rows to RabbitMQ and retries failed publishes.
- Inventory consumes product stock messages from RabbitMQ.
- Inventory stores only `variantId`, not a `ProductVariant` entity relation.
- Cart and order items store product variant snapshots instead of JPA relations to `ProductVariant`.
- Cart now runs as its own service and calls product/inventory via HTTP clients instead of direct module references.
- Order starts at `PENDING_INVENTORY`, requests a stock reservation, then requests payment after inventory accepts the order.
- Payment success moves the order to `CONFIRMING_INVENTORY`; the order becomes `CONFIRMED` only after inventory acknowledges the reservation commit.
- Payment failure moves the order to `RELEASING_INVENTORY`; the terminal failure status is applied only after inventory acknowledges the release.
- Payment timeout uses a cancellation handshake. A completed payment rejects cancellation so the order proceeds instead of releasing paid stock.
- Inventory and payment timeout handlers use pessimistic order/payment locks to serialize callbacks with compensation.
- Late inventory reservation responses for cancelled orders are released automatically.
- Payment stores `userId` and publishes payment outcome messages instead of reading or updating orders directly.
- Payment method/provider enums live in `common.payment` as an API contract, not inside payment entities.
- RabbitMQ is now the cross-module event transport and Outbox Pattern is in place for publish reliability.
- RabbitMQ consumers use `processed_message` to make message handling idempotent across retries/redeliveries.
- Shared event payloads are owned by `packages/api-contracts`.
- The gateway is owned by `platform/gateway`.
- Identity stores its RSA private key as PKCS#8 PEM and its public key as X.509 PEM on a persistent volume.
- Only identity uses the private key to sign RS256 access tokens; gateway and downstream services verify them through the public JWKS endpoint.
- JWTs and JWKS entries share a stable `kid` derived from the public key, allowing verifiers to select the correct key.
- Planned services are documented under `services/` but are not added to the reactor until they have executable code.

## Local RabbitMQ

Run RabbitMQ locally:

```bash
docker compose up -d rabbitmq
```

RabbitMQ management UI:

- URL: `http://localhost:15672`
- Username: `guest`
- Password: `guest`

## Next service boundaries

Planned boundaries only. They have no code and are not part of the Maven reactor.
The placeholder directories under `services/` were removed; their intent is
recorded here instead.

1. `customer-service`: planned owner of customer profiles, addresses, preferences,
   and customer lifecycle data. Authentication credentials remain owned by
   `identity-service`.
2. `search-service`: planned read-optimized product search boundary. It should
   consume product events rather than query the product database directly.
3. `recommendation-service`: planned recommendation boundary. It may use Python,
   but its public API and events must be versioned in `packages/api-contracts`.

See [refactor-plan.md](refactor-plan.md) before adding any of these: the current
decomposition is being consolidated from 8 services down to 5, so a new boundary
needs to justify its operational cost first.

## Required patterns

- Domain events for cross-module side effects.
- Outbox pattern for reliable event publishing.
- Saga pattern for order placement.
- Idempotency keys for checkout, order placement, payment initiation, and payment callbacks.
- Anti-corruption clients such as `ProductClient`, `InventoryClient`, `PaymentClient`, and `CartClient`.
