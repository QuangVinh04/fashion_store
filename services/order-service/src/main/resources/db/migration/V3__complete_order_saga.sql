alter table orders
    add column if not exists payment_id varchar(36),
    add column if not exists saga_failure_reason varchar(500),
    add column if not exists compensation_target_status varchar(30);

drop index if exists uq_orders_idempotency_key;

create unique index if not exists uq_orders_user_idempotency_key
    on orders(user_id, idempotency_key);
