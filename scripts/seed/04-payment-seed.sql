-- 04-payment-seed.sql
-- Seed dữ liệu cho payment_database (Port: 5436 / Service: payment-service)
-- Mỗi bảng có từ 10 đến 15 dòng dữ liệu, đồng bộ User UUID và Order ID.
BEGIN;
SET client_encoding = 'UTF8';

-- 1. Payments (12 thanh toán đơn hàng + nạp ví)
INSERT INTO payment (
    id, order_id, user_id, type, method, provider, status,
    amount, provider_amount, provider_currency, transaction_id, merchant_reference,
    paid_at, created_at, updated_at
) VALUES
    ('pay-01', 'ord-01', '00000000-0000-0000-0000-000000000011', 'ORDER', 'ONLINE', 'VNPAY', 'COMPLETED',
     1158000, 1158000, 'VND', 'VNPAY-TX-10001', 'MER-ORD-01', now() - interval '7 days', now() - interval '7 days', now()),

    ('pay-02', 'ord-02', '00000000-0000-0000-0000-000000000012', 'ORDER', 'COD', 'COD', 'COMPLETED',
     1253000, 1253000, 'VND', 'COD-TX-10002', 'MER-ORD-02', now() - interval '4 days', now() - interval '6 days', now()),

    ('pay-03', 'ord-03', '00000000-0000-0000-0000-000000000013', 'ORDER', 'ONLINE', 'PAYOS', 'COMPLETED',
     898000, 898000, 'VND', 'PAYOS-TX-10003', 'MER-ORD-03', now() - interval '4 days', now() - interval '4 days', now()),

    ('pay-04', 'ord-04', '00000000-0000-0000-0000-000000000014', 'ORDER', 'ONLINE', 'VNPAY', 'COMPLETED',
     722000, 722000, 'VND', 'VNPAY-TX-10004', 'MER-ORD-04', now() - interval '2 days', now() - interval '2 days', now()),

    ('pay-05', 'ord-05', '00000000-0000-0000-0000-000000000015', 'ORDER', 'COD', 'COD', 'COD_PENDING',
     729000, 729000, 'VND', 'COD-TX-10005', 'MER-ORD-05', null, now() - interval '1 day', now()),

    ('pay-06', 'ord-06', '00000000-0000-0000-0000-000000000016', 'ORDER', 'COD', 'COD', 'COD_PENDING',
     834000, 834000, 'VND', 'COD-TX-10006', 'MER-ORD-06', null, now() - interval '12 hours', now()),

    ('pay-07', 'ord-07', '00000000-0000-0000-0000-000000000017', 'ORDER', 'ONLINE', 'PAYOS', 'PENDING',
     594000, 594000, 'VND', 'PAYOS-TX-10007', 'MER-ORD-07', null, now() - interval '2 hours', now()),

    ('pay-08', 'ord-08', '00000000-0000-0000-0000-000000000018', 'ORDER', 'COD', 'COD', 'CANCELLED',
     354000, 354000, 'VND', 'COD-TX-10008', 'MER-ORD-08', null, now() - interval '8 days', now()),

    ('pay-09', 'ord-09', '00000000-0000-0000-0000-000000000019', 'ORDER', 'WALLET', 'INTERNAL', 'REFUNDED',
     824000, 824000, 'VND', 'WAL-TX-10009', 'MER-ORD-09', now() - interval '14 days', now() - interval '14 days', now()),

    ('pay-10', 'ord-10', '00000000-0000-0000-0000-000000000020', 'ORDER', 'ONLINE', 'VNPAY', 'COMPLETED',
     599000, 599000, 'VND', 'VNPAY-TX-10010', 'MER-ORD-10', now() - interval '5 days', now() - interval '5 days', now()),

    ('pay-11', 'ord-11', '00000000-0000-0000-0000-000000000011', 'ORDER', 'ONLINE', 'PAYOS', 'COMPLETED',
     660000, 660000, 'VND', 'PAYOS-TX-10011', 'MER-ORD-11', now() - interval '15 days', now() - interval '15 days', now()),

    ('pay-12', 'ord-12', '00000000-0000-0000-0000-000000000002', 'ORDER', 'ONLINE', 'VNPAY', 'COMPLETED',
     1014000, 1014000, 'VND', 'VNPAY-TX-10012', 'MER-ORD-12', now() - interval '12 days', now() - interval '12 days', now())
ON CONFLICT (id) DO NOTHING;

