package com.fashionstore.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig {

    // JWT Keycloak (iss, aud=fashion-api, JWKS) cấu hình qua spring.security.oauth2.resourceserver.jwt.*;
    // token được chuyển tiếp nguyên vẹn, mỗi service tự kiểm tra lại
    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(authorize -> authorize
                        .pathMatchers(
                                "/actuator/health/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/identity/v3/api-docs/**",
                                "/catalog/v3/api-docs/**",
                                "/order/v3/api-docs/**",
                                "/payment/v3/api-docs/**",
                                "/api/v1/payments/vnpay/return",
                                "/api/v1/payments/vnpay/ipn"
                        ).permitAll()
                        .pathMatchers("/internal/**").denyAll()
                        .pathMatchers(HttpMethod.GET,
                                "/api/v1/product/**",
                                "/api/v1/products/**",
                                "/api/v1/category/**",
                                "/api/v1/categories/**",
                                "/api/v1/brands/**",
                                "/api/v1/color-options/**",
                                "/api/v1/size-options/**",
                                "/api/v1/size-charts/**",
                                "/api/v1/wishlist/check/**"
                        ).permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/files/*/content").permitAll()
                        .pathMatchers("/api/v1/cart/**").permitAll()
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }
}
