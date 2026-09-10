-- Schema thật đang lệch với entity: cart dùng cột is_active (boolean) trong khi Cart.status là enum
-- string; cart_item bắt buộc product_name NOT NULL nhưng CartItem không hề có field này (tên/size/color
-- luôn được enrich sống từ product-service lúc đọc, không snapshot lúc ghi như OrderItem/CheckoutItem).

alter table cart
    add column if not exists status varchar(20) not null default 'ACTIVE';

update cart set status = case when is_active then 'ACTIVE' else 'ABANDONED' end;

alter table cart
    drop column if exists is_active;

alter table cart_item
    drop column if exists product_name;
