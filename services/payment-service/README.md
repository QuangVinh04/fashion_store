# Payment Service

Owns payment records, provider integration, callbacks, and payment status events.

## Code structure

Follow [the Backend structure](../../docs/backend-code-structure.md): feature modules `payment` (payments, refunds, VNPay/payOS handlers, callbacks), `wallet` (accounts, ledger, top-up, wallet payments) and `shared` (outbox, Rabbit config, security, `AdminPaging`). Inside each feature, controllers accept HTTP, services own business flows, repositories/entities own persistence, clients adapt external/internal HTTP, and event classes own message entry points and outbox delivery.

- PaymentServiceImpl and CallbackPaymentServiceImpl coordinate payment initiation and callbacks. PaymentTransactionService owns short database transactions around provider calls; PaymentStateServiceImpl applies verified order-payment results.
- PaymentHandler, its registry and VNPay/payOS adapters live in client alongside VnPayFeignClient. The provider interface represents genuinely different integrations.
- WalletService consolidates store-balance payments, top-ups (via Payment type=TOP_UP), refund validations/credits, and atomic account ledger updates.
- Outbox (OutboxEvent, repository, recorder, relay) lives in `shared/outbox`; queue/routing/consumer identities remain unchanged.
- `ModularityTest` + `src/test/resources/modulith-known-violations.txt` freeze the payment ↔ wallet coupling; new violations fail the build.
- Tests follow their component package: provider tests in client, relay/consumer tests in event, business tests in service. PostgreSQL and RabbitMQ tests require Docker.

## Local dependencies

Test selection and runtime requirements: [Backend test guide](../../docs/backend-test-guide.md). Local verification includes H2 integration and HTTP/security tests; PostgreSQL/RabbitMQ tests use the `external-infrastructure` tag.

From the repository root:

```bash
docker compose up -d rabbitmq payment-postgres
```

RabbitMQ management UI:

- `http://localhost:15672`
- `guest / guest`

## Run

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
mvn -f services/payment-service/pom.xml spring-boot:run
```

Default HTTP port: `8085`.

## Messaging

Consumes:

- `payment.requested` and `payment.cancellation.requested` via queue `payment.saga-command-v1`

Publishes through outbox:

- `payment.completed`, `payment.failed`, `payment.cancelled`, and `payment.cancellation.rejected`

The service owns its own `payment`, `outbox_event`, and `processed_message` tables.
See [the local payment flow](../../docs/local-payment-flow.md) for local setup and verification.
