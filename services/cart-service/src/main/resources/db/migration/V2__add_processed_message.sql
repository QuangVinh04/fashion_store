create table processed_message (
    id varchar(36) primary key,
    message_id varchar(64) not null,
    consumer_name varchar(120) not null,
    processed_at timestamp not null,
    created_at timestamp,
    updated_at timestamp,
    created_by varchar(255),
    updated_by varchar(255),
    constraint uq_cart_processed_message unique(message_id, consumer_name)
);
