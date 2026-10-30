---
trigger: glob
globs: apps/backoffice/**
---

# Backoffice

- Stack: Next.js 14 (App Router) + MUI 5 + Tailwind 3, template Materio. Package manager: **pnpm**.
- Dev server: `cd apps/backoffice && pnpm dev` (cổng 5173). Browser mở qua `http://localhost:8084`.
- Kiểm tra: `pnpm lint`.

## Vị trí code

| thứ | thư mục |
|---|---|
| route / page | `src/app/` |
| màn hình theo nghiệp vụ | `src/views/` (catalog, customers, dashboard, inventory, media, orders, promotions, returns, reviews, wallets) |
| component dùng chung của backoffice | `src/components/backoffice/` |
| theme | `src/@core/theme/`, `src/configs/primaryColorConfig.ts` |
| API client | `src/libs/api/client.ts` |
| auth | `src/context/AuthContext.tsx` |

- Gọi API qua `src/libs/api/client.ts`. Client này dùng session cookie và CSRF của BFF.
