package com.fashionstore.common.util;

import java.security.SecureRandom;

public final class VerificationCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private VerificationCodeGenerator() {
    }

    public static String generateSixDigitCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
