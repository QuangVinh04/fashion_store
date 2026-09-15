create table coupon_usage (
    id varchar(36) primary key,
    created_at timestamp,
    updated_at timestamp,
    promotion_id varchar(36) not null,
    user_id varchar(36) not null,
    order_id varchar(36) not null unique,
    used_at timestamp not null,
    status varchar(20) not null,
    constraint fk_coupon_usage_promotion foreign key (promotion_id) references promotion (id) on delete cascade,
    constraint uq_coupon_usage_promo_user_order unique (promotion_id, user_id, order_id)
);

create index idx_coupon_usage_promotion_user on coupon_usage (promotion_id, user_id);
create index idx_coupon_usage_order on coupon_usage (order_id);
