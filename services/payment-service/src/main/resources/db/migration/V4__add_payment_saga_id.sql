-- Payment nhớ sagaId của lệnh đã tạo ra nó, để event sinh từ callback cổng thanh toán vẫn
-- mang đúng correlationId về cho saga.
alter table payment
    add column if not exists saga_id varchar(36);

create index if not exists idx_payment_saga_id on payment (saga_id);
