package com.fashionstore.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommonUtilitiesTest {

    @Test
    void createsNormalizedSlug() {
        assertEquals("ao-so-mi-nam", SlugUtils.makeSlug("Ao so mi nam"));
    }
}
