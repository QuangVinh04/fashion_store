create table checkout (
    id varchar(36) primary key,
    created_at timestamp,
    updated_at timestamp,
    order_id varchar(36) unique,
    user_id varchar(36) not null,
    status varchar(20) not null,
    payment_method varchar(20) not null,
    payment_provider varchar(30) not null,
    shipping_method varchar(20) not null,
    coupon_code varchar(100),
    subtotal_amount numeric(19, 2) not null,
    discount_amount numeric(19, 2) not null,
    shipping_fee numeric(19, 2) not null,
    total_amount numeric(19, 2) not null,
    submitted_at timestamp,
    expired_at timestamp
);

create table checkout_item (
    id varchar(36) primary key,
    created_at timestamp,
    updated_at timestamp,
    checkout_id varchar(36) not null,
    variant_id varchar(36) not null,
    product_name varchar(255) not null,
    size_name varchar(20),
    color_name varchar(50),
    unit_price numeric(19, 2) not null,
    quantity integer not null,
    line_total numeric(19, 2) not null,
    constraint fk_checkout_item_checkout foreign key (checkout_id) references checkout (id) on delete cascade
);

create table orders (
    id varchar(36) primary key,
    created_at timestamp,
    updated_at timestamp,
    order_code varchar(30) not null unique,
    user_id varchar(36) not null,
    status varchar(30) not null,
    recipient_name varchar(120) not null,
    recipient_phone varchar(20) not null,
    shipping_address varchar(500) not null,
    shipping_provider varchar(100),
    tracking_code varchar(100),
    subtotal_amount numeric(19, 2) not null,
    discount_amount numeric(19, 2) not null,
    shipping_fee numeric(19, 2) not null,
    total_amount numeric(19, 2) not null
);

create table order_item (
    id varchar(36) primary key,
    created_at timestamp,
    updated_at timestamp,
    order_id varchar(36) not null,
    variant_id varchar(36) not null,
    product_name varchar(255) not null,
    size_name varchar(20),
    color_name varchar(50),
    unit_price numeric(19, 2) not null,
    quantity integer not null,
    line_total numeric(19, 2) not null,
    constraint fk_order_item_order foreign key (order_id) references orders (id) on delete cascade
);
