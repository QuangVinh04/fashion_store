package com.fashionstore.gateway.config;

import com.fashionstore.common.redis.RedisService;
import com.fashionstore.common.security.JwtBlacklistValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtDecoder jwtDecoder) {
        return http
                .authorizeExchange(authorize -> authorize
                        .pathMatchers(
                                "/actuator/health/**",
                                "/api/v1/payments/vnpay/return",
                                "/api/v1/payments/vnpay/ipn"
                        ).permitAll()
                        .pathMatchers("/api/v1/auth/logout").authenticated()
                        .pathMatchers("/api/v1/auth/**").permitAll()
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
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtDecoder(jwtDecoder)))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            RedisService redisService) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> blacklistValidator =
                new JwtBlacklistValidator(redisService);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains("fashion-api")),
                blacklistValidator));
        return decoder;
    }
}
