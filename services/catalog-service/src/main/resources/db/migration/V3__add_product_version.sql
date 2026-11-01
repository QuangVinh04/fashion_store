-- Optimistic locking: hai admin sửa cùng một sản phẩm thì bản lưu sau nhận 409 thay vì ghi đè âm thầm.
ALTER TABLE product ADD COLUMN version bigint NOT NULL DEFAULT 0;
