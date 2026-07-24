# Payment Service

Owns payment records, provider integration, callbacks, and payment status events.

## Local dependencies

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
