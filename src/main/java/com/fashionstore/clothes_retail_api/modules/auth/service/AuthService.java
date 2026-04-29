package com.fashionstore.clothes_retail_api.modules.auth.service;

import com.fashionstore.clothes_retail_api.modules.auth.dto.AuthResponse;
import com.fashionstore.clothes_retail_api.modules.auth.dto.LoginRequest;
import com.fashionstore.clothes_retail_api.modules.auth.dto.RegisterRequest;

public interface AuthService {
    void register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    void verifyEmail(String token);
    void resendVerification(String email);


}
