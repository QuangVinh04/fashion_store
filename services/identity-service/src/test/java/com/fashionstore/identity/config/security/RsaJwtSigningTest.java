package com.fashionstore.identity.config.security;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RsaJwtSigningTest {

    private Path tempDirectory;

    @BeforeEach
    void createTestDirectory() throws Exception {
        tempDirectory = Path.of("target", "test-keys", UUID.randomUUID().toString());
        Files.createDirectories(tempDirectory);
    }

    @Test
    void tokenSignedWithPrivateKeyIsVerifiedWithPublicKey() throws Exception {
        SecurityConfig config = new SecurityConfig();
        RsaKeyMaterial keyMaterial = new RsaKeyPairStore().loadOrCreate(
                tempDirectory.resolve("private.pem"),
                tempDirectory.resolve("public.pem"),
                null);
        RSAKey rsaKey = config.rsaKey(keyMaterial);
        var encoder = config.jwtEncoder(rsaKey);
        var decoder = config.jwtDecoder(rsaKey, "fashion-store-test");
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("fashion-store-test")
                .subject("user-1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(keyMaterial.keyId())
                .build();

        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        var decoded = decoder.decode(token);

        assertEquals("user-1", decoded.getSubject());
        assertEquals(keyMaterial.keyId(), decoded.getHeaders().get("kid"));
    }
}