-- 2. Payment Refunds (10 lệnh hoàn tiền)
INSERT INTO payment_refund (
    id, payment_id, order_id, amount, provider, status,
    provider_refund_id, idempotency_key, reason, completed_at, created_at, updated_at
) VALUES
    ('ref-01', 'pay-09', 'ord-09', 824000, 'INTERNAL', 'COMPLETED', 'REF-WAL-001', 'IDEMP-REF-001', 'Đổi trả hàng do rộng eo', now() - interval '10 days', now() - interval '10 days', now()),
    ('ref-02', 'pay-01', 'ord-01', 50000, 'VNPAY', 'COMPLETED', 'REF-VNP-002', 'IDEMP-REF-002', 'Hoàn phí chênh lệch khuyến mãi', now() - interval '4 days', now() - interval '4 days', now()),
    ('ref-03', 'pay-02', 'ord-02', 25000, 'COD', 'COMPLETED', 'REF-COD-003', 'IDEMP-REF-003', 'Hoàn phí giao hàng chậm trễ', now() - interval '3 days', now() - interval '3 days', now()),
    ('ref-04', 'pay-03', 'ord-03', 898000, 'PAYOS', 'PENDING', null, 'IDEMP-REF-004', 'Khách đề nghị hủy đơn hoàn tiền', null, now() - interval '1 day', now()),
    ('ref-05', 'pay-10', 'ord-10', 599000, 'VNPAY', 'COMPLETED', 'REF-VNP-005', 'IDEMP-REF-005', 'Khách trả hàng kho đã nhận', now() - interval '1 day', now() - interval '2 days', now()),
    ('ref-06', 'pay-11', 'ord-11', 50000, 'PAYOS', 'COMPLETED', 'REF-PAY-006', 'IDEMP-REF-006', 'Hoàn tiền voucher bù đắp trải nghiệm', now() - interval '10 days', now() - interval '10 days', now()),
    ('ref-07', 'pay-12', 'ord-12', 1014000, 'VNPAY', 'COMPLETED', 'REF-VNP-007', 'IDEMP-REF-007', 'Hoàn đơn hàng mẫu', now() - interval '6 days', now() - interval '6 days', now()),
    ('ref-08', 'pay-04', 'ord-04', 30000, 'VNPAY', 'PENDING', null, 'IDEMP-REF-008', 'Bù trừ voucher chưa áp dụng', null, now() - interval '2 hours', now()),
    ('ref-09', 'pay-05', 'ord-05', 30000, 'COD', 'FAILED', null, 'IDEMP-REF-009', 'Lỗi tài khoản nhận hoàn tiền', null, now() - interval '5 hours', now()),
    ('ref-10', 'pay-06', 'ord-06', 50000, 'COD', 'PENDING', null, 'IDEMP-REF-010', 'Xác minh khiếu nại cước vận chuyển', null, now() - interval '3 hours', now())
ON CONFLICT (id) DO NOTHING;

-- 3. Wallet Account (10 tài khoản ví điện tử người dùng)
INSERT INTO wallet_account (
    id, user_id, currency, balance, version, created_at, updated_at
) VALUES
    ('wal-acc-01', '00000000-0000-0000-0000-000000000002', 'VND', 1500000, 0, now() - interval '30 days', now()),
    ('wal-acc-02', '00000000-0000-0000-0000-000000000011', 'VND', 2340000, 0, now() - interval '30 days', now()),
    ('wal-acc-03', '00000000-0000-0000-0000-000000000012', 'VND', 850000, 0, now() - interval '30 days', now()),
    ('wal-acc-04', '00000000-0000-0000-0000-000000000013', 'VND', 500000, 0, now() - interval '30 days', now()),
    ('wal-acc-05', '00000000-0000-0000-0000-000000000014', 'VND', 1200000, 0, now() - interval '30 days', now()),
    ('wal-acc-06', '00000000-0000-0000-0000-000000000015', 'VND', 300000, 0, now() - interval '30 days', now()),
    ('wal-acc-07', '00000000-0000-0000-0000-000000000016', 'VND', 1800000, 0, now() - interval '30 days', now()),
    ('wal-acc-08', '00000000-0000-0000-0000-000000000017', 'VND', 950000, 0, now() - interval '30 days', now()),
    ('wal-acc-09', '00000000-0000-0000-0000-000000000018', 'VND', 2000000, 0, now() - interval '30 days', now()),
    ('wal-acc-10', '00000000-0000-0000-0000-000000000019', 'VND', 1650000, 0, now() - interval '30 days', now()),
    ('wal-acc-11', '00000000-0000-0000-0000-000000000020', 'VND', 750000, 0, now() - interval '30 days', now())
