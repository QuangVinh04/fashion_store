---
trigger: glob
globs: apps/**
---

# Code style TypeScript/React

Luật chung nằm trong mục **Code style** của `AGENTS.md`. File này chỉ thêm luật riêng cho frontend.

- **Không dùng `any`.** Khai báo type hoặc interface. Dữ liệu chưa rõ kiểu: dùng `unknown` rồi thu hẹp.
- **Component ngắn.** Component > 250 dòng: tách sub-component hoặc custom hook (`useXxx`). Mỗi file một component chính.
- **Gọi API trong lớp API.** Backoffice: `src/libs/api/`. Storefront: `src/api/`. Page và component gọi hàm của lớp này, không gọi `fetch` trực tiếp.
- **`sx` ngắn.** `sx` > ~5 thuộc tính hoặc dùng lại ở nhiều chỗ: tách thành hằng số ngoài component hoặc `styled`.
- **Không `console.log`** trong code commit.
- **Formatter.** Backoffice dùng `.prettierrc.json` hiện có. Storefront chưa có Prettier: khi người dùng yêu cầu thì thêm, dùng cùng config với backoffice.
