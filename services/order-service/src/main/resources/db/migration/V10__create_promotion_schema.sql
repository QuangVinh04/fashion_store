create table promotion (
    id varchar(36) primary key,
    created_at timestamp,
    updated_at timestamp,
    code varchar(50) not null unique,
    type varchar(20) not null,
    value numeric(19, 2) not null,
    max_discount numeric(19, 2),
    min_order_value numeric(19, 2),
    start_at timestamp not null,
    end_at timestamp not null,
    total_quota integer,
    per_user_quota integer,
    scope_type varchar(20) not null,
    scope_ids text,
    active boolean not null default true
);

create index idx_promotion_code on promotion (code);
create index idx_promotion_active_end_at on promotion (active, end_at);
