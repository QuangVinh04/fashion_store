package com.fashionstore.identity.config.security;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RsaKeyPairStoreTest {

    private Path tempDirectory;

    private final RsaKeyPairStore store = new RsaKeyPairStore();

    @BeforeEach
    void createTestDirectory() throws Exception {
        tempDirectory = Path.of("target", "test-keys", UUID.randomUUID().toString());
        Files.createDirectories(tempDirectory);
    }

    @Test
    void generatesAndReloadsSeparatePemKeys() throws Exception {
        Path privateKeyFile = tempDirectory.resolve("private.pem");
        Path publicKeyFile = tempDirectory.resolve("public.pem");

        RsaKeyMaterial generated = store.loadOrCreate(privateKeyFile, publicKeyFile, null);
        RsaKeyMaterial reloaded = store.loadOrCreate(privateKeyFile, publicKeyFile, null);

        assertEquals(generated.keyId(), reloaded.keyId());
        assertEquals(generated.privateKey().getModulus(), reloaded.privateKey().getModulus());
        assertEquals(generated.publicKey().getModulus(), reloaded.publicKey().getModulus());
        assertTrue(Files.readString(privateKeyFile).contains("BEGIN PRIVATE KEY"));
        assertTrue(Files.readString(publicKeyFile).contains("BEGIN PUBLIC KEY"));
        assertFalse(Files.readString(publicKeyFile).contains("PRIVATE"));
    }

    @Test
    void migratesLegacyPrivateJwkAndRemovesIt() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();
        RSAKey legacyKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("legacy-key")
                .build();
        Path legacyJwkFile = tempDirectory.resolve("identity-rsa.jwk");
        Files.writeString(legacyJwkFile, legacyKey.toJSONString(), StandardCharsets.UTF_8);

        RsaKeyMaterial migrated = store.loadOrCreate(
                tempDirectory.resolve("private.pem"),
                tempDirectory.resolve("public.pem"),
                legacyJwkFile);

        assertEquals(
                ((RSAPublicKey) keyPair.getPublic()).getModulus(),
                migrated.publicKey().getModulus());
        assertFalse(Files.exists(legacyJwkFile));
    }
}
