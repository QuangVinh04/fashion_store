package com.fashionstore.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SlugUtils {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private SlugUtils() {
    }

    public static String makeSlug(String input) {
        if (input == null) {
            return "";
        }
        String hyphenated = WHITESPACE.matcher(input.trim()).replaceAll("-");
        String normalized = Normalizer.normalize(hyphenated, Normalizer.Form.NFD);
        return NON_LATIN.matcher(normalized).replaceAll("")
                .toLowerCase(Locale.ENGLISH)
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
