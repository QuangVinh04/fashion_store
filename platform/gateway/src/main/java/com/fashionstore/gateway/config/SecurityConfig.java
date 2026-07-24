package com.fashionstore.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(authorize -> authorize
                        .pathMatchers(
                                "/actuator/health/**",
                                "/api/v1/auth/**",
                                "/api/v1/payments/vnpay/return",
                                "/api/v1/payments/vnpay/ipn"
                        ).permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/product/**", "/api/v1/category/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/files/*/content").permitAll()
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }
}
