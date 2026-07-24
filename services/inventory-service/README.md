# Inventory Service

Owns available stock, reserved stock, and order inventory reservations.

## Local dependencies

From the repository root:

```bash
docker compose up -d rabbitmq inventory-postgres
```

## Run

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
mvn -f services/inventory-service/pom.xml spring-boot:run
```

Default HTTP port: `8086`.

## Messaging

Consumes:

- `product.variant.stock` via queue `inventory.product-variant-stock`
- inventory reservation, confirmation, and release commands

Publishes:

- inventory reserved/rejected results through the outbox
