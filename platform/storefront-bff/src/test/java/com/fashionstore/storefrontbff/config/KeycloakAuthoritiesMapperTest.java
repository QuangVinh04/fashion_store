package com.fashionstore.storefrontbff.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakAuthoritiesMapperTest {

    @Test
    void replacesDefaultOidcAuthoritiesWithKeycloakRolesFromIdToken() {
        OidcIdToken idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(60), Map.of(
                "sub", "kc-user-1",
                "realm_access", Map.of("roles", List.of("ADMIN", "USER")),
                "resource_access", Map.of("fashion-api", Map.of("roles", List.of("product:write")))));

        Set<String> mapped = new SecurityConfig().keycloakAuthoritiesMapper()
                .mapAuthorities(List.of(new OidcUserAuthority(idToken), new SimpleGrantedAuthority("SCOPE_openid")))
                .stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        assertThat(mapped).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER", "product:write");
    }
}
