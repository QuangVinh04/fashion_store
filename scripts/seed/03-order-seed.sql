-- 03-order-seed.sql
-- Seed dữ liệu cho order_database (Port: 5435 / Service: order-service)
-- Mỗi bảng có từ 10 đến 24 dòng dữ liệu, đồng bộ User UUID và Variant ID.
BEGIN;
SET client_encoding = 'UTF8';

-- 1. Cart (12 giỏ hàng khách hàng, dùng ON CONFLICT (user_id) DO NOTHING)
INSERT INTO cart (id, user_id, status, created_at, updated_at) VALUES
    ('1efb8674-02de-4fa9-8fa9-593086e688a5', '7cb10f2e-3068-4a3a-b15e-981dd47c1273', 'ACTIVE', now(), now()),
    ('cart-02', '00000000-0000-0000-0000-000000000011', 'ACTIVE', now(), now()),
    ('cart-03', '00000000-0000-0000-0000-000000000012', 'ACTIVE', now(), now()),
    ('cart-04', '00000000-0000-0000-0000-000000000013', 'ACTIVE', now(), now()),
    ('cart-05', '00000000-0000-0000-0000-000000000014', 'ACTIVE', now(), now()),
    ('cart-06', '00000000-0000-0000-0000-000000000015', 'ACTIVE', now(), now()),
    ('cart-07', '00000000-0000-0000-0000-000000000016', 'ABANDONED', now() - interval '5 days', now()),
    ('cart-08', '00000000-0000-0000-0000-000000000017', 'ACTIVE', now(), now()),
    ('cart-09', '00000000-0000-0000-0000-000000000018', 'ACTIVE', now(), now()),
    ('cart-10', '00000000-0000-0000-0000-000000000019', 'ABANDONED', now() - interval '3 days', now()),
    ('cart-11', '00000000-0000-0000-0000-000000000020', 'ACTIVE', now(), now()),
    ('cart-12', '00000000-0000-0000-0000-000000000001', 'ACTIVE', now(), now())
ON CONFLICT (user_id) DO NOTHING;

-- 2. Cart Items (18 sản phẩm trong giỏ)
INSERT INTO cart_item (
    id, cart_id, variant_id, product_id, product_name,
    size_name, color_name, unit_price, quantity, created_at, updated_at
) VALUES
    ('ci-01', 'cart-02', 'var-01-m', 'prod-01', 'Áo Sơ Mi Trắng Oxford Cổ Đức', 'M', 'Trắng', 529000, 1, now(), now()),
    ('ci-02', 'cart-02', 'var-04-30', 'prod-04', 'Quần Jeans Xanh Dáng Suông Straight Fit', '30', 'Xanh denim', 699000, 1, now(), now()),
    ('ci-03', 'cart-03', 'var-08-m', 'prod-08', 'Đầm Liền Thân Công Sở Thanh Lịch', 'M', 'Đen', 799000, 1, now(), now()),
    ('ci-04', 'cart-03', 'var-12-m', 'prod-12', 'Chân Váy Chữ A Dáng Midi Thời Thượng', 'M', 'Đen', 479000, 1, now(), now()),
    ('ci-05', 'cart-04', 'var-02-m', 'prod-02', 'Áo Polo Nam Classic Pima Cotton', 'M', 'Đen', 449000, 2, now(), now()),
    ('ci-06', 'cart-05', 'var-03-m', 'prod-03', 'Áo Thun Trơn Compact Cổ Tròn', 'M', 'Trắng', 249000, 3, now(), now()),
    ('ci-07', 'cart-06', 'var-05-l', 'prod-05', 'Áo Khoác Bomber Gió Siêu Nhẹ 2 Lớp', 'L', 'Đen', 849000, 1, now(), now()),
    ('ci-08', 'cart-07', 'var-07-m', 'prod-07', 'Quần Short Đũi Nam Thoáng Mát', 'M', 'Be / Khaki', 329000, 1, now(), now()),
    ('ci-09', 'cart-08', 'var-06-31', 'prod-06', 'Quần Tây May Đo Slimfit Co Giãn', '31', 'Xám tiêu', 569000, 1, now(), now()),
    ('ci-10', 'cart-08', 'var-01-l', 'prod-01', 'Áo Sơ Mi Trắng Oxford Cổ Đức', 'L', 'Trắng', 529000, 1, now(), now()),
    ('ci-11', 'cart-09', 'var-09-l', 'prod-09', 'Áo Hoodie Nỉ Bông Unisex Oversize', 'L', 'Xám tiêu', 599000, 1, now(), now()),
    ('ci-12', 'cart-10', 'var-10-m', 'prod-10', 'Áo Len Cổ Lọ Dệt Kim Vintage', 'M', 'Nâu đất', 680000, 1, now(), now()),
    ('ci-13', 'cart-11', 'var-11-l', 'prod-11', 'Áo Khoác Denim Nam Classic Trucker', 'L', 'Xanh denim', 989000, 1, now(), now()),
    ('ci-14', '1efb8674-02de-4fa9-8fa9-593086e688a5', 'var-02-l', 'prod-02', 'Áo Polo Nam Classic Pima Cotton', 'L', 'Đen', 449000, 1, now(), now()),
    ('ci-15', '1efb8674-02de-4fa9-8fa9-593086e688a5', 'var-04-31', 'prod-04', 'Quần Jeans Xanh Dáng Suông Straight Fit', '31', 'Xanh denim', 699000, 1, now(), now())
