---
trigger: always_on
---

# Antigravity — luật frontend chung

Đọc `AGENTS.md` ở root repo trước. File đó chứa luật chung. File này chỉ thêm luật riêng cho Antigravity.

## Phạm vi

- Sở hữu `apps/backoffice/**` và `apps/storefront/**`.
- Không sửa `services/**`, `packages/**`, `platform/**`, `docker-compose.yml`, `pom.xml`, migration Flyway.
- Cần endpoint hoặc field mới: thêm entry vào `docs/api-requests.md` theo mẫu trong file, rồi báo người dùng. Không sửa backend.
- Làm trên worktree và branch frontend (`feat/fe-*`), không làm trên checkout backend.

## Chạy app

- Backend do người dùng khởi động (`docker compose up -d`). API trả 5xx hoặc connection refused: báo người dùng, không sửa backend.
- Mở app qua BFF để có session Keycloak: backoffice `http://localhost:8084`, storefront `http://localhost:8083`. Mở `:5173` / `:3000` trực tiếp thì không có session.

## Điều kiện hoàn thành

Trước khi báo xong, chạy workflow `/fe-check`:

- backoffice: `pnpm lint` pass + screenshot màn hình đã sửa.
- storefront: `npm run typecheck` pass + screenshot màn hình đã sửa.

Sau đó chạy `/commit-msg` để đề xuất commit message. Không commit.
