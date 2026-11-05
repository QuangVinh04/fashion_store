package com.fashionstore.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.identity.dto.group.KeycloakGroupRepresentation;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.service.SystemGroups;
import org.junit.jupiter.api.Test;

class GroupPermissionPolicyTest {

    @Test
    void rejectsCustomerGroupAsNotAssignable() {
        AppException error = catchThrowableOfType(
                () -> GroupPermissionPolicy.requireAssignable(KeycloakGroupRepresentation.named(SystemGroups.CUSTOMER)),
                AppException.class);

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.CUSTOMER_GROUP_NOT_ASSIGNABLE);
    }

    @Test
    void acceptsAdminAndCustomGroups() {
        assertThatCode(() -> {
            GroupPermissionPolicy.requireAssignable(KeycloakGroupRepresentation.named(SystemGroups.ADMIN));
            GroupPermissionPolicy.requireAssignable(KeycloakGroupRepresentation.named("Sales"));
        }).doesNotThrowAnyException();
    }
}
