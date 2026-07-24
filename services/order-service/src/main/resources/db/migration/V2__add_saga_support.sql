alter table checkout_item
    add column if not exists cart_item_id varchar(36);

alter table order_item
    add column if not exists cart_item_id varchar(36);

alter table orders
    add column if not exists idempotency_key varchar(120),
    add column if not exists inventory_reservation_id varchar(36);

update orders
set idempotency_key = id
where idempotency_key is null;

alter table orders
    alter column idempotency_key set not null;

create unique index if not exists uq_orders_idempotency_key on orders(idempotency_key);
create index if not exists idx_orders_status_updated_at on orders(status, updated_at);

create table if not exists outbox_event (
    id varchar(36) primary key,
    event_type varchar(300) not null,
    routing_key varchar(120) not null,
    payload text not null,
    status varchar(20) not null,
    attempts integer not null default 0,
    next_attempt_at timestamp not null,
    published_at timestamp,
    last_error varchar(1000),
    created_at timestamp,
    updated_at timestamp,
    created_by varchar(255),
    updated_by varchar(255)
);

create table if not exists processed_message (
    id varchar(36) primary key,
    message_id varchar(255) not null,
    consumer_name varchar(120) not null,
    processed_at timestamp not null,
    created_at timestamp,
    updated_at timestamp,
    created_by varchar(255),
    updated_by varchar(255),
    constraint uq_order_processed_message unique(message_id, consumer_name)
);
