# CLAUDE.md

Fashion Store Platform — e-commerce microservices monorepo.
Java 21 · Spring Boot 3.5 · Maven multi-module (`./mvnw`) · Postgres-per-service · RabbitMQ · Flyway · Docker Compose.

## Commands

```powershell
.\scripts\build.ps1      # mvnw -B -DskipTests compile   (pins JAVA_HOME to jdk-21)
.\scripts\test.ps1       # mvnw -B test, all modules
docker compose config    # validate first
docker compose up -d
```

Targeted runs from bash — **must** pin JDK 21 (`java` on PATH is 1.8, `JAVA_HOME` defaults to jdk-17):

```bash
export JAVA_HOME="C:/Program Files/Java/jdk-21"

./mvnw -o -pl services/catalog-service -am clean test     # one service + its deps
./mvnw -o -pl services/catalog-service clean test         # one service (deps installed)
./mvnw -o -pl services/catalog-service test -Dtest=ProductServiceImplTest
./mvnw -o -pl services/catalog-service test -Dtest='ProductServiceImplTest#createsVariant*'
./mvnw -o -DskipTests clean install                       # whole reactor
```

- `-o` (offline) works, local repo is warm — drop it only if a dep is missing.
- All client traffic enters via gateway `http://localhost:8080`. Do not call service ports from a client.

| module | port | database | host port |
|---|---|---|---|
| `platform/gateway` (`api-gateway`) | 8080 | — | — |
| `identity-service` | 8082 | `identity_database` | 5438 |
| `payment-service` | 8085 | `payment_database` | 5433 |
| `catalog-service` | 8087 | `catalog_database` | 5435 |
| `order-service` | 8089 | `order_database` | 5437 |
| `notification-service` | 8090 | `notification_database` | 5439 |

## Layout

```text
apps/            storefront, backoffice — README stubs only, no build yet
services/        identity, catalog, order, payment, notification
packages/
  api-contracts  artifactId event-contracts, package com.fashionstore.contracts
  common-library artifactId common-library,  package com.fashionstore.common
  test-utils     artifactId test-utils (declared in reactor, unused by services)
platform/        gateway/ (Spring Cloud Gateway) + docker, helm, kubernetes, kafka, keycloak, observability
docs/            microservices-roadmap.md, refactor-plan.md
scripts/         build.ps1, test.ps1
```

## Architecture boundaries

- **Database-per-service.** Each service owns its schema and its own `src/main/resources/db/migration`. Never read or write another service's tables; never add a cross-service FK.
- **Cross-service talk** = RabbitMQ events/commands (`com.fashionstore.contracts.*`) or an HTTP client behind the gateway. Nothing else.
- **`packages/api-contracts` holds only wire DTOs** for cross-service messages. Entities, repositories, business services, mappers and controllers stay in their owning service, always.
- **`packages/common-library` holds only framework-level plumbing** (below). No domain logic.
- Merged services — treat as one deployable each, no re-splitting:
  - `catalog-service` ⊃ product + inventory + file
  - `order-service` ⊃ cart

## Shared code — reuse, do not re-implement

`com.fashionstore.common.*` (common-library):

| need | use |
|---|---|
| REST envelope / paging | `dto.ApiResponse`, `dto.PageResponse` |
| errors | `exception.AppException`, `BaseErrorCode`, `ErrorCode`, `GlobalExceptionHandler` |
| JPA base classes | `persistence.BaseEntity`, `persistence.AuditedEntity` |
| security | `security.CurrentUserProvider`, `ApiAuthenticationEntryPoint`, `ApiAccessDeniedHandler` |
| messaging infra | `messaging.outbox.OutboxEventStatus`, `messaging.processed.@EnableProcessedMessages` + `ProcessedMessageService` |
| misc | `util.SlugUtils`, `web.CorrelationIdFilter`, `payment.PaymentMethod/PaymentProvider` |

`com.fashionstore.contracts.*` (api-contracts): `common.EventEnvelope`, `common.EventTypes`, plus `inventory.*`, `order.*`, `payment.*`, `notification.*` commands/events. Add a new event **here** and reference `EventTypes` — never hardcode a routing key string in two services.

## Package convention inside a service

