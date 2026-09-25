package com.fashionstore.backofficebff.controller;

import com.fashionstore.backofficebff.dto.SessionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionControllerTest {

    private final SessionController controller = new SessionController();

    @Test
    void reportsMappedAuthoritiesOfTheAuthenticationNotThePrincipal() {
        OidcIdToken idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "kc-user-1", "email", "user@fashion.local"));
        // Principal giữ authority mặc định của Spring; token mang authority đã qua GrantedAuthoritiesMapper
        DefaultOidcUser principal = new DefaultOidcUser(List.of(new OidcUserAuthority(idToken)), idToken);
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "keycloak");

        SessionResponse session = controller.session(authentication).getData();

        assertThat(session.authenticated()).isTrue();
        assertThat(session.userId()).isEqualTo("kc-user-1");
        assertThat(session.email()).isEqualTo("user@fashion.local");
        assertThat(session.roles()).containsExactly("ROLE_USER");
    }

    @Test
    void reportsAnonymous() {
        assertThat(controller.session(null).getData()).isEqualTo(SessionResponse.anonymous());
    }
}
