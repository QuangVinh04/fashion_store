package com.fashionstore.identity.service;

import org.springframework.security.oauth2.jwt.Jwt;

public interface UserProvisioningService {

    /**
     * Đảm bảo user của token Keycloak tồn tại trong identity (id = sub) và email khớp với Keycloak.
     */
    void provision(Jwt jwt);

    /**
     * Như {@link #provision(Jwt)} nhưng từ dữ liệu thô (event của Keycloak, chưa có token).
     * Idempotent: chạy lại, hoặc chạy sau khi request đầu tiên của user đã tạo dòng, không đổi gì thêm.
     *
     * @param name tên hiển thị; rỗng thì dùng email
     */
    void provision(String subject, String email, String name, boolean emailVerified);
}
