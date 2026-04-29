package com.fashionstore.clothes_retail_api.modules.auth.service;

import com.fashionstore.clothes_retail_api.modules.auth.entity.User;
import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;

import com.nimbusds.jose.proc.SecurityContext;
import jakarta.annotation.PostConstruct;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

@Service
@Slf4j
public class JwtService {

    @NonFinal
    @Value("${jwt.signerKey}")
    private String signerKey;

    @NonFinal
    @Value("${jwt.valid-duration}")
    private long validDuration; // seconds

    private JwtEncoder jwtEncoder;

    @PostConstruct
    public void init() {
        SecretKeySpec key = new SecretKeySpec(signerKey.getBytes(), "HmacSHA512");
        JWKSource<SecurityContext> jwkSource = new ImmutableSecret<>(key);
        this.jwtEncoder = new NimbusJwtEncoder(jwkSource);
    }
    public String generateAccessToken(User user) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getEmail())
                .claim("scope", buildScope(user))
                .claim("userId", user.getId())
                .issuedAt(Instant.now())
                .id(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusSeconds(validDuration))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS512).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    // Gom roles + permissions, space-separated (chuẩn OAuth2 scope)
    private String buildScope(User user) {
        StringJoiner joiner = new StringJoiner(" ");
        if (!CollectionUtils.isEmpty(user.getRoles())) {
            user.getRoles().forEach(role -> {
                joiner.add("ROLE_" + role.getName()); // "ROLE_ADMIN"
                role.getPermissions().forEach(permission ->
                        joiner.add(permission.getName())  // "product:write"
                );
            });
        }
        return joiner.toString();
    }
}
