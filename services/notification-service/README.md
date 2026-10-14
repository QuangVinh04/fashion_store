# Notification service

Sends existing transactional emails and persists customer website notifications. Order confirmation, shipping, delivery, cancellation and return/refund updates are emitted by order-service through its transactional outbox using `notification.in-app.requested`. In-app notifications work even if the order has no email address; anonymous checkout continues to use email only.

## Website flow

Open the storefront through `http://localhost:8083`. The header bell appears after login and shows unread count, history pages and read actions. Clicking a notification marks it read and opens its order. “Đọc tất cả” marks the current user's unread notifications read. No browser token storage is needed: the BFF session and TokenRelay authenticate REST and the WebSocket upgrade through api-gateway. Both gateway and notification-service validate Keycloak JWTs.

| Endpoint | Behavior |
| --- | --- |
| `GET /api/v1/notifications?pageNo=1&pageSize=10` | Own history and unread count; page size 1–50 |
| `PATCH /api/v1/notifications/{id}/read` | Own notification only; idempotent; otherwise 404 |
| `PATCH /api/v1/notifications/read-all` | Mark own unread notifications read |
| `WS /api/v1/notifications/ws` | Receive `{ "type": "notifications.changed" }` after a database commit |

REST uses the shared `ApiResponse` envelope. List data is `{page: {pageNo, pageSize, totalPage, totalElements, items}, unreadCount}`. Items contain `id`, `type`, `title`, `message`, `orderId`, ISO `createdAt` and nullable ISO `readAt`. Read actions keep the BFF's existing CSRF requirement.

The socket is receive-only: clients cannot choose a recipient or send mutations. The frontend reloads history on connection, reconnect and change signals, ignores stale REST responses, and closes the socket on unmount/account change. Server heartbeats run every 25 seconds and close expired JWT connections; every live signal also checks expiry. The shared processed-message guard deduplicates Rabbit deliveries in the same transaction as persistence. Rollbacks do not send signals.

## Configuration and startup

`NOTIFICATION_ALLOWED_ORIGINS` is a comma-separated list of exact browser origins, defaulting to `http://localhost:8083,http://127.0.0.1:8083`. Configure the deployed storefront origin when using HTTPS/custom domains. Origin validation follows the [Spring WebSocket API](https://docs.spring.io/spring-framework/reference/6.2/web/websocket/server.html).

The BFF WebSocket route uses `API_GATEWAY_WS_URL` (local default `ws://localhost:8080`; Compose sets `ws://api-gateway:8080`). Gateway uses `NOTIFICATION_WS_URL` (default `ws://notification-service:8090`) and `NOTIFICATION_BASE_URL` (default `http://notification-service:8090`). Set both notification URLs to localhost when running these services outside Docker. The routes use Spring Cloud Gateway's [WebSocket routing filter](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/global-filters.html).

Build the changed modules with Java 21 before rebuilding Compose images:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
.\mvnw.cmd -pl services/notification-service,services/order-service,platform/gateway,platform/storefront-bff -am -DskipTests package
docker compose up -d --build notification-service order-service api-gateway storefront-bff
```

Flyway adds `V3__in_app_notifications.sql`; existing migrations are preserved. Existing orders are not backfilled; notifications begin with events emitted after this version is deployed. No sample notifications are shipped. The bell reuses storefront Popover/Button and existing color/font tokens; no new theme is introduced.

This version supports **one notification-service instance** for live delivery. Database history survives service restarts and missed signals. Multiple replicas require shared live fanout (for example Redis Pub/Sub) before scaling.

## Verification

Tests cover producer milestones, missing email/anonymous checkout, owned history and read actions, duplicate consumer delivery, transactional rollback, ISO JSON timestamps, authenticated sockets, rejected origins, account isolation and token expiry. A gateway test performs a real WebSocket upgrade and verifies Bearer/Origin forwarding; the BFF test verifies TokenRelay and CSRF on read actions. Frontend tests cover API paths, same-origin ws/wss, reconnect and disposal.

Browser checks use temporary API/WebSocket fixtures against the actual Vite app at 375px and 1440px: history, pagination, new signal refresh, read-all, empty/error/loading. Screenshots and the UI probe report are under `reports/notifications-ui` and `reports/notifications-probe` (ignored QA artifacts).

Validation limits in this workspace: Docker is unavailable, so the existing broker/Postgres container suites and complete Keycloak → BFF → gateway → RabbitMQ → Postgres flow cannot be verified. The full common-library suite currently stops in `ConfirmedRabbitSenderTest` while inspecting a quoted PATH entry before starting Docker. The storefront suite has three existing failures in `receipt-return.test.mjs`: missing `returnDisputeReply`, then two evidence-upload cases affected by the leftover response fixture. Notifications do not modify those files. Production frontend builds emit the existing bundle-size warning.

The changed backend sources compile and focused tests pass. Executable JAR packaging was attempted but stopped at gateway: Maven cannot replace `platform/gateway/target/api-gateway-0.0.1-SNAPSHOT.jar.original`, which is owned by `BUILTIN\Administrators` and denies rename access in this session. Packaging/deployment requires resolving that generated-file permission or using a fresh build directory; no file permissions or running user services were changed.
