-- Danh sách đơn admin: sort mặc định created_at DESC, lọc theo status.
CREATE INDEX idx_orders_created_at ON public.orders USING btree (created_at DESC);
CREATE INDEX idx_orders_status_created_at ON public.orders USING btree (status, created_at DESC);

-- FK không tự có index: chi tiết đơn và xoá cascade đang quét cả bảng order_item.
CREATE INDEX idx_order_item_order_id ON public.order_item USING btree (order_id);

-- Ô tìm kiếm dùng LIKE '%keyword%' trên mã đơn, tên và SĐT người nhận; B-tree không dùng được.
-- Cả ba nhánh OR đều cần index thì Postgres mới tránh được seq scan.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_orders_order_code_trgm ON public.orders USING gin (lower(order_code) gin_trgm_ops);
CREATE INDEX idx_orders_recipient_name_trgm ON public.orders USING gin (lower(recipient_name) gin_trgm_ops);
CREATE INDEX idx_orders_recipient_phone_trgm ON public.orders USING gin (recipient_phone gin_trgm_ops);
