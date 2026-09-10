-- Một checkout chỉ được sinh đúng một đơn. Entity đã khai báo unique nhưng ddl-auto: none nên ràng buộc
-- phải nằm ở đây mới có thật. Partial index vì đơn cũ tạo trước khi có cột này vẫn để null.
create unique index if not exists uq_orders_checkout_id
    on orders (checkout_id)
 where checkout_id is not null;

-- Danh sách đơn của một khách, sắp theo thời gian tạo — truy vấn của GET /api/v1/orders.
create index if not exists idx_orders_user_created_at
    on orders (user_id, created_at desc);

-- Scanner cho checkout hết hạn.
create index if not exists idx_checkout_status_created_at
    on checkout (status, created_at);