ON CONFLICT (id) DO NOTHING;

-- 3. Promotions (10 mã giảm giá phong phú)
INSERT INTO promotion (
    id, code, type, value, max_discount, min_order_value,
    start_at, end_at, total_quota, per_user_quota, scope_type, scope_ids, active, created_at, updated_at
) VALUES
    ('promo-01', 'WELCOME50', 'FIXED', 50000, 50000, 300000, now() - interval '30 days', now() + interval '90 days', 500, 1, 'ALL', null, true, now(), now()),
    ('promo-02', 'FREESHIP', 'FIXED', 30000, 30000, 250000, now() - interval '30 days', now() + interval '90 days', 1000, 3, 'ALL', null, true, now(), now()),
    ('promo-03', 'LINO10', 'PERCENT', 10, 100000, 500000, now() - interval '15 days', now() + interval '60 days', 200, 2, 'ALL', null, true, now(), now()),
    ('promo-04', 'VIP15', 'PERCENT', 15, 200000, 1000000, now() - interval '10 days', now() + interval '60 days', 100, 1, 'ALL', null, true, now(), now()),
    ('promo-05', 'FLASH30', 'PERCENT', 30, 150000, 400000, now() - interval '5 days', now() + interval '10 days', 150, 1, 'ALL', null, true, now(), now()),
    ('promo-06', 'HE2026', 'PERCENT', 12, 120000, 600000, now() - interval '10 days', now() + interval '45 days', 300, 2, 'ALL', null, true, now(), now()),
    ('promo-07', 'TETVUI', 'FIXED', 100000, 100000, 800000, now() - interval '60 days', now() - interval '10 days', 50, 1, 'ALL', null, false, now(), now()),
    ('promo-08', 'BLACKFRIDAY', 'PERCENT', 20, 300000, 700000, now() - interval '20 days', now() + interval '30 days', 500, 2, 'ALL', null, true, now(), now()),
    ('promo-09', 'MEMBER20', 'PERCENT', 20, 200000, 800000, now() - interval '10 days', now() + interval '60 days', 200, 1, 'ALL', null, true, now(), now()),
    ('promo-10', 'GIAM10K', 'FIXED', 10000, 10000, 150000, now() - interval '10 days', now() + interval '90 days', 1000, 5, 'ALL', null, true, now(), now())
ON CONFLICT (id) DO NOTHING;

