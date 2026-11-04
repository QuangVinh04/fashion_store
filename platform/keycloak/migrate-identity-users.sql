-- Xuất user cũ của identity_database thành payload partialImport cho realm fashion-store.
--
-- GIỮ NGUYÊN id: Keycloak dùng users.id làm sub của token, nên userId đang lưu ở order/catalog/payment
-- và bản ghi identity (hồ sơ, địa chỉ) vẫn khớp — JIT provisioning sẽ thấy user đã tồn tại.
--
-- Mật khẩu BCrypt KHÔNG được chuyển (Keycloak không có hash provider bcrypt): user phải đặt lại mật khẩu
-- (required action UPDATE_PASSWORD, hoặc admin gửi email "Update Password").
-- Quyền nằm ở nhóm Keycloak, không phải client role của user: user có role cũ ADMIN vào nhóm /Admin, còn lại
-- không vào nhóm nào (Customer là default group nên user mới import tự vào). Realm phải có nhóm Admin trước khi import.
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
        'realmRoles', (
            select json_agg(role_name)
            from (
                select r.name as role_name
                from user_roles ur
                join roles r on r.id = ur.role_id
                where ur.user_id = u.id
                union
                select 'default-roles-fashion-store'
            ) realm_roles
        ),
        'groups', coalesce((
            select json_agg('/Admin'::text)
            from user_roles ur
            join roles r on r.id = ur.role_id
            where ur.user_id = u.id and r.name = 'ADMIN'
        ), '[]'::json)
    )), '[]'::json)
)
from users u
where u.password is not null; -- chỉ user cũ; user tạo qua JIT đã có sẵn trong Keycloak
