create table inventory_reservation (
    id varchar(36) primary key,
    order_id varchar(36) not null unique,
    status varchar(20) not null,
    rejection_reason varchar(500),
    created_at timestamp,
    updated_at timestamp,
    created_by varchar(255),
    updated_by varchar(255)
);

create table inventory_reservation_item (
    id varchar(36) primary key,
    reservation_id varchar(36) not null references inventory_reservation(id) on delete cascade,
    variant_id varchar(36) not null,
    quantity integer not null check (quantity > 0),
    created_at timestamp,
    updated_at timestamp,
    created_by varchar(255),
    updated_by varchar(255)
);

create index idx_inventory_reservation_item_reservation
    on inventory_reservation_item(reservation_id);

create table outbox_event (
    id varchar(36) primary key,
    event_type varchar(120) not null,
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
