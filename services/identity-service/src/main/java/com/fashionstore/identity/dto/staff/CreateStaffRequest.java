package com.fashionstore.identity.dto.staff;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

/** Keycloak 26 user profile yêu cầu cả tên lẫn họ nên tách {@code firstName} / {@code lastName}. */
public record CreateStaffRequest(
        @NotBlank @Email String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String temporaryPassword,
        @NotEmpty Set<String> groupIds) {

    // Record tự sinh toString in mọi component; ghi đè để mật khẩu không lọt vào log.
    @Override
    public String toString() {
        return "CreateStaffRequest[email=" + email + ", groupIds=" + groupIds + "]";
    }
}
