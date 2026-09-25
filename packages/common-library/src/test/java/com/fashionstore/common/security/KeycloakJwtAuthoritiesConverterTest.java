package com.fashionstore.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycloakJwtAuthoritiesConverterTest {

    private final KeycloakJwtAuthoritiesConverter converter = new KeycloakJwtAuthoritiesConverter();

    @Test
    void mapsRealmRolesWithPrefixAndApiClientRolesAsIs() {
        Jwt jwt = jwt(Map.of(
                "sub", "user-1",
                "realm_access", Map.of("roles", List.of("ADMIN", "USER")),
                "resource_access", Map.of(
                        "fashion-api", Map.of("roles", List.of("product:write")),
                        "account", Map.of("roles", List.of("manage-account")))));

        assertEquals(Set.of("ROLE_ADMIN", "ROLE_USER", "product:write"), names(converter.convert(jwt)));
    }

    @Test
    void returnsEmptyWhenRoleClaimsAreMissing() {
        assertTrue(converter.convert(jwt(Map.of("sub", "user-1"))).isEmpty());
    }

    @Test
    void readsServiceAccountClientRolesWithoutRealmAccess() {
        Map<String, Object> claims = Map.of(
                "sub", "svc",
                "resource_access", Map.of("fashion-api", Map.of("roles", List.of("internal-caller"))));

        assertEquals(Set.of("internal-caller"), names(converter.convert(claims)));
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
    }

    private static Set<String> names(Collection<GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}
