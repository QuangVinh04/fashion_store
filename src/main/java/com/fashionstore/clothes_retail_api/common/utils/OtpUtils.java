package com.fashionstore.clothes_retail_api.common.utils;

import java.util.Random;

public class OtpUtils {
    public static String generateVerifyCode() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000));  // Tạo mã 6 chữ số ngẫu nhiên
    }
}
