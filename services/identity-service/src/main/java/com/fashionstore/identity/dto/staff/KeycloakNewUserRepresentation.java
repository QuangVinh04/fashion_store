package com.fashionstore.identity.dto.staff;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Body {@code POST /users} của Keycloak Admin API cho tài khoản nhân viên do admin tạo. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeycloakNewUserRepresentation(
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        boolean emailVerified,
        List<Credential> credentials) {

    private static final String PASSWORD_CREDENTIAL = "password";

    public record Credential(String type, String value, boolean temporary) {
    }

    /** Admin tạo nên email coi như đã xác minh; mật khẩu tạm buộc đổi ở lần đăng nhập đầu. */
    public static KeycloakNewUserRepresentation withTemporaryPassword(
            String email, String firstName, String lastName, String password) {
        Credential credential = new Credential(PASSWORD_CREDENTIAL, password, true);
        return new KeycloakNewUserRepresentation(email, email, firstName, lastName, true, true, List.of(credential));
    }
}
