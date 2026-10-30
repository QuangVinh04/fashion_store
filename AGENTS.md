# AGENTS.md

Luật chung cho mọi coding agent trong repo này (Claude Code, Antigravity).

| file | ai đọc | nội dung |
|---|---|---|
| `AGENTS.md` | mọi agent | luật chung: phân quyền, lệnh, topology, ràng buộc, git |
| `CLAUDE.md` + `.claude/rules/*.md` | Claude Code | backend: kiến trúc, migration, security, quy trình làm việc |
| `.agents/rules/*.md` + `.agents/workflows/*.md` | Antigravity | frontend: backoffice, storefront, điều kiện hoàn thành |
| `docs/api-requests.md` | cả hai | frontend gửi yêu cầu API cho backend |
| `docs/architecture.md`, `docs/services/*.md`, `docs/flows/*.md` | cả hai | kiến trúc, từng service, luồng nghiệp vụ |

Luật chung chỉ sửa ở `AGENTS.md`. Không chép lại nội dung file này sang file khác.

## Dự án

Fashion Store Platform — monorepo e-commerce theo kiến trúc microservices.

- Backend: Java 21 · Spring Boot 3.5 · Maven multi-module (`./mvnw`) · Postgres-per-service · RabbitMQ · Flyway · Docker Compose.
- Frontend: `apps/backoffice` (Next.js 14 + MUI 5, template Materio, pnpm) · `apps/storefront` (Vite + React 18 + MUI 7 + Tailwind 4, npm).

## Phân quyền thư mục

Hai agent làm song song. Mỗi agent chỉ sửa thư mục mình sở hữu.

| chủ sở hữu | thư mục |
|---|---|
| **Claude Code** (backend) | `services/**`, `packages/**`, `platform/**`, `docker-compose.yml`, root `pom.xml`, `**/db/migration/**`, `scripts/**` |
| **Antigravity** (frontend) | `apps/backoffice/**`, `apps/storefront/**` |
| dùng chung (chỉ sửa khi task yêu cầu) | `AGENTS.md`, `docs/api-requests.md` |

- **API contract** (endpoint, request/response DTO, Swagger) thuộc Claude Code.
- Frontend cần endpoint hoặc field mới: thêm một entry vào `docs/api-requests.md`. Không sửa code Java.
- Claude Code implement yêu cầu, cập nhật Swagger, chuyển entry sang mục **Done**.
- Mỗi agent làm trên worktree và branch riêng: `feat/be-*` cho backend, `feat/fe-*` cho frontend. Không chạy hai agent trên cùng một checkout.

## Git

- Agent **không** commit, push, merge, rebase, reset. Chỉ người dùng commit.
- Khi xong task, agent đề xuất một commit message tiếng Anh, ngắn gọn, theo dạng các commit hiện có: `feat(scope): ...`, `fix(scope): ...`, `refactor(scope): ...`.

## Lệnh

Backend (PowerShell):

```powershell
.\scripts\build.ps1      # mvnw -B -DskipTests compile   (pin JAVA_HOME sang jdk-21)
.\scripts\test.ps1       # mvnw -B test, toàn bộ module
docker compose config    # validate trước
docker compose up -d
```

Frontend:

```bash
cd apps/backoffice && pnpm dev          # Next.js cổng 5173, browser mở qua backoffice-bff :8084
cd apps/backoffice && pnpm lint
cd apps/storefront && npm run dev       # Vite cổng 3000, browser mở qua storefront-bff :8083
cd apps/storefront && npm run typecheck # tsc --noEmit
```

## Topology khi chạy

- Browser vào qua `storefront-bff` `http://localhost:8083` và `backoffice-bff` `http://localhost:8084` (OAuth2 client, session cookie, `TokenRelay`). BFF cũng proxy dev server của UI. Mở URL của BFF, không mở `:3000` / `:5173` trực tiếp.
- `api-gateway` `http://localhost:8080` là đường duy nhất tới các service (traffic từ BFF, Bearer cho Swagger/dev). Client không gọi thẳng cổng của service.
- SPA không lưu access token. Request thay đổi dữ liệu gửi kèm CSRF cookie của BFF. Login, đăng ký, logout là redirect sang Keycloak.
- Keycloak realm `fashion-store` tại `http://localhost:8180` là nơi duy nhất cấp token (`platform/keycloak/README.md`).

| module | cổng | database | host port |
|---|---|---|---|
| `apps/storefront` (dev) | 3000 | — | — |
| `apps/backoffice` (dev) | 5173 | — | — |
| `platform/storefront-bff` | 8083 | — (session trong Redis) | — |
| `platform/backoffice-bff` | 8084 | — (session trong Redis) | — |
| `platform/gateway` (`api-gateway`) | 8080 | — | — |
| `keycloak` (image, `platform/keycloak`) | 8180→8080 | `keycloak_database` | — |
| `identity-service` | 8082 | `identity_database` | 5438 |
| `payment-service` | 8085 | `payment_database` | 5433 |
| `catalog-service` | 8087 | `catalog_database` | 5435 |
| `order-service` | 8089 | `order_database` | 5437 |
| `notification-service` | 8090 | `notification_database` | 5439 |

## Cấu trúc repo

```text
apps/            backoffice (Next.js admin), storefront (Vite shop)
services/        identity, catalog, order, payment, notification
packages/
  api-contracts  artifactId event-contracts, package com.fashionstore.contracts
  common-library artifactId common-library,  package com.fashionstore.common
  test-utils     artifactId test-utils (khai báo trong reactor, service chưa dùng)
platform/        gateway/, storefront-bff/, backoffice-bff/ (Spring Cloud Gateway) + keycloak (realm export) + docker, helm, kubernetes, kafka, observability
docs/            architecture.md, backend-code-structure.md, services/, flows/, api-requests.md
scripts/         build.ps1, test.ps1
```

## Ràng buộc chung

Không làm các việc sau nếu người dùng không yêu cầu rõ:

- ❌ Sửa thư mục của agent khác (xem Phân quyền).
- ❌ Bỏ qua gateway hoặc BFF. Đổi cổng, `SPRING_APPLICATION_NAME`, tên biến môi trường DB của service.
- ❌ Nới lỏng security để code chạy được. Frontend: không lưu token vào `localStorage`, không bỏ CSRF header.
- ❌ Thêm module vào root `pom.xml` hoặc service vào `docker-compose.yml` như tác dụng phụ của task khác.
- ❌ Đổi package manager: backoffice dùng pnpm, storefront dùng npm (`package-lock.json`).

## Cách làm việc

- **Trả lời ngắn.** Không chào hỏi, không mở đầu, không nhắc lại yêu cầu. Đưa diff hoặc lệnh trước. Trả lời bằng ngôn ngữ của người dùng.
- **Code tối thiểu.** Đọc code xung quanh trước khi viết. Chọn cách đơn giản nhất chạy được. Không thêm abstraction, config, nhánh phòng thủ không ai cần. Không mở rộng phạm vi ngoài yêu cầu.
- **Báo kết quả thật.** Test hoặc build lỗi, hoặc có bước bị bỏ qua: nói rõ, kèm output.
- **Không chắc thì hỏi.** Luật hoặc yêu cầu chưa rõ: hỏi người dùng, không tự thêm.
