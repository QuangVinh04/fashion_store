package com.fashionstore.clothes_retail_api.modules.auth.service;

import org.springframework.web.multipart.MultipartFile;

public interface EmailService {
    void sendVerificationEmail(String to, String fullName, String token);

}
