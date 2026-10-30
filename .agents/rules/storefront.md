---
trigger: glob
globs: apps/storefront/**
---

# Storefront

- Stack: Vite 6 + React 18 + MUI 7 + Tailwind 4. Package manager: **npm** (`package-lock.json`).
- Dev server: `cd apps/storefront && npm run dev` (cổng 3000). Browser mở qua `http://localhost:8083`.
- Kiểm tra: `npm run typecheck`.

## Vị trí code

| thứ | thư mục |
|---|---|
| route | `src/app/routes.ts` |
| page | `src/pages/` |
| component | `src/components/` |
| style | `src/styles/` |
| API client và các hàm gọi API | `src/api/client.ts`, `src/api/*.ts` |
| auth, giỏ hàng | `src/context/AuthContext.tsx`, `src/context/CartContext.tsx` |

- Gọi API qua `src/api/client.ts`. Client này dùng session cookie và CSRF của BFF.
- `vite.config.ts`: giữ plugin React và Tailwind, comment trong file yêu cầu không xóa.
