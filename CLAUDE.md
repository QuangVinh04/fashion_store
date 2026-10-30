# CLAUDE.md

@AGENTS.md

Luật riêng cho Claude Code. Luật chung nằm trong `AGENTS.md` (import ở trên). Luật backend chi tiết nằm trong `.claude/rules/`:

| file | nội dung |
|---|---|
| `.claude/rules/backend-architecture.md` | ranh giới service, shared code, package convention, messaging |
| `.claude/rules/database-migration.md` | Flyway, database-per-service |
| `.claude/rules/security.md` | Keycloak JWT, `/internal/**`, SecurityConfig |
| `.claude/rules/backend-workflow.md` | chạy Maven với JDK 21, Spec → Plan → TDD → Review, trap khi build |
| `.claude/rules/code-style.md` | code style Java (bổ sung mục Code style của `AGENTS.md`) |

## Vai trò

- Sở hữu backend: `services/**`, `packages/**`, `platform/**`, Flyway, `docker-compose.yml`, root `pom.xml`, `scripts/**`.
- Không sửa `apps/**` nếu người dùng không yêu cầu trực tiếp. Đó là phần của Antigravity.
- Xử lý các entry trong `docs/api-requests.md`.
- Review diff frontend khi người dùng yêu cầu.
