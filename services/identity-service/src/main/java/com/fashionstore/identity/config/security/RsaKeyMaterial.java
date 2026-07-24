package com.fashionstore.identity.config.security;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

public record RsaKeyMaterial(
        RSAPublicKey publicKey,
        RSAPrivateKey privateKey,
        String keyId
) {
}
