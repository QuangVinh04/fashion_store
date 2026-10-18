-- 01-identity-seed.sql
-- Seed dữ liệu cho identity_database (Port: 5434 / Service: identity-service)
-- Đồng bộ hoàn toàn với User UUID trong platform/keycloak/realm-fashion-store.json
BEGIN;
SET client_encoding = 'UTF8';

-- 1. Permissions (12 quyền)
INSERT INTO permissions (id, name, description) VALUES
    ('perm-01', 'product:read', 'Xem danh sách và chi tiết sản phẩm'),
    ('perm-02', 'product:write', 'Thêm, sửa, xóa sản phẩm và biến thể'),
    ('perm-03', 'category:read', 'Xem danh mục và thương hiệu'),
    ('perm-04', 'category:write', 'Quản lý danh mục và thương hiệu'),
    ('perm-05', 'order:read', 'Xem danh sách và chi tiết đơn hàng'),
    ('perm-06', 'order:write', 'Xử lý, cập nhật trạng thái đơn hàng'),
    ('perm-07', 'inventory:manage', 'Quản lý kho hàng và điều chỉnh tồn kho'),
    ('perm-08', 'user:read', 'Xem thông tin khách hàng'),
    ('perm-09', 'user:manage', 'Khóa, mở khóa và quản lý tài khoản'),
    ('perm-10', 'promotion:manage', 'Quản lý mã giảm giá và khuyến mãi'),
    ('perm-11', 'payment:manage', 'Tra cứu giao dịch và xử lý hoàn tiền'),
    ('perm-12', 'report:view', 'Xem báo cáo doanh thu và vận hành')
ON CONFLICT (name) DO NOTHING;

-- 2. Roles
INSERT INTO roles (id, name, description) VALUES
    ('00000000-0000-0000-0000-000000000001', 'USER', 'Khách hàng mua sắm'),
    ('00000000-0000-0000-0000-000000000002', 'ADMIN', 'Quản trị viên hệ thống'),
    ('00000000-0000-0000-0000-000000000003', 'STAFF', 'Nhân viên vận hành cửa hàng')
ON CONFLICT (name) DO NOTHING;

-- 3. Role Permissions
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('00000000-0000-0000-0000-000000000001', 'perm-01'),
    ('00000000-0000-0000-0000-000000000001', 'perm-03'),
    ('00000000-0000-0000-0000-000000000002', 'perm-01'),
    ('00000000-0000-0000-0000-000000000002', 'perm-02'),
    ('00000000-0000-0000-0000-000000000002', 'perm-03'),
    ('00000000-0000-0000-0000-000000000002', 'perm-04'),
    ('00000000-0000-0000-0000-000000000002', 'perm-05'),
    ('00000000-0000-0000-0000-000000000002', 'perm-06'),
    ('00000000-0000-0000-0000-000000000002', 'perm-07'),
    ('00000000-0000-0000-0000-000000000002', 'perm-08'),
    ('00000000-0000-0000-0000-000000000002', 'perm-09'),
    ('00000000-0000-0000-0000-000000000002', 'perm-10'),
    ('00000000-0000-0000-0000-000000000002', 'perm-11'),
    ('00000000-0000-0000-0000-000000000002', 'perm-12')
ON CONFLICT DO NOTHING;

