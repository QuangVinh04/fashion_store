package com.fashionstore.clothes_retail_api.common.utils;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;


public class StringUtils {
    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");

    public static String makeSlug(String input) {
        if (input == null) return "";

        // 1. Chuyển khoảng trắng thành dấu gạch ngang
        String nowhitespace = WHITESPACE.matcher(input).replaceAll("-");

        // 2. Loại bỏ dấu tiếng Việt (ví dụ: á -> a, đ -> d)
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");

        // 3. Chuyển về chữ thường, bỏ gạch ngang thừa ở đầu/cuối
        return slug.toLowerCase(Locale.ENGLISH)
                .replaceAll("-+", "-")      // Loại bỏ nhiều dấu gạch ngang liên tiếp
                .replaceAll("^-|-$", "");   // Loại bỏ dấu gạch ngang ở đầu và cuối
    }
}
