package com.fashionstore.identity.config.security;

import com.fashionstore.common.redis.RedisService;
import com.fashionstore.common.security.JwtBlacklistValidator;
import com.fashionstore.common.security.GatewayHeaderAuthenticationFilter;
import com.fashionstore.identity.service.CustomUserDetailsService;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.file.Path;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.internal.secret-token:fashion-store-internal-secret-token}")
    private String internalSecretToken;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/auth/**", "/actuator/health/**", "/error").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .addFilterBefore(new InternalTokenAuthFilter(internalSecretToken),
                        org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .addFilterBefore(new GatewayHeaderAuthenticationFilter(),
                        org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    AuthenticationProvider authenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    RsaKeyMaterial rsaKeyMaterial(
            @Value("${security.jwt.private-key-file:.data/identity-private.pem}") String privateKeyFile,
            @Value("${security.jwt.public-key-file:.data/identity-public.pem}") String publicKeyFile,
            @Value("${security.jwt.legacy-jwk-file:.data/identity-rsa.jwk}") String legacyJwkFile
    ) {
        return new RsaKeyPairStore().loadOrCreate(
                Path.of(privateKeyFile),
                Path.of(publicKeyFile),
                legacyJwkFile.isBlank() ? null : Path.of(legacyJwkFile));
    }

    @Bean
    RSAKey rsaKey(RsaKeyMaterial keyMaterial) {
        return new RSAKey.Builder(keyMaterial.publicKey())
                .privateKey(keyMaterial.privateKey())
                .keyID(keyMaterial.keyId())
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey rsaKey) {
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    JwtDecoder jwtDecoder(
            RSAKey rsaKey,
            @Value("${security.jwt.issuer}") String issuer,
            RedisService redisService
    ) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        // 1. Validator mặc định kiểm tra issuer và expiration
        OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefaultWithIssuer(issuer);
        // 2. Custom Validator kiểm tra Blacklist trên Redis
        OAuth2TokenValidator<Jwt> blacklistValidator = new JwtBlacklistValidator(redisService);
        OAuth2TokenValidator<Jwt> audienceValidator =
                new JwtClaimValidator<java.util.List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains("fashion-api"));
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(defaultValidator, audienceValidator, blacklistValidator));
        return decoder;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

}
