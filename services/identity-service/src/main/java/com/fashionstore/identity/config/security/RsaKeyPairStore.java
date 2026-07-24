package com.fashionstore.identity.config.security;

import com.nimbusds.jose.jwk.RSAKey;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class RsaKeyPairStore {

    private static final String PRIVATE_KEY_TYPE = "PRIVATE KEY";
    private static final String PUBLIC_KEY_TYPE = "PUBLIC KEY";

    public RsaKeyMaterial loadOrCreate(
            Path privateKeyFile,
            Path publicKeyFile,
            Path legacyJwkFile
    ) {
        try {
            boolean privateKeyExists = Files.exists(privateKeyFile);
            boolean publicKeyExists = Files.exists(publicKeyFile);
            if (privateKeyExists && publicKeyExists) {
                return loadPemKeyPair(privateKeyFile, publicKeyFile);
            }
            if (privateKeyExists || publicKeyExists) {
                throw new IllegalStateException(
                        "Both JWT private and public key files must exist or both must be absent");
            }
            if (legacyJwkFile != null && Files.exists(legacyJwkFile)) {
                RsaKeyMaterial migrated = loadLegacyJwk(legacyJwkFile);
                writePemKeyPair(privateKeyFile, publicKeyFile, migrated);
                Files.delete(legacyJwkFile);
                return migrated;
            }

            RsaKeyMaterial generated = generateKeyPair();
            writePemKeyPair(privateKeyFile, publicKeyFile, generated);
            return generated;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load or create RSA signing keys", exception);
        }
    }

    private RsaKeyMaterial loadPemKeyPair(Path privateKeyFile, Path publicKeyFile) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(
                new PKCS8EncodedKeySpec(readPem(privateKeyFile, PRIVATE_KEY_TYPE)));
        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                new X509EncodedKeySpec(readPem(publicKeyFile, PUBLIC_KEY_TYPE)));
        validatePair(privateKey, publicKey);
        return new RsaKeyMaterial(publicKey, privateKey, keyId(publicKey));
    }

    private RsaKeyMaterial loadLegacyJwk(Path legacyJwkFile) throws Exception {
        RSAKey rsaKey = RSAKey.parse(Files.readString(legacyJwkFile, StandardCharsets.UTF_8));
        if (!rsaKey.isPrivate()) {
            throw new IllegalStateException("Legacy JWT JWK does not contain a private key");
        }
        RSAPublicKey publicKey = rsaKey.toRSAPublicKey();
        RSAPrivateKey privateKey = rsaKey.toRSAPrivateKey();
        validatePair(privateKey, publicKey);
        return new RsaKeyMaterial(publicKey, privateKey, keyId(publicKey));
    }

    private RsaKeyMaterial generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        return new RsaKeyMaterial(publicKey, privateKey, keyId(publicKey));
    }

    private void writePemKeyPair(
            Path privateKeyFile,
            Path publicKeyFile,
            RsaKeyMaterial keyMaterial
    ) throws Exception {
        createParentDirectory(privateKeyFile);
        createParentDirectory(publicKeyFile);
        Files.writeString(
                privateKeyFile,
                toPem(PRIVATE_KEY_TYPE, keyMaterial.privateKey().getEncoded()),
                StandardCharsets.US_ASCII);
        Files.writeString(
                publicKeyFile,
                toPem(PUBLIC_KEY_TYPE, keyMaterial.publicKey().getEncoded()),
                StandardCharsets.US_ASCII);
        setFilePermissions(privateKeyFile, publicKeyFile);
    }

    private void validatePair(RSAPrivateKey privateKey, RSAPublicKey publicKey) {
        if (!privateKey.getModulus().equals(publicKey.getModulus())) {
            throw new IllegalStateException("JWT private and public keys do not belong to the same key pair");
        }
    }

    private String keyId(RSAPublicKey publicKey) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private byte[] readPem(Path path, String type) throws Exception {
        String content = Files.readString(path, StandardCharsets.US_ASCII)
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(content);
    }

    private String toPem(String type, byte[] encoded) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n"
                + body
                + "\n-----END " + type + "-----\n";
    }

    private void createParentDirectory(Path path) throws Exception {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private void setFilePermissions(Path privateKeyFile, Path publicKeyFile) throws Exception {
        try {
            Files.setPosixFilePermissions(
                    privateKeyFile,
                    PosixFilePermissions.fromString("rw-------"));
            Files.setPosixFilePermissions(
                    publicKeyFile,
                    PosixFilePermissions.fromString("rw-r--r--"));
        } catch (UnsupportedOperationException ignored) {
            // Windows does not expose POSIX file permissions.
        }
    }
}