-- 4. Orders (12 đơn hàng chuẩn schema orders)
INSERT INTO orders (
    id, order_code, user_id, status, recipient_name, recipient_phone, recipient_email,
    shipping_address, shipping_provider, tracking_code,
    subtotal_amount, discount_amount, shipping_fee, total_amount,
    idempotency_key, currency, district_id, ward_code, province_id,
    delivered_at, completed_at, created_at, updated_at
) VALUES
    ('ord-01', 'ORD-2026-0001', '00000000-0000-0000-0000-000000000011', 'COMPLETED',
     'Nguyễn Văn An', '0912345601', 'nguyen.van.an@fashion.local',
     'Số 15 Hàng Bài, Phường Tràng Tiền, Quận Hoàn Kiếm, Hà Nội', 'GHN', 'GHN-ORD-0001',
     1228000, 100000, 30000, 1158000,
     'idemp-ord-01', 'VND', 1482, '10101', 201,
     now() - interval '5 days', now() - interval '4 days', now() - interval '7 days', now()),

    ('ord-02', 'ORD-2026-0002', '00000000-0000-0000-0000-000000000012', 'COMPLETED',
     'Trần Thị Bình', '0912345602', 'tran.thi.binh@fashion.local',
     '45 Nguyễn Thị Minh Khai, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh', 'GHN', 'GHN-ORD-0002',
     1278000, 50000, 25000, 1253000,
     'idemp-ord-02', 'VND', 1442, '20101', 202,
     now() - interval '4 days', now() - interval '3 days', now() - interval '6 days', now()),

    ('ord-03', 'ORD-2026-0003', '00000000-0000-0000-0000-000000000013', 'DELIVERED',
     'Lê Hoàng Nam', '0912345603', 'le.hoang.nam@fashion.local',
     '88 Trần Phú, Phường Hải Châu 1, Quận Hải Châu, Đà Nẵng', 'GHN', 'GHN-ORD-0003',
     898000, 30000, 30000, 898000,
     'idemp-ord-03', 'VND', 1530, '40101', 203,
     now() - interval '1 day', null, now() - interval '4 days', now()),

    ('ord-04', 'ORD-2026-0004', '00000000-0000-0000-0000-000000000014', 'SHIPPING',
     'Phạm Minh Trang', '0912345604', 'pham.minh.trang@fashion.local',
     '204 Cầu Giấy, Phường Dịch Vọng, Quận Cầu Giấy, Hà Nội', 'GHN', 'GHN-ORD-0004',
     747000, 50000, 25000, 722000,
     'idemp-ord-04', 'VND', 1485, '10203', 201,
     null, null, now() - interval '2 days', now()),

    ('ord-05', 'ORD-2026-0005', '00000000-0000-0000-0000-000000000015', 'PROCESSING',
     'Vũ Đức Thắng', '0912345605', 'vu.duc.thang@fashion.local',
     '12 Lạch Tray, Phường Lạch Tray, Quận Ngô Quyền, Hải Phòng', 'GHN', 'GHN-ORD-0005',
     699000, 0, 30000, 729000,
     'idemp-ord-05', 'VND', 1560, '30102', 204,
     null, null, now() - interval '1 day', now()),

    ('ord-06', 'ORD-2026-0006', '00000000-0000-0000-0000-000000000016', 'CONFIRMED',
     'Hoàng Thu Thảo', '0912345606', 'hoang.thu.thao@fashion.local',
     '56 Nguyễn Trãi, Phường Ninh Kiều, Quận Ninh Kiều, Cần Thơ', 'GHN', 'GHN-ORD-0006',
     849000, 50000, 35000, 834000,
     'idemp-ord-06', 'VND', 1580, '50104', 205,
     null, null, now() - interval '12 hours', now()),

    ('ord-07', 'ORD-2026-0007', '00000000-0000-0000-0000-000000000017', 'PENDING',
     'Đỗ Quốc Bảo', '0912345607', 'do.quoc.bao@fashion.local',
     '180 Võ Thị Sáu, Phường Võ Thị Sáu, Quận 3, TP. Hồ Chí Minh', 'GHN', null,
     569000, 0, 25000, 594000,
     'idemp-ord-07', 'VND', 1444, '20205', 202,
     null, null, now() - interval '2 hours', now()),

    ('ord-08', 'ORD-2026-0008', '00000000-0000-0000-0000-000000000018', 'CANCELLED',
     'Ngô Phương Linh', '0912345608', 'ngo.phuong.linh@fashion.local',
     '77 Nguyễn Thái Học, Phường Điện Biên, Quận Ba Đình, Hà Nội', 'GHN', null,
     329000, 0, 25000, 354000,
     'idemp-ord-08', 'VND', 1481, '10103', 201,
     null, null, now() - interval '8 days', now() - interval '7 days'),

    ('ord-09', 'ORD-2026-0009', '00000000-0000-0000-0000-000000000019', 'RETURNED',
     'Bùi Anh Tuấn', '0912345609', 'bui.anh.tuan@fashion.local',
     '320 Nguyễn Văn Linh, Phường Tân Phú, Quận 7, TP. Hồ Chí Minh', 'GHN', 'GHN-ORD-0009',
     799000, 0, 25000, 824000,
     'idemp-ord-09', 'VND', 1450, '20701', 202,
     now() - interval '10 days', null, now() - interval '14 days', now()),

    ('ord-10', 'ORD-2026-0010', '00000000-0000-0000-0000-000000000020', 'COMPLETED',
     'Đinh Ngọc Mai', '0912345610', 'dinh.ngoc.mai@fashion.local',
     '95 Hùng Vương, Phường Phú Hội, Thành phố Huế, Thừa Thiên Huế', 'GHN', 'GHN-ORD-0010',
     599000, 30000, 30000, 599000,
     'idemp-ord-10', 'VND', 1600, '60102', 206,
     now() - interval '2 days', now() - interval '1 day', now() - interval '5 days', now()),

    ('ord-11', 'ORD-2026-0011', '00000000-0000-0000-0000-000000000011', 'COMPLETED',
     'Nguyễn Văn An', '0912345601', 'nguyen.van.an@fashion.local',
     'Số 15 Hàng Bài, Phường Tràng Tiền, Quận Hoàn Kiếm, Hà Nội', 'GHN', 'GHN-ORD-0011',
     680000, 50000, 30000, 660000,
     'idemp-ord-11', 'VND', 1482, '10101', 201,
     now() - interval '12 days', now() - interval '11 days', now() - interval '15 days', now()),

    ('ord-12', 'ORD-2026-0012', '7cb10f2e-3068-4a3a-b15e-981dd47c1273', 'COMPLETED',
     'Khách Hàng Mẫu', '0901000002', 'customer@fashion.local',
     '123 Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh', 'GHN', 'GHN-ORD-0012',
     989000, 0, 25000, 1014000,
     'idemp-ord-12', 'VND', 1442, '20101', 202,
     now() - interval '9 days', now() - interval '8 days', now() - interval '12 days', now())
