---
description: Đề xuất commit message tiếng Anh ngắn gọn, không commit
---

1. Chạy `git status --short` và `git diff` để xem thay đổi.
2. Đề xuất một commit message tiếng Anh, một dòng, ngắn gọn, theo dạng các commit hiện có (`git log --oneline -10`): `feat(scope): ...`, `fix(scope): ...`, `refactor(scope): ...`.
3. Liệt kê các file nên đưa vào commit.
4. Không chạy `git add`, `git commit`, `git push`. Người dùng tự commit.
