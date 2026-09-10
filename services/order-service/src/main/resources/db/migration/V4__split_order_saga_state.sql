-- Tách state điều phối khỏi state nghiệp vụ: order_saga giữ tiến độ saga, orders chỉ giữ thứ khách hàng
-- và bộ phận vận hành cần thấy.

-- 1. Bảng saga (entity OrderSaga đã map tới nó từ trước nhưng bảng chưa hề tồn tại)
create table if not exists order_saga (
    id                       varchar(36) primary key,
    order_id                 varchar(36) not null unique,
    status                   varchar(20) not null,
    current_step             varchar(30) not null,
    inventory_reservation_id varchar(36),
    payment_id               varchar(36),
    failure_code             varchar(60),
    failure_reason           varchar(500),
    retry_count              integer     not null default 0,
    step_deadline            timestamp,
    created_at               timestamp   not null,
    updated_at               timestamp   not null,
    completed_at             timestamp,
    constraint fk_order_saga_order foreign key (order_id) references orders (id)
);

-- Phục vụ đúng query của scanner: status in (...) and step_deadline < now()
create index if not exists idx_order_saga_due on order_saga (status, step_deadline);

-- 2. Cột orders mà entity yêu cầu nhưng bảng chưa có
alter table orders
    add column if not exists checkout_id      varchar(36),
    add column if not exists currency         varchar(3) not null default 'VND',
    add column if not exists payment_method   varchar(20),
    add column if not exists payment_provider varchar(30),
    add column if not exists cancel_reason    varchar(500);

-- Đơn cũ đang mang status của mô hình cũ: quy về đúng 10 giá trị nghiệp vụ còn lại.
update orders set status = 'PENDING'
 where status in ('PENDING_INVENTORY', 'INVENTORY_RESERVED', 'PENDING_PAYMENT', 'PAYMENT_AUTHORIZED', 'PROMOTION_APPLIED');
update orders set status = 'CANCELLED'
 where status in ('CANCELLING_PAYMENT', 'COMPENSATING', 'RELEASING_INVENTORY', 'MANUAL_REVIEW');
update orders set status = 'CONFIRMED'
 where status = 'CONFIRMING_INVENTORY';

update orders set cancel_reason = saga_failure_reason
 where cancel_reason is null
   and saga_failure_reason is not null;

-- 3. Bỏ cột thuộc mô hình cũ: "sau khi nhả kho xong thì đơn đi đâu" giờ suy ra từ order_saga.status
alter table orders
    drop column if exists compensation_target_status,
    drop column if exists saga_failure_reason;

-- 4. Đồng bộ outbox_event với entity (entity đã rút về đúng bảng, chỉ còn thiếu aggregate_id)
alter table outbox_event
    add column if not exists aggregate_id varchar(36);

create index if not exists idx_outbox_event_pending
    on outbox_event (status, next_attempt_at);