ON CONFLICT (id) DO NOTHING;

-- 5. Order Items (18 chi tiết đơn hàng)
INSERT INTO order_item (
    id, order_id, variant_id, product_name, size_name, color_name,
    unit_price, quantity, line_total, created_at, updated_at
) VALUES
    ('oi-01', 'ord-01', 'var-01-m', 'Áo Sơ Mi Trắng Oxford Cổ Đức', 'M', 'Trắng', 529000, 1, 529000, now(), now()),
    ('oi-02', 'ord-01', 'var-04-30', 'Quần Jeans Xanh Dáng Suông Straight Fit', '30', 'Xanh denim', 699000, 1, 699000, now(), now()),
    ('oi-03', 'ord-02', 'var-08-m', 'Đầm Liền Thân Công Sở Thanh Lịch', 'M', 'Đen', 799000, 1, 799000, now(), now()),
    ('oi-04', 'ord-02', 'var-12-m', 'Chân Váy Chữ A Dáng Midi Thời Thượng', 'M', 'Đen', 479000, 1, 479000, now(), now()),
    ('oi-05', 'ord-03', 'var-02-m', 'Áo Polo Nam Classic Pima Cotton', 'M', 'Đen', 449000, 2, 898000, now(), now()),
    ('oi-06', 'ord-04', 'var-03-m', 'Áo Thun Trơn Compact Cổ Tròn', 'M', 'Trắng', 249000, 3, 747000, now(), now()),
    ('oi-07', 'ord-05', 'var-04-31', 'Quần Jeans Xanh Dáng Suông Straight Fit', '31', 'Xanh denim', 699000, 1, 699000, now(), now()),
    ('oi-08', 'ord-06', 'var-05-l', 'Áo Khoác Bomber Gió Siêu Nhẹ 2 Lớp', 'L', 'Đen', 849000, 1, 849000, now(), now()),
    ('oi-09', 'ord-07', 'var-06-31', 'Quần Tây May Đo Slimfit Co Giãn', '31', 'Xám tiêu', 569000, 1, 569000, now(), now()),
    ('oi-10', 'ord-08', 'var-07-m', 'Quần Short Đũi Nam Thoáng Mát', 'M', 'Be / Khaki', 329000, 1, 329000, now(), now()),
    ('oi-11', 'ord-09', 'var-08-l', 'Đầm Liền Thân Công Sở Thanh Lịch', 'L', 'Đen', 799000, 1, 799000, now(), now()),
    ('oi-12', 'ord-10', 'var-09-m', 'Áo Hoodie Nỉ Bông Unisex Oversize', 'M', 'Xám tiêu', 599000, 1, 599000, now(), now()),
    ('oi-13', 'ord-11', 'var-10-m', 'Áo Len Cổ Lọ Dệt Kim Vintage', 'M', 'Nâu đất', 680000, 1, 680000, now(), now()),
    ('oi-14', 'ord-12', 'var-11-l', 'Áo Khoác Denim Nam Classic Trucker', 'L', 'Xanh denim', 989000, 1, 989000, now(), now())
