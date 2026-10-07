-- Chuyển sang bảng processed_message chuẩn của common-library (ProcessedMessageService):
-- khoá idempotency là cặp (message_id, consumer_name) thay vì chỉ message_id.
alter table processed_message rename to processed_message_v1;

create table processed_message (
    id varchar(255) primary key,
    message_id varchar(255) not null,
    consumer_name varchar(120) not null,
    processed_at timestamp not null,
    created_at timestamp,
    updated_at timestamp,
    constraint uk_processed_message unique (message_id, consumer_name)
);

create index idx_processed_message_consumer on processed_message (consumer_name, processed_at);

-- Giữ lại dấu vết cũ để message đã gửi email trước khi nâng cấp không bị gửi lại.
insert into processed_message (id, message_id, consumer_name, processed_at, created_at, updated_at)
select gen_random_uuid()::varchar, message_id, 'notification-email-v1', processed_at, processed_at, processed_at
from processed_message_v1;

drop table processed_message_v1;
