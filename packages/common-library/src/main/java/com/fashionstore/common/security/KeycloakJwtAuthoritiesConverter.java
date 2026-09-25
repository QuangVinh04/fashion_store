package com.fashionstore.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Keycloak token claims -> authorities.
 * realm_access.roles => ROLE_<role>; resource_access.fashion-api.roles => as-is (permissions).
 */
public class KeycloakJwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    public static final String API_CLIENT_ID = "fashion-api";
    // Client role của service account (client_credentials) được gọi /internal/**
    public static final String INTERNAL_CALLER = "internal-caller";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        return convert(jwt.getClaims());
    }

    public Collection<GrantedAuthority> convert(Map<String, Object> claims) {
        Stream<String> realmRoles = roles(claims.get("realm_access")).map(role -> "ROLE_" + role);
        Stream<String> apiRoles = claims.get("resource_access") instanceof Map<?, ?> resourceAccess
                ? roles(resourceAccess.get(API_CLIENT_ID))
                : Stream.empty();
        return Stream.concat(realmRoles, apiRoles)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Stream<String> roles(Object access) {
        if (access instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> roles) {
            return roles.stream().map(Object::toString);
        }
        return Stream.empty();
    }
}