ON CONFLICT (id) DO NOTHING;

-- 4. Wallet Ledger Entries (15 bút toán biến động số dư ví)
INSERT INTO wallet_ledger_entry (
    id, account_id, user_id, direction, amount, balance_after,
    source_type, source_id, operation_id, created_at, updated_at
) VALUES
    ('wle-01', 'wal-acc-02', '00000000-0000-0000-0000-000000000011', 'CREDIT', 2000000, 2000000, 'TOP_UP', 'PAYOS-TOPUP-01', 'OP-WAL-001', now() - interval '20 days', now()),
    ('wle-02', 'wal-acc-02', '00000000-0000-0000-0000-000000000011', 'CREDIT', 340000, 2340000, 'REFUND', 'ref-02', 'OP-WAL-002', now() - interval '4 days', now()),

    ('wle-03', 'wal-acc-03', '00000000-0000-0000-0000-000000000012', 'CREDIT', 1000000, 1000000, 'TOP_UP', 'PAYOS-TOPUP-02', 'OP-WAL-003', now() - interval '18 days', now()),
    ('wle-04', 'wal-acc-03', '00000000-0000-0000-0000-000000000012', 'DEBIT', 150000, 850000, 'ORDER_PAYMENT', 'ord-sample-sub', 'OP-WAL-004', now() - interval '10 days', now()),

    ('wle-05', 'wal-acc-04', '00000000-0000-0000-0000-000000000013', 'CREDIT', 500000, 500000, 'TOP_UP', 'PAYOS-TOPUP-03', 'OP-WAL-005', now() - interval '15 days', now()),

    ('wle-06', 'wal-acc-05', '00000000-0000-0000-0000-000000000014', 'CREDIT', 1200000, 1200000, 'TOP_UP', 'PAYOS-TOPUP-04', 'OP-WAL-006', now() - interval '12 days', now()),

    ('wle-07', 'wal-acc-06', '00000000-0000-0000-0000-000000000015', 'CREDIT', 300000, 300000, 'TOP_UP', 'PAYOS-TOPUP-05', 'OP-WAL-007', now() - interval '10 days', now()),

    ('wle-08', 'wal-acc-07', '00000000-0000-0000-0000-000000000016', 'CREDIT', 1800000, 1800000, 'TOP_UP', 'PAYOS-TOPUP-06', 'OP-WAL-008', now() - interval '9 days', now()),

    ('wle-09', 'wal-acc-08', '00000000-0000-0000-0000-000000000017', 'CREDIT', 950000, 950000, 'TOP_UP', 'PAYOS-TOPUP-07', 'OP-WAL-009', now() - interval '8 days', now()),

    ('wle-10', 'wal-acc-09', '00000000-0000-0000-0000-000000000018', 'CREDIT', 2000000, 2000000, 'TOP_UP', 'PAYOS-TOPUP-08', 'OP-WAL-010', now() - interval '7 days', now()),

    ('wle-11', 'wal-acc-10', '00000000-0000-0000-0000-000000000019', 'CREDIT', 826000, 826000, 'TOP_UP', 'PAYOS-TOPUP-09', 'OP-WAL-011', now() - interval '15 days', now()),
    ('wle-12', 'wal-acc-10', '00000000-0000-0000-0000-000000000019', 'DEBIT', 824000, 2000, 'ORDER_PAYMENT', 'ord-09', 'OP-WAL-012', now() - interval '14 days', now()),
    ('wle-13', 'wal-acc-10', '00000000-0000-0000-0000-000000000019', 'CREDIT', 824000, 826000, 'REFUND', 'ref-01', 'OP-WAL-013', now() - interval '10 days', now()),
    ('wle-14', 'wal-acc-10', '00000000-0000-0000-0000-000000000019', 'CREDIT', 824000, 1650000, 'TOP_UP', 'PAYOS-TOPUP-10', 'OP-WAL-014', now() - interval '5 days', now()),

    ('wle-15', 'wal-acc-11', '00000000-0000-0000-0000-000000000020', 'CREDIT', 750000, 750000, 'TOP_UP', 'PAYOS-TOPUP-11', 'OP-WAL-015', now() - interval '3 days', now())
ON CONFLICT (id) DO NOTHING;

COMMIT;
