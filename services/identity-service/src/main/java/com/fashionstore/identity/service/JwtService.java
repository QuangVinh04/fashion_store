package com.fashionstore.identity.service;


import com.fashionstore.identity.config.security.RsaKeyMaterial;
import com.fashionstore.identity.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final RsaKeyMaterial rsaKeyMaterial;

    @Value("${security.jwt.issuer}")
    private String issuer;

    @Value("${security.jwt.access-token-ttl-seconds:3600}")
    private long accessTokenTtlSeconds;

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Set<String> roles = new LinkedHashSet<>();
        Set<String> authorities = new LinkedHashSet<>();
        user.getRoles().forEach(role -> {
            roles.add(role.getName());
            authorities.add("ROLE_" + role.getName());
            role.getPermissions().forEach(permission -> authorities.add(permission.getName()));
        });

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId())
                .audience(List.of("fashion-api"))
                .issuedAt(now)
                .id(UUID.randomUUID().toString())
                .expiresAt(now.plusSeconds(accessTokenTtlSeconds))
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .claim("scope", String.join(" ", authorities))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(rsaKeyMaterial.keyId())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    // Gom roles + permissions, space-separated (chuẩn OAuth2 scope)
}
