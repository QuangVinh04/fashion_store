package com.fashionstore.backofficebff.config;

import com.fashionstore.common.security.KeycloakJwtAuthoritiesConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.OidcBackChannelServerLogoutHandler;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.oidc.server.session.InMemoryReactiveOidcSessionRegistry;
import org.springframework.security.oauth2.client.oidc.server.session.ReactiveOidcSessionRegistry;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.HttpStatusServerAccessDeniedHandler;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import java.net.URI;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.util.Set;

@Configuration
public class SecurityConfig {

    // Cookie không phân biệt port: tên riêng để không đè cookie của storefront-bff trên cùng localhost
    static final String CSRF_COOKIE_NAME = "FS_BACKOFFICE_XSRF";

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveClientRegistrationRepository clientRegistrations,
            ReactiveOidcSessionRegistry oidcSessionRegistry,
            @Value("${server.reactive.session.cookie.name}") String sessionCookieName) {
        return http
                .authorizeExchange(authorize -> authorize
                        .pathMatchers("/actuator/health/**", "/bff/session", "/_next/**", "/favicon.ico", "/images/**", "/access-denied").permitAll()
                        // Đăng nhập đi qua Keycloak, không mở API auth cũ cho browser
                        .pathMatchers("/api/v1/auth/**").denyAll()
                        // Backoffice chỉ dành cho ADMIN — chặn ngay ở BFF, token không được relay
                        .anyExchange().hasRole("ADMIN"))
                .oauth2Login(login -> login.authorizationRequestResolver(pkceAuthorizationRequestResolver(clientRegistrations)))
                .logout(logout -> logout.logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrations)))
                // Keycloak huỷ phiên (admin bị khoá, đăng xuất ở nơi khác) -> xoá session BFF ngay
                .oidcLogout(oidc -> oidc
                        .oidcSessionRegistry(oidcSessionRegistry)
                        .backChannel(backChannel -> backChannel.logoutHandler(
                                backChannelLogoutHandler(oidcSessionRegistry, sessionCookieName))))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        // SPA gửi nguyên giá trị cookie qua header X-XSRF-TOKEN
                        .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .build();
    }

    // sid Keycloak -> session BFF. Trong RAM: đủ cho một instance mỗi BFF; scale ngang thì cần registry dùng chung
    @Bean
    ReactiveOidcSessionRegistry oidcSessionRegistry() {
        return new InMemoryReactiveOidcSessionRegistry();
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

    // API chưa đăng nhập -> 401 cho SPA; trang UI -> chuyển sang Keycloak
    private static ServerAuthenticationEntryPoint authenticationEntryPoint() {
        DelegatingServerAuthenticationEntryPoint entryPoint = new DelegatingServerAuthenticationEntryPoint(
                new DelegatingServerAuthenticationEntryPoint.DelegateEntry(
                        ServerWebExchangeMatchers.pathMatchers("/api/**", "/admin/**"),
                        new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)));
        entryPoint.setDefaultEntryPoint(new RedirectServerAuthenticationEntryPoint(
                "/oauth2/authorization/" + OAuth2ClientConfig.REGISTRATION_ID));
        return entryPoint;
    }

    // API từ chối quyền -> 403 cho SPA; trang UI -> chuyển sang /access-denied
    private static ServerAccessDeniedHandler accessDeniedHandler() {
        ServerAccessDeniedHandler redirectHandler = (exchange, denied) -> {
            var response = exchange.getResponse();
            response.setStatusCode(HttpStatus.FOUND);
            response.getHeaders().setLocation(URI.create("/access-denied"));
            return response.setComplete();
        };

        ServerWebExchangeMatcher apiMatcher = ServerWebExchangeMatchers.pathMatchers("/api/**", "/admin/**");
        HttpStatusServerAccessDeniedHandler forbiddenHandler = new HttpStatusServerAccessDeniedHandler(HttpStatus.FORBIDDEN);
        return (exchange, denied) -> apiMatcher.matches(exchange)
                .flatMap(matchResult -> matchResult.isMatch()
                        ? forbiddenHandler.handle(exchange, denied)
                        : redirectHandler.handle(exchange, denied));




    }

    // Mặc định handler tìm cookie SESSION; BFF đặt tên cookie riêng nên phải khai báo lại
    private static OidcBackChannelServerLogoutHandler backChannelLogoutHandler(
            ReactiveOidcSessionRegistry oidcSessionRegistry, String sessionCookieName) {
        OidcBackChannelServerLogoutHandler handler = new OidcBackChannelServerLogoutHandler(oidcSessionRegistry);
        handler.setSessionCookieName(sessionCookieName);
        return handler;
    }

    private static CookieServerCsrfTokenRepository csrfTokenRepository() {
        CookieServerCsrfTokenRepository repository = CookieServerCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(CSRF_COOKIE_NAME);
        return repository;
    }
}
