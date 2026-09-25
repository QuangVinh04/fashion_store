-- Mật khẩu do Keycloak quản lý; user tạo qua JIT provisioning không có password trong identity.
-- Giữ cột (dữ liệu cũ phục vụ import sang Keycloak), chỉ bỏ NOT NULL.
alter table users alter column password drop not null;
