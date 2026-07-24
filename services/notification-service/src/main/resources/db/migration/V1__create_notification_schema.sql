create table processed_message (
    message_id varchar(64) primary key,
    processed_at timestamp with time zone not null
);
