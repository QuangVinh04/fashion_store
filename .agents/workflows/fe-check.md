---
description: Kiểm tra frontend trước khi báo xong (lint/typecheck + screenshot)
---

1. Xác định app đã sửa bằng `git status --short apps/`.
2. Nếu có thay đổi trong `apps/backoffice/`: chạy `pnpm lint` trong `apps/backoffice`.
3. Nếu có thay đổi trong `apps/storefront/`: chạy `npm run typecheck` trong `apps/storefront`.
4. Lệnh nào lỗi: dừng, báo lỗi kèm output, không báo xong.
5. Mở từng màn hình đã sửa trên browser qua BFF (backoffice `http://localhost:8084`, storefront `http://localhost:8083`) và chụp screenshot.
6. Backend không chạy hoặc không đăng nhập được: báo người dùng, không bỏ qua bước screenshot.
7. Báo kết quả: lệnh đã chạy, pass/fail, screenshot.
