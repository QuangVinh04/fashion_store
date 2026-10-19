-- ID giữ kho chỉ lưu trên order_saga (V28 đã chép dữ liệu cũ sang); cột trên orders không còn dùng.
alter table orders drop column if exists inventory_reservation_id;
