-- Snapshot địa chỉ giao hàng trên checkout, chụp cùng lúc tính phí ship; đơn chỉ chép lại từ đây.
-- Checkout mở trước migration này không có snapshot — đặt đơn từ chúng bị từ chối, khách mở checkout mới.
alter table checkout
    add column recipient_name varchar(120),
    add column recipient_phone varchar(20),
    add column province varchar(100),
    add column district varchar(100),
    add column ward varchar(100),
    add column detail_address varchar(255),
    add column district_id integer,
    add column ward_code varchar(20);
