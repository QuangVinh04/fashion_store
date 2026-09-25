package com.fashionstore.identity.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProvisioningServiceImplTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    UserProvisioningServiceImpl provisioningService;

    @Test
    void createsUserOnFirstRequestWithKeycloakSubjectAsId() {
        when(userRepository.findById("kc-1")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("new@fashion.local")).thenReturn(false);

        provisioningService.provision(jwt(Map.of(
                "sub", "kc-1", "email", "new@fashion.local", "email_verified", true, "name", "New Customer")));

        verify(userRepository).insertProvisionedUser("kc-1", "new@fashion.local", "New Customer", true);
    }

    @Test
    void fallsBackToEmailWhenTokenHasNoName() {
        when(userRepository.findById("kc-1")).thenReturn(Optional.empty());

        provisioningService.provision(jwt(Map.of("sub", "kc-1", "email", "noname@fashion.local")));

        verify(userRepository).insertProvisionedUser("kc-1", "noname@fashion.local", "noname@fashion.local", false);
    }

    @Test
    void refusesToCreateWhenEmailBelongsToAnotherUser() {
        when(userRepository.findById("kc-1")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("legacy@fashion.local")).thenReturn(true);

        assertThatThrownBy(() -> provisioningService.provision(jwt(Map.of("sub", "kc-1", "email", "legacy@fashion.local"))))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(IdentityErrorCode.ACCOUNT_LINK_CONFLICT);
        verify(userRepository, never()).insertProvisionedUser(any(), any(), any(), anyBoolean());
    }

    @Test
    void neverProvisionsServiceAccountTokensWithoutEmail() {
        when(userRepository.findById("service-account-order-service")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provisioningService.provision(jwt(Map.of("sub", "service-account-order-service"))))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(IdentityErrorCode.USER_NOT_FOUND);
        verify(userRepository, never()).insertProvisionedUser(any(), any(), any(), anyBoolean());
    }

    @Test
    void syncsEmailChangedInKeycloak() {
        User user = User.builder().email("old@fashion.local").fullName("Customer").build();
        user.setId("kc-1");
        when(userRepository.findById("kc-1")).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("new@fashion.local")).thenReturn(false);

        provisioningService.provision(jwt(Map.of("sub", "kc-1", "email", "new@fashion.local", "email_verified", true)));

        assertThat(user.getEmail()).isEqualTo("new@fashion.local");
        assertThat(user.getIsEmailVerified()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void existingUserWithSameEmailIsLeftUntouched() {
        User user = User.builder().email("same@fashion.local").fullName("Edited in profile").build();
        user.setId("kc-1");
        when(userRepository.findById("kc-1")).thenReturn(Optional.of(user));

        provisioningService.provision(jwt(Map.of("sub", "kc-1", "email", "same@fashion.local", "name", "Keycloak Name")));

        assertThat(user.getFullName()).isEqualTo("Edited in profile");
        verify(userRepository, never()).save(any());
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none");
        claims.forEach(builder::claim);
        return builder.build();
    }
}
