-- V7 bỏ cart_item.product_name với lý do tên luôn được enrich sống từ catalog. Nhưng
-- checkout_item.product_name là NOT NULL, nên một variant biến mất khỏi catalog (hoặc catalog chết) làm
-- POST /api/v1/checkouts ném NOT NULL violation. Tên quay lại thành snapshot ghi lúc thêm vào giỏ.
-- Nullable vì các dòng có từ trước không có gì để backfill; checkout từ chối chúng bằng CART_ITEM_STALE.
alter table cart_item
    add column if not exists product_name varchar(255);
