package com.fashionstore.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonUtilitiesTest {

    @Test
    void createsNormalizedSlug() {
        assertEquals("ao-so-mi-nam", SlugUtils.makeSlug("Ao so mi nam"));
    }

    @Test
    void createsSixDigitVerificationCode() {
        assertTrue(VerificationCodeGenerator.generateSixDigitCode().matches("\\d{6}"));
    }
}
