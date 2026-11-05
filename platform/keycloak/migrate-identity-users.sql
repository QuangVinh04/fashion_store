-- Xuất user cũ của identity_database thành payload partialImport cho realm fashion-store.
--
-- GIỮ NGUYÊN id: Keycloak dùng users.id làm sub của token, nên userId đang lưu ở order/catalog/payment
-- và bản ghi identity (hồ sơ, địa chỉ) vẫn khớp — JIT provisioning sẽ thấy user đã tồn tại.
--
-- Mật khẩu BCrypt KHÔNG được chuyển (Keycloak không có hash provider bcrypt): user phải đặt lại mật khẩu
-- (required action UPDATE_PASSWORD, hoặc admin gửi email "Update Password").
-- Bảng role cũ (user_roles, roles) đã bị drop ở V2: user import không vào nhóm nào ngoài Customer (default group),
-- admin cũ phải được gán nhóm /Admin bằng tay sau khi import.
--
-- Cách chạy: xem platform/keycloak/README.md, mục "Migrating legacy identity users".
select json_build_object(
    'ifResourceExists', 'SKIP',
    'users', coalesce(json_agg(json_build_object(
        'id', u.id,
        'username', lower(u.email),
        'email', u.email,
        'firstName', u.full_name,
        'enabled', u.is_active,
        'emailVerified', u.is_email_verified,
        'requiredActions', json_build_array('UPDATE_PASSWORD'),
        'realmRoles', json_build_array('default-roles-fashion-store'),
        'groups', '[]'::json
    )), '[]'::json)
)
from users u
where u.password is not null; -- chỉ user cũ; user tạo qua JIT đã có sẵn trong Keycloak