-- 4. Users (12 tài khoản đồng bộ UUID Keycloak)
INSERT INTO users (id, email, password, full_name, phone, address, avatar, is_active, is_email_verified, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000001', 'admin@fashion.local', '{noop}Admin@12345', 'Quản Trị Viên', '0901000001', 'Tòa nhà LINO Tower, Cầu Giấy, Hà Nội', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400', true, true, now(), now()),
    ('7cb10f2e-3068-4a3a-b15e-981dd47c1273', 'customer@fashion.local', '{noop}Customer@12345', 'Khách Hàng Mẫu', '0901000002', '123 Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh', 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400', true, true, now(), now()),
    ('00000000-0000-0000-0000-000000000011', 'nguyen.van.an@fashion.local', '{noop}User@12345', 'Nguyễn Văn An', '0912345601', 'Số 15 Hàng Bài, Phường Tràng Tiền, Quận Hoàn Kiếm, Hà Nội', 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400', true, true, now(), now()),
    ('00000000-0000-0000-0000-000000000012', 'tran.thi.binh@fashion.local', '{noop}User@12345', 'Trần Thị Bình', '0912345602', '45 Nguyễn Thị Minh Khai, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=400', true, true, now(), now()),
    ('00000000-0000-0000-0000-000000000013', 'le.hoang.nam@fashion.local', '{noop}User@12345', 'Lê Hoàng Nam', '0912345603', '88 Trần Phú, Phường Hải Châu 1, Quận Hải Châu, Đà Nẵng', 'https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=400', true, true, now(), now()),
    ('00000000-0000-0000-0000-000000000014', 'pham.minh.trang@fashion.local', '{noop}User@12345', 'Phạm Minh Trang', '0912345604', '204 Cầu Giấy, Phường Dịch Vọng, Quận Cầu Giấy, Hà Nội', 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=400', true, true, now(), now()),
    ('00000000-0000-0000-0000-000000000015', 'vu.duc.thang@fashion.local', '{noop}User@12345', 'Vũ Đức Thắng', '0912345605', '12 Lạch Tray, Phường Lạch Tray, Quận Ngô Quyền, Hải Phòng', 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=400', true, true, now(), now()),
    ('00000000-0000-0000-0000-000000000016', 'hoang.thu.thao@fashion.local', '{noop}User@12345', 'Hoàng Thu Thảo', '0912345606', '56 Nguyễn Trãi, Phường Ninh Kiều, Quận Ninh Kiều, Cần Thơ', 'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400', true, true, now(), now()),
    ('00000000-0000-0000-0000-000000000017', 'do.quoc.bao@fashion.local', '{noop}User@12345', 'Đỗ Quốc Bảo', '0912345607', '180 Võ Thị Sáu, Phường Võ Thị Sáu, Quận 3, TP. Hồ Chí Minh', 'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=400', true, true, now(), now()),
    ('00000000-0000-0000-0000-000000000018', 'ngo.phuong.linh@fashion.local', '{noop}User@12345', 'Ngô Phương Linh', '0912345608', '77 Nguyễn Thái Học, Phường Điện Biên, Quận Ba Đình, Hà Nội', 'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400', true, true, now(), now()),
    ('00000000-0000-0000-0000-000000000019', 'bui.anh.tuan@fashion.local', '{noop}User@12345', 'Bùi Anh Tuấn', '0912345609', '320 Nguyễn Văn Linh, Phường Tân Phú, Quận 7, TP. Hồ Chí Minh', 'https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=400', true, true, now(), now()),
    ('00000000-0000-0000-0000-000000000020', 'dinh.ngoc.mai@fashion.local', '{noop}User@12345', 'Đinh Ngọc Mai', '0912345610', '95 Hùng Vương, Phường Phú Hội, Thành phố Huế, Thừa Thiên Huế', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400', true, true, now(), now())
ON CONFLICT (email) DO NOTHING;

-- 5. User Roles
INSERT INTO user_roles (user_id, role_id) VALUES
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002'),
    ('7cb10f2e-3068-4a3a-b15e-981dd47c1273', '00000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-000000000013', '00000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-000000000014', '00000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-000000000015', '00000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-000000000016', '00000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-000000000017', '00000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-000000000018', '00000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-000000000019', '00000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-000000000020', '00000000-0000-0000-0000-000000000001')
ON CONFLICT DO NOTHING;

-- 6. User Addresses (12 địa chỉ chuẩn GHN Location IDs)
INSERT INTO user_addresses (
    id, user_id, recipient_name, phone,
    province, district, ward, detail_address,
    province_id, district_id, ward_code, is_default, created_at, updated_at
) VALUES
    ('addr-01', '7cb10f2e-3068-4a3a-b15e-981dd47c1273', 'Khách Hàng Mẫu', '0901000002',
     'Hồ Chí Minh', 'Quận 1', 'Phường Bến Nghé', '123 Lê Lợi',
     202, 1442, '20101', true, now(), now()),
    ('addr-02', '00000000-0000-0000-0000-000000000011', 'Nguyễn Văn An', '0912345601',
     'Hà Nội', 'Quận Hoàn Kiếm', 'Phường Tràng Tiền', 'Số 15 Hàng Bài',
     201, 1482, '10101', true, now(), now()),
    ('addr-03', '00000000-0000-0000-0000-000000000012', 'Trần Thị Bình', '0912345602',
     'Hồ Chí Minh', 'Quận 1', 'Phường Bến Nghé', '45 Nguyễn Thị Minh Khai',
     202, 1442, '20101', true, now(), now()),
    ('addr-04', '00000000-0000-0000-0000-000000000013', 'Lê Hoàng Nam', '0912345603',
     'Đà Nẵng', 'Quận Hải Châu', 'Phường Hải Châu 1', '88 Trần Phú',
     203, 1530, '40101', true, now(), now()),
    ('addr-05', '00000000-0000-0000-0000-000000000014', 'Phạm Minh Trang', '0912345604',
     'Hà Nội', 'Quận Cầu Giấy', 'Phường Dịch Vọng', '204 Cầu Giấy',
     201, 1485, '10203', true, now(), now()),
    ('addr-06', '00000000-0000-0000-0000-000000000015', 'Vũ Đức Thắng', '0912345605',
     'Hải Phòng', 'Quận Ngô Quyền', 'Phường Lạch Tray', '12 Lạch Tray',
     204, 1560, '30102', true, now(), now()),
    ('addr-07', '00000000-0000-0000-0000-000000000016', 'Hoàng Thu Thảo', '0912345606',
     'Cần Thơ', 'Quận Ninh Kiều', 'Phường An Hòa', '56 Nguyễn Trãi',
     205, 1580, '50104', true, now(), now()),
    ('addr-08', '00000000-0000-0000-0000-000000000017', 'Đỗ Quốc Bảo', '0912345607',
     'Hồ Chí Minh', 'Quận 3', 'Phường Võ Thị Sáu', '180 Võ Thị Sáu',
     202, 1444, '20205', true, now(), now()),
    ('addr-09', '00000000-0000-0000-0000-000000000018', 'Ngô Phương Linh', '0912345608',
     'Hà Nội', 'Quận Ba Đình', 'Phường Điện Biên', '77 Nguyễn Thái Học',
     201, 1481, '10103', true, now(), now()),
    ('addr-10', '00000000-0000-0000-0000-000000000019', 'Bùi Anh Tuấn', '0912345609',
     'Hồ Chí Minh', 'Quận 7', 'Phường Tân Phú', '320 Nguyễn Văn Linh',
     202, 1450, '20701', true, now(), now()),
    ('addr-11', '00000000-0000-0000-0000-000000000020', 'Đinh Ngọc Mai', '0912345610',
     'Thừa Thiên Huế', 'Thành phố Huế', 'Phường Phú Hội', '95 Hùng Vương',
     206, 1600, '60102', true, now(), now()),
    ('addr-12', '00000000-0000-0000-0000-000000000011', 'Nguyễn Văn An (Văn phòng)', '0912345601',
     'Hà Nội', 'Quận Đống Đa', 'Phường Ô Chợ Dừa', '15 Hoàng Cầu',
     201, 1484, '10201', false, now(), now())
ON CONFLICT (id) DO NOTHING;

COMMIT;
