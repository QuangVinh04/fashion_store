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

-- outbox_event đã được V2/V3 (di sản product-service) tạo trong cùng database này,
-- nên ở đây chỉ bù hai cột kiểm toán mà bản V2 thiếu. Entity OutboxEvent của phần
-- inventory kế thừa AuditedEntity nên bắt buộc phải có created_by/updated_by.
alter table outbox_event add column if not exists created_by varchar(255);
alter table outbox_event add column if not exists updated_by varchar(255);
