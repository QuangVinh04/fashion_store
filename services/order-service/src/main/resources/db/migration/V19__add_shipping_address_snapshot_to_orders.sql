-- Snapshot địa chỉ giao hàng nhúng thẳng vào orders (value object, không có bảng riêng): recipient_name /
-- recipient_phone đã có sẵn, thêm phần địa chỉ + mã GHN để vận đơn không phải gọi lại identity-service.
-- Đơn tạo trước migration này để trống các cột mới — các đơn đó không tạo được vận đơn.
alter table orders
    add column province varchar(100),
    add column district varchar(100),
    add column ward varchar(100),
    add column detail_address varchar(255),
    add column district_id integer,
    add column ward_code varchar(20);