ON CONFLICT (id) DO NOTHING;

-- 6. Order Status History (18 dòng theo dõi trạng thái)
INSERT INTO order_status_history (
    id, order_id, from_status, to_status, action, changed_by, reason, created_at, updated_at
) VALUES
    ('osh-01', 'ord-01', 'PENDING', 'CONFIRMED', 'AUTO_CONFIRM', 'SYSTEM', 'Thanh toán online thành công', now() - interval '7 days', now()),
    ('osh-02', 'ord-01', 'CONFIRMED', 'PROCESSING', 'WAREHOUSE_PICK', 'STAFF_KHO', 'Đã in phiếu đóng gói', now() - interval '6 days', now()),
    ('osh-03', 'ord-01', 'PROCESSING', 'SHIPPING', 'DISPATCH', 'GHN_SHIPPER', 'Đã bàn giao cho đơn vị vận chuyển GHN', now() - interval '6 days', now()),
    ('osh-04', 'ord-01', 'SHIPPING', 'DELIVERED', 'GHN_DELIVERED', 'GHN_WEBHOOK', 'Giao hàng thành công tới khách', now() - interval '5 days', now()),
    ('osh-05', 'ord-01', 'DELIVERED', 'COMPLETED', 'CUSTOMER_CONFIRM', '00000000-0000-0000-0000-000000000011', 'Khách xác nhận đã nhận đủ hàng', now() - interval '4 days', now()),

    ('osh-06', 'ord-02', 'PENDING', 'CONFIRMED', 'COD_CONFIRMED', 'SYSTEM', 'Đơn hàng COD xác thực thành công', now() - interval '6 days', now()),
    ('osh-07', 'ord-02', 'CONFIRMED', 'SHIPPING', 'DISPATCH', 'GHN_SHIPPER', 'Shipper GHN đã lấy hàng', now() - interval '5 days', now()),
    ('osh-08', 'ord-02', 'SHIPPING', 'DELIVERED', 'GHN_DELIVERED', 'GHN_WEBHOOK', 'Giao hàng và thu COD thành công', now() - interval '4 days', now()),
    ('osh-09', 'ord-02', 'DELIVERED', 'COMPLETED', 'AUTO_COMPLETE', 'SYSTEM', 'Tự động hoàn tất sau 24h', now() - interval '3 days', now()),

    ('osh-10', 'ord-03', 'PENDING', 'CONFIRMED', 'AUTO_CONFIRM', 'SYSTEM', 'Thanh toán PayOS thành công', now() - interval '4 days', now()),
    ('osh-11', 'ord-03', 'CONFIRMED', 'SHIPPING', 'DISPATCH', 'GHN_SHIPPER', 'Đang vận chuyển', now() - interval '2 days', now()),
    ('osh-12', 'ord-03', 'SHIPPING', 'DELIVERED', 'GHN_DELIVERED', 'GHN_WEBHOOK', 'Đã giao tới khách hàng', now() - interval '1 day', now()),

    ('osh-13', 'ord-04', 'PENDING', 'CONFIRMED', 'AUTO_CONFIRM', 'SYSTEM', 'Thanh toán VNPay thành công', now() - interval '2 days', now()),
    ('osh-14', 'ord-04', 'CONFIRMED', 'SHIPPING', 'DISPATCH', 'GHN_SHIPPER', 'Bàn giao bưu tá', now() - interval '1 day', now()),

    ('osh-15', 'ord-05', 'PENDING', 'CONFIRMED', 'COD_CONFIRMED', 'SYSTEM', 'Xác nhận đơn COD', now() - interval '1 day', now()),
    ('osh-16', 'ord-05', 'CONFIRMED', 'PROCESSING', 'PACKING', 'STAFF_KHO', 'Đang đóng gói hàng', now() - interval '18 hours', now()),

    ('osh-17', 'ord-08', 'PENDING', 'CANCELLED', 'USER_CANCEL', '00000000-0000-0000-0000-000000000018', 'Khách hàng đổi ý muốn chọn màu khác', now() - interval '8 days', now() - interval '7 days'),

    ('osh-18', 'ord-09', 'DELIVERED', 'RETURNED', 'RETURN_COMPLETE', 'STAFF_QC', 'Kho đã nhận hàng trả và hoàn tất kiểm tra', now() - interval '10 days', now())
