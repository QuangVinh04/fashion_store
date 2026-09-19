-- Thêm cột recipient_email vào checkout và orders để snapshot email người nhận/khách hàng
alter table checkout
    add column if not exists recipient_email varchar(255);

alter table orders
    add column if not exists recipient_email varchar(255);
