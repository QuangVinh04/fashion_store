-- Ghi lại đơn vị tiền tệ mà AuthorizePaymentCommand yêu cầu, thay vì ngầm định VND như trước.
alter table payment
    add column if not exists currency varchar(3);

update payment set currency = 'VND' where currency is null;