ON CONFLICT (id) DO NOTHING;

-- 7. Shipment (10 vận đơn giao hàng GHN)
INSERT INTO shipment (
    id, order_id, provider, tracking_code, ghn_order_code,
    to_district_id, to_ward_code, fee, weight_gram, status, created_at, updated_at
) VALUES
    ('shp-01', 'ord-01', 'GHN', 'GHN-ORD-0001', 'GHN260001', 1482, '10101', 30000, 1000, 'DELIVERED', now() - interval '6 days', now()),
    ('shp-02', 'ord-02', 'GHN', 'GHN-ORD-0002', 'GHN260002', 1442, '20101', 25000, 690, 'DELIVERED', now() - interval '5 days', now()),
    ('shp-03', 'ord-03', 'GHN', 'GHN-ORD-0003', 'GHN260003', 1530, '40101', 30000, 560, 'DELIVERED', now() - interval '3 days', now()),
    ('shp-04', 'ord-04', 'GHN', 'GHN-ORD-0004', 'GHN260004', 1485, '10203', 25000, 660, 'SHIPPING', now() - interval '1 day', now()),
    ('shp-05', 'ord-05', 'GHN', 'GHN-ORD-0005', 'GHN260005', 1560, '30102', 30000, 680, 'PICKED', now() - interval '10 hours', now()),
    ('shp-06', 'ord-06', 'GHN', 'GHN-ORD-0006', 'GHN260006', 1580, '50104', 35000, 450, 'PENDING', now() - interval '4 hours', now()),
    ('shp-07', 'ord-09', 'GHN', 'GHN-ORD-0009', 'GHN260009', 1450, '20701', 25000, 400, 'DELIVERED', now() - interval '12 days', now()),
    ('shp-08', 'ord-10', 'GHN', 'GHN-ORD-0010', 'GHN260010', 1600, '60102', 30000, 620, 'DELIVERED', now() - interval '4 days', now()),
    ('shp-09', 'ord-11', 'GHN', 'GHN-ORD-0011', 'GHN260011', 1482, '10101', 30000, 480, 'DELIVERED', now() - interval '14 days', now()),
    ('shp-10', 'ord-12', 'GHN', 'GHN-ORD-0012', 'GHN260012', 1442, '20101', 25000, 750, 'DELIVERED', now() - interval '11 days', now())
ON CONFLICT (id) DO NOTHING;

-- 8. Return Request (10 yêu cầu trả hàng)
INSERT INTO return_request (
    id, order_id, user_id, reason, images, status, reject_reason, reviewed_by, reviewed_at, created_at, updated_at
) VALUES
    ('ret-01', 'ord-09', '00000000-0000-0000-0000-000000000019', 'Mặc thử bị rộng eo, muốn đổi size nhỏ hơn', '["https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=600"]', 'REFUNDED', null, 'staff@fashion.local', now() - interval '10 days', now() - interval '11 days', now()),
    ('ret-02', 'ord-01', '00000000-0000-0000-0000-000000000011', 'Màu áo thực tế sáng hơn mong đợi một chút', '["https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=600"]', 'REJECTED', 'Sản phẩm đã qua sử dụng và cắt tem mác', 'staff@fashion.local', now() - interval '4 days', now() - interval '5 days', now()),
    ('ret-03', 'ord-02', '00000000-0000-0000-0000-000000000012', 'Chọn nhầm size 31 thay vì 30', null, 'APPROVED', null, 'staff@fashion.local', now() - interval '1 day', now() - interval '2 days', now()),
    ('ret-04', 'ord-03', '00000000-0000-0000-0000-000000000013', 'Áo bị rộng vai', null, 'REQUESTED', null, null, null, now() - interval '6 hours', now()),
    ('ret-05', 'ord-10', '00000000-0000-0000-0000-000000000020', 'Không thích màu xám tiêu này nữa', null, 'RECEIVED', null, 'staff@fashion.local', now() - interval '1 day', now() - interval '2 days', now()),
    ('ret-06', 'ord-11', '00000000-0000-0000-0000-000000000011', 'Muốn đổi sang màu be', null, 'CANCELLED', 'Khách báo giữ lại dùng', 'staff@fashion.local', now() - interval '8 days', now() - interval '9 days', now()),
    ('ret-07', 'ord-12', '7cb10f2e-3068-4a3a-b15e-981dd47c1273', 'Áo khoác hơi dài tay', null, 'APPROVED', null, 'staff@fashion.local', now() - interval '6 days', now() - interval '7 days', now()),
    ('ret-08', 'ord-04', '00000000-0000-0000-0000-000000000014', 'Đặt thừa số lượng', null, 'REQUESTED', null, null, null, now() - interval '1 hour', now()),
    ('ret-09', 'ord-05', '00000000-0000-0000-0000-000000000015', 'Muốn hủy đổi mẫu khác', null, 'REQUESTED', null, null, null, now() - interval '2 hours', now()),
    ('ret-10', 'ord-06', '00000000-0000-0000-0000-000000000016', 'Nhầm địa chỉ nhận', null, 'REQUESTED', null, null, null, now() - interval '3 hours', now())
