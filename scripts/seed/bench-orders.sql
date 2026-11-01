-- Nạp 200k đơn giả (mỗi đơn 2 dòng hàng) để đo hiệu năng danh sách admin. Chỉ chạy trên DB dev của order-service.
-- Chạy:  docker exec -i fashion-store-order-postgres-1 psql -U root -d order_database < scripts/seed/bench-orders.sql
-- Dọn:   delete from orders where id like 'bench-%';   (order_item xoá theo cascade)
INSERT INTO orders (id, created_at, updated_at, order_code, user_id, status, recipient_name, recipient_phone,
                    shipping_address, subtotal_amount, discount_amount, shipping_fee, total_amount, idempotency_key,
                    currency, payment_method)
SELECT 'bench-' || n,
       now() - (n || ' minutes')::interval,
       now(),
       'BENCH' || lpad(n::text, 8, '0'),
       'bench-user-' || (n % 5000),
       (ARRAY['COMPLETED','COMPLETED','COMPLETED','CANCELLED','PROCESSING','CONFIRMED','PACKED','SHIPPING','DELIVERED','REFUNDED'])[1 + n % 10],
       (ARRAY['Nguyen Van A','Tran Thi B','Le Van C','Pham Thi D','Hoang Van E'])[1 + n % 5] || ' ' || n,
       '09' || lpad((n % 100000000)::text, 8, '0'),
       n || ' Duong Bench, Quan 1, TP HCM',
       300000, 0, 30000, 330000,
       'bench-key-' || n,
       'VND',
       (ARRAY['COD','ONLINE','WALLET'])[1 + n % 3]
FROM generate_series(1, 200000) AS n
ON CONFLICT DO NOTHING;

INSERT INTO order_item (id, created_at, updated_at, order_id, variant_id, product_name, unit_price, quantity, line_total)
SELECT 'bench-oi-' || n || '-' || line, now(), now(), 'bench-' || n, 'bench-variant-' || line,
       'Bench Ao thun ' || (n % 200), 150000, 1 + (line % 2), 150000 * (1 + (line % 2))
FROM generate_series(1, 200000) AS n, generate_series(1, 2) AS line
ON CONFLICT DO NOTHING;

ANALYZE orders;
ANALYZE order_item;
