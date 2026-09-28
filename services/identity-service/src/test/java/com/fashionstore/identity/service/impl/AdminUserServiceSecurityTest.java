package com.fashionstore.identity.service.impl;

import com.fashionstore.identity.client.KeycloakAdminClient;
import com.fashionstore.identity.dto.user.AdminUpdateUserRequest;
import com.fashionstore.identity.mapper.UserAddressMapperImpl;
import com.fashionstore.identity.mapper.UserMapperImpl;
import com.fashionstore.identity.repository.UserAddressRepository;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.AdminUserService;
import com.fashionstore.identity.service.CurrentUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

/** Luật ADMIN nằm ở tầng service (@PreAuthorize) — kiểm bằng method security thật, không mock. */
@SpringJUnitConfig(AdminUserServiceSecurityTest.Config.class)
class AdminUserServiceSecurityTest {

    @Configuration
    @EnableMethodSecurity
    @Import({AdminUserServiceImpl.class, UserMapperImpl.class, UserAddressMapperImpl.class})
    static class Config {
    }

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    UserAddressRepository userAddressRepository;

    @MockitoBean
    KeycloakAdminClient keycloakAdminClient;

    @MockitoBean
    CurrentUserProvider currentUserProvider;

    @Autowired
    AdminUserService adminUserService;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customerCannotUseAnyAdminUserOperation() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("kc-customer", null, "ROLE_USER"));

        assertThatThrownBy(() -> adminUserService.searchUsers(null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> adminUserService.getUser("kc-other"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> adminUserService.updateUser("kc-other", new AdminUpdateUserRequest("X", "0912345678")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> adminUserService.updateStatus("kc-other", false))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(keycloakAdminClient, userRepository);
    }
}