ON CONFLICT (id) DO NOTHING;


-- 10. Coupon Usage (10 lượt sử dụng mã ưu đãi)
INSERT INTO coupon_usage (
    id, promotion_id, user_id, order_id, used_at, status, created_at, updated_at
) VALUES
    ('cu-01', 'promo-03', '00000000-0000-0000-0000-000000000011', 'ord-01', now() - interval '7 days', 'APPLIED', now(), now()),
    ('cu-02', 'promo-01', '00000000-0000-0000-0000-000000000012', 'ord-02', now() - interval '6 days', 'APPLIED', now(), now()),
    ('cu-03', 'promo-02', '00000000-0000-0000-0000-000000000013', 'ord-03', now() - interval '4 days', 'APPLIED', now(), now()),
    ('cu-04', 'promo-01', '00000000-0000-0000-0000-000000000014', 'ord-04', now() - interval '2 days', 'APPLIED', now(), now()),
    ('cu-05', 'promo-01', '00000000-0000-0000-0000-000000000016', 'ord-06', now() - interval '12 hours', 'APPLIED', now(), now()),
    ('cu-06', 'promo-02', '00000000-0000-0000-0000-000000000020', 'ord-10', now() - interval '5 days', 'APPLIED', now(), now()),
    ('cu-07', 'promo-01', '00000000-0000-0000-0000-000000000011', 'ord-11', now() - interval '15 days', 'APPLIED', now(), now()),
    ('cu-08', 'promo-10', '00000000-0000-0000-0000-000000000015', 'ord-05', now() - interval '1 day', 'RESERVED', now(), now()),
    ('cu-09', 'promo-02', '00000000-0000-0000-0000-000000000017', 'ord-07', now() - interval '2 hours', 'RESERVED', now(), now()),
    ('cu-10', 'promo-01', '00000000-0000-0000-0000-000000000018', 'ord-08', now() - interval '8 days', 'CANCELLED', now(), now())
ON CONFLICT (id) DO NOTHING;

