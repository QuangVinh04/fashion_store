package com.fashionstore.identity.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserProviderTest {

    @Mock
    UserRepository userRepository;

    @Mock
    UserProvisioningService userProvisioningService;

    @InjectMocks
    CurrentUserProvider currentUserProvider;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void provisionsThenLoadsUserByKeycloakSubject() {
        Jwt jwt = authenticate("kc-1");
        User user = user("kc-1");
        when(userRepository.findById("kc-1")).thenReturn(Optional.of(user));

        assertThat(currentUserProvider.getCurrentUser()).isSameAs(user);
        verify(userProvisioningService).provision(jwt);
    }

    @Test
    void concurrentFirstRequestStillLoadsTheUserCreatedByTheOtherRequest() {
        Jwt jwt = authenticate("kc-1");
        doThrow(new DataIntegrityViolationException("duplicate key")).when(userProvisioningService).provision(jwt);
        when(userRepository.findById("kc-1")).thenReturn(Optional.of(user("kc-1")));

        assertThat(currentUserProvider.getCurrentUserId()).isEqualTo("kc-1");
    }

    @Test
    void rejectsNonJwtAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", null, List.of()));

        assertThatThrownBy(() -> currentUserProvider.getCurrentUser()).isInstanceOf(AppException.class);
    }

    private static Jwt authenticate(String subject) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).claim("email", "a@b.c").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        return jwt;
    }

    private static User user(String id) {
        User user = User.builder().email("a@b.c").fullName("A").build();
        user.setId(id);
        return user;
    }
}
