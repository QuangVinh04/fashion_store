create table in_app_notification (
    id varchar(36) primary key,
    user_id varchar(255) not null,
    type varchar(64) not null,
    title varchar(255) not null,
    message text not null,
    order_id varchar(255) not null,
    read_at timestamp with time zone,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_notification_user_history on in_app_notification (user_id, created_at desc, id desc);
create index idx_notification_user_unread on in_app_notification (user_id) where read_at is null;
