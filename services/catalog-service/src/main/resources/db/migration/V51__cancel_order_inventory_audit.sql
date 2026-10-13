create table order_inventory_cancellation (
 id varchar(36) primary key, created_at timestamp, updated_at timestamp,
 cancellation_id varchar(36) not null unique,
 order_id varchar(36) not null unique,
 reservation_id varchar(36) not null unique references inventory_reservation(id),
 original_status varchar(30) not null
);