Layer packages, flat — one package per layer, no per-feature sub-packages:

```text
com.fashionstore.<service>
  controller/  dto/  model|entity/  repository/  service/ + service/impl/
  mapper/  client/  messaging|event/  cache/  exception/  config/  util/
  <Service>Application.java
```

- Sub-dividing is allowed **inside `model`/`entity` only** (e.g. `model/enumeration`, `model/attribute`, `model/option`).
- `client/`, `cache/`, `messaging/`, `event/` exist only where a service actually needs them — do not create empty ones.
- Entity package name differs by service: `model/` in catalog + order, `entity/` in identity + payment. Follow whatever the service you are editing already uses; do not rename a service's package wholesale unless asked.
- `catalog-service` is the reference implementation of this layout (`services/catalog-service/README.md`). Known deviations still to be flattened: `order-service/cart/**`, `payment-service/{common,order,gateway}/**`.
- Split by layer, not by domain: e.g. outbox lives as `model/OutboxEvent` + `repository/OutboxEventRepository` + `event/OutboxEventRecorder|OutboxEventRelay`.

## Constraints & Gotchas

Never, without an explicit request:

- ❌ Move an entity, repository, or business service into `packages/**`.
- ❌ Put anything but cross-service wire DTOs in `api-contracts`; put domain logic in `common-library`.
- ❌ Point one service's datasource at another service's database, or query across databases.
- ❌ Re-create `product-service`, `inventory-service`, `file-service`, or `cart-service` — they are merged and deleted.
- ❌ Add a module to the root `pom.xml` `<modules>` or a service to `docker-compose.yml` as a side effect of another task.
- ❌ Edit or renumber an existing Flyway migration. Add a new `V<n>__*.sql`. Catalog reserves ranges: `V1–V19` product, `V20–V39` inventory, `V40+` file.
- ❌ Bypass the gateway, or change a service's port / `SPRING_APPLICATION_NAME` / DB env var names.
- ❌ Weaken security to make something pass — `SecurityConfig` chains, JWT decoding, `hasRole`/`hasAuthority` rules are behaviour, not boilerplate.
- ❌ `git push`, force-push, reset --hard, or commit unless asked. The working tree currently carries a large uncommitted service-merge changeset.

Traps:

- `packages/api-contracts` builds artifactId **`event-contracts`** — the directory name and the artifact do not match.
- Direct `./mvnw` without `JAVA_HOME=jdk-21` fails with `release version 21 not supported`. That is the JDK, not your code.
- After moving/renaming packages, run with `clean`. Stale classes in `target/test-classes` produce a bogus `TestEngine with ID 'junit-jupiter' failed to discover tests`.
- MapStruct + Lombok: mapper impls are generated into `target/generated-sources`. If a mapping looks missing, `clean` before debugging.
- `apps/storefront` and `apps/backoffice` contain only a README. There is no frontend build to run.
- RabbitMQ payloads carry no FQCN (`Jackson2JsonMessageConverter`, type inferred from the listener signature), so moving an event class is safe — but renaming an `EventTypes` value is a wire break for every consumer.

## Working style

**Output — terse.** No greeting, no preamble, no restating the request, no summary of what you are about to do. Lead with the diff or the command. Skip "great question", "you're right", and closing pleasantries. Reply in the user's language.

**Coding — minimal first.** Read the surrounding code before writing. Pick the simplest thing that works. The established `service/` interface + `service/impl/` pair stays — but add nothing beyond it: no extra abstraction layer, no factory, no config knob, no defensive branch that nothing needs. Respect the layering (`controller → service/impl → repository → model`) and never widen scope past what was asked. Readability over cleverness.

**Logic changes and refactors — Spec → Plan → TDD → Review.**

1. Spec: state in 1–3 lines what changes and how it will be verified.
2. Plan: list the files to touch; get sign-off before large or cross-service edits.
3. TDD: write or update the test first, watch it fail, then implement until `./mvnw -o -pl services/<svc> clean test` is green.
4. Review: re-read your own `git diff` before reporting. Report the real result — if a test fails or a step was skipped, say so with the output.

Typo fixes, renames, and doc edits skip the workflow. Just do them.
