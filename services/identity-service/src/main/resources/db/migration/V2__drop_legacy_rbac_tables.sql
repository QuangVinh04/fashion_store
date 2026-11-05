-- RBAC cũ đã chuyển sang Keycloak group + client role của fashion-api; identity không còn đọc các bảng này.
DROP TABLE public.role_permissions;
DROP TABLE public.user_roles;
DROP TABLE public.roles;
DROP TABLE public.permissions;
