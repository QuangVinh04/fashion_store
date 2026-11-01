package com.fashionstore.catalog.product.service.impl;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Normalizes size codes for matching and orders them from smallest to largest.
 */
final class SizeCodes {

    private static final List<String> LETTER_LADDER = List.of("XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL");
    private static final Map<String, String> LETTER_ALIASES = Map.of("2XL", "XXL", "3XL", "XXXL");
    private static final int LETTER_GROUP = 0;
    private static final int NUMERIC_GROUP = 1;
    private static final int OTHER_GROUP = 2;

    static final Comparator<String> SMALLEST_FIRST = Comparator
            .comparingInt(SizeCodes::group)
            .thenComparingInt(SizeCodes::ladderIndex)
            .thenComparing(SizeCodes::numericValue, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(SizeCodes::normalize);

    private SizeCodes() {
    }

    static String normalize(String sizeCode) {
        return sizeCode == null ? "" : sizeCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String canonical(String sizeCode) {
        String normalized = normalize(sizeCode);
        return LETTER_ALIASES.getOrDefault(normalized, normalized);
    }

    private static int ladderIndex(String sizeCode) {
        return Math.max(LETTER_LADDER.indexOf(canonical(sizeCode)), 0);
    }

    private static int group(String sizeCode) {
        if (LETTER_LADDER.contains(canonical(sizeCode))) {
            return LETTER_GROUP;
        }
        return numericValue(sizeCode) == null ? OTHER_GROUP : NUMERIC_GROUP;
    }

    private static BigDecimal numericValue(String sizeCode) {
        try {
            return new BigDecimal(normalize(sizeCode));
        } catch (NumberFormatException notNumeric) {
            return null;
        }
    }
}
