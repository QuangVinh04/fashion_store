package com.fashionstore.product.util;

import com.fashionstore.common.util.SlugUtils;

public class StringUtils {

    public static String normalizeCode(String value) {
        String cleaned = cleanText(value);
        return cleaned == null ? null : cleaned.replaceAll("\\s+", "_").toUpperCase();
    }

    public static String cleanText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String normalizeSlug(String slug, String fallbackName) {
        String value = slug == null || slug.isBlank() ? fallbackName : slug;
        return SlugUtils.makeSlug(value);
    }
}
