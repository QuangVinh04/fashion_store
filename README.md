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

| module | port | database |
|---|---|---|
| `platform/gateway` | 8080 | — |
| `services/identity-service` | 8082 | `identity_database` |
| `services/payment-service` | 8085 | `payment_database` |
| `services/catalog-service` | 8087 | `catalog_database` |
| `services/order-service` | 8089 | `order_database` |
| `services/notification-service` | 8090 | `notification_database` |
| `packages/api-contracts` (artifactId `event-contracts`) | — | — |
| `packages/common-library` | — | — |

`customer-service`, `search-service`, and `recommendation-service` are planned
boundaries with no code yet; they are described in
[docs/microservices-roadmap.md](docs/microservices-roadmap.md).

The service decomposition has been consolidated from 8 services down to 5.
`catalog-service` absorbed `product-service`, `inventory-service`, and
`file-service`; `order-service` absorbed `cart-service`. The reasoning is in
[Why the decomposition shrank from 8 services to 5](docs/microservices-roadmap.md#why-the-decomposition-shrank-from-8-services-to-5);
what remains is in [docs/refactor-plan.md](docs/refactor-plan.md).


## Build

Java 21 is required. The scripts pin `JAVA_HOME` to `C:\Program Files\Java\jdk-21`
themselves; calling `mvnw` directly does not.

```powershell
.\scripts\build.ps1
```

Run tests:

```powershell
.\scripts\test.ps1
```

One service, or one test class:

```bash
./mvnw -pl services/catalog-service -am clean test
./mvnw -pl services/catalog-service test -Dtest=ProductServiceImplTest
```

Validate and start local infrastructure:

```powershell
docker compose config
docker compose up -d
```

All public HTTP traffic enters through the gateway at `http://localhost:8080`.

## Project documentation

- [Architecture roadmap](docs/microservices-roadmap.md) — service ownership, saga flow, required patterns, known gaps
- [Service consolidation plan](docs/refactor-plan.md) — the active backlog
- [CLAUDE.md](CLAUDE.md) — conventions, commands and boundary rules in machine-readable form

## Service conventions

Each Spring Boot service owns its database and migrations, and lays its code out
by layer — one flat package per layer, no per-feature sub-packages:

```text
controller/
dto/
model/ or entity/     # sub-dividing allowed here only
repository/
service/
  impl/
mapper/
client/
messaging/ or event/
cache/
exception/
config/
util/
```

A service keeps only the packages it actually needs. `model/` is used by
`catalog-service` and `order-service`, `entity/` by `identity-service` and
`payment-service` — follow whichever the service you are editing already uses.
`catalog-service` is the reference implementation; see
[its README](services/catalog-service/README.md).

Cross-service event DTOs belong in `packages/api-contracts`. Domain entities,
repositories, and business services must not be moved into shared packages.
