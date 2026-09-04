# Fashion Store Platform

Spring Boot microservices monorepo for the Fashion Store ecommerce platform.

## Repository layout

```text
apps/                    Frontend applications
services/                Business microservices
packages/                Shared contracts and narrowly scoped libraries
platform/                Gateway and deployment infrastructure
docs/                    Architecture and operational documentation
scripts/                 Local build and test commands
.github/workflows/       CI pipelines
```

Active backend modules:

- `platform/gateway`
- `services/identity-service`
- `services/product-service`
- `services/inventory-service`
- `services/order-service`
- `services/payment-service`
- `services/notification-service`
- `packages/api-contracts`

`customer-service`, `search-service`, and `recommendation-service` are planned
boundaries with no code yet; they are described in
[docs/microservices-roadmap.md](docs/microservices-roadmap.md).

The service decomposition is being consolidated from 8 services down to 5 — see
[docs/refactor-plan.md](docs/refactor-plan.md) for the prioritised plan.

## Build

Java 21 is required.

```powershell
.\scripts\build.ps1
```

Run tests:

```powershell
.\scripts\test.ps1
```

Validate and start local infrastructure:

```powershell
docker compose config
docker compose up -d
```

All public HTTP traffic enters through the gateway at `http://localhost:8080`.

## Project documentation

- [Architecture roadmap](docs/microservices-roadmap.md)
- [Service consolidation plan](docs/refactor-plan.md)

## Service conventions

Each Spring Boot service owns its database and migrations. A service keeps only
the packages it actually needs, using this convention:

```text
controller/
dto/
entity/
repository/
service/
  impl/
mapper/
client/
messaging/
cache/
exception/
config/
util/
```

Cross-service event DTOs belong in `packages/api-contracts`. Domain entities,
repositories, and business services must not be moved into shared packages.
