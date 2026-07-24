package com.fashionstore.identity.service;

import com.fashionstore.identity.dto.AuthResponse;
import com.fashionstore.identity.dto.LoginRequest;
import com.fashionstore.identity.dto.RegisterRequest;

public interface AuthService {
    void register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    void verifyEmail(String token);
    void resendVerification(String email);


}
