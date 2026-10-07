-- Outbox của identity theo cùng khuôn với catalog/order/payment:
-- mỗi dòng tự mang trạng thái, số lần thử và lịch thử lại, để một dòng lỗi không chặn cả hàng đợi.
alter table outbox_event add column status varchar(20) not null default 'PENDING';
alter table outbox_event add column attempts int not null default 0;
alter table outbox_event add column next_attempt_at timestamp not null default now();
alter table outbox_event add column last_error varchar(1000);

-- Dòng đã gửi trước migration này.
update outbox_event set status = 'PUBLISHED' where published_at is not null;

create index idx_outbox_event_pending on outbox_event (status, next_attempt_at);