-- 11. Checkout Sessions (10 phiên thanh toán)
INSERT INTO checkout (
    id, user_id, order_id, status, payment_method, payment_provider, shipping_method, coupon_code,
    subtotal_amount, discount_amount, shipping_fee, total_amount, submitted_at, expired_at, created_at, updated_at
) VALUES
    ('chk-01', '00000000-0000-0000-0000-000000000011', 'ord-01', 'COMPLETED', 'ONLINE', 'VNPAY', 'STANDARD', 'LINO10', 1228000, 100000, 30000, 1158000, now() - interval '7 days', now() + interval '1 day', now(), now()),
    ('chk-02', '00000000-0000-0000-0000-000000000012', 'ord-02', 'COMPLETED', 'COD', 'COD', 'STANDARD', 'WELCOME50', 1278000, 50000, 25000, 1253000, now() - interval '6 days', now() + interval '1 day', now(), now()),
    ('chk-03', '00000000-0000-0000-0000-000000000013', 'ord-03', 'COMPLETED', 'ONLINE', 'PAYOS', 'STANDARD', 'FREESHIP', 898000, 30000, 30000, 898000, now() - interval '4 days', now() + interval '1 day', now(), now()),
    ('chk-04', '00000000-0000-0000-0000-000000000014', 'ord-04', 'COMPLETED', 'ONLINE', 'VNPAY', 'STANDARD', 'WELCOME50', 747000, 50000, 25000, 722000, now() - interval '2 days', now() + interval '1 day', now(), now()),
    ('chk-05', '00000000-0000-0000-0000-000000000015', 'ord-05', 'COMPLETED', 'COD', 'COD', 'STANDARD', null, 699000, 0, 30000, 729000, now() - interval '1 day', now() + interval '1 day', now(), now()),
    ('chk-06', '00000000-0000-0000-0000-000000000016', 'ord-06', 'COMPLETED', 'COD', 'COD', 'STANDARD', 'WELCOME50', 849000, 50000, 35000, 834000, now() - interval '12 hours', now() + interval '1 day', now(), now()),
    ('chk-07', '00000000-0000-0000-0000-000000000017', 'ord-07', 'PENDING', 'ONLINE', 'PAYOS', 'STANDARD', null, 569000, 0, 25000, 594000, null, now() + interval '30 minutes', now(), now()),
    ('chk-08', '00000000-0000-0000-0000-000000000018', 'ord-08', 'EXPIRED', 'COD', 'COD', 'STANDARD', null, 329000, 0, 25000, 354000, null, now() - interval '7 days', now() - interval '8 days', now()),
    ('chk-09', '00000000-0000-0000-0000-000000000019', 'ord-09', 'COMPLETED', 'ONLINE', 'WALLET', 'STANDARD', null, 799000, 0, 25000, 824000, now() - interval '14 days', now() + interval '1 day', now(), now()),
    ('chk-10', '00000000-0000-0000-0000-000000000020', 'ord-10', 'COMPLETED', 'ONLINE', 'VNPAY', 'STANDARD', 'FREESHIP', 599000, 30000, 30000, 599000, now() - interval '5 days', now() + interval '1 day', now(), now())
ON CONFLICT (id) DO NOTHING;

-- 12. Checkout Items (10 dòng)
INSERT INTO checkout_item (
    id, checkout_id, variant_id, product_name, size_name, color_name,
    unit_price, quantity, line_total, created_at, updated_at
) VALUES
    ('cki-01', 'chk-01', 'var-01-m', 'Áo Sơ Mi Trắng Oxford Cổ Đức', 'M', 'Trắng', 529000, 1, 529000, now(), now()),
    ('cki-02', 'chk-01', 'var-04-30', 'Quần Jeans Xanh Dáng Suông Straight Fit', '30', 'Xanh denim', 699000, 1, 699000, now(), now()),
    ('cki-03', 'chk-02', 'var-08-m', 'Đầm Liền Thân Công Sở Thanh Lịch', 'M', 'Đen', 799000, 1, 799000, now(), now()),
    ('cki-04', 'chk-03', 'var-02-m', 'Áo Polo Nam Classic Pima Cotton', 'M', 'Đen', 449000, 2, 898000, now(), now()),
    ('cki-05', 'chk-04', 'var-03-m', 'Áo Thun Trơn Compact Cổ Tròn', 'M', 'Trắng', 249000, 3, 747000, now(), now()),
    ('cki-06', 'chk-05', 'var-04-31', 'Quần Jeans Xanh Dáng Suông Straight Fit', '31', 'Xanh denim', 699000, 1, 699000, now(), now()),
    ('cki-07', 'chk-06', 'var-05-l', 'Áo Khoác Bomber Gió Siêu Nhẹ 2 Lớp', 'L', 'Đen', 849000, 1, 849000, now(), now()),
    ('cki-08', 'chk-07', 'var-06-31', 'Quần Tây May Đo Slimfit Co Giãn', '31', 'Xám tiêu', 569000, 1, 569000, now(), now()),
    ('cki-09', 'chk-09', 'var-08-l', 'Đầm Liền Thân Công Sở Thanh Lịch', 'L', 'Đen', 799000, 1, 799000, now(), now()),
    ('cki-10', 'chk-10', 'var-09-m', 'Áo Hoodie Nỉ Bông Unisex Oversize', 'M', 'Xám tiêu', 599000, 1, 599000, now(), now())
ON CONFLICT (id) DO NOTHING;

COMMIT;
