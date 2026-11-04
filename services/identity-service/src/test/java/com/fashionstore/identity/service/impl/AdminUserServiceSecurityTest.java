package com.fashionstore.identity.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.PermissionNames;
import com.fashionstore.identity.client.KeycloakAdminClient;
import com.fashionstore.identity.client.KeycloakGroupClient;
import com.fashionstore.identity.dto.user.AdminUpdateUserRequest;
import com.fashionstore.identity.mapper.UserAddressMapperImpl;
import com.fashionstore.identity.mapper.UserMapperImpl;
import com.fashionstore.identity.repository.UserAddressRepository;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.AdminUserService;
import com.fashionstore.identity.service.CurrentUserProvider;
import com.fashionstore.identity.service.GroupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/** Luật quyền (customer:read / customer:manage) nằm ở tầng service (@PreAuthorize) — kiểm bằng method security thật, không mock. */
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

    @MockitoBean
    GroupService groupService;

    @MockitoBean
    KeycloakGroupClient groupClient;

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

    @Test
    void customerReaderCanSearchButCannotChangeStatus() {
        authenticate(PermissionNames.CUSTOMER_READ);
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        assertThat(adminUserService.searchUsers(null, null, PageRequest.of(0, 20)).getItems()).isEmpty();
        assertThatThrownBy(() -> adminUserService.updateStatus("kc-other", false))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> adminUserService.updateUser("kc-other", new AdminUpdateUserRequest("X", "0912345678")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(keycloakAdminClient);
    }

    @Test
    void roleAdminAloneIsDenied() {
        authenticate("ROLE_ADMIN");

        assertThatThrownBy(() -> adminUserService.searchUsers(null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> adminUserService.getUser("kc-other"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void customerManagerCanUpdateButCannotRead() {
        authenticate(PermissionNames.CUSTOMER_MANAGE);
        when(userRepository.findById("kc-other")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.updateUser("kc-other", new AdminUpdateUserRequest("X", "0912345678")))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> adminUserService.getUser("kc-other"))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("kc-staff", null, authority));
    }
}
