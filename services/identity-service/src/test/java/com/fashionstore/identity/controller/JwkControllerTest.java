package com.fashionstore.identity.controller;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwkControllerTest {

    @Test
    void exposesOnlyPublicRsaKeyMaterial() throws Exception {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        var keyPair = keyPairGenerator.generateKeyPair();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey(keyPair.getPrivate())
                .keyID("test-key")
                .build();

        Map<String, Object> response = new JwkController(rsaKey).getJwkSet();

        List<?> keys = (List<?>) response.get("keys");
        assertEquals(1, keys.size());
        Map<?, ?> publicKey = (Map<?, ?>) keys.getFirst();
        assertEquals("RSA", publicKey.get("kty"));
        assertEquals("test-key", publicKey.get("kid"));
        assertTrue(publicKey.containsKey("n"));
        assertTrue(publicKey.containsKey("e"));
        assertFalse(publicKey.containsKey("d"));
        assertFalse(publicKey.containsKey("p"));
        assertFalse(publicKey.containsKey("q"));
    }
}
