-- Gộp từ cart-service (P4). V2 của cart tạo processed_message đã bị bỏ:
-- order-service tạo bảng đó rồi ở V2__add_saga_support.sql.

create table cart (
    id varchar(36) primary key,
    created_at timestamp,
    updated_at timestamp,
    user_id varchar(36) not null unique,
    is_active boolean not null default true
);

create table cart_item (
    id varchar(36) primary key,
    created_at timestamp,
    updated_at timestamp,
    cart_id varchar(36) not null,
    variant_id varchar(36) not null,
    product_id varchar(36) not null,
    product_name varchar(255) not null,
    size_name varchar(20),
    color_name varchar(50),
    unit_price numeric(19, 2) not null,
    quantity integer not null,
    constraint fk_cart_item_cart foreign key (cart_id) references cart (id) on delete cascade,
    constraint uq_cart_item_cart_variant unique (cart_id, variant_id)
);
