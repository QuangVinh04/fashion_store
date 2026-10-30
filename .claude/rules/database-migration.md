# Database và Flyway

- Mỗi service có database riêng (bảng cổng/database trong `AGENTS.md`). Không trỏ datasource của service này sang database của service khác. Không query xuyên database.
- Không sửa hoặc đánh số lại migration Flyway đã có. Thêm file mới `V<n>__*.sql`.
- `catalog-service` dành sẵn dải số: `V1–V19` product, `V20–V39` inventory, `V40+` file.
