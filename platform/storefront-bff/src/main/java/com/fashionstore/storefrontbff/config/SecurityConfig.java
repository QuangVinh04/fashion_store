package com.fashionstore.storefrontbff.config;

import com.fashionstore.common.security.KeycloakJwtAuthoritiesConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.util.Set;

@Configuration
public class SecurityConfig {

    // Cookie không phân biệt port: tên riêng để không đè cookie của backoffice-bff trên cùng localhost
    static final String CSRF_COOKIE_NAME = "FS_STOREFRONT_XSRF";

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveClientRegistrationRepository clientRegistrations) {
        return http
                .authorizeExchange(authorize -> authorize
                        // Đăng ký / đăng nhập đi qua Keycloak, không mở API auth cũ cho browser
                        .pathMatchers("/api/v1/auth/**").denyAll()
                        // Phân quyền API do api-gateway + service đảm nhận
                        .anyExchange().permitAll())
                .oauth2Login(login -> login.authorizationRequestResolver(pkceAuthorizationRequestResolver(clientRegistrations)))
                .logout(logout -> logout.logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrations)))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        // SPA gửi nguyên giá trị cookie qua header X-XSRF-TOKEN
                        .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    @Bean
    GrantedAuthoritiesMapper keycloakAuthoritiesMapper() {
        KeycloakJwtAuthoritiesConverter converter = new KeycloakJwtAuthoritiesConverter();
        return authorities -> authorities.stream()
                .filter(OidcUserAuthority.class::isInstance)
                .map(authority -> converter.convert(((OidcUserAuthority) authority).getAttributes()))
                .findFirst()
                .orElse(Set.of());
    }

    // CsrfToken trong WebFlux là lazy: subscribe để cookie XSRF luôn được ghi cho SPA đọc
    @Bean
    WebFilter csrfCookieWebFilter() {
        return (exchange, chain) -> {
            Mono<CsrfToken> csrfToken = exchange.getAttribute(CsrfToken.class.getName());
            return csrfToken == null
                    ? chain.filter(exchange)
                    : csrfToken.then(chain.filter(exchange));
        };
    }

    private static DefaultServerOAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver(
            ReactiveClientRegistrationRepository clientRegistrations) {
        DefaultServerOAuth2AuthorizationRequestResolver resolver =
                new DefaultServerOAuth2AuthorizationRequestResolver(clientRegistrations);
        // Confidential client vẫn dùng PKCE (realm bắt buộc S256)
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }

    private static OidcClientInitiatedServerLogoutSuccessHandler oidcLogoutSuccessHandler(
            ReactiveClientRegistrationRepository clientRegistrations) {
        OidcClientInitiatedServerLogoutSuccessHandler handler =
                new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrations);
        handler.setPostLogoutRedirectUri("{baseUrl}");
        return handler;
    }

    private static CookieServerCsrfTokenRepository csrfTokenRepository() {
        CookieServerCsrfTokenRepository repository = CookieServerCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(CSRF_COOKIE_NAME);
        return repository;
    }
}
